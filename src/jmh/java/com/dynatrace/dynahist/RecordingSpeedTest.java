/*
 * Copyright 2021 Dynatrace LLC
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
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.HdrHistogram.DoubleHistogram;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;

public class RecordingSpeedTest {
  private static final long RANGE = 1_000_000_000;
  private static final long MIN = 1000;
  private static final long MAX = MIN * RANGE;
  private static final int PRECISION_DIGITS = 2;
  private static final double PRECISION = Math.pow(10., -PRECISION_DIGITS);
  private static final double DD_SKETCH_RELATIVE_ACCURACY =
      PRECISION * 0.5; // parameter for DDSketch to have comparable relative bin widths
  private static final int NUM_VALUES = 1_000_000;
  private static final int NUM_TEST_DATA_SETS = 100;
  private static final long INCREMENT = 1;
  private static final double ABSOLUTE_ERROR = MIN * PRECISION;
  private static final double[][] TEST_DATA_DOUBLE = new double[NUM_TEST_DATA_SETS][];
  private static final long[][] TEST_DATA_LONG = new long[NUM_TEST_DATA_SETS][];

  static {
    final Random random = new Random(0);
    for (int j = 0; j < NUM_TEST_DATA_SETS; ++j) {
      double[] dataDouble = new double[NUM_VALUES];
      long[] dataLong = new long[NUM_VALUES];
      for (int i = 0; i < NUM_VALUES; ++i) {
        dataDouble[i] = MIN * Math.pow(RANGE, random.nextDouble());
        dataLong[i] = (long) dataDouble[i];
      }
      TEST_DATA_DOUBLE[j] = dataDouble;
      TEST_DATA_LONG[j] = dataLong;
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordHdrLongHistogram(Blackhole blackhole) {
    long[] testData = TEST_DATA_LONG[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    org.HdrHistogram.Histogram histogram =
        new org.HdrHistogram.Histogram(MIN, MAX, PRECISION_DIGITS);
    for (int i = 0; i < NUM_VALUES; ++i) {
      histogram.recordValueWithCount(testData[i], INCREMENT);
    }
    blackhole.consume(histogram.getTotalCount());
    blackhole.consume(histogram.getMinValue());
    blackhole.consume(histogram.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordHdrDoubleHistogram(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DoubleHistogram histogram = new DoubleHistogram(RANGE, PRECISION_DIGITS);
    for (int i = 0; i < NUM_VALUES; ++i) {
      histogram.recordValueWithCount(testData[i], INCREMENT);
    }
    blackhole.consume(histogram.getTotalCount());
    blackhole.consume(histogram.getMinValue());
    blackhole.consume(histogram.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchPaginatedLogarithmic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), PaginatedStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchPaginatedCubic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY), PaginatedStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchPaginatedQuadratic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY), PaginatedStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchPaginatedLinear(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY), PaginatedStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchSparseLogarithmic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), SparseStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchSparseCubic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY), SparseStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchSparseQuadratic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY), SparseStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchSparseLinear(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY), SparseStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchUnboundedSizeDenseLogarithmic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), UnboundedSizeDenseStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchUnboundedSizeDenseCubic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new CubicallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
            UnboundedSizeDenseStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchUnboundedSizeDenseQuadratic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new QuadraticallyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
            UnboundedSizeDenseStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDDSketchUnboundedSizeDenseLinear(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    DDSketch sketch =
        new DDSketch(
            new LinearlyInterpolatedMapping(DD_SKETCH_RELATIVE_ACCURACY),
            UnboundedSizeDenseStore::new);
    for (int i = 0; i < NUM_VALUES; ++i) {
      sketch.accept(testData[i], INCREMENT);
    }
    blackhole.consume(sketch.getCount());
    blackhole.consume(sketch.getMinValue());
    blackhole.consume(sketch.getMaxValue());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDynaHistStaticLogLinear(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    Histogram histogram =
        Histogram.createStatic(LogLinearLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
    for (int i = 0; i < NUM_VALUES; ++i) {
      histogram.addValue(testData[i], INCREMENT);
    }
    blackhole.consume(histogram.getTotalCount());
    blackhole.consume(histogram.getMin());
    blackhole.consume(histogram.getMax());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDynaHistStaticLogQuadratic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    Histogram histogram =
        Histogram.createStatic(LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
    for (int i = 0; i < NUM_VALUES; ++i) {
      histogram.addValue(testData[i], INCREMENT);
    }
    blackhole.consume(histogram.getTotalCount());
    blackhole.consume(histogram.getMin());
    blackhole.consume(histogram.getMax());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDynaHistStaticLogOptimal(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    Histogram histogram =
        Histogram.createStatic(LogOptimalLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
    for (int i = 0; i < NUM_VALUES; ++i) {
      histogram.addValue(testData[i], INCREMENT);
    }
    blackhole.consume(histogram.getTotalCount());
    blackhole.consume(histogram.getMin());
    blackhole.consume(histogram.getMax());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDynaHistDynamicLogLinear(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    Histogram histogram =
        Histogram.createDynamic(LogLinearLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
    for (int i = 0; i < NUM_VALUES; ++i) {
      histogram.addValue(testData[i], INCREMENT);
    }
    blackhole.consume(histogram.getTotalCount());
    blackhole.consume(histogram.getMin());
    blackhole.consume(histogram.getMax());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDynaHistDynamicLogQuadratic(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    Histogram histogram =
        Histogram.createDynamic(LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
    for (int i = 0; i < NUM_VALUES; ++i) {
      histogram.addValue(testData[i], INCREMENT);
    }
    blackhole.consume(histogram.getTotalCount());
    blackhole.consume(histogram.getMin());
    blackhole.consume(histogram.getMax());
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void recordDynaHistDynamicLogOptimal(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    Histogram histogram =
        Histogram.createDynamic(LogOptimalLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
    for (int i = 0; i < NUM_VALUES; ++i) {
      histogram.addValue(testData[i], INCREMENT);
    }
    blackhole.consume(histogram.getTotalCount());
    blackhole.consume(histogram.getMin());
    blackhole.consume(histogram.getMax());
  }
}
