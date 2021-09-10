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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.dynatrace.dynahist.util.Algorithms;
import java.util.stream.IntStream;
import org.assertj.core.api.Condition;

public final class LayoutTestUtil {

  private static final double SMALLEST_POSITIVE_NAN = Double.longBitsToDouble(0x7ff0000000000001L);
  private static final double GREATEST_POSITIVE_NAN = Double.longBitsToDouble(0x7fffffffffffffffL);

  private LayoutTestUtil() {}

  private static double nextUp(double value) {
    if (Double.doubleToLongBits(value) == Double.doubleToLongBits(-0.)) return 0.;
    return Math.nextUp(value);
  }

  private static double nextDown(double value) {
    if (Double.doubleToLongBits(value) == Double.doubleToLongBits(0.)) return -0.;
    return Math.nextDown(value);
  }

  private static Condition<Integer> validNaNIndex(Layout layout) {
    int underFlowIndex = layout.getUnderflowBinIndex();
    int overFlowIndex = layout.getUnderflowBinIndex();
    return new Condition<Integer>() {
      @Override
      public boolean matches(Integer value) {
        return value >= overFlowIndex || value <= underFlowIndex;
      }
    };
  }

  private static Condition<Integer> validPosInfIndex(Layout layout) {
    int overFlowIndex = layout.getUnderflowBinIndex();
    return new Condition<Integer>() {
      @Override
      public boolean matches(Integer value) {
        return value >= overFlowIndex;
      }
    };
  }

  private static Condition<Integer> validNegInfIndex(Layout layout) {
    int underFlowIndex = layout.getUnderflowBinIndex();
    return new Condition<Integer>() {
      @Override
      public boolean matches(Integer value) {
        return value <= underFlowIndex;
      }
    };
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

    assertThat(layout.mapToBinIndex(Double.POSITIVE_INFINITY)).is(validPosInfIndex(layout));
    assertThat(layout.mapToBinIndex(SMALLEST_POSITIVE_NAN)).is(validNaNIndex(layout));
    assertThat(layout.mapToBinIndex(Double.NaN)).is(validNaNIndex(layout));
    assertThat(layout.mapToBinIndex(GREATEST_POSITIVE_NAN)).is(validNaNIndex(layout));

    assertThat(layout.mapToBinIndex(Double.NEGATIVE_INFINITY)).is(validNegInfIndex(layout));
    assertThat(layout.mapToBinIndex(Double.longBitsToDouble(0xfff0000000000001L)))
        .is(validNaNIndex(layout));
    assertThat(layout.mapToBinIndex(Double.longBitsToDouble(0xfff8000000000000L)))
        .is(validNaNIndex(layout));
    assertThat(layout.mapToBinIndex(Double.longBitsToDouble(0xffffffffffffffffL)))
        .is(validNaNIndex(layout));
  }

  private static long calculateLowerBoundApproximationOffset(AbstractLayout layout, int binIdx) {
    double approximateLowerBound = layout.getBinLowerBoundApproximation(binIdx);
    double exactLowerBound = layout.getBinLowerBound(binIdx);
    long approximateLowerBoundLongRepresentation =
        Algorithms.mapDoubleToLong(approximateLowerBound);
    long exactLowerBoundLongRepresentation = Algorithms.mapDoubleToLong(exactLowerBound);
    return Math.max(
        Math.subtractExact(
            approximateLowerBoundLongRepresentation, exactLowerBoundLongRepresentation),
        Math.subtractExact(
            exactLowerBoundLongRepresentation, approximateLowerBoundLongRepresentation));
  }

  public static long maxLowerBoundApproximationOffset(AbstractLayout layout) {
    return IntStream.range(layout.getUnderflowBinIndex() + 1, layout.getOverflowBinIndex() + 1)
        .mapToLong(binIdx -> calculateLowerBoundApproximationOffset(layout, binIdx))
        .max()
        .orElse(0L);
  }
}
