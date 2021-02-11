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
package com.dynatrace.dynahist.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LayoutTestUtil {

  private LayoutTestUtil() {}

  private static double nextUp(double value) {
    if (Double.doubleToLongBits(value) == Double.doubleToLongBits(-0.)) return 0.;
    return Math.nextUp(value);
  }

  private static double nextDown(double value) {
    if (Double.doubleToLongBits(value) == Double.doubleToLongBits(0.)) return -0.;
    return Math.nextDown(value);
  }

  public static void assertConsistency(Layout layout) {

    assertEquals(
        layout.getBinLowerBound(layout.getUnderflowBinIndex()),
        layout.getBinLowerBound(layout.getUnderflowBinIndex() - 1),
        0d);
    assertEquals(
        layout.getBinLowerBound(layout.getUnderflowBinIndex()),
        layout.getBinLowerBound(Integer.MIN_VALUE),
        0d);
    assertEquals(
        layout.getBinUpperBound(layout.getUnderflowBinIndex()),
        layout.getBinUpperBound(layout.getUnderflowBinIndex() - 1),
        0d);
    assertEquals(
        layout.getBinUpperBound(layout.getUnderflowBinIndex()),
        layout.getBinUpperBound(Integer.MIN_VALUE),
        0d);

    assertEquals(
        layout.getBinLowerBound(layout.getOverflowBinIndex()),
        layout.getBinLowerBound(layout.getOverflowBinIndex() + 1),
        0d);
    assertEquals(
        layout.getBinLowerBound(layout.getOverflowBinIndex()),
        layout.getBinLowerBound(Integer.MAX_VALUE),
        0d);
    assertEquals(
        layout.getBinUpperBound(layout.getOverflowBinIndex()),
        layout.getBinUpperBound(layout.getOverflowBinIndex() + 1),
        0d);
    assertEquals(
        layout.getBinUpperBound(layout.getOverflowBinIndex()),
        layout.getBinUpperBound(Integer.MAX_VALUE),
        0d);

    for (int i = layout.getUnderflowBinIndex(); i <= layout.getOverflowBinIndex(); ++i) {

      double lowerBound = layout.getBinLowerBound(i);
      if (i == layout.getUnderflowBinIndex()) {
        assertEquals(Double.NEGATIVE_INFINITY, lowerBound, 0d);
      } else {
        assertEquals(i, layout.mapToBinIndex(lowerBound));
        assertEquals(i - 1, layout.mapToBinIndex(nextDown(lowerBound)));
      }

      double upperBound = layout.getBinUpperBound(i);
      if (i == layout.getOverflowBinIndex()) {
        assertEquals(Double.POSITIVE_INFINITY, upperBound, 0d);
      } else {
        assertEquals(i, layout.mapToBinIndex(upperBound));
        assertEquals(i + 1, layout.mapToBinIndex(nextUp(upperBound)));
      }
    }

    assertEquals(
        layout.getBinLowerBound(layout.getUnderflowBinIndex() + 1),
        layout.getNormalRangeLowerBound(),
        0d);
    assertEquals(
        layout.getBinUpperBound(layout.getOverflowBinIndex() - 1),
        layout.getNormalRangeUpperBound(),
        0d);

    assertTrue(layout.getUnderflowBinIndex() >= layout.mapToBinIndex(Double.NEGATIVE_INFINITY));
    assertTrue(layout.getOverflowBinIndex() <= layout.mapToBinIndex(Double.POSITIVE_INFINITY));

    final int nanBinIndex = layout.mapToBinIndex(Double.NaN);
    assertTrue(
        nanBinIndex >= layout.getOverflowBinIndex()
            || nanBinIndex <= layout.getUnderflowBinIndex());
  }
}
