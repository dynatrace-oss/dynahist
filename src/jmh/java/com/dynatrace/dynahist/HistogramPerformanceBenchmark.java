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

import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import java.util.Random;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class HistogramPerformanceBenchmark {
  private static final int RANGE = 1_000_000_000;
  private static final long MIN = 1000;
  private static final long MAX = MIN * RANGE;
  private static final int PRECISION_DIGITS = 3;
  private static final double PRECISION = Math.pow(10., -PRECISION_DIGITS);
  private static final double ABSOLUTE_ERROR = 1e-8;
  private static final int NUM_VALUES = 10_000_000;
  private static final long INCREMENT = 1;

  private static double[] createTestData() {
    final double[] values = new double[NUM_VALUES];
    final Random random = new Random(0);
    for (int i = 0; i < NUM_VALUES; ++i) {
      values[i] = MIN * Math.pow(RANGE, random.nextDouble());
    }
    return values;
  }

  static final double[] TEST_DATA_DOUBLE = createTestData();
  Histogram staticHistogramLogLinearLayout;
  Histogram staticHistogramLogQuadraticLayout;
  Histogram dynamicHistogramLogLinearLayout;
  Histogram dynamicHistogramLogQuadraticLayout;

  @Setup
  public void setup() {
    staticHistogramLogLinearLayout =
        Histogram.createStatic(LogLinearLayout.create(ABSOLUTE_ERROR, PRECISION, MIN, MAX));
    staticHistogramLogQuadraticLayout =
        Histogram.createStatic(LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, MIN, MAX));
    dynamicHistogramLogLinearLayout =
        Histogram.createDynamic(LogLinearLayout.create(ABSOLUTE_ERROR, PRECISION, MIN, MAX));
    dynamicHistogramLogQuadraticLayout =
        Histogram.createDynamic(LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, MIN, MAX));
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void insertStaticHistogramLogLinearLayout() {
    for (int i = 0; i < NUM_VALUES; ++i) {
      staticHistogramLogLinearLayout.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void insertStaticHistogramLogQuadraticLayout() {
    for (int i = 0; i < NUM_VALUES; ++i) {
      staticHistogramLogQuadraticLayout.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void insertDynamicHistogramLogLinearLayout() {
    for (int i = 0; i < NUM_VALUES; ++i) {
      dynamicHistogramLogLinearLayout.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void insertDynamicHistogramLogQuadraticLayout() {
    for (int i = 0; i < NUM_VALUES; ++i) {
      dynamicHistogramLogQuadraticLayout.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
    }
  }
}
