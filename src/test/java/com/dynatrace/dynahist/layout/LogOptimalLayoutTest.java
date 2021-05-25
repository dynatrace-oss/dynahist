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

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import com.dynatrace.dynahist.util.Algorithms;
import java.io.IOException;
import org.assertj.core.data.Offset;
import org.junit.Test;

public class LogOptimalLayoutTest extends AbstractErrorLimitingLayoutTest {

  @Override
  protected AbstractLayout createLayout(
      double absoluteBinWidthLimit,
      double relativeBinWidthLimit,
      double valueRangeLowerBound,
      double valueRangeUpperBound) {
    return LogOptimalLayout.create(
        absoluteBinWidthLimit, relativeBinWidthLimit, valueRangeLowerBound, valueRangeUpperBound);
  }

  @Test
  public void testOverflowAndUnderflowIndices() {
    {
      LogOptimalLayout layout = LogOptimalLayout.create(1e-7, 1e-6, -1e12, 1e12);
      assertEquals(30933622, layout.getOverflowBinIndex());
      assertEquals(-30933623, layout.getUnderflowBinIndex());
    }
    {
      LogOptimalLayout layout = LogOptimalLayout.create(1e-7, 1e-6, 1e12, 1e12);
      assertEquals(30933622, layout.getOverflowBinIndex());
      assertEquals(30933620, layout.getUnderflowBinIndex());
    }
  }

  @Test
  public void testSerialization() throws IOException {
    double valueRangeUpperBound = 1e7;
    double valueRangeLowerBound = -1e6;
    double relativeBinWidthLimit = 1e-3;
    double absoluteBinWidthLimit = 1e-9;
    LogOptimalLayout layout =
        LogOptimalLayout.create(
            absoluteBinWidthLimit,
            relativeBinWidthLimit,
            valueRangeLowerBound,
            valueRangeUpperBound);
    LogOptimalLayout deserializedLayout =
        SerializationTestUtil.testSerialization(
            layout,
            LogOptimalLayout::write,
            LogOptimalLayout::read,
            "003E112E0BE826D6953F50624DD2F1A9FCCBBF03CAE303");

    assertEquals(deserializedLayout, layout);
  }

  @Test
  public void testToString() {
    Layout layout = LogOptimalLayout.create(1e-8, 1e-2, -1e6, 1e6);
    assertEquals(
        "LogOptimalLayout [absoluteBinWidthLimit=1.0E-8, relativeBinWidthLimit=0.01, underflowBinIndex=-2878, overflowBinIndex=2877]",
        layout.toString());
  }

  @Test
  public void testGetWidth() {
    Layout layout = LogOptimalLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createStatic(layout);
    histogram.addValue(0);
    histogram.addValue(10);
    assertEquals(9.999999999999999E-9, histogram.getFirstNonEmptyBin().getWidth(), 0);
    assertEquals(0.08473892129759442, histogram.getLastNonEmptyBin().getWidth(), 0);
  }

  @Test
  public void testEquals() {
    Layout layout = LogOptimalLayout.create(1e-8, 1e-2, -1e6, 1e6);
    assertFalse(layout.equals(null));
    assertFalse(layout.equals(LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6)));
    assertFalse(layout.equals(LogOptimalLayout.create(1e-7, 1e-2, -1e6, 1e6)));
    assertFalse(
        LogOptimalLayout.create(1, 0, 1, 10).equals(LogOptimalLayout.create(1, 1e-3, 1, 10)));
    assertFalse(layout.equals(LogOptimalLayout.create(1e-8, 1e-2, -1e5, 1e6)));
    assertFalse(layout.equals(LogOptimalLayout.create(1e-8, 1e-2, -1e6, 1e5)));
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

        double factorNormal = LogOptimalLayout.calculateFactorNormal(relativeBinWidthLimit);
        double factorSubnormal = LogOptimalLayout.calculateFactorSubNormal(absoluteBinWidthLimit);
        int firstNormalIdx = LogOptimalLayout.calculateFirstNormalIndex(relativeBinWidthLimit);
        long unsignedValueBitsNormalLimitApproximate =
            LogOptimalLayout.calculateUnsignedValueBitsNormalLimitApproximate(
                factorSubnormal, firstNormalIdx);
        long unsignedValueBitsNormalLimit =
            LogOptimalLayout.calculateUnsignedValueBitsNormalLimit(factorSubnormal, firstNormalIdx);
        double unsignedNormalLimit = Double.longBitsToDouble(unsignedValueBitsNormalLimit);

        double offsetApproximate =
            LogOptimalLayout.calculateOffsetApproximate(
                unsignedNormalLimit, factorNormal, firstNormalIdx);
        double offset =
            LogOptimalLayout.calculateOffset(
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
    assertEquals(-1348565571, createLayout(1e-6, 1e-4, -10, 1000).hashCode());
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
        LogLinearLayout layout =
            LogLinearLayout.create(
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
