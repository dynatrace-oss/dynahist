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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.LongPredicate;
import org.assertj.core.data.Percentage;
import org.junit.Test;

public class AlgorithmsTest {

  @Test
  public void testInterpolate() {
    assertEquals(4.5, Algorithms.interpolate(3.5, 3, 4, 4, 5), 0d);
    assertEquals(4.5, Algorithms.interpolate(3, 3, 4, 3, 5), 0d);
    assertEquals(4.5, Algorithms.interpolate(2, 3, 4, 3, 5), 0d);
    assertEquals(4, Algorithms.interpolate(2, 3, 4, 4, 5), 0d);
    assertEquals(5, Algorithms.interpolate(6, 3, 4, 4, 5), 0d);

    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(2, 3, Double.POSITIVE_INFINITY, 4, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(3.5, 3, Double.POSITIVE_INFINITY, 4, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(5, 3, Double.POSITIVE_INFINITY, 4, Double.POSITIVE_INFINITY),
        0d);

    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(2, 3, Double.NEGATIVE_INFINITY, 4, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(3.5, 3, Double.NEGATIVE_INFINITY, 4, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(5, 3, Double.NEGATIVE_INFINITY, 4, Double.POSITIVE_INFINITY),
        0d);

    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(2, 3, Double.NEGATIVE_INFINITY, 4, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(3.5, 3, Double.NEGATIVE_INFINITY, 4, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(5, 3, Double.NEGATIVE_INFINITY, 4, Double.NEGATIVE_INFINITY),
        0d);

    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(2, 3, Double.POSITIVE_INFINITY, 4, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(3.5, 3, Double.POSITIVE_INFINITY, 4, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(5, 3, Double.POSITIVE_INFINITY, 4, Double.NEGATIVE_INFINITY),
        0d);

    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(-1, 0, Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(0, 0, Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(1, 0, Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(-1, 0, Double.NEGATIVE_INFINITY, 0, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(0, 0, Double.NEGATIVE_INFINITY, 0, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(1, 0, Double.NEGATIVE_INFINITY, 0, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(-1, 0, Double.POSITIVE_INFINITY, 0, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(0, 0, Double.POSITIVE_INFINITY, 0, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(1, 0, Double.POSITIVE_INFINITY, 0, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(-1, 0, Double.NEGATIVE_INFINITY, 0, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(0, 0, Double.NEGATIVE_INFINITY, 0, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(1, 0, Double.NEGATIVE_INFINITY, 0, Double.NEGATIVE_INFINITY),
        0d);

    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(-1, 0, Double.POSITIVE_INFINITY, 0, 0),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY, Algorithms.interpolate(0, 0, Double.POSITIVE_INFINITY, 0, 0), 0d);
    assertEquals(
        Double.POSITIVE_INFINITY, Algorithms.interpolate(1, 0, Double.POSITIVE_INFINITY, 0, 0), 0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(-1, 0, Double.NEGATIVE_INFINITY, 0, 0),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY, Algorithms.interpolate(0, 0, Double.NEGATIVE_INFINITY, 0, 0), 0d);
    assertEquals(
        Double.NEGATIVE_INFINITY, Algorithms.interpolate(1, 0, Double.NEGATIVE_INFINITY, 0, 0), 0d);

    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(-1, 0, 0, 0, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY, Algorithms.interpolate(0, 0, 0, 0, Double.POSITIVE_INFINITY), 0d);
    assertEquals(
        Double.POSITIVE_INFINITY, Algorithms.interpolate(1, 0, 0, 0, Double.POSITIVE_INFINITY), 0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(-1, 0, 0, 0, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY, Algorithms.interpolate(0, 0, 0, 0, Double.NEGATIVE_INFINITY), 0d);
    assertEquals(
        Double.NEGATIVE_INFINITY, Algorithms.interpolate(1, 0, 0, 0, Double.NEGATIVE_INFINITY), 0d);

    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(-1, -1, Double.POSITIVE_INFINITY, 1, 0),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(0, -1, Double.POSITIVE_INFINITY, 1, 0),
        0d);
    assertEquals(0, Algorithms.interpolate(1, -1, Double.POSITIVE_INFINITY, 1, 0), 0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(-1, -1, Double.NEGATIVE_INFINITY, 1, 0),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(0, -1, Double.NEGATIVE_INFINITY, 1, 0),
        0d);
    assertEquals(0, Algorithms.interpolate(1, -1, Double.NEGATIVE_INFINITY, 1, 0), 0d);

    assertEquals(0, Algorithms.interpolate(-1, -1, 0, 1, Double.POSITIVE_INFINITY), 0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(0, -1, 0, 1, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(1, -1, 0, 1, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(0, Algorithms.interpolate(-1, -1, 0, 1, Double.NEGATIVE_INFINITY), 0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(0, -1, 0, 1, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(1, -1, 0, 1, Double.NEGATIVE_INFINITY),
        0d);

    assertThat(Algorithms.interpolate(Math.nextUp(1e30), 1e30, 1e30, 1e300, 1e300))
        .isCloseTo(Math.nextUp(1e30), Percentage.withPercentage(1e-6));
    assertThat(Algorithms.interpolate(Math.nextUp(1e30), 1e300, 1e300, 1e30, 1e30))
        .isCloseTo(Math.nextUp(1e30), Percentage.withPercentage(1e-6));
    assertThat(Algorithms.interpolate(Math.nextDown(-1e30), -1e30, -1e30, -1e300, -1e300))
        .isCloseTo(Math.nextDown(-1e30), Percentage.withPercentage(1e-6));
    assertThat(Algorithms.interpolate(Math.nextDown(-1e30), -1e300, -1e300, -1e30, -1e30))
        .isCloseTo(Math.nextDown(-1e30), Percentage.withPercentage(1e-6));
  }

  @Test
  public void testInterpolateSymmetry() {
    Random random = new Random(0);
    int numTestCycles = 1000;
    for (int i = 0; i < numTestCycles; ++i) {
      double[] xVals = {random.nextDouble(), random.nextDouble(), random.nextDouble()};
      Arrays.sort(xVals);
      double x1 = xVals[0];
      double x = xVals[1];
      double x2 = xVals[2];
      double y1 = random.nextDouble();
      double y2 = random.nextDouble();

      double interpolatedValue1 = Algorithms.interpolate(x, x1, y1, x2, y2);
      double interpolatedValue2 = Algorithms.interpolate(x, x2, y2, x1, y1);
      assertEquals(0, Double.compare(interpolatedValue1, interpolatedValue2));
    }
  }

  @Test
  public void testInterpolateMonotonicity() {
    Random random = new Random(0);
    int numTestCycles = 1000;
    for (int i = 0; i < numTestCycles; ++i) {
      double[] xVals = {random.nextDouble(), random.nextDouble(), random.nextDouble()};
      Arrays.sort(xVals);
      double x1 = xVals[0];
      double x = xVals[1];
      double x2 = xVals[2];
      double y1 = random.nextDouble();
      double y2 = random.nextDouble();
      double interpolatedValueLeft = Algorithms.interpolate(Math.nextDown(x), x1, y1, x2, y2);
      double interpolatedValueMid = Algorithms.interpolate(x, x1, y1, x2, y2);
      double interpolatedValueRight = Algorithms.interpolate(Math.nextUp(x), x1, y1, x2, y2);

      if (y1 <= y2) {
        assertTrue(interpolatedValueLeft <= interpolatedValueMid);
        assertTrue(interpolatedValueMid <= interpolatedValueRight);
      } else {
        assertTrue(interpolatedValueLeft >= interpolatedValueMid);
        assertTrue(interpolatedValueMid >= interpolatedValueRight);
      }
    }
  }

  @Test
  public void testInterpolateNaN() {
    assertEquals(Double.NaN, Algorithms.interpolate(Double.NaN, 3, 4, 4, 5), 0d);
    assertEquals(Double.NaN, Algorithms.interpolate(3.5, Double.NaN, 4, 4, 5), 0d);
    assertEquals(Double.NaN, Algorithms.interpolate(3.5, 3, 4, Double.NaN, 5), 0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(3.5, Double.NEGATIVE_INFINITY, 4, Double.POSITIVE_INFINITY, 5),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(3.5, 2, Double.NEGATIVE_INFINITY, 4, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(3.5, Double.NEGATIVE_INFINITY, 3, Double.POSITIVE_INFINITY, 4),
        0d);
    assertEquals(
        3, Algorithms.interpolate(3, Double.NEGATIVE_INFINITY, 3, Double.POSITIVE_INFINITY, 3), 0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(0, Double.NEGATIVE_INFINITY, 0, Double.POSITIVE_INFINITY, 1),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(0, Double.NEGATIVE_INFINITY, 0, Double.POSITIVE_INFINITY, -1),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(0, Double.NEGATIVE_INFINITY, 1, Double.POSITIVE_INFINITY, 0),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(0, Double.NEGATIVE_INFINITY, -1, Double.POSITIVE_INFINITY, 0),
        0d);

    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(0, 1, Double.NEGATIVE_INFINITY, 2, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(1, 1, Double.NEGATIVE_INFINITY, 2, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(1.5, 1, Double.NEGATIVE_INFINITY, 2, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(2, 1, Double.NEGATIVE_INFINITY, 2, Double.POSITIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(3, 1, Double.NEGATIVE_INFINITY, 2, Double.POSITIVE_INFINITY),
        0d);

    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(0, 1, Double.POSITIVE_INFINITY, 2, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.POSITIVE_INFINITY,
        Algorithms.interpolate(1, 1, Double.POSITIVE_INFINITY, 2, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NaN,
        Algorithms.interpolate(1.5, 1, Double.POSITIVE_INFINITY, 2, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(2, 1, Double.POSITIVE_INFINITY, 2, Double.NEGATIVE_INFINITY),
        0d);
    assertEquals(
        Double.NEGATIVE_INFINITY,
        Algorithms.interpolate(3, 1, Double.POSITIVE_INFINITY, 2, Double.NEGATIVE_INFINITY),
        0d);
  }

  @Test
  public void testCalculateMidpoint() {
    assertEquals(-1, Algorithms.calculateMidpoint(Long.MIN_VALUE, Long.MAX_VALUE));
    assertEquals(-1, Algorithms.calculateMidpoint(Long.MIN_VALUE, Long.MAX_VALUE - 1));
    assertEquals(-2, Algorithms.calculateMidpoint(Long.MIN_VALUE, Long.MAX_VALUE - 2));
    assertEquals(-2, Algorithms.calculateMidpoint(Long.MIN_VALUE, Long.MAX_VALUE - 3));
    assertEquals(0, Algorithms.calculateMidpoint(Long.MIN_VALUE + 1, Long.MAX_VALUE));
    assertEquals(0, Algorithms.calculateMidpoint(Long.MIN_VALUE + 2, Long.MAX_VALUE));
    assertEquals(Long.MAX_VALUE, Algorithms.calculateMidpoint(Long.MAX_VALUE, Long.MAX_VALUE));
    assertEquals(
        Long.MAX_VALUE - 1, Algorithms.calculateMidpoint(Long.MAX_VALUE - 1, Long.MAX_VALUE));
    assertEquals(
        Long.MAX_VALUE - 1, Algorithms.calculateMidpoint(Long.MAX_VALUE - 2, Long.MAX_VALUE));
    assertEquals(Long.MIN_VALUE, Algorithms.calculateMidpoint(Long.MIN_VALUE, Long.MIN_VALUE));
    assertEquals(Long.MIN_VALUE, Algorithms.calculateMidpoint(Long.MIN_VALUE + 1, Long.MIN_VALUE));
    assertEquals(
        Long.MIN_VALUE + 1, Algorithms.calculateMidpoint(Long.MIN_VALUE + 2, Long.MIN_VALUE));
    assertEquals(
        Long.MIN_VALUE + 1, Algorithms.calculateMidpoint(Long.MIN_VALUE + 3, Long.MIN_VALUE));
    assertEquals(Long.MIN_VALUE / 2, Algorithms.calculateMidpoint(0, Long.MIN_VALUE));
    assertEquals(Long.MAX_VALUE / 2, Algorithms.calculateMidpoint(0, Long.MAX_VALUE));

    assertEquals(-5L, Algorithms.calculateMidpoint(-4L, -6L));
    assertEquals(-6L, Algorithms.calculateMidpoint(-4L, -7L));
    assertEquals(-6L, Algorithms.calculateMidpoint(-5L, -7L));
    assertEquals(-6L, Algorithms.calculateMidpoint(-4L, -8L));
    assertEquals(5L, Algorithms.calculateMidpoint(4L, 6L));
    assertEquals(5L, Algorithms.calculateMidpoint(4L, 7L));
    assertEquals(6L, Algorithms.calculateMidpoint(5L, 7L));
    assertEquals(6L, Algorithms.calculateMidpoint(4L, 8L));
    assertEquals(0L, Algorithms.calculateMidpoint(-2L, 3L));
    assertEquals(1L, Algorithms.calculateMidpoint(-1L, 4L));
    assertEquals(-1L, Algorithms.calculateMidpoint(-3L, 2L));
  }

  @Test
  public void testMapDoubleToLong() {
    assertEquals(0, Algorithms.mapDoubleToLong(0));
    assertEquals(1, Algorithms.mapDoubleToLong(Double.MIN_VALUE));
    assertEquals(0x7fefffffffffffffL, Algorithms.mapDoubleToLong(Double.MAX_VALUE));
    assertEquals(0x7ff0000000000000L, Algorithms.mapDoubleToLong(Double.POSITIVE_INFINITY));

    assertEquals(-1, Algorithms.mapDoubleToLong(-0d));
    assertEquals(-2, Algorithms.mapDoubleToLong(-Double.MIN_VALUE));
    assertEquals(0x8010000000000000L, Algorithms.mapDoubleToLong(-Double.MAX_VALUE));
    assertEquals(0x800fffffffffffffL, Algorithms.mapDoubleToLong(Double.NEGATIVE_INFINITY));
    assertEquals(0x7ff8000000000000L, Algorithms.mapDoubleToLong(Double.NaN));
  }

  @Test
  public void testLongToDouble() {
    assertEquals(0, Algorithms.mapLongToDouble(0), 0d);
    assertEquals(Double.MIN_VALUE, Algorithms.mapLongToDouble(1), 0d);
    assertEquals(Double.MAX_VALUE, Algorithms.mapLongToDouble(0x7fefffffffffffffL), 0d);
    assertEquals(Double.POSITIVE_INFINITY, Algorithms.mapLongToDouble(0x7ff0000000000000L), 0d);

    assertEquals(-0d, Algorithms.mapLongToDouble(-1), 0d);
    assertEquals(-Double.MIN_VALUE, Algorithms.mapLongToDouble(-2), 0d);
    assertEquals(-Double.MAX_VALUE, Algorithms.mapLongToDouble(-1 - 0x7fefffffffffffffL), 0d);
    assertEquals(
        Double.NEGATIVE_INFINITY, Algorithms.mapLongToDouble(-1 - 0x7ff0000000000000L), 0d);

    assertEquals(Double.NaN, Algorithms.mapLongToDouble(0x7ff8000000000000L), 0d);
    assertEquals(Double.NaN, Algorithms.mapLongToDouble(Long.MAX_VALUE), 0d);
    assertEquals(Double.NaN, Algorithms.mapLongToDouble(0x7ff0000000000000L + 1), 0d);
    assertEquals(Double.NaN, Algorithms.mapLongToDouble(0x800fffffffffffffL - 1), 0d);
    assertEquals(Double.NaN, Algorithms.mapLongToDouble(-2 - 0x7ff0000000000000L), 0d);
    assertEquals(Double.NaN, Algorithms.mapLongToDouble(Long.MIN_VALUE), 0d);
  }

  @Test
  public void testDoubleMapping() {
    int cycles = 100000;
    Random rnd = new Random(0);

    for (int i = 0; i < cycles; ++i) {
      double d = rnd.nextDouble() * 2 - 1;
      assertEquals(d, Algorithms.mapLongToDouble(Algorithms.mapDoubleToLong(d)), 0d);
    }
  }

  @Test
  public void testFindFirst() {
    int maxNumEvaluations = 65;
    testFindFirst(3, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(Long.MAX_VALUE - 1, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(Long.MIN_VALUE + 1, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(0, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(0, -1, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(0, Long.MIN_VALUE, 1, maxNumEvaluations);
    testFindFirst(0, -1, 1, maxNumEvaluations);
    testFindFirst(0, -1, 0, maxNumEvaluations);
    testFindFirst(0, 0, 1, maxNumEvaluations);
    testFindFirst(0, 0, 0, maxNumEvaluations);
    testFindFirst(1, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(10, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(100, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(Long.MAX_VALUE - 2, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(Long.MAX_VALUE - 1, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirst(0, 0, Long.MAX_VALUE, maxNumEvaluations);

    assertThrows(
        IllegalArgumentException.class, () -> Algorithms.findFirst(l -> false, 0, Long.MAX_VALUE));
    assertThrows(IllegalArgumentException.class, () -> Algorithms.findFirst(l -> false, 1, -1));
    assertThrows(IllegalArgumentException.class, () -> Algorithms.findFirst(l -> false, -1, 1, 2));
    assertThrows(IllegalArgumentException.class, () -> Algorithms.findFirst(l -> false, -1, 1, -2));
  }

  private static void testFindFirstWithInitialGuess(
      long firstTrueIndex, long min, long max, long initialGuess, int maxNumEvaluations) {
    Set<Long> evaluatedValues = new HashSet<>();

    LongPredicate predicate =
        value -> {
          assertTrue(
              evaluatedValues.add(
                  value)); // check, if predicate is not evaluated with same value twice
          return value >= firstTrueIndex;
        };

    assertEquals(firstTrueIndex, Algorithms.findFirst(predicate, min, max, initialGuess));
    assertThat(evaluatedValues.size()).isLessThanOrEqualTo(maxNumEvaluations);
  }

  private static void testFindFirst(
      long firstTrueIndex, long min, long max, int maxNumEvaluations) {
    Set<Long> evaluatedValues = new HashSet<>();

    LongPredicate predicate =
        value -> {
          assertTrue(
              evaluatedValues.add(
                  value)); // check, if predicate is not evaluated with same value twice
          return value >= firstTrueIndex;
        };

    assertEquals(firstTrueIndex, Algorithms.findFirst(predicate, min, max));
    assertThat(evaluatedValues.size()).isLessThanOrEqualTo(maxNumEvaluations);
  }

  @Test
  public void testFindFirstWithInitialGuess() {
    int maxNumEvaluations = 128;
    testFindFirstWithInitialGuess(
        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, maxNumEvaluations);
    testFindFirstWithInitialGuess(
        Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirstWithInitialGuess(
        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE + 1, maxNumEvaluations);
    testFindFirstWithInitialGuess(
        Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE - 1, maxNumEvaluations);
    testFindFirstWithInitialGuess(
        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    testFindFirstWithInitialGuess(
        Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, maxNumEvaluations);
    testFindFirstWithInitialGuess(
        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE - 1, maxNumEvaluations);
    testFindFirstWithInitialGuess(
        Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE + 1, maxNumEvaluations);
    assertThrows(
        IllegalArgumentException.class,
        () -> Algorithms.findFirst(l -> false, Long.MIN_VALUE, Long.MAX_VALUE, 0));
  }

  @Test
  public void testFindFirstWithInitialGuess2() {
    int maxNumEvaluationsWithInitialGuess = 128;
    int maxNumEvaluations = 65;
    for (int j = 0; j < 100; ++j) {
      long firstTrueIndex = Long.MAX_VALUE - j;
      for (int i = 0; i < 100; ++i) {
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE - i,
            maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex, Long.MIN_VALUE, Long.MAX_VALUE, i, maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            -1 - i,
            maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MIN_VALUE + i,
            maxNumEvaluationsWithInitialGuess);
      }
      testFindFirst(firstTrueIndex, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    }
    for (int j = 0; j < 100; ++j) {
      long firstTrueIndex = Long.MIN_VALUE + j;
      for (int i = 0; i < 100; ++i) {
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE - i,
            maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex, Long.MIN_VALUE, Long.MAX_VALUE, i, maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            -1 - i,
            maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MIN_VALUE + i,
            maxNumEvaluationsWithInitialGuess);
      }
      testFindFirst(firstTrueIndex, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    }

    for (int j = 0; j < 100; ++j) {
      long firstTrueIndex = j;
      for (int i = 0; i < 100; ++i) {
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE - i,
            maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex, Long.MIN_VALUE, Long.MAX_VALUE, i, maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            -1 - i,
            maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MIN_VALUE + i,
            maxNumEvaluationsWithInitialGuess);
      }
      testFindFirst(firstTrueIndex, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    }

    for (int j = 0; j < 100; ++j) {
      long firstTrueIndex = -j - 1;
      for (int i = 0; i < 100; ++i) {
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE - i,
            maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex, Long.MIN_VALUE, Long.MAX_VALUE, i, maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            -1 - i,
            maxNumEvaluationsWithInitialGuess);
        testFindFirstWithInitialGuess(
            firstTrueIndex,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MIN_VALUE + i,
            maxNumEvaluationsWithInitialGuess);
      }
      testFindFirst(firstTrueIndex, Long.MIN_VALUE, Long.MAX_VALUE, maxNumEvaluations);
    }

    for (int j = 0; j < 20; ++j) {
      for (int i = 0; i <= j; ++i) {
        long firstTrueIndex = Long.MAX_VALUE - i;
        for (int k = 0; k <= j; ++k) {
          testFindFirstWithInitialGuess(
              firstTrueIndex,
              Long.MAX_VALUE - j,
              Long.MAX_VALUE,
              Long.MAX_VALUE - k,
              maxNumEvaluationsWithInitialGuess);
        }
      }
    }

    for (int j = 0; j < 20; ++j) {
      for (int i = 0; i <= j; ++i) {
        long firstTrueIndex = Long.MIN_VALUE + i;
        for (int k = 0; k <= j; ++k) {
          testFindFirstWithInitialGuess(
              firstTrueIndex,
              Long.MIN_VALUE,
              Long.MIN_VALUE + j,
              Long.MIN_VALUE + k,
              maxNumEvaluationsWithInitialGuess);
        }
      }
    }
  }

  @Test
  public void testFindFirstWithInitialGuess3() {
    testFindFirstWithInitialGuess(1L, Long.MIN_VALUE, Long.MAX_VALUE, 0L, 2);
    testFindFirstWithInitialGuess(134325, Long.MIN_VALUE, Long.MAX_VALUE, 134324, 2);
    testFindFirstWithInitialGuess(0, Long.MIN_VALUE, Long.MAX_VALUE, 1, 4);
    testFindFirstWithInitialGuess(134324, Long.MIN_VALUE, Long.MAX_VALUE, 134325, 4);
    testFindFirstWithInitialGuess(2L, Long.MIN_VALUE, Long.MAX_VALUE, 0L, 4);
    testFindFirstWithInitialGuess(3L, Long.MIN_VALUE, Long.MAX_VALUE, 0L, 4);
  }

  @Test
  public void testInterpolateRandom() {

    SplittableRandom random = new SplittableRandom(0);

    int numIterations = 1000;
    long a = 10;
    long maxb = 100;
    long c = 10;
    for (int i = 0; i < numIterations; ++i) {
      long b = random.nextLong(maxb);

      long l =
          random.nextLong(
              Algorithms.NEGATIVE_INFINITY_MAPPED_TO_LONG + 1 + a,
              Algorithms.POSITIVE_INFINITY_MAPPED_TO_LONG - b - c);
      double x1 = Algorithms.mapLongToDouble(l);

      double x2 = Algorithms.mapLongToDouble(l + b);

      if (random.nextBoolean()) {
        double t = x1;
        x1 = x2;
        x2 = t;
      }

      double y1 = random.nextDouble(-1, 1);
      double y2 = random.nextDouble(-1, 1);

      double previousY = Double.NaN;
      for (long j = 0; j < a + b + c; ++j) {

        double x = Algorithms.mapLongToDouble(l + j - a);
        double y = Algorithms.interpolate(x, x1, y1, x2, y2);

        assertThat(y).isBetween(Math.min(y1, y2), Math.max(y1, y2));

        if (!Double.isNaN(previousY)) {
          if ((y1 <= y2 && x1 <= x2) || (y1 >= y2 && x1 >= x2)) {
            assertThat(y).isGreaterThanOrEqualTo(previousY);
          }
          if ((y1 <= y2 && x1 >= x2) || (y1 >= y2 && x1 <= x2)) {
            assertThat(y).isLessThanOrEqualTo(previousY);
          }
        }
        previousY = y;
      }
    }
  }

  @Test
  public void testInterpolateEqualX() {
    SplittableRandom random = new SplittableRandom(0);

    int numIterations = 1000;
    for (int i = 0; i < numIterations; ++i) {
      double x = random.nextDouble(-10, 10);
      double y1 = random.nextDouble(-10, 10);
      double y2 = random.nextDouble(-10, 10);
      double expectedY = 0.5 * y1 + 0.5 * y2;
      assertEquals(expectedY, Algorithms.interpolate(Math.nextDown(x), x, y1, x, y2), 0d);
      assertEquals(expectedY, Algorithms.interpolate(x, x, y1, x, y2), 0d);
      assertEquals(expectedY, Algorithms.interpolate(Math.nextUp(x), x, y1, x, y2), 0d);
    }
  }

  @Test
  public void testInterpolateNegativeZero() {

    assertEquals(Algorithms.mapDoubleToLong(-0.), Algorithms.mapDoubleToLong(-0. * 5.));

    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(-0., -1., -0., -0., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(-0., -0., -0., -0., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(
            Algorithms.interpolate(Double.POSITIVE_INFINITY, -0., -0., -0., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(
            Algorithms.interpolate(Double.NEGATIVE_INFINITY, -0., -0., -0., -0.)));

    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(
            Algorithms.interpolate(Double.NEGATIVE_INFINITY, -10., -0., 10., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(-100., -10., -0., 10., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(-10., -10., -0., 10., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(-1., -10., -0., 10., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(0., -10., -0., 10., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(1., -10., -0., 10., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(10., -10., -0., 10., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(100., -10., -0., 10., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(
            Algorithms.interpolate(Double.POSITIVE_INFINITY, -10., -0., 10., 0.)));

    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(
            Algorithms.interpolate(Double.NEGATIVE_INFINITY, -10., 0., 10., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(-100., -10., 0., 10., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(-10., -10., 0., 10., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(-1., -10., 0., 10., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(0., -10., 0., 10., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(1., -10., 0., 10., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(10., -10., 0., 10., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(100., -10., 0., 10., -0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(-0.),
        Algorithms.mapDoubleToLong(
            Algorithms.interpolate(Double.POSITIVE_INFINITY, -10., 0., 10., -0.)));

    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(5., 5., -0., 5., 0.)));
    assertEquals(
        Algorithms.mapDoubleToLong(0.),
        Algorithms.mapDoubleToLong(Algorithms.interpolate(5., 5., 0., 5., -0.)));
  }

  @Test
  public void testClip() {

    assertEquals(-3, Algorithms.clip(1, -3, -3));
    assertEquals(3, Algorithms.clip(1, 3, 7));
    assertEquals(5, Algorithms.clip(5, 3, 7));
    assertEquals(7, Algorithms.clip(8, 3, 7));
    assertThrows(IllegalArgumentException.class, () -> Algorithms.clip(10, 5, 4));
  }
}
