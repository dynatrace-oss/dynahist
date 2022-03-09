/*
 * Copyright 2020-2022 Dynatrace LLC
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

import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;

public abstract class AbstractRecordingSpeedBenchmark<T> {

  private static final int NUM_VALUES = 1_000_000;
  private static final int NUM_TEST_DATA_SETS = 100;
  private static final double[][] TEST_DATA_DOUBLE = new double[NUM_TEST_DATA_SETS][];

  static {
    final SplittableRandom random = new SplittableRandom(0);
    for (int j = 0; j < NUM_TEST_DATA_SETS; ++j) {
      double[] dataDouble = new double[NUM_VALUES];
      for (int i = 0; i < NUM_VALUES; ++i) {
        dataDouble[i] = MIN * Math.pow(RANGE, random.nextDouble());
      }
      TEST_DATA_DOUBLE[j] = dataDouble;
    }
  }

  protected abstract T create();

  protected abstract void add(T histogram, double value);

  protected abstract void consume(T histogram, Blackhole blackhole);

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void test(Blackhole blackhole) {
    double[] testData = TEST_DATA_DOUBLE[ThreadLocalRandom.current().nextInt(NUM_TEST_DATA_SETS)];
    T histogram = create();
    for (int i = 0; i < NUM_VALUES; ++i) {
      add(histogram, testData[i]);
    }
    consume(histogram, blackhole);
  }
}
