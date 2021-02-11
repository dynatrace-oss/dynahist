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
package com.dynatrace.dynahist.demo;

import static org.junit.Assert.assertEquals;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import org.junit.Test;

public class BinIteration {

  @Test
  public void testBinIterationAscendingOrder() {
    Layout layout = LogQuadraticLayout.create(1e-3, 1e-2, 0, 1E6);
    Histogram histogram =
        Histogram.createDynamic(layout)
            .addValue(7.5, 3)
            .addValue(3.5, 6)
            .addValue(8.5, 11)
            .addValue(9.5, 2);

    long[] expectedBinCounts = new long[] {6L, 3L, 11L, 2L};

    BinIterator binIterator = histogram.getFirstNonEmptyBin();
    int counter = 0;
    assertEquals(expectedBinCounts[counter], binIterator.getBinCount());
    while (!binIterator.isLastNonEmptyBin()) {
      counter += 1;
      binIterator.next();
      binIterator.getBinCount();
      assertEquals(expectedBinCounts[counter], binIterator.getBinCount());
    }
  }

  @Test
  public void testBinIterationDescendingOrder() {
    Layout layout = LogQuadraticLayout.create(1e-3, 1e-2, 0, 1E6);
    Histogram histogram =
        Histogram.createDynamic(layout)
            .addValue(7.5, 3)
            .addValue(3.5, 6)
            .addValue(8.5, 11)
            .addValue(9.5, 2);

    long[] expectedBinCounts = new long[] {2L, 11L, 3L, 6L};

    BinIterator binIterator = histogram.getLastNonEmptyBin();
    int counter = 0;
    assertEquals(expectedBinCounts[counter], binIterator.getBinCount());
    while (!binIterator.isFirstNonEmptyBin()) {
      counter += 1;
      binIterator.previous();
      binIterator.getBinCount();
      assertEquals(expectedBinCounts[counter], binIterator.getBinCount());
    }
  }
}
