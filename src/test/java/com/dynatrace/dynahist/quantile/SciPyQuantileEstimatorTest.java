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
package com.dynatrace.dynahist.quantile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.IntStream;
import org.junit.Test;

public class SciPyQuantileEstimatorTest {

  private static final int NUM_P_VALUES = 10000;

  private static final double[] P_VALUES =
      IntStream.range(0, NUM_P_VALUES + 1).mapToDouble(i -> i / (double) NUM_P_VALUES).toArray();

  private static final Collection<QuantileEstimator> QUANTILE_ESTIMATORS =
      Arrays.asList(
          SciPyQuantileEstimator.create(0.5, 0.5),
          SciPyQuantileEstimator.create(0., 0.5),
          SciPyQuantileEstimator.create(0.5, 0.),
          SciPyQuantileEstimator.create(0.4, 0.3),
          SciPyQuantileEstimator.create(0.5, 1),
          SciPyQuantileEstimator.create(1, 0.5),
          SciPyQuantileEstimator.create(1, 0.),
          SciPyQuantileEstimator.create(0., 1.),
          SciPyQuantileEstimator.create(1., 0.),
          SciPyQuantileEstimator.create(1, 1.),
          SciPyQuantileEstimator.create());

  @Test
  public void test() {
    double[] values = {6., 47., 49., 15., 42., 41., 7., 39., 43., 40., 36.};
    Arrays.sort(values);
    QuantileEstimator quantileEstimator = SciPyQuantileEstimator.create(0.4, 0.4);

    assertEquals(19.200000000000003, quantileEstimator.estimateQuantile(0.25, values), 0);
    assertEquals(40, quantileEstimator.estimateQuantile(0.5, values), 0);
    assertEquals(42.8, quantileEstimator.estimateQuantile(0.75, values), 0);
  }

  @Test
  public void test2() {
    double[] values = {3, 5};
    Arrays.sort(values);
    QuantileEstimator quantileEstimator = SciPyQuantileEstimator.create(0.4, 0.4);
    assertEquals(3, quantileEstimator.estimateQuantile(0, values), 0);
    assertEquals(3, quantileEstimator.estimateQuantile(0.25, values), 0);
    assertEquals(4, quantileEstimator.estimateQuantile(0.5, values), 0);
    assertEquals(5, quantileEstimator.estimateQuantile(0.75, values), 0);
    assertEquals(5, quantileEstimator.estimateQuantile(1, values), 0);
  }

  @Test
  public void test3() {
    double[] values = {3, 5};
    Arrays.sort(values);
    QuantileEstimator quantileEstimator = SciPyQuantileEstimator.create(0.5, 0.5);
    assertEquals(3, quantileEstimator.estimateQuantile(0, values), 0);
    assertEquals(3, quantileEstimator.estimateQuantile(0.25, values), 0);
    assertEquals(4, quantileEstimator.estimateQuantile(0.5, values), 0);
    assertEquals(5, quantileEstimator.estimateQuantile(0.75, values), 0);
    assertEquals(5, quantileEstimator.estimateQuantile(1, values), 0);
  }

  @Test
  public void testSingleValue() {
    double value = 5;
    double[] values = {value};

    for (QuantileEstimator quantileEstimator : QUANTILE_ESTIMATORS) {
      for (double p : P_VALUES) {
        assertEquals(value, quantileEstimator.estimateQuantile(p, values), 0);
      }
    }
  }

  @Test
  public void testNoValues() {
    double[] values = {};
    for (QuantileEstimator quantileEstimator : QUANTILE_ESTIMATORS) {
      for (double p : P_VALUES) {
        assertEquals(Double.NaN, quantileEstimator.estimateQuantile(p, values), 0);
      }
    }
  }

  @Test
  public void testMedian() {
    double[] values = IntStream.range(0, 20).mapToDouble(i -> i).toArray();
    double trueMedian = 9.5;

    double[] alphapValues = {0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
    double[] betapValues = {0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};

    for (double alphap : alphapValues) {
      for (double betap : betapValues) {
        QuantileEstimator estimator = SciPyQuantileEstimator.create(alphap, betap);
        assertEquals(
            trueMedian + (alphap - betap) * 0.5, estimator.estimateQuantile(0.5, values), 1e-10);
      }
    }
  }

  @Test
  public void testSciPyQuantileEstimatorConstructor() {
    assertThrows(IllegalArgumentException.class, () -> SciPyQuantileEstimator.create(-1, 1));
    assertThrows(IllegalArgumentException.class, () -> SciPyQuantileEstimator.create(2, 1));
    assertThrows(IllegalArgumentException.class, () -> SciPyQuantileEstimator.create(1, -1));
    assertThrows(IllegalArgumentException.class, () -> SciPyQuantileEstimator.create(1, 2));
  }

  @Test
  public void testToString() {
    double alphap = 0.5;
    double betap = 0.7;
    assertEquals(
        "SciPyQuantileEstimator [alphap=0.5, betap=0.7]",
        SciPyQuantileEstimator.create(alphap, betap).toString());
  }
}
