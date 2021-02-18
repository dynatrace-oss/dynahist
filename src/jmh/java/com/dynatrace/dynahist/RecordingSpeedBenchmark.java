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
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.HdrHistogram.DoubleHistogram;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;

public class RecordingSpeedBenchmark {
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

  private static void assertCondition(boolean condition, String description) {
    if (!condition) {
      throw new RuntimeException(description);
    }
  }

  private static void assertException(Runnable runnable, String description) {
    try {
      runnable.run();
      throw new RuntimeException(description);
    } catch (RuntimeException e) {
      // expect that exception is thrown
    }
  }

  /**
   * This method demonstrates performance relevant behavioral differences between HdrHistogram,
   * DDSketch, and DynaHist that must be taken into account when comparing the benchmark results.
   */
  private static void demonstratePerformanceRelevantDifferencesBetweenDataStructures() {

    final double value = 2.5362386543;

    // Recording of exact minimum and maximum values
    // =============================================
    // HdrHistogram and DDSketch do not keep track of the exact minimum and the exact maximum value.
    // This saves a couple of CPU operations and is a small performance advantage over DynaHist.
    {
      {
        DoubleHistogram histogram = new DoubleHistogram(RANGE, PRECISION_DIGITS);
        histogram.recordValue(value);
        assertCondition(
            histogram.getMinValue() < value,
            "HdrHistogram does not keep track of the exact minimum value!");
        assertCondition(
            histogram.getMaxValue() < value,
            "HdrHistogram does not keep track of the exact maximum value! In this example the reported maximum is even less than the recorded value!");
      }
      {
        DDSketch sketch =
            new DDSketch(
                new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), UnboundedSizeDenseStore::new);
        sketch.accept(value);
        assertCondition(
            sketch.getMinValue() > value,
            "DDSketch does not keep track of the exact minimum value! In this example the reported minimum is even greater than the recorded value!");
        assertCondition(
            sketch.getMaxValue() > value,
            "DDSketch does not keep track of the exact maximum value!");
      }
      {
        Histogram histogram =
            Histogram.createDynamic(LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
        histogram.addValue(value);
        assertCondition(
            histogram.getMin() == value, "DynaHist keeps track of the exact minimum value!");
        assertCondition(
            histogram.getMax() == value, "DynaHist keeps track of the exact maximum value!");
      }
    }

    // Overflow handling
    // =================
    // DynaHist is protected against overflows and throws a runtime exception, if the total count
    // would exceed Long.MAX_VALUE. HdrHistogram and DDSketch have a small performance advantage,
    // since they do not have such a special handling. The total count of HdrHistogram may overflow
    // and can even become negative. DDSketch uses double counters which cannot overflow by nature,
    // but which can lead to silent loss of updates as demonstrated below.
    {
      DoubleHistogram histogram = new DoubleHistogram(RANGE, PRECISION_DIGITS);
      histogram.recordValueWithCount(value, Long.MAX_VALUE);
      histogram.recordValueWithCount(value, 1); // this update leads to an overflow
      assertCondition(
          histogram.getTotalCount() == Long.MIN_VALUE,
          "HdrHistogram total count may overflow and become negative!");
    }
    {
      DDSketch sketch =
          new DDSketch(
              new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), UnboundedSizeDenseStore::new);
      sketch.accept(value, Long.MAX_VALUE - 1);
      sketch.accept(value); // this update is swallowed
      assertCondition(
          sketch.getCount() == Long.MAX_VALUE, "DDSketch may swallow updates silently!");
    }
    {
      DDSketch sketch =
          new DDSketch(
              new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), UnboundedSizeDenseStore::new);
      sketch.accept(value, Long.MAX_VALUE - 1);
      assertCondition(
          ((long) sketch.getCount()) != Long.MAX_VALUE - 1, "DDSketch counts may not be exact!");
    }
    {
      Histogram histogram =
          Histogram.createDynamic(LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
      histogram.addValue(value, Long.MAX_VALUE);
      assertException(
          () -> histogram.addValue(value),
          "DynaHist throws an exception when the long count overflows!");
    }

    // Negative increments
    // ===================
    // DynaHist and DDSketch are protected against negative increments and throw an illegal argument
    // exception. HdrHistogram has a small performance advantage, since it does not have a special
    // handling for negative increments.
    {
      DoubleHistogram histogram = new DoubleHistogram(RANGE, PRECISION_DIGITS);
      histogram.recordValueWithCount(value, -1);
      assertCondition(
          histogram.getTotalCount() == -1,
          "HdrHistogram does not throw an exception for negative increments!");
    }
    {
      DDSketch sketch =
          new DDSketch(
              new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), UnboundedSizeDenseStore::new);
      assertException(
          () -> sketch.accept(value, -1), "DDSketch throws an exception for negative increments!");
    }
    {
      Histogram histogram =
          Histogram.createDynamic(LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));
      assertException(
          () -> histogram.addValue(value, -1),
          "DynaHist throws an exception for negative increments!");
    }
  }

  static {
    demonstratePerformanceRelevantDifferencesBetweenDataStructures();

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
