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
package com.dynatrace.dynahist.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import java.io.IOException;
import org.junit.Test;

public class ErrorLimitingLayout1Test extends AbstractErrorLimitingLayoutTest {

  @Test
  public void test() {
    assertTrue(4. * StrictMath.log1p(Double.MAX_VALUE) <= 2840d);
  }

  @Test
  public void testCalculateOffsetSpecialValues() {
    assertEquals(2049d, ErrorLimitingLayout1.calculateOffset(Long.MAX_VALUE), 0d);
    assertEquals(2049d, ErrorLimitingLayout1.calculateOffset(0x7fffffffffffffffL), 0d);
    assertEquals(
        2048.5, ErrorLimitingLayout1.calculateOffset(Double.doubleToLongBits(Double.NaN)), 0d);
    assertEquals(
        2048d,
        ErrorLimitingLayout1.calculateOffset(Double.doubleToLongBits(Double.POSITIVE_INFINITY)),
        0d);
    assertEquals(
        2d, ErrorLimitingLayout1.calculateOffset(Double.doubleToLongBits(Double.MIN_NORMAL)), 0d);
    assertEquals(1d, ErrorLimitingLayout1.calculateOffset(0L), 0d);
  }

  @Override
  protected ErrorLimitingLayout1 createLayout(
      double absoluteError, double relativeError, double minValue, double maxValue) {
    return ErrorLimitingLayout1.create(absoluteError, relativeError, minValue, maxValue);
  }

  @Test
  public void testOverflowAndUnderflowIndices() {
    {
      ErrorLimitingLayout1 layout = ErrorLimitingLayout1.create(1e-7, 1e-6, -1e12, 1e12);
      assertEquals(44219012, layout.getOverflowBinIndex());
      assertEquals(-44219013, layout.getUnderflowBinIndex());
    }
    {
      ErrorLimitingLayout1 layout = ErrorLimitingLayout1.create(1e-7, 1e-6, 1e12, 1e12);
      assertEquals(44219012, layout.getOverflowBinIndex());
      assertEquals(44219010, layout.getUnderflowBinIndex());
    }
  }

  @Test
  public void testSerialization() throws IOException {
    double maxValue = 1e7;
    double minValue = -1e6;
    double relativeError = 1e-3;
    double absoluteError = 1e-9;
    ErrorLimitingLayout1 layout =
        ErrorLimitingLayout1.create(absoluteError, relativeError, minValue, maxValue);
    ErrorLimitingLayout1 deserializedLayout =
        SerializationTestUtil.testSerialization(
            layout,
            ErrorLimitingLayout1::write,
            ErrorLimitingLayout1::read,
            "003E112E0BE826D6953F50624DD2F1A9FCDFFE048CB205");

    assertEquals(deserializedLayout, layout);
  }

  @Test
  public void testToString() {
    Layout layout = ErrorLimitingLayout1.create(1e-8, 1e-2, -1e6, 1e6);
    assertEquals(
        "ErrorLimitingLayout1 [absoluteError=1.0E-8, relativeError=0.01, underflowBinIndex=-4107, overflowBinIndex=4106]",
        layout.toString());
  }

  @Test
  public void testGetWidth() {
    Layout layout = ErrorLimitingLayout1.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createStatic(layout);
    histogram.addValue(0);
    histogram.addValue(10);
    assertEquals(9.999999999999999E-9, histogram.getFirstNonEmptyBin().getWidth(), 0);
    assertEquals(0.057622250121310614, histogram.getLastNonEmptyBin().getWidth(), 0);
  }

  @Test
  public void testEquals() {
    Layout layout = ErrorLimitingLayout1.create(1e-8, 1e-2, -1e6, 1e6);
    assertFalse(layout.equals(null));
    assertFalse(layout.equals(ErrorLimitingLayout2.create(1e-8, 1e-2, -1e6, 1e6)));
    assertFalse(layout.equals(ErrorLimitingLayout1.create(1e-7, 1e-2, -1e6, 1e6)));
    assertFalse(
        ErrorLimitingLayout1.create(1, 0, 1, 10)
            .equals(ErrorLimitingLayout1.create(1, 1e-3, 1, 10)));
    assertFalse(layout.equals(ErrorLimitingLayout1.create(1e-8, 1e-2, -1e5, 1e6)));
    assertFalse(layout.equals(ErrorLimitingLayout1.create(1e-8, 1e-2, -1e6, 1e5)));
  }

  @Test
  public void testCalculateFunctions() {
    assertThrows(
        IllegalArgumentException.class, () -> ErrorLimitingLayout1.create(-1, 1e-2, -1e6, 1e6));
    assertThrows(
        IllegalArgumentException.class, () -> ErrorLimitingLayout1.create(1e-8, -1, -1e6, 1e6));
  }
}
