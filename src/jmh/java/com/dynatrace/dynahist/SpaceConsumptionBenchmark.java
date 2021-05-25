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

import static com.dynatrace.dynahist.Constants.*;
import static java.util.stream.Collectors.toList;

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
import com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout;
import com.dynatrace.dynahist.serialization.SerializationUtil;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import java.util.zip.Deflater;
import org.HdrHistogram.DoubleHistogram;
import org.openjdk.jol.info.GraphLayout;

public class SpaceConsumptionBenchmark {
  private static final long[] TEST_SIZES = generateTestSizes(1_000_000, 0.01);
  private static final int NUM_ITERATIONS = 100;

  private static long[] generateTestSizes(long maxTestSize, double relativeIncrement) {
    List<Long> sizes = new ArrayList<>();
    double size = maxTestSize;
    long sizeL = maxTestSize;
    sizes.add(maxTestSize);
    do {
      size /= (1. + relativeIncrement);
      sizeL = (long) size;
      sizes.add(sizeL);
    } while (sizeL > 0L);
    return sizes.stream().mapToLong(Long::longValue).sorted().distinct().toArray();
  }

  private interface Result {
    double getJolMemoryFootprint();

    double getEstimatedMemoryFootprint();

    double getRawSerializedSize();

    double getCompressedSerializedSize();
  }

  protected abstract static class TestConfiguration<H> {
    private final String description;

    protected TestConfiguration(String description) {
      this.description = description;
    }

    protected abstract H create();

    protected abstract void add(H histogram, double value);

    protected abstract double getEstimatedFootPrint(H histogram);

    protected abstract double getCompressedSerializedSize(H histogram) throws IOException;

    protected abstract double getRawSerializedSize(H histogram) throws IOException;

    protected final String getDescription() {
      return description;
    }
  }

  private interface TestResult {
    long[] getTestSizes();

    double[] getAvgJolMemoryFootprintsInBytes();

    double[] getAvgEstimatedMemoryFootprintsInBytes();

    double[] getAvgRawSerializedSizesInBytes();

    double[] getAvgCompressedSerializedSizesInBytes();

    String getDescription();
  }

  private static <H> void test(
      TestConfiguration<H> testConfiguration, Consumer<TestResult> testResultConsumer) {

    List<DoubleSummaryStatistics> jolMemoryFootprints =
        Stream.generate(DoubleSummaryStatistics::new).limit(TEST_SIZES.length).collect(toList());
    List<DoubleSummaryStatistics> estimatedMemoryFootprints =
        Stream.generate(DoubleSummaryStatistics::new).limit(TEST_SIZES.length).collect(toList());
    List<DoubleSummaryStatistics> rawSerializedSizes =
        Stream.generate(DoubleSummaryStatistics::new).limit(TEST_SIZES.length).collect(toList());
    List<DoubleSummaryStatistics> compressedSerializedSizes =
        Stream.generate(DoubleSummaryStatistics::new).limit(TEST_SIZES.length).collect(toList());

    final Random random = new Random(0);
    for (int i = 0; i < NUM_ITERATIONS; ++i) {
      long size = 0;
      H histogram = testConfiguration.create();
      for (int testSizeIndex = 0; testSizeIndex < TEST_SIZES.length; testSizeIndex += 1) {
        long testSize = TEST_SIZES[testSizeIndex];
        while (size < testSize) {
          double value = MIN * Math.pow(RANGE, random.nextDouble());
          testConfiguration.add(histogram, value);
          size += 1;
        }
        jolMemoryFootprints
            .get(testSizeIndex)
            .accept(GraphLayout.parseInstance(histogram).totalSize());
        estimatedMemoryFootprints
            .get(testSizeIndex)
            .accept(testConfiguration.getEstimatedFootPrint(histogram));
        try {
          rawSerializedSizes
              .get(testSizeIndex)
              .accept(testConfiguration.getRawSerializedSize(histogram));
          compressedSerializedSizes
              .get(testSizeIndex)
              .accept(testConfiguration.getCompressedSerializedSize(histogram));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    ToDoubleFunction<DoubleSummaryStatistics> toAvgInBytes = DoubleSummaryStatistics::getAverage;

    double[] avgJolMemoryFootprintsInBytes =
        jolMemoryFootprints.stream().mapToDouble(toAvgInBytes).toArray();
    double[] avgEstimatedMemoryFootprintsInBytes =
        estimatedMemoryFootprints.stream().mapToDouble(toAvgInBytes).toArray();
    double[] avgRawSerializedSizesInBytes =
        rawSerializedSizes.stream().mapToDouble(toAvgInBytes).toArray();
    double[] avgCompressedSerializedSizesInBytes =
        compressedSerializedSizes.stream().mapToDouble(toAvgInBytes).toArray();
    String description = testConfiguration.getDescription();
    testResultConsumer.accept(
        new TestResult() {

          @Override
          public long[] getTestSizes() {
            return TEST_SIZES;
          }

          @Override
          public double[] getAvgJolMemoryFootprintsInBytes() {
            return avgJolMemoryFootprintsInBytes;
          }

          @Override
          public double[] getAvgEstimatedMemoryFootprintsInBytes() {
            return avgEstimatedMemoryFootprintsInBytes;
          }

          @Override
          public double[] getAvgRawSerializedSizesInBytes() {
            return avgRawSerializedSizesInBytes;
          }

          @Override
          public double[] getAvgCompressedSerializedSizesInBytes() {
            return avgCompressedSerializedSizesInBytes;
          }

          @Override
          public String getDescription() {
            return description;
          }
        });
  }

  private static final class HdrDoubleHistogramTestConfiguration
      extends TestConfiguration<DoubleHistogram> {

    public HdrDoubleHistogramTestConfiguration() {
      super("HdrHistogram.DoubleHistogram");
    }

    @Override
    protected DoubleHistogram create() {
      return new DoubleHistogram(MAX / MIN, PRECISION_DIGITS);
    }

    @Override
    protected void add(DoubleHistogram histogram, double value) {
      histogram.recordValue(value);
    }

    @Override
    protected double getEstimatedFootPrint(DoubleHistogram histogram) {
      return histogram.getEstimatedFootprintInBytes();
    }

    @Override
    protected double getCompressedSerializedSize(DoubleHistogram histogram) throws IOException {
      DoubleHistogram histogramCopy =
          histogram.copy(); // use copy, because serialization blows up the memory footprint
      ByteBuffer byteBuffer = ByteBuffer.allocate(histogramCopy.getNeededByteBufferCapacity());
      histogramCopy.encodeIntoCompressedByteBuffer(byteBuffer);
      return byteBuffer.position();
    }

    @Override
    protected double getRawSerializedSize(DoubleHistogram histogram) throws IOException {
      DoubleHistogram histogramCopy =
          histogram.copy(); // use copy, because serialization blows up the memory footprint
      ByteBuffer byteBuffer = ByteBuffer.allocate(histogramCopy.getNeededByteBufferCapacity());
      histogramCopy.encodeIntoByteBuffer(byteBuffer);
      return byteBuffer.position();
    }
  }

  private static final class DynaHistTestConfiguration extends TestConfiguration<Histogram> {
    private final Supplier<Histogram> histogramSupplier;

    public DynaHistTestConfiguration(Supplier<Histogram> histogramSupplier, String description) {
      super(description);
      this.histogramSupplier = histogramSupplier;
    }

    @Override
    protected Histogram create() {
      return histogramSupplier.get();
    }

    @Override
    protected void add(Histogram histogram, double value) {
      histogram.addValue(value);
    }

    @Override
    protected double getEstimatedFootPrint(Histogram histogram) {
      return histogram.getEstimatedFootprintInBytes();
    }

    @Override
    protected double getCompressedSerializedSize(Histogram histogram) throws IOException {
      return SerializationUtil.writeCompressed(histogram).length;
    }

    @Override
    protected double getRawSerializedSize(Histogram histogram) throws IOException {
      return SerializationUtil.write(histogram).length;
    }
  }

  private static final class DDSketchTestConfiguration extends TestConfiguration<DDSketch> {
    private final Supplier<DDSketch> ddSketchSupplier;

    public DDSketchTestConfiguration(Supplier<DDSketch> ddSketchSupplier, String description) {
      super(description);
      this.ddSketchSupplier = ddSketchSupplier;
    }

    @Override
    protected DDSketch create() {
      return ddSketchSupplier.get();
    }

    @Override
    protected void add(DDSketch histogram, double value) {
      histogram.accept(value);
    }

    @Override
    protected double getEstimatedFootPrint(DDSketch histogram) {
      return Double.NaN;
    }

    @Override
    protected double getCompressedSerializedSize(DDSketch histogram) throws IOException {
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        Deflater deflater = new Deflater();
        deflater.setInput(histogram.serialize().array());
        deflater.finish();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
          outputStream.write(buffer, 0, deflater.deflate(buffer));
        }
        return outputStream.toByteArray().length;
      }
    }

    @Override
    protected double getRawSerializedSize(DDSketch histogram) throws IOException {
      return histogram.serializedSize();
    }
  }

  private static void writeResults(
      List<TestResult> testResults, Function<TestResult, double[]> accessor, String fileName) {
    try (FileWriter writer = new FileWriter(fileName)) {
      writer.write(
          LongStream.of(TEST_SIZES)
              .mapToObj(Long::toString)
              .collect(Collectors.joining(";", "Description;", "\n")));
      for (TestResult testResult : testResults) {
        writer.write(
            DoubleStream.of(accessor.apply(testResult))
                .mapToObj(Double::toString)
                .collect(Collectors.joining(";", testResult.getDescription() + ";", "\n")));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {

    List<TestConfiguration> testConfigurations = new ArrayList<>();

    testConfigurations.add(new HdrDoubleHistogramTestConfiguration());
    testConfigurations.add(
        new DynaHistTestConfiguration(
            () -> Histogram.createStatic(LogLinearLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (static, log-linear)"));
    testConfigurations.add(
        new DynaHistTestConfiguration(
            () ->
                Histogram.createStatic(
                    LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (static, log-quadratic)"));
    testConfigurations.add(
        new DynaHistTestConfiguration(
            () ->
                Histogram.createStatic(LogOptimalLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (static, log-optimal)"));
    testConfigurations.add(
        new DynaHistTestConfiguration(
            () ->
                Histogram.createStatic(
                    OpenTelemetryExponentialBucketsLayout.create(EXP_BUCKET_PRECISION)),
            "DynaHist (static, otel-exp-buckets)"));
    testConfigurations.add(
        new DynaHistTestConfiguration(
            () ->
                Histogram.createDynamic(LogLinearLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (dynamic, log-linear)"));
    testConfigurations.add(
        new DynaHistTestConfiguration(
            () ->
                Histogram.createDynamic(
                    LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (dynamic, log-quadratic)"));
    testConfigurations.add(
        new DynaHistTestConfiguration(
            () ->
                Histogram.createDynamic(LogOptimalLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX)),
            "DynaHist (dynamic, log-optimal)"));
    testConfigurations.add(
        new DynaHistTestConfiguration(
            () ->
                Histogram.createDynamic(
                    OpenTelemetryExponentialBucketsLayout.create(EXP_BUCKET_PRECISION)),
            "DynaHist (dynamic, otel-exp-buckets)"));

    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), PaginatedStore::new),
            "DDSketch (paginated, log)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    PaginatedStore::new),
            "DDSketch (paginated, cubic)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    PaginatedStore::new),
            "DDSketch (paginated, quadratic)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    PaginatedStore::new),
            "DDSketch (paginated, linear)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    UnboundedSizeDenseStore::new),
            "DDSketch (unbounded-dense, log)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    UnboundedSizeDenseStore::new),
            "DDSketch (unbounded-dense, cubic)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    UnboundedSizeDenseStore::new),
            "DDSketch (unbounded-dense, quadratic)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    UnboundedSizeDenseStore::new),
            "DDSketch (unbounded-dense, linear)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), SparseStore::new),
            "DDSketch (sparse, log)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    SparseStore::new),
            "DDSketch (sparse, cubic)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
                    SparseStore::new),
            "DDSketch (sparse, quadratic)"));
    testConfigurations.add(
        new DDSketchTestConfiguration(
            () ->
                new DDSketch(
                    new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY), SparseStore::new),
            "DDSketch (sparse, linear)"));

    List<TestResult> testResults = Arrays.asList(new TestResult[testConfigurations.size()]);

    IntStream.range(0, testConfigurations.size())
        .parallel()
        .forEach(i -> test(testConfigurations.get(i), r -> testResults.set(i, r)));

    writeResults(
        testResults,
        TestResult::getAvgEstimatedMemoryFootprintsInBytes,
        "charts/memory-footprint-estimated.txt");
    writeResults(
        testResults,
        TestResult::getAvgJolMemoryFootprintsInBytes,
        "charts/memory-footprint-jol.txt");
    writeResults(
        testResults,
        TestResult::getAvgRawSerializedSizesInBytes,
        "charts/serialization-size-raw.txt");
    writeResults(
        testResults,
        TestResult::getAvgCompressedSerializedSizesInBytes,
        "charts/serialization-size-compressed.txt");
  }
}
