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

public class LogLinearLayoutTest extends AbstractErrorLimitingLayoutTest {

  @Test
  public void test() {
    assertTrue(4. * StrictMath.log1p(Double.MAX_VALUE) <= 2840d);
  }

  @Test
  public void testMapToBinIndexHelperSpecialValues() {
    assertEquals(2049d, LogLinearLayout.mapToBinIndexHelper(Long.MAX_VALUE), 0d);
    assertEquals(2049d, LogLinearLayout.mapToBinIndexHelper(0x7fffffffffffffffL), 0d);
    assertEquals(
        2048.5, LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(Double.NaN)), 0d);
    assertEquals(
        2048d,
        LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(Double.POSITIVE_INFINITY)),
        0d);
    assertEquals(
        2d, LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(Double.MIN_NORMAL)), 0d);
    assertEquals(1d, LogLinearLayout.mapToBinIndexHelper(0L), 0d);

    assertEquals(1022., LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(0.25)), 0d);
    assertEquals(1023., LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(0.5)), 0d);
    assertEquals(1024., LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(1)), 0d);
    assertEquals(1025., LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(2)), 0d);
    assertEquals(1026., LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(4)), 0d);
    assertEquals(1027., LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(8)), 0d);
    assertEquals(1028., LogLinearLayout.mapToBinIndexHelper(Double.doubleToLongBits(16)), 0d);
  }

  @Override
  protected AbstractLayout createLayout(
      double absoluteBinWidthLimit,
      double relativeBinWidthLimit,
      double valueRangeLowerBound,
      double valueRangeUpperBound) {
    return LogLinearLayout.create(
        absoluteBinWidthLimit, relativeBinWidthLimit, valueRangeLowerBound, valueRangeUpperBound);
  }

  @Test
  public void testOverflowAndUnderflowIndices() {
    {
      LogLinearLayout layout = LogLinearLayout.create(1e-7, 1e-6, -1e12, 1e12);
      assertEquals(44219012, layout.getOverflowBinIndex());
      assertEquals(-44219013, layout.getUnderflowBinIndex());
    }
    {
      LogLinearLayout layout = LogLinearLayout.create(1e-7, 1e-6, 1e12, 1e12);
      assertEquals(44219012, layout.getOverflowBinIndex());
      assertEquals(44219010, layout.getUnderflowBinIndex());
    }
  }

  @Test
  public void testSerialization() throws IOException {
    double valueRangeUpperBound = 1e7;
    double valueRangeLowerBound = -1e6;
    double relativeBinWidthLimit = 1e-3;
    double absoluteBinWidthLimit = 1e-9;
    LogLinearLayout layout =
        LogLinearLayout.create(
            absoluteBinWidthLimit,
            relativeBinWidthLimit,
            valueRangeLowerBound,
            valueRangeUpperBound);
    LogLinearLayout deserializedLayout =
        SerializationTestUtil.testSerialization(
            layout,
            LogLinearLayout::write,
            LogLinearLayout::read,
            "003E112E0BE826D6953F50624DD2F1A9FCDFFE048CB205");

    assertEquals(deserializedLayout, layout);
  }

  @Test
  public void testToString() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    assertEquals(
        "LogLinearLayout [absoluteBinWidthLimit=1.0E-8, relativeBinWidthLimit=0.01, underflowBinIndex=-4107, overflowBinIndex=4106]",
        layout.toString());
  }

  @Test
  public void testGetWidth() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createStatic(layout);
    histogram.addValue(0);
    histogram.addValue(10);
    assertEquals(9.999999999999999E-9, histogram.getFirstNonEmptyBin().getWidth(), 0);
    assertEquals(0.057622250121310614, histogram.getLastNonEmptyBin().getWidth(), 0);
  }

  @Test
  public void testEquals() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    assertFalse(layout.equals(null));
    assertFalse(layout.equals(LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6)));
    assertFalse(layout.equals(LogLinearLayout.create(1e-7, 1e-2, -1e6, 1e6)));
    assertFalse(LogLinearLayout.create(1, 0, 1, 10).equals(LogLinearLayout.create(1, 1e-3, 1, 10)));
    assertFalse(layout.equals(LogLinearLayout.create(1e-8, 1e-2, -1e5, 1e6)));
    assertFalse(layout.equals(LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e5)));
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

        double factorNormal = LogLinearLayout.calculateFactorNormal(relativeBinWidthLimit);
        double factorSubnormal = LogLinearLayout.calculateFactorSubNormal(absoluteBinWidthLimit);
        int firstNormalIdx = LogLinearLayout.calculateFirstNormalIndex(relativeBinWidthLimit);
        long unsignedValueBitsNormalLimitApproximate =
            LogLinearLayout.calculateUnsignedValueBitsNormalLimitApproximate(
                factorSubnormal, firstNormalIdx);
        long unsignedValueBitsNormalLimit =
            LogLinearLayout.calculateUnsignedValueBitsNormalLimit(factorSubnormal, firstNormalIdx);

        double offsetApproximate =
            LogLinearLayout.calculateOffsetApproximate(
                unsignedValueBitsNormalLimit, factorNormal, firstNormalIdx);
        double offset =
            LogLinearLayout.calculateOffset(
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
    assertEquals(-1299004750, createLayout(1e-6, 1e-4, -10, 1000).hashCode());
  }
}
