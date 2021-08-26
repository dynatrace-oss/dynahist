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

import static com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import com.dynatrace.dynahist.util.Algorithms;
import java.math.BigInteger;
import java.util.function.LongPredicate;
import org.junit.Test;

public class OpenTelemetryExponentialBucketsLayoutTest {

  @Test
  public void testConsistency() {
    for (int precision = 0; precision <= MAX_PRECISION; ++precision) {
      Layout layout = OpenTelemetryExponentialBucketsLayout.create(precision);
      LayoutTestUtil.assertConsistency(layout);
    }
  }

  @Test
  public void testMapping0() {
    Layout layout = OpenTelemetryExponentialBucketsLayout.create(0);

    assertEquals(0, layout.mapToBinIndex(Double.longBitsToDouble(0L)));
    assertEquals(1, layout.mapToBinIndex(Double.longBitsToDouble(1L)));
    assertEquals(2, layout.mapToBinIndex(Double.longBitsToDouble(2L)));
    assertEquals(2, layout.mapToBinIndex(Double.longBitsToDouble(3L)));
    assertEquals(3, layout.mapToBinIndex(Double.longBitsToDouble(4L)));
    assertEquals(3, layout.mapToBinIndex(Double.longBitsToDouble(5L)));
    assertEquals(3, layout.mapToBinIndex(Double.longBitsToDouble(6L)));
    assertEquals(3, layout.mapToBinIndex(Double.longBitsToDouble(7L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(8L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(9L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(10L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(11L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(12L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(13L)));
    assertEquals(53, layout.mapToBinIndex(Double.MIN_NORMAL));
    assertEquals(1074, layout.mapToBinIndex(0.5));
    assertEquals(1075, layout.mapToBinIndex(1));
    assertEquals(2097, layout.mapToBinIndex(Double.MAX_VALUE / 2.));
    assertEquals(2098, layout.mapToBinIndex(Double.MAX_VALUE));
    assertEquals(2099, layout.mapToBinIndex(Double.POSITIVE_INFINITY));
    assertEquals(
        2099, layout.mapToBinIndex(Double.longBitsToDouble(0x7ff0000000000001L))); // "smallest" NaN
    assertEquals(
        2099, layout.mapToBinIndex(Double.longBitsToDouble(0x7ff8000000000000L))); // standard NaN
    assertEquals(
        2099, layout.mapToBinIndex(Double.longBitsToDouble(0x7fffffffffffffffL))); // "greatest" NaN

    assertEquals(0, layout.mapToBinIndex(-Double.longBitsToDouble(0L)));
    assertEquals(-1, layout.mapToBinIndex(-Double.longBitsToDouble(1L)));
    assertEquals(-2, layout.mapToBinIndex(-Double.longBitsToDouble(2L)));
    assertEquals(-2, layout.mapToBinIndex(-Double.longBitsToDouble(3L)));
    assertEquals(-3, layout.mapToBinIndex(-Double.longBitsToDouble(4L)));
    assertEquals(-3, layout.mapToBinIndex(-Double.longBitsToDouble(5L)));
    assertEquals(-3, layout.mapToBinIndex(-Double.longBitsToDouble(6L)));
    assertEquals(-3, layout.mapToBinIndex(-Double.longBitsToDouble(7L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(8L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(9L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(10L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(11L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(12L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(13L)));
    assertEquals(-53, layout.mapToBinIndex(-Double.MIN_NORMAL));
    assertEquals(-1074, layout.mapToBinIndex(-0.5));
    assertEquals(-1075, layout.mapToBinIndex(-1));
    assertEquals(-2097, layout.mapToBinIndex(-Double.MAX_VALUE / 2.));
    assertEquals(-2098, layout.mapToBinIndex(-Double.MAX_VALUE));
    assertEquals(-2099, layout.mapToBinIndex(-Double.POSITIVE_INFINITY));
    assertEquals(-2099, layout.mapToBinIndex(Double.longBitsToDouble(0xfff0000000000001L)));
    assertEquals(-2099, layout.mapToBinIndex(Double.longBitsToDouble(0xfff8000000000000L)));
    assertEquals(-2099, layout.mapToBinIndex(Double.longBitsToDouble(0xffffffffffffffffL)));
  }

  @Test
  public void testMapping1() {
    Layout layout = OpenTelemetryExponentialBucketsLayout.create(1);

    assertEquals(0, layout.mapToBinIndex(Double.longBitsToDouble(0L)));
    assertEquals(1, layout.mapToBinIndex(Double.longBitsToDouble(1L)));
    assertEquals(2, layout.mapToBinIndex(Double.longBitsToDouble(2L)));
    assertEquals(3, layout.mapToBinIndex(Double.longBitsToDouble(3L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(4L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(5L)));
    assertEquals(5, layout.mapToBinIndex(Double.longBitsToDouble(6L)));
    assertEquals(5, layout.mapToBinIndex(Double.longBitsToDouble(7L)));
    assertEquals(6, layout.mapToBinIndex(Double.longBitsToDouble(8L)));
    assertEquals(6, layout.mapToBinIndex(Double.longBitsToDouble(9L)));
    assertEquals(6, layout.mapToBinIndex(Double.longBitsToDouble(10L)));
    assertEquals(6, layout.mapToBinIndex(Double.longBitsToDouble(11L)));
    assertEquals(7, layout.mapToBinIndex(Double.longBitsToDouble(12L)));
    assertEquals(7, layout.mapToBinIndex(Double.longBitsToDouble(13L)));
    assertEquals(104, layout.mapToBinIndex(Double.MIN_NORMAL));
    assertEquals(2146, layout.mapToBinIndex(0.5));
    assertEquals(2148, layout.mapToBinIndex(1));
    assertEquals(4193, layout.mapToBinIndex(Double.MAX_VALUE / 2.));
    assertEquals(4195, layout.mapToBinIndex(Double.MAX_VALUE));
    assertEquals(4196, layout.mapToBinIndex(Double.POSITIVE_INFINITY));
    assertEquals(
        4196, layout.mapToBinIndex(Double.longBitsToDouble(0x7ff0000000000001L))); // "smallest" NaN
    assertEquals(
        4197, layout.mapToBinIndex(Double.longBitsToDouble(0x7ff8000000000000L))); // standard NaN
    assertEquals(
        4197, layout.mapToBinIndex(Double.longBitsToDouble(0x7fffffffffffffffL))); // "greatest" NaN

    assertEquals(0, layout.mapToBinIndex(-Double.longBitsToDouble(0L)));
    assertEquals(-1, layout.mapToBinIndex(-Double.longBitsToDouble(1L)));
    assertEquals(-2, layout.mapToBinIndex(-Double.longBitsToDouble(2L)));
    assertEquals(-3, layout.mapToBinIndex(-Double.longBitsToDouble(3L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(4L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(5L)));
    assertEquals(-5, layout.mapToBinIndex(-Double.longBitsToDouble(6L)));
    assertEquals(-5, layout.mapToBinIndex(-Double.longBitsToDouble(7L)));
    assertEquals(-6, layout.mapToBinIndex(-Double.longBitsToDouble(8L)));
    assertEquals(-6, layout.mapToBinIndex(-Double.longBitsToDouble(9L)));
    assertEquals(-6, layout.mapToBinIndex(-Double.longBitsToDouble(10L)));
    assertEquals(-6, layout.mapToBinIndex(-Double.longBitsToDouble(11L)));
    assertEquals(-7, layout.mapToBinIndex(-Double.longBitsToDouble(12L)));
    assertEquals(-7, layout.mapToBinIndex(-Double.longBitsToDouble(13L)));
    assertEquals(-104, layout.mapToBinIndex(-Double.MIN_NORMAL));
    assertEquals(-2146, layout.mapToBinIndex(-0.5));
    assertEquals(-2148, layout.mapToBinIndex(-1));
    assertEquals(-4193, layout.mapToBinIndex(-Double.MAX_VALUE / 2.));
    assertEquals(-4195, layout.mapToBinIndex(-Double.MAX_VALUE));
    assertEquals(-4196, layout.mapToBinIndex(-Double.POSITIVE_INFINITY));
    assertEquals(-4196, layout.mapToBinIndex(Double.longBitsToDouble(0xfff0000000000001L)));
    assertEquals(-4197, layout.mapToBinIndex(Double.longBitsToDouble(0xfff8000000000000L)));
    assertEquals(-4197, layout.mapToBinIndex(Double.longBitsToDouble(0xffffffffffffffffL)));
  }

  @Test
  public void testMapping2() {
    Layout layout = OpenTelemetryExponentialBucketsLayout.create(2);

    assertEquals(0, layout.mapToBinIndex(Double.longBitsToDouble(0L)));
    assertEquals(1, layout.mapToBinIndex(Double.longBitsToDouble(1L)));
    assertEquals(2, layout.mapToBinIndex(Double.longBitsToDouble(2L)));
    assertEquals(3, layout.mapToBinIndex(Double.longBitsToDouble(3L)));
    assertEquals(4, layout.mapToBinIndex(Double.longBitsToDouble(4L)));
    assertEquals(5, layout.mapToBinIndex(Double.longBitsToDouble(5L)));
    assertEquals(6, layout.mapToBinIndex(Double.longBitsToDouble(6L)));
    assertEquals(7, layout.mapToBinIndex(Double.longBitsToDouble(7L)));
    assertEquals(8, layout.mapToBinIndex(Double.longBitsToDouble(8L)));
    assertEquals(8, layout.mapToBinIndex(Double.longBitsToDouble(9L)));
    assertEquals(9, layout.mapToBinIndex(Double.longBitsToDouble(10L)));
    assertEquals(9, layout.mapToBinIndex(Double.longBitsToDouble(11L)));
    assertEquals(10, layout.mapToBinIndex(Double.longBitsToDouble(12L)));
    assertEquals(10, layout.mapToBinIndex(Double.longBitsToDouble(13L)));
    assertEquals(11, layout.mapToBinIndex(Double.longBitsToDouble(14L)));
    assertEquals(11, layout.mapToBinIndex(Double.longBitsToDouble(15L)));
    assertEquals(12, layout.mapToBinIndex(Double.longBitsToDouble(16L)));
    assertEquals(12, layout.mapToBinIndex(Double.longBitsToDouble(17L)));
    assertEquals(12, layout.mapToBinIndex(Double.longBitsToDouble(18L)));
    assertEquals(12, layout.mapToBinIndex(Double.longBitsToDouble(19L)));
    assertEquals(13, layout.mapToBinIndex(Double.longBitsToDouble(20L)));
    assertEquals(204, layout.mapToBinIndex(Double.MIN_NORMAL));
    assertEquals(4288, layout.mapToBinIndex(0.5));
    assertEquals(4292, layout.mapToBinIndex(1));
    assertEquals(8383, layout.mapToBinIndex(Double.MAX_VALUE / 2.));
    assertEquals(8387, layout.mapToBinIndex(Double.MAX_VALUE));
    assertEquals(8388, layout.mapToBinIndex(Double.POSITIVE_INFINITY));
    assertEquals(
        8388, layout.mapToBinIndex(Double.longBitsToDouble(0x7ff0000000000001L))); // "smallest" NaN
    assertEquals(
        8390, layout.mapToBinIndex(Double.longBitsToDouble(0x7ff8000000000000L))); // standard NaN
    assertEquals(
        8391, layout.mapToBinIndex(Double.longBitsToDouble(0x7fffffffffffffffL))); // "greatest" NaN

    assertEquals(0, layout.mapToBinIndex(-Double.longBitsToDouble(0L)));
    assertEquals(-1, layout.mapToBinIndex(-Double.longBitsToDouble(1L)));
    assertEquals(-2, layout.mapToBinIndex(-Double.longBitsToDouble(2L)));
    assertEquals(-3, layout.mapToBinIndex(-Double.longBitsToDouble(3L)));
    assertEquals(-4, layout.mapToBinIndex(-Double.longBitsToDouble(4L)));
    assertEquals(-5, layout.mapToBinIndex(-Double.longBitsToDouble(5L)));
    assertEquals(-6, layout.mapToBinIndex(-Double.longBitsToDouble(6L)));
    assertEquals(-7, layout.mapToBinIndex(-Double.longBitsToDouble(7L)));
    assertEquals(-8, layout.mapToBinIndex(-Double.longBitsToDouble(8L)));
    assertEquals(-8, layout.mapToBinIndex(-Double.longBitsToDouble(9L)));
    assertEquals(-9, layout.mapToBinIndex(-Double.longBitsToDouble(10L)));
    assertEquals(-9, layout.mapToBinIndex(-Double.longBitsToDouble(11L)));
    assertEquals(-10, layout.mapToBinIndex(-Double.longBitsToDouble(12L)));
    assertEquals(-10, layout.mapToBinIndex(-Double.longBitsToDouble(13L)));
    assertEquals(-11, layout.mapToBinIndex(-Double.longBitsToDouble(14L)));
    assertEquals(-11, layout.mapToBinIndex(-Double.longBitsToDouble(15L)));
    assertEquals(-12, layout.mapToBinIndex(-Double.longBitsToDouble(16L)));
    assertEquals(-12, layout.mapToBinIndex(-Double.longBitsToDouble(17L)));
    assertEquals(-12, layout.mapToBinIndex(-Double.longBitsToDouble(18L)));
    assertEquals(-12, layout.mapToBinIndex(-Double.longBitsToDouble(19L)));
    assertEquals(-13, layout.mapToBinIndex(-Double.longBitsToDouble(20L)));
    assertEquals(-204, layout.mapToBinIndex(-Double.MIN_NORMAL));
    assertEquals(-4288, layout.mapToBinIndex(-0.5));
    assertEquals(-4292, layout.mapToBinIndex(-1));
    assertEquals(-8383, layout.mapToBinIndex(-Double.MAX_VALUE / 2.));
    assertEquals(-8387, layout.mapToBinIndex(-Double.MAX_VALUE));
    assertEquals(-8388, layout.mapToBinIndex(-Double.POSITIVE_INFINITY));
    assertEquals(-8388, layout.mapToBinIndex(Double.longBitsToDouble(0xfff0000000000001L)));
    assertEquals(-8390, layout.mapToBinIndex(Double.longBitsToDouble(0xfff8000000000000L)));
    assertEquals(-8391, layout.mapToBinIndex(Double.longBitsToDouble(0xffffffffffffffffL)));
  }

  @Test
  public void testLowerBoundApproximation() {
    for (int precision = 0; precision <= MAX_PRECISION; ++precision) {
      OpenTelemetryExponentialBucketsLayout layout =
          OpenTelemetryExponentialBucketsLayout.create(precision);
      assertThat(LayoutTestUtil.maxLowerBoundApproximationOffset(layout)).isEqualTo(0L);
    }
  }

  @Test
  public void testBoundaryConsistency() {
    double tolerance = 1e-14;
    for (int precision = 0; precision <= MAX_PRECISION; ++precision) {
      double relativeErrorLimit = Math.pow(2., Math.pow(2., -precision)) * (1 + tolerance);

      int len = 1 << precision;
      long[] boundaries = OpenTelemetryExponentialBucketsLayout.calculateBoundaries(precision);
      assertThat(2 * boundaries[0]).isGreaterThanOrEqualTo(1L << (52 - precision));
      for (int i = 1; i < len; ++i) {
        assertThat(boundaries[i - 1]).isLessThan(boundaries[i]);
      }
      for (int i = 1; i < len; ++i) {
        assertThat(boundaries[i - 1] - ((i == 1) ? 0L : boundaries[i - 2]))
            .isLessThanOrEqualTo(boundaries[i] - boundaries[i - 1]);
      }
      for (int i = 0; i < len; ++i) {
        double low =
            (i > 0) ? Double.longBitsToDouble(0x3ff0000000000000L | boundaries[i - 1]) : 1.;
        double high =
            (i < len - 1) ? Double.longBitsToDouble(0x3ff0000000000000L | boundaries[i]) : 2.;
        assertThat(low).isLessThanOrEqualTo(high);
        assertThat(low * relativeErrorLimit).isGreaterThanOrEqualTo(high);
      }
    }
  }

  @Test
  public void testHashCode() {
    Layout layout = OpenTelemetryExponentialBucketsLayout.create(3);
    assertEquals(93, layout.hashCode());
  }

  @Test
  public void testToString() {
    Layout layout = OpenTelemetryExponentialBucketsLayout.create(3);
    assertEquals("OpenTelemetryExponentialBucketsLayout [precision=3]", layout.toString());
  }

  @Test
  public void testEquals() {
    Layout layout3a = OpenTelemetryExponentialBucketsLayout.create(3);
    Layout layout3b = OpenTelemetryExponentialBucketsLayout.create(3);
    Layout layout3c = new OpenTelemetryExponentialBucketsLayout(3);
    Layout layout4 = OpenTelemetryExponentialBucketsLayout.create(4);

    assertTrue(layout3a == layout3b);
    assertTrue(layout3a != layout3c);
    assertTrue(layout3a.equals(layout3a));
    assertTrue(layout3a.equals(layout3b));
    assertTrue(layout3a.equals(layout3c));
    assertFalse(layout3a.equals(layout4));
    assertFalse(layout3a.equals(new Object()));
    assertFalse(layout3a.equals(null));
  }

  @Test
  public void testCreate() {
    assertThrows(
        IllegalArgumentException.class, () -> OpenTelemetryExponentialBucketsLayout.create(-1));
    assertThrows(
        IllegalArgumentException.class,
        () -> OpenTelemetryExponentialBucketsLayout.create(MAX_PRECISION + 1));
  }

  @Test
  public void testAccuracy() {
    double tolerance = 1e-14;
    for (int precision = 0; precision <= MAX_PRECISION; ++precision) {
      OpenTelemetryExponentialBucketsLayout layout =
          OpenTelemetryExponentialBucketsLayout.create(precision);
      double relativeErrorLimit = Math.pow(2., Math.pow(2., -precision)) * (1 + tolerance);
      for (int i = layout.getUnderflowBinIndex() + 1; i < layout.getOverflowBinIndex(); ++i) {
        double low = layout.getBinLowerBound(i);
        double high = layout.getBinUpperBound(i);
        if (low > 0. && high > 0.) {
          assertThat(low).isLessThanOrEqualTo(high);
          assertThat(low * relativeErrorLimit).isGreaterThanOrEqualTo(high);
        } else if (low < 0. && high < 0.) {
          assertThat(low).isLessThanOrEqualTo(high);
          assertThat(high * relativeErrorLimit).isLessThanOrEqualTo(low);
        } else {
          assertEquals(0., low, 0.);
          assertEquals(0., high, 0.);
        }
      }
    }
  }

  @Test
  public void testInclusiveness() {
    for (int precision = 0; precision <= MAX_PRECISION; ++precision) {
      OpenTelemetryExponentialBucketsLayout layout =
          OpenTelemetryExponentialBucketsLayout.create(precision);
      for (int exponent = -1000; exponent <= 1000; ++exponent) {
        double x = Math.pow(2., exponent);
        assertThat(layout.mapToBinIndex(x)).isGreaterThan(layout.mapToBinIndex(Math.nextDown(x)));
        assertThat(layout.mapToBinIndex(x)).isEqualTo(layout.mapToBinIndex(Math.nextUp(x)));
        assertThat(layout.mapToBinIndex(-x)).isLessThan(layout.mapToBinIndex(Math.nextUp(-x)));
        assertThat(layout.mapToBinIndex(-x)).isEqualTo(layout.mapToBinIndex(Math.nextDown(-x)));
      }
    }
  }

  @Test
  public void testSqrt2() {

    final double sqrt2LowerBound = Math.nextDown(StrictMath.sqrt(2.));
    final double sqrt2UpperBound = Math.nextUp(sqrt2LowerBound);

    assertThat(Math.pow(sqrt2LowerBound, 2)).isLessThan(2.);
    assertThat(Math.pow(sqrt2UpperBound, 2)).isGreaterThan(2.);
    for (int precision = 1; precision <= MAX_PRECISION; ++precision) {
      OpenTelemetryExponentialBucketsLayout layout =
          OpenTelemetryExponentialBucketsLayout.create(precision);
      for (int exponent = -100; exponent <= 100; ++exponent) {
        assertThat(layout.mapToBinIndex(sqrt2UpperBound))
            .isGreaterThan(layout.mapToBinIndex(sqrt2LowerBound));
        assertThat(layout.mapToBinIndex(sqrt2UpperBound))
            .isEqualTo(layout.mapToBinIndex(Math.nextUp(sqrt2UpperBound)));
        assertThat(layout.mapToBinIndex(sqrt2LowerBound))
            .isEqualTo(layout.mapToBinIndex(Math.nextDown(sqrt2LowerBound)));
        assertThat(layout.mapToBinIndex(-sqrt2UpperBound))
            .isLessThan(layout.mapToBinIndex(-sqrt2LowerBound));
        assertThat(layout.mapToBinIndex(-sqrt2UpperBound))
            .isEqualTo(layout.mapToBinIndex(Math.nextDown(-sqrt2UpperBound)));
        assertThat(layout.mapToBinIndex(-sqrt2LowerBound))
            .isEqualTo(layout.mapToBinIndex(Math.nextUp(-sqrt2LowerBound)));
      }
    }
  }

  @Test
  public void testNumBuckets() {
    StringBuilder sb = new StringBuilder();

    sb.append(" p    num. positive buckets    relative bucket width\n");
    sb.append("----------------------------------------------------\n");

    for (int precision = 0; precision <= MAX_PRECISION; ++precision) {
      OpenTelemetryExponentialBucketsLayout layout =
          OpenTelemetryExponentialBucketsLayout.create(precision);

      String precisionStr = Integer.toString(precision);
      String bucketStr = Integer.toString(layout.getOverflowBinIndex() - 1);
      String widthStr =
          String.format("%.3f", ((Math.pow(2., Math.pow(2., -precision)) - 1) * 100.)) + " %";

      String paddedPrecisionStr = "  ".substring(precisionStr.length()) + precisionStr;
      String paddedBucketStr =
          "                         ".substring(bucketStr.length()) + bucketStr;
      String paddedWidthStr = "                         ".substring(widthStr.length()) + widthStr;

      sb.append(paddedPrecisionStr).append(paddedBucketStr).append(paddedWidthStr).append('\n');
    }

    assertThat(sb.toString())
        .isEqualTo(
            ""
                + " p    num. positive buckets    relative bucket width\n"
                + "----------------------------------------------------\n"
                + " 0                     2098                100.000 %\n"
                + " 1                     4195                 41.421 %\n"
                + " 2                     8387                 18.921 %\n"
                + " 3                    16767                  9.051 %\n"
                + " 4                    33518                  4.427 %\n"
                + " 5                    67005                  2.190 %\n"
                + " 6                   133946                  1.089 %\n"
                + " 7                   267764                  0.543 %\n"
                + " 8                   535273                  0.271 %\n"
                + " 9                  1070035                  0.135 %\n"
                + "10                  2139047                  0.068 %\n");
  }

  static long[] calculateExpectedBoundaries(int precision) {
    int len = 1 << precision;
    long[] boundaries = new long[len];
    boundaries[0] = 0L;
    for (int i = 0; i < len; ++i) {
      boundaries[i] = calculateBoundaryExact(len, i);
    }
    return boundaries;
  }

  @Test
  public void testBoundaries() {

    for (int precision = 0; precision <= MAX_PRECISION; ++precision) {

      int len = 1 << precision;

      long[] expectedBoundaries = calculateExpectedBoundaries(precision);
      long[] actualBoundaries = new long[len];

      OpenTelemetryExponentialBucketsLayout layout =
          OpenTelemetryExponentialBucketsLayout.create(precision);

      int startIndex = layout.mapToBinIndex(1.);

      for (int idx = 0; idx < len; ++idx) {
        actualBoundaries[idx] =
            Double.doubleToRawLongBits(layout.getBinLowerBound(startIndex + idx))
                & 0xfffffffffffffL;
      }

      assertThat(actualBoundaries).isEqualTo(expectedBoundaries);
    }
  }

  static long calculateBoundaryApproximate(int len, int i) {
    return 0x000fffffffffffffL & Double.doubleToRawLongBits(Math.pow(2., i / (double) len));
  }

  // This function calculates the mantissa representation m
  // of the smallest double-precision floating-point value
  // x := (1 + m * 2^-52) for which x >= 2^(i/len).
  // This is equivalent to (1 + m * 2^-52) >= 2^(i/len)
  // and further to (2^52 + m)^(len) >= 2^(52 * len + i).
  // This inequality can be evaluated exactly
  // and platform-independent using BigInteger.
  // m can then be found using binary search.
  static long calculateBoundaryExact(int len, int i) {
    final BigInteger expected = BigInteger.valueOf(2).pow(52 * len + i);
    LongPredicate predicate =
        m -> {
          BigInteger actual = BigInteger.valueOf(m).add(BigInteger.valueOf(1L << 52)).pow(len);
          return actual.compareTo(expected) >= 0;
        };
    long initialGuess = calculateBoundaryApproximate(len, i);
    return Algorithms.findFirst(predicate, 0x0000000000000000L, 0x0010000000000000L, initialGuess);
  }

  @Test
  public void testPrecalculatedBoundaryConstans() {

    long[] expected = new long[1 << MAX_PRECISION];
    long[] actual = new long[1 << MAX_PRECISION];

    for (int i = 0; i < 1 << MAX_PRECISION; ++i) {
      expected[i] = calculateBoundaryExact(1 << MAX_PRECISION, i);
      actual[i] = getBoundaryConstant(i);
    }

    // System.out.println(LongStream.of(expected).mapToObj(l -> String.format("0x%013xL",
    // l)).collect(Collectors.joining(",", "{", "}")));

    assertThat(actual).isEqualTo(expected);
  }
}
