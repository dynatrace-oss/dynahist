/*
 * Copyright 2020-2021 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dynatrace.dynahist;

import com.datadoghq.sketch.ddsketch.DDSketch;
import com.datadoghq.sketch.ddsketch.mapping.CubicallyInterpolatedMapping;
import com.datadoghq.sketch.ddsketch.mapping.LinearlyInterpolatedMapping;
import com.datadoghq.sketch.ddsketch.mapping.LogarithmicMapping;
import com.datadoghq.sketch.ddsketch.mapping.QuadraticallyInterpolatedMapping;
import com.datadoghq.sketch.ddsketch.store.PaginatedStore;
import com.datadoghq.sketch.ddsketch.store.SparseStore;
import com.datadoghq.sketch.ddsketch.store.UnboundedSizeDenseStore;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogOptimalLayout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import com.dynatrace.dynahist.serialization.SerializationUtil;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import org.HdrHistogram.DoubleHistogram;
import org.openjdk.jol.info.GraphLayout;

public class SpaceConsumptionBenchmark {
  private static final long RANGE = 1_000_000_000;
  private static final long MIN = 1000;
  private static final long MAX = MIN * RANGE;
  private static final int PRECISION_DIGITS = 2;
  private static final double PRECISION = Math.pow(10., -PRECISION_DIGITS);
  private static final double DD_SKETCH_RELATIVE_ACCURACY =
      PRECISION * 0.5; // parameter for DDSketch to have comparable relative bin widths
  private static final int NUM_VALUES = 1_000_000;
  private static final long INCREMENT = 1;
  private static final double ABSOLUTE_ERROR = MIN * PRECISION;
  private static final double BYTES_TO_KILO_BYTES = 1. / 1024.;

  private static final int NUM_ITERATIONS = 100;

  private interface Result {
    double getJolMemoryFootprint();

    double getEstimatedMemoryFootprint();

    double getRawSerializedSize();

    double getCompressedSerializedSize();
  }

  private abstract static class Test {
    private String description;
    private DoubleSummaryStatistics jolMemoryFootprints = new DoubleSummaryStatistics();
    private DoubleSummaryStatistics estimatedMemoryFootprints = new DoubleSummaryStatistics();
    private DoubleSummaryStatistics rawSerializedSizes = new DoubleSummaryStatistics();
    private DoubleSummaryStatistics compressedSerializedSizes = new DoubleSummaryStatistics();

    protected Test(String description) {
      this.description = description;
    }

    public void add(double[] values) {
      try {
        Result result = test(values);
        jolMemoryFootprints.accept(result.getJolMemoryFootprint());
        estimatedMemoryFootprints.accept(result.getEstimatedMemoryFootprint());
        rawSerializedSizes.accept(result.getRawSerializedSize());
        compressedSerializedSizes.accept(result.getCompressedSerializedSize());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    protected abstract Result test(double[] values) throws IOException;

    public String getDescription() {
      return description;
    }

    public double getAvgJolMemoryFootprintKB() {
      return jolMemoryFootprints.getAverage() * BYTES_TO_KILO_BYTES;
    }

    public double getAvgEstimatedMemoryFootprintKB() {
      return estimatedMemoryFootprints.getAverage() * BYTES_TO_KILO_BYTES;
    }

    public double getAvgSerializedSizesKB() {
      return rawSerializedSizes.getAverage() * BYTES_TO_KILO_BYTES;
    }

    public double getAvgCompressedSerializedSizeKB() {
      return compressedSerializedSizes.getAverage() * BYTES_TO_KILO_BYTES;
    }
  }

  private static final class HdrDoubleHistogramTest extends Test {

    public HdrDoubleHistogramTest() {
      super("HdrHistogram.DoubleHistogram");
    }

    private byte[] writeHdr(DoubleHistogram histogram) {
      ByteBuffer byteBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
      histogram.encodeIntoByteBuffer(byteBuffer);
      byte[] serializedHdrHistogram = new byte[byteBuffer.position()];
      byteBuffer.get(serializedHdrHistogram);
      return serializedHdrHistogram;
    }

    private byte[] writeHdrCompressed(DoubleHistogram histogram) {
      ByteBuffer byteBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
      histogram.encodeIntoCompressedByteBuffer(byteBuffer);
      byte[] serializedHdrHistogram = new byte[byteBuffer.position()];
      byteBuffer.get(serializedHdrHistogram);
      return serializedHdrHistogram;
    }

    @Override
    protected Result test(double[] values) {
      DoubleHistogram histogram = new DoubleHistogram(MAX / MIN, PRECISION_DIGITS);

      for (double v : values) {
        histogram.recordValueWithCount(v, INCREMENT);
      }
      long jolMemoryFootprint = GraphLayout.parseInstance(histogram).totalSize();
      long estimatedFootprint = histogram.getEstimatedFootprintInBytes();
      long serializedSize = writeHdr(histogram).length;
      long compressedSize = writeHdrCompressed(histogram).length;
      return new Result() {
        @Override
        public double getJolMemoryFootprint() {
          return jolMemoryFootprint;
        }

        @Override
        public double getEstimatedMemoryFootprint() {
          return estimatedFootprint;
        }

        @Override
        public double getRawSerializedSize() {
          return serializedSize;
        }

        @Override
        public double getCompressedSerializedSize() {
          return compressedSize;
        }
      };
    }
  }

  private static final class DynaHistTest extends Test {
    private final Supplier<Histogram> histogramSupplier;

    public DynaHistTest(Supplier<Histogram> histogramSupplier, String description) {
      super(description);
      this.histogramSupplier = histogramSupplier;
    }

    @Override
    protected Result test(double[] values) throws IOException {
      Histogram histogram = histogramSupplier.get();
      for (double v : values) {
        histogram.addValue(v, INCREMENT);
      }
      long jolMemoryFootprint = GraphLayout.parseInstance(histogram).totalSize();
      long estimatedFootprint = histogram.getEstimatedFootprintInBytes();
      long serializedSize = SerializationUtil.write(histogram).length;
      long compressedSize = SerializationUtil.writeCompressed(histogram).length;
      return new Result() {
        @Override
        public double getJolMemoryFootprint() {
          return jolMemoryFootprint;
        }

        @Override
        public double getEstimatedMemoryFootprint() {
          return estimatedFootprint;
        }

        @Override
        public double getRawSerializedSize() {
          return serializedSize;
        }

        @Override
        public double getCompressedSerializedSize() {
          return compressedSize;
        }
      };
    }
  }

  private static final class DDSketchTest extends Test {
    private final Supplier<DDSketch> ddSketchSupplier;

    public DDSketchTest(Supplier<DDSketch> ddSketchSupplier, String description) {
      super(description);
      this.ddSketchSupplier = ddSketchSupplier;
    }

    @Override
    protected Result test(double[] values) throws IOException {
      DDSketch histogram = ddSketchSupplier.get();
      for (double v : values) {
        histogram.accept(v, INCREMENT);
      }
      long jolMemoryFootprint = GraphLayout.parseInstance(histogram).totalSize();
      double estimatedFootprint = Double.NaN;
      long serializedSize = histogram.serializedSize();
      double compressedSize;
      {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
          Deflater deflater = new Deflater();
          deflater.setInput(histogram.serialize().array());
          deflater.finish();
          byte[] buffer = new byte[1024];
          while (!deflater.finished()) {
            outputStream.write(buffer, 0, deflater.deflate(buffer));
          }
          compressedSize = outputStream.toByteArray().length;
        }
      }

      return new Result() {
        @Override
        public double getJolMemoryFootprint() {
          return jolMemoryFootprint;
        }

        @Override
        public double getEstimatedMemoryFootprint() {
          return estimatedFootprint;
        }

        @Override
        public double getRawSerializedSize() {
          return serializedSize;
        }

        @Override
        public double getCompressedSerializedSize() {
          return compressedSize;
        }
      };
    }
  }

  public static void main(String[] args) throws IOException {

    final Random random = new Random(0);
    Supplier<double[]> randomValueSupplier =
        () -> {
          final double[] values = new double[NUM_VALUES];
          for (int i = 0; i < NUM_VALUES; ++i) {
            // assume a log-uniform (reciprocal) distribution
            // see https://en.wikipedia.org/wiki/Reciprocal_distribution
            values[i] = MIN * Math.pow(RANGE, random.nextDouble());
          }
          return values;
        };

    List<Test> tests = new ArrayList<>();
    tests.add(new HdrDoubleHistogramTest());

    tests.add(
        new DynaHistTest(
            () -> Histogram.createStatic(LogLinearLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (static, log-linear)"));
    tests.add(
        new DynaHistTest(
            () ->
                Histogram.createStatic(
                    LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (static, log-quadratic)"));
    tests.add(
        new DynaHistTest(
            () ->
                Histogram.createStatic(LogOptimalLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (static, log-optimal)"));
    tests.add(
        new DynaHistTest(
            () ->
                Histogram.createDynamic(LogLinearLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (dynamic, log-linear)"));
    tests.add(
        new DynaHistTest(
            () ->
                Histogram.createDynamic(
                    LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (dynamic, log-quadratic)"));
    tests.add(
        new DynaHistTest(
            () ->
                Histogram.createDynamic(LogOptimalLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (dynamic, log-optimal)"));

    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), PaginatedStore::new),
            "DDSketch (paginated, log)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    PaginatedStore::new),
            "DDSketch (paginated, cubic)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    PaginatedStore::new),
            "DDSketch (paginated, quadratic)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    PaginatedStore::new),
            "DDSketch (paginated, linear)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    UnboundedSizeDenseStore::new),
            "DDSketch (unbounded-dense, log)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    UnboundedSizeDenseStore::new),
            "DDSketch (unbounded-dense, cubic)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    UnboundedSizeDenseStore::new),
            "DDSketch (unbounded-dense, quadratic)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    UnboundedSizeDenseStore::new),
            "DDSketch (unbounded-dense, linear)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), SparseStore::new),
            "DDSketch (sparse, log)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    SparseStore::new),
            "DDSketch (sparse, cubic)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    SparseStore::new),
            "DDSketch (sparse, quadratic)"));
    tests.add(
        new DDSketchTest(
            () ->
                new DDSketch(
                    new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY), SparseStore::new),
            "DDSketch (sparse, linear)"));

    for (int iteration = 0; iteration < NUM_ITERATIONS; ++iteration) {
      double[] data = randomValueSupplier.get();
      for (Test test : tests) {
        test.add(data);
      }
    }

    try (FileWriter writer = new FileWriter("charts/memory-footprint-estimated.txt")) {

      for (Test test : tests) {
        writer.write(
            String.format(
                "%s;%s\n", test.getDescription(), test.getAvgEstimatedMemoryFootprintKB()));
      }
    }

    try (FileWriter writer = new FileWriter("charts/memory-footprint-jol.txt")) {
      for (Test test : tests) {
        writer.write(
            String.format("%s;%s\n", test.getDescription(), test.getAvgJolMemoryFootprintKB()));
      }
    }

    try (FileWriter writer = new FileWriter("charts/serialization-size-raw.txt")) {
      for (Test test : tests) {
        writer.write(
            String.format("%s;%s\n", test.getDescription(), test.getAvgSerializedSizesKB()));
      }
    }

    try (FileWriter writer = new FileWriter("charts/serialization-size-compressed.txt")) {
      for (Test test : tests) {
        writer.write(
            String.format(
                "%s;%s\n", test.getDescription(), test.getAvgCompressedSerializedSizeKB()));
      }
    }
  }
}
