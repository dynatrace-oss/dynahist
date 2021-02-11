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
package com.dynatrace.dynahist.util;

import static com.dynatrace.dynahist.util.Preconditions.checkArgument;

import java.util.function.LongPredicate;

public final class Algorithms {

  private static final String INVALID_PREDICATE_MSG_FORMAT_STRING =
      "It is expected that the predicate evaluated at the maximum (%s) evaluates to true!";

  private Algorithms() {}

  /**
   * Interpolates the y-value at given x-value from two given points (x1, y1) and (x2, y2).
   *
   * <p>This implementation is strictly symmetric. Meaning that interpolate(x,x1,y1,x2,y2) ==
   * interpolate(x,x2,y2,x1,y1) always holds. Furthermore, it is guaranteed that the return value is
   * always in the range [min(y1,y2),max(y1,y2)]. In addition, this interpolation function is
   * monotonic in x.
   *
   * @param x the x-value
   * @param x1 the x-value of point 1
   * @param y1 the y-value of point 1
   * @param x2 the x-value of point 2
   * @param y2 the y-value of point 2
   * @return the interpolated y-value
   */
  public static double interpolate(double x, double x1, double y1, double x2, double y2) {
    if (Double.doubleToLongBits(y1) == Double.doubleToLongBits(y2)) {
      return y1;
    }
    if ((x <= x1 && x1 < x2) || (x >= x1 && x1 > x2)) {
      return y1;
    }
    if ((x <= x2 && x2 < x1) || (x >= x2 && x2 > x1)) {
      return y2;
    }
    final double r;
    if (x1 != x2 && Double.isFinite(y1) && Double.isFinite(y2)) {
      double deltaX = x2 - x1;
      double deltaY = y2 - y1;
      final double r1 = y1 + deltaY * ((x - x1) / deltaX);
      final double r2 = y2 + deltaY * ((x - x2) / deltaX);
      r = r1 * 0.5 + r2 * 0.5;
    } else {
      r = y1 * 0.5 + y2 * 0.5;
    }
    if (r >= y1 && r >= y2) {
      return Math.max(y1, y2);
    } else if (r <= y1 && r <= y2) {
      return Math.min(y1, y2);
    } else {
      return r;
    }
  }

  /**
   * Calculates the midpoint of two given {@code long} values rounded down to the nearest {@code
   * long} value.
   *
   * <p>This implementation works for any values which would lead to over- or underflows when
   * calculating the midpoint using (a + b) / 2 directly. Furthermore, this implementation is
   * branch-free.
   *
   * @param a the first value
   * @param b the second value
   * @return the midpoint
   */
  public static long calculateMidpoint(long a, long b) {
    long a2 = (a ^ 0x8000000000000000L) >>> 1;
    long b2 = (b ^ 0x8000000000000000L) >>> 1;
    return ((a2 + b2) + (a & b & 1L)) ^ 0x8000000000000000L;
  }

  public static final long NEGATIVE_INFINITY_MAPPED_TO_LONG =
      mapDoubleToLong(Double.NEGATIVE_INFINITY);

  public static final long POSITIVE_INFINITY_MAPPED_TO_LONG =
      mapDoubleToLong(Double.POSITIVE_INFINITY);

  /**
   * Bidirectional mapping of a {@code double} value to a {@code long} value.
   *
   * <p>Except for {@link Double#NaN} values, the natural ordering of double values as defined by
   * {@link Double#compare(double, double)} will be maintained.
   *
   * <p>Inverse mapping can be performed using {@link #mapLongToDouble(long)}.
   *
   * @param x the value
   * @return the corresponding long value
   */
  public static long mapDoubleToLong(double x) {
    long l = Double.doubleToRawLongBits(x);
    return ((l >> 62) >>> 1) ^ l;
  }

  /**
   * Bidirectional mapping of a {@code long} value to a {@code double} value.
   *
   * <p>Inverse mapping can be performed using {@link #mapDoubleToLong(double)}.
   *
   * @param l long value
   * @return the corresponding double value
   */
  public static double mapLongToDouble(long l) {
    return Double.longBitsToDouble(((l >> 62) >>> 1) ^ l);
  }

  /**
   * Finds the first long value in the range [min, max] for which the given predicate returns {@code
   * true}.
   *
   * <p>The predicate must return {@code false} for all long values smaller than some value X from
   * [min, max] and must return {@code true} for all long values equal to or greater than X. The
   * return value of this function will be X.
   *
   * <p>The time complexity is logarithmic in terms of the interval length max - min.
   *
   * @param predicate the predicate
   * @param min the lower bound of the search interval
   * @param max the upper bound of the search interval
   * @return the smallest value for which the predicate evaluates to {@code true}
   */
  public static long findFirst(LongPredicate predicate, final long min, final long max) {
    checkArgument(min <= max);
    long low = min;
    long high = max;
    while (low + 1 < high) {
      long mid = calculateMidpoint(low, high);
      if (predicate.test(mid)) {
        high = mid;
      } else {
        low = mid;
      }
    }
    checkArgument(high != max || predicate.test(high), INVALID_PREDICATE_MSG_FORMAT_STRING, max);
    if (low == min && low != high && predicate.test(min)) {
      return min;
    }
    return high;
  }

  /**
   * Finds the first long value in the range [min, max] for which the given predicate returns {@code
   * true}.
   *
   * <p>The predicate must return {@code false} for all long values smaller than some value X from
   * [min, max] and must return {@code true} for all long values equal to or greater than X. The
   * return value of this function will be X.
   *
   * <p>The time complexity is logarithmic in terms of the interval length max - min.
   *
   * <p>This function allows to give an initial guess which might speed up finding X, if the initial
   * guess is already close to X.
   *
   * @param predicate the predicate
   * @param min the lower bound of the search interval
   * @param max the upper bound of the search interval
   * @param initialGuess an initial guess
   * @return the smallest value for which the predicate evaluates to {@code true}
   */
  public static long findFirst(
      LongPredicate predicate, final long min, final long max, final long initialGuess) {
    checkArgument(min <= initialGuess);
    checkArgument(initialGuess <= max);

    long low;
    long high;
    long increment = 1;
    if (predicate.test(initialGuess)) {
      low = initialGuess;
      do {
        high = low;
        if (high == min) {
          return min;
        }
        low = high - increment;
        if (low >= high || low < min) {
          low = min;
        }
        increment <<= 1;
      } while (predicate.test(low));
    } else {
      high = initialGuess;
      do {
        low = high;
        checkArgument(low != max, INVALID_PREDICATE_MSG_FORMAT_STRING, max);
        high = low + increment;
        if (high <= low || high > max) {
          high = max;
        }
        increment <<= 1;
      } while (!predicate.test(high));
    }

    while (low + 1 < high) {
      long mid = calculateMidpoint(low, high);
      if (predicate.test(mid)) {
        high = mid;
      } else {
        low = mid;
      }
    }
    return high;
  }

  /**
   * Clips a given value to a given interval.
   *
   * @param value the value
   * @param min the minimum value of the interval (inclusive)
   * @param max the maximum value of the interval (inclusive)
   * @return the clipped value
   */
  public static int clip(int value, int min, int max) {

    if (value >= min && value <= max) {
      return value;
    } else {
      if (min > max) {
        throw new IllegalArgumentException();
      } else if (value >= min) {
        return max;
      } else {
        return min;
      }
    }
  }
}
