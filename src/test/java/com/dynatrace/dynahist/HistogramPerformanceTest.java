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
import com.dynatrace.dynahist.layout.Layout;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public class HistogramPerformanceTest {

  private static final int RANGE = 1_000_000_000;
  private static final long MIN = 1000;
  private static final long MAX = MIN * RANGE;
  private static final int PRECISION_DIGITS = 3;
  private static final double PRECISION = Math.pow(10., -PRECISION_DIGITS);
  private static final int NUM_CYCLES = 100;
  private static final int NUM_VALUES = 10_000_000;
  private static final long INCREMENT = 1;

  private static double[] createTestDataDouble() {
    final double[] values = new double[NUM_VALUES];
    final Random random = new Random(0);
    for (int i = 0; i < NUM_VALUES; ++i) {
      values[i] = MIN * Math.pow(RANGE, random.nextDouble());
    }
    return values;
  }

  private static final double[] TEST_DATA_DOUBLE = createTestDataDouble();

  private static final List<Layout> LAYOUTS =
      Arrays.asList(
          ErrorLimitingLayout1.create(1, PRECISION, MIN, MAX),
          ErrorLimitingLayout2.create(1, PRECISION, MIN, MAX),
          ErrorLimitingLayout2.create(1, PRECISION, MIN, MAX),
          ErrorLimitingLayout2.create(1, PRECISION, MIN, MAX),
          ErrorLimitingLayout2.create(1, PRECISION, MIN, MAX),
          ErrorLimitingLayout2.create(1, PRECISION, MIN, MAX));

  private static final List<Function<Layout, Histogram>> HISTOGRAM_SUPPLIERS =
      Arrays.asList(l -> new DynamicHistogram(l), l -> new StaticHistogram(l));
  private static final List<String> HISTOGRAM_CLASS_NAMES =
      Arrays.asList(DynamicHistogram.class.getSimpleName(), StaticHistogram.class.getSimpleName());

  public static void main(String[] args) {

    for (Layout layout : LAYOUTS) {
      for (int supplierIdx = 0; supplierIdx < HISTOGRAM_SUPPLIERS.size(); ++supplierIdx) {
        final Function<Layout, Histogram> supplier = HISTOGRAM_SUPPLIERS.get(supplierIdx);

        final long start = System.currentTimeMillis();
        for (int m = 0; m < NUM_CYCLES; ++m) {
          final Histogram histogram = supplier.apply(layout);
          for (int i = 0; i < NUM_VALUES; ++i) {
            histogram.addValue(TEST_DATA_DOUBLE[i], INCREMENT);
          }
        }
        final long end = System.currentTimeMillis();

        final double avgRecordingTimeNanos =
            ((end - start) * 1e6) / ((double) NUM_VALUES * (double) NUM_CYCLES);
        System.out.println(
            "Avg recording time ("
                + HISTOGRAM_CLASS_NAMES.get(supplierIdx)
                + ", "
                + layout
                + ") = "
                + avgRecordingTimeNanos
                + "ns.");
      }
    }
  }
}
