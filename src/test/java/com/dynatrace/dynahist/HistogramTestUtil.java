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

import static org.junit.Assert.assertEquals;

import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.Layout;
import java.util.Map;
import java.util.TreeMap;

public final class HistogramTestUtil {

  private HistogramTestUtil() {}

  public static Map<Integer, Long> checkHistogramDataConsistency(Histogram histogramData) {

    final Layout layout = histogramData.getLayout();
    final long totalCount = histogramData.getTotalCount();
    final long overflowCount = histogramData.getOverflowCount();
    final long underflowCount = histogramData.getUnderflowCount();
    final double min = histogramData.getMin();
    final double max = histogramData.getMax();

    final Map<Integer, Long> nonEmptyBinsFromGetCount = new TreeMap<>();
    {
      for (int binIndex = layout.getUnderflowBinIndex();
          binIndex <= layout.getOverflowBinIndex();
          binIndex += 1) {
        long count = histogramData.getCount(binIndex);
        if (count > 0) {
          nonEmptyBinsFromGetCount.put(binIndex, count);
        }
      }
    }

    final Map<Integer, Long> nonEmptyBinsFromForwardBinIteration = new TreeMap<>();
    {
      if (!histogramData.isEmpty()) {
        BinIterator binIterator = histogramData.getFirstNonEmptyBin();
        while (true) {
          nonEmptyBinsFromForwardBinIteration.put(
              binIterator.getBinIndex(), binIterator.getBinCount());
          assertEquals(
              totalCount,
              binIterator.getLessCount()
                  + binIterator.getBinCount()
                  + binIterator.getGreaterCount());
          assertEquals(
              Math.max(min, layout.getBinLowerBound(binIterator.getBinIndex())),
              binIterator.getLowerBound(),
              0d);
          assertEquals(
              Math.min(max, layout.getBinUpperBound(binIterator.getBinIndex())),
              binIterator.getUpperBound(),
              0d);
          if (binIterator.isLastNonEmptyBin()) {
            break;
          }
          binIterator.next();
        }
      }
    }

    final Map<Integer, Long> nonEmptyBinsFromForwardBinIteration2 = new TreeMap<>();
    {
      if (!histogramData.isEmpty()) {
        BinIterator binIterator = histogramData.getFirstNonEmptyBin();
        while (true) {
          nonEmptyBinsFromForwardBinIteration2.put(
              binIterator.getBinIndex(), binIterator.getBinCount());
          assertEquals(
              totalCount,
              binIterator.getLessCount()
                  + binIterator.getBinCount()
                  + binIterator.getGreaterCount());
          assertEquals(
              Math.max(min, layout.getBinLowerBound(binIterator.getBinIndex())),
              binIterator.getLowerBound(),
              0d);
          assertEquals(
              Math.min(max, layout.getBinUpperBound(binIterator.getBinIndex())),
              binIterator.getUpperBound(),
              0d);
          if (binIterator.isLastNonEmptyBin()) {
            break;
          }
          binIterator.next();
          binIterator.previous();
          binIterator.next();
        }
      }
    }

    final Map<Integer, Long> nonEmptyBinsFromBackwardBinIteration = new TreeMap<>();
    {
      if (!histogramData.isEmpty()) {
        BinIterator binIterator = histogramData.getLastNonEmptyBin();
        while (true) {
          nonEmptyBinsFromBackwardBinIteration.put(
              binIterator.getBinIndex(), binIterator.getBinCount());
          assertEquals(
              totalCount,
              binIterator.getLessCount()
                  + binIterator.getBinCount()
                  + binIterator.getGreaterCount());
          assertEquals(
              Math.max(min, layout.getBinLowerBound(binIterator.getBinIndex())),
              binIterator.getLowerBound(),
              0d);
          assertEquals(
              Math.min(max, layout.getBinUpperBound(binIterator.getBinIndex())),
              binIterator.getUpperBound(),
              0d);
          if (binIterator.isFirstNonEmptyBin()) {
            break;
          }
          binIterator.previous();
        }
      }
    }

    final Map<Integer, Long> nonEmptyBinsFromBackwardBinIteration2 = new TreeMap<>();
    {
      if (!histogramData.isEmpty()) {
        BinIterator binIterator = histogramData.getLastNonEmptyBin();
        while (true) {
          nonEmptyBinsFromBackwardBinIteration2.put(
              binIterator.getBinIndex(), binIterator.getBinCount());
          assertEquals(
              totalCount,
              binIterator.getLessCount()
                  + binIterator.getBinCount()
                  + binIterator.getGreaterCount());
          assertEquals(
              Math.max(min, layout.getBinLowerBound(binIterator.getBinIndex())),
              binIterator.getLowerBound(),
              0d);
          assertEquals(
              Math.min(max, layout.getBinUpperBound(binIterator.getBinIndex())),
              binIterator.getUpperBound(),
              0d);
          if (binIterator.isFirstNonEmptyBin()) {
            break;
          }
          binIterator.previous();
          binIterator.next();
          binIterator.previous();
        }
      }
    }

    /*
     * final Map<Integer, Long> nonEmptyBinsFromOrderAccess = new TreeMap<>(); { if
     * (!histogramData.isEmpty()) { for (long rank = 0; rank < totalCount;
     * ++order) { BinIterator bin = histogramData.getBinByRank(rank);
     * nonEmptyBinsFromOrderAccess.compute(bin.getBinIndex(), (idx, count) -> (count
     * == null)?1:count+1); } } }
     */

    Map<Integer, Long> nonEmptyBins = nonEmptyBinsFromGetCount;

    assertEquals(nonEmptyBins, nonEmptyBinsFromGetCount);
    assertEquals(nonEmptyBins, nonEmptyBinsFromForwardBinIteration);
    assertEquals(nonEmptyBins, nonEmptyBinsFromBackwardBinIteration);
    assertEquals(nonEmptyBins, nonEmptyBinsFromForwardBinIteration2);
    assertEquals(nonEmptyBins, nonEmptyBinsFromBackwardBinIteration2);
    // assertEquals(nonEmptyBins, nonEmptyBinsFromOrderAccess);

    assertEquals(
        underflowCount, nonEmptyBins.getOrDefault(layout.getUnderflowBinIndex(), 0L).longValue());
    assertEquals(
        overflowCount, nonEmptyBins.getOrDefault(layout.getOverflowBinIndex(), 0L).longValue());
    assertEquals(totalCount, nonEmptyBins.values().stream().mapToLong(Long::longValue).sum());

    return nonEmptyBins;
  }

  public static int numberOfNonEmptyBins(final Histogram histogram) {
    if (histogram.getTotalCount() == 0L) {
      return 0;
    }
    int count = 1;
    final BinIterator iterator = histogram.getFirstNonEmptyBin();
    while (iterator.getGreaterCount() != 0L) {
      iterator.next();
      count += 1;
    }
    return count;
  }
}
