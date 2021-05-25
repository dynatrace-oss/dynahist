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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import com.dynatrace.dynahist.util.Algorithms;
import java.io.IOException;
import org.assertj.core.data.Offset;
import org.junit.Test;

public class LogQuadraticLayoutTest extends AbstractErrorLimitingLayoutTest {

  @Test
  public void test() {
    assertTrue(4. * StrictMath.log1p(Double.MAX_VALUE) <= 2840d);
  }

  @Test
  public void testMapToBinIndexHelperSpecialValues() {
    assertEquals(6144d, LogQuadraticLayout.mapToBinIndexHelper(Long.MAX_VALUE), 0d);
    assertEquals(6144d, LogQuadraticLayout.mapToBinIndexHelper(0x7fffffffffffffffL), 0d);
    assertEquals(
        6142.75, LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(Double.NaN)), 0d);
    assertEquals(
        6141d,
        LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(Double.POSITIVE_INFINITY)),
        0d);
    assertEquals(
        3d, LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(Double.MIN_NORMAL)), 0d);
    assertEquals(0d, LogQuadraticLayout.mapToBinIndexHelper(0L), 0d);

    assertEquals(3063., LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(0.25)), 0d);
    assertEquals(3066., LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(0.5)), 0d);
    assertEquals(3069., LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(1)), 0d);
    assertEquals(3072., LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(2)), 0d);
    assertEquals(3075., LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(4)), 0d);
    assertEquals(3078., LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(8)), 0d);
    assertEquals(3081., LogQuadraticLayout.mapToBinIndexHelper(Double.doubleToLongBits(16)), 0d);
  }

  @Override
  protected AbstractLayout createLayout(
      double absoluteBinWidthLimit,
      double relativeBinWidthLimit,
      double valueRangeLowerBound,
      double valueRangeUpperBound) {
    return LogQuadraticLayout.create(
        absoluteBinWidthLimit, relativeBinWidthLimit, valueRangeLowerBound, valueRangeUpperBound);
  }

  @Test
  public void testOverflowAndUnderflowIndices() {
    {
      final LogQuadraticLayout layout = LogQuadraticLayout.create(1e-7, 1e-6, -1e12, 1e12);
      assertEquals(33391320, layout.getOverflowBinIndex());
      assertEquals(-33391321, layout.getUnderflowBinIndex());
    }
    {
      final LogQuadraticLayout layout = LogQuadraticLayout.create(1e-7, 1e-6, 1e12, 1e12);
      assertEquals(33391320, layout.getOverflowBinIndex());
      assertEquals(33391318, layout.getUnderflowBinIndex());
    }
  }

  @Test
  public void testSerialization() throws IOException {
    double valueRangeUpperBound = 1e7;
    double valueRangeLowerBound = -1e6;
    double relativeBinWidthLimit = 1e-3;
    double absoluteBinWidthLimit = 1e-9;
    LogQuadraticLayout layout =
        LogQuadraticLayout.create(
            absoluteBinWidthLimit,
            relativeBinWidthLimit,
            valueRangeLowerBound,
            valueRangeUpperBound);
    LogQuadraticLayout deserializedLayout =
        SerializationTestUtil.testSerialization(
            layout,
            LogQuadraticLayout::write,
            LogQuadraticLayout::read,
            "003E112E0BE826D6953F50624DD2F1A9FC8FE303F48904");

    assertEquals(deserializedLayout, layout);
  }

  @Test
  public void testToString() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    assertEquals(
        "LogQuadraticLayout [absoluteBinWidthLimit=1.0E-8, relativeBinWidthLimit=0.01, underflowBinIndex=-3107, overflowBinIndex=3106]",
        layout.toString());
  }

  @Test
  public void testGetWidth() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createStatic(layout);
    histogram.addValue(0);
    histogram.addValue(10);
    assertEquals(9.999999999999999E-9, histogram.getFirstNonEmptyBin().getWidth(), 0);
    assertEquals(0.031135683241927836, histogram.getLastNonEmptyBin().getWidth(), 0);
  }

  @Test
  public void testEquals() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    assertFalse(layout.equals(null));
    assertFalse(layout.equals(LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6)));
    assertFalse(layout.equals(LogQuadraticLayout.create(1e-7, 1e-2, -1e6, 1e6)));
    assertFalse(
        LogQuadraticLayout.create(1, 0, 1, 10).equals(LogQuadraticLayout.create(1, 1e-3, 1, 10)));
    assertFalse(layout.equals(LogQuadraticLayout.create(1e-8, 1e-2, -1e5, 1e6)));
    assertFalse(layout.equals(LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e5)));
  }

  @Test
  public void testInitialGuesses() {

    final double[] absoluteBinWidthLimits = {
      1e-6, 1e-5, 1e-4, 1e-3, 1e-2, 1e-1, 1e0, 1e1, 1e2, 1e3
    };
    final double[] relativeBinWidthLimits = {
      0, 1e-100, 1e-6, 1e-5, 1e-4, 1e-3, 1e-2, 1e-1, 1e0, 1e1, 1e2, 1e3
    };
    for (final double absoluteBinWidthLimit : absoluteBinWidthLimits) {
      for (final double relativeBinWidthLimit : relativeBinWidthLimits) {

        double factorNormal = LogQuadraticLayout.calculateFactorNormal(relativeBinWidthLimit);
        double factorSubnormal = LogQuadraticLayout.calculateFactorSubNormal(absoluteBinWidthLimit);
        int firstNormalIdx = LogQuadraticLayout.calculateFirstNormalIndex(relativeBinWidthLimit);
        long unsignedValueBitsNormalLimitApproximate =
            LogQuadraticLayout.calculateUnsignedValueBitsNormalLimitApproximate(
                factorSubnormal, firstNormalIdx);
        long unsignedValueBitsNormalLimit =
            LogQuadraticLayout.calculateUnsignedValueBitsNormalLimit(
                factorSubnormal, firstNormalIdx);

        double offsetApproximate =
            LogQuadraticLayout.calculateOffsetApproximate(
                unsignedValueBitsNormalLimit, factorNormal, firstNormalIdx);
        double offset =
            LogQuadraticLayout.calculateOffset(
                unsignedValueBitsNormalLimit, factorNormal, firstNormalIdx);

        assertThat(Algorithms.mapDoubleToLong(offsetApproximate))
            .isCloseTo(Algorithms.mapDoubleToLong(offset), Offset.offset(1L));

        assertThat(unsignedValueBitsNormalLimitApproximate)
            .isCloseTo(unsignedValueBitsNormalLimit, Offset.offset(1L));
      }
    }
  }

  @Test
  public void testHashCode() {
    assertEquals(-1339415786, createLayout(1e-6, 1e-4, -10, 1000).hashCode());
  }

  private double testFunction(double mantissaPlus1) {
    return (mantissaPlus1 - 1d) * (5d - mantissaPlus1);
  }

  @Test
  public void testMonotonicityCloseTo2() {
    double mantissaPlus1 = 2;
    for (long l = 0; l < 10_000_000L; ++l) {
      double nextMantissaPlus1 = Math.nextDown(mantissaPlus1);
      assertThat(testFunction(nextMantissaPlus1)).isLessThanOrEqualTo(testFunction(mantissaPlus1));
      mantissaPlus1 = nextMantissaPlus1;
    }
  }

  @Test
  public strictfp void testMonotonicityCloseTo2Strict() {
    double mantissaPlus1 = 2;
    for (long l = 0; l < 10_000_000L; ++l) {
      double nextMantissaPlus1 = Math.nextDown(mantissaPlus1);
      assertThat(testFunction(nextMantissaPlus1)).isLessThanOrEqualTo(testFunction(mantissaPlus1));
      mantissaPlus1 = nextMantissaPlus1;
    }
  }

  @Test
  public void testMonotonicityCloseTo1() {
    double mantissaPlus1 = 1;
    for (long l = 0; l < 10_000_000L; ++l) {
      double nextMantissaPlus1 = Math.nextUp(mantissaPlus1);
      assertThat(testFunction(nextMantissaPlus1))
          .isGreaterThanOrEqualTo(testFunction(mantissaPlus1));
      mantissaPlus1 = nextMantissaPlus1;
    }
  }

  @Test
  public strictfp void testMonotonicityCloseTo1Strict() {
    double mantissaPlus1 = 1;
    for (long l = 0; l < 10_000_000L; ++l) {
      double nextMantissaPlus1 = Math.nextUp(mantissaPlus1);
      assertThat(testFunction(nextMantissaPlus1))
          .isGreaterThanOrEqualTo(testFunction(mantissaPlus1));
      mantissaPlus1 = nextMantissaPlus1;
    }
  }

  @Test
  public void testLowerBoundApproximation() {
    final double[] absoluteBinWidthLimits = {
      1e-6, 1e-5, 1e-4, 1e-3, 1e-2, 1e-1, 1e0, 1e1, 1e2, 1e3
    };
    final double[] relativeBinWidthLimits = {
      0, 1e-100, 1e-6, 1e-5, 1e-4, 1e-3, 1e-2, 1e-1, 1e0, 1e1, 1e2, 1e3
    };
    for (final double absoluteBinWidthLimit : absoluteBinWidthLimits) {
      for (final double relativeBinWidthLimit : relativeBinWidthLimits) {
        LogQuadraticLayout layout =
            LogQuadraticLayout.create(
                absoluteBinWidthLimit,
                relativeBinWidthLimit,
                -absoluteBinWidthLimit * 1e6,
                absoluteBinWidthLimit * 1e6);
        assertThat(LayoutTestUtil.maxLowerBoundApproximationOffset(layout))
            .isLessThanOrEqualTo(2000L);
      }
    }
  }
}
