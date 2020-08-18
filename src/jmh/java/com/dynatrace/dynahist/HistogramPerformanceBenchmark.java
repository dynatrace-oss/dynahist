/*
 * Copyright 2020 Dynatrace LLC
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

import com.dynatrace.dynahist.layout.ErrorLimitingLayout1;
import com.dynatrace.dynahist.layout.ErrorLimitingLayout2;
import java.util.Random;
import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
public class HistogramPerformanceBenchmark {
  private static final int RANGE = 1_000_000_000;
  private static final long MIN = 1000;
  private static final long MAX = MIN * RANGE;
  private static int PRECISION_DIGITS = 3;
  private static double PRECISION = Math.pow(10., -PRECISION_DIGITS);
  private static double ABOLUTE_ERROR = 1e-8;
  private static final int NUM_VALUES = 10_000_000;
  private static final long INCREMENT = 1;

  private static double[] createTEST_DATA_DOUBLEDataDouble() {
    final double values[] = new double[NUM_VALUES];
    final Random random = new Random(0);
    for (int i = 0; i < NUM_VALUES; ++i) {
      values[i] = MIN * Math.pow(RANGE, random.nextDouble());
    }
    return values;
  }

  static final double[] TEST_DATA_DOUBLE = createTEST_DATA_DOUBLEDataDouble();
  Histogram staticHistogramErrorLimitingLayout1;
  Histogram staticHistogramErrorLimitingLayout2;
  Histogram dynamicHistogramErrorLimitingLayout1;
  Histogram dynamicHistogramErrorLimitingLayout2;

  @Setup
  public void setup() {
    staticHistogramErrorLimitingLayout1 =
        Histogram.createStatic(ErrorLimitingLayout1.create(ABOLUTE_ERROR, PRECISION, MIN, MAX));
    staticHistogramErrorLimitingLayout2 =
        Histogram.createStatic(ErrorLimitingLayout2.create(ABOLUTE_ERROR, PRECISION, MIN, MAX));
    dynamicHistogramErrorLimitingLayout1 =
        Histogram.createDynamic(ErrorLimitingLayout1.create(ABOLUTE_ERROR, PRECISION, MIN, MAX));
    dynamicHistogramErrorLimitingLayout2 =
        Histogram.createDynamic(ErrorLimitingLayout2.create(ABOLUTE_ERROR, PRECISION, MIN, MAX));
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void insertStaticHistogramErrorLimitingLayout1() {
    for (int i = 0; i < NUM_VALUES; ++i) {
      staticHistogramErrorLimitingLayout1.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void insertStaticHistogramErrorLimitingLayout2() {
    for (int i = 0; i < NUM_VALUES; ++i) {
      staticHistogramErrorLimitingLayout2.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void insertDynamicHistogramErrorLimitingLayout1() {
    for (int i = 0; i < NUM_VALUES; ++i) {
      dynamicHistogramErrorLimitingLayout1.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void insertDynamicHistogramErrorLimitingLayout2() {
    for (int i = 0; i < NUM_VALUES; ++i) {
      dynamicHistogramErrorLimitingLayout2.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
    }
  }
}
