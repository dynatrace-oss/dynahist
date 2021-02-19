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

import com.datadoghq.sketch.ddsketch.DDSketch;
import com.datadoghq.sketch.ddsketch.mapping.LogarithmicMapping;
import com.datadoghq.sketch.ddsketch.store.UnboundedSizeDenseStore;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import java.io.IOException;
import org.HdrHistogram.DoubleHistogram;

public class Comparison {

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
   * Demonstrates performance relevant behavioral differences between HdrHistogram, DDSketch, and
   * DynaHist that must be taken into account when comparing the benchmark results.
   */
  public static void main(String[] args) throws IOException {

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
}
