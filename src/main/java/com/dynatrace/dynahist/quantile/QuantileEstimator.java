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

import java.util.function.LongToDoubleFunction;

/** A quantile estimator. */
@FunctionalInterface
public interface QuantileEstimator {

  /**
   * Estimates the quantile from sorted data which can be randomly accessed through the given
   * function.
   *
   * <p>If numValues is equal to 0 the return value will always be {@link Double#isNaN()}.
   *
   * <p>The behavior is undefined, if the given function is not monotonic increasing.
   *
   * @param p specifies the quantile, must be in [0,1], e.g. 0.5 specifies the median
   * @param sortedValueFunction a function that gives access the i-th largest (0-based) value if the
   *     argument is equal to i, the function must accept any nonnegative arguments less than
   *     numValues
   * @param numValues the number of values
   * @return the quantile estimate
   */
  double estimateQuantile(
      final double p, final LongToDoubleFunction sortedValueFunction, final long numValues);

  /**
   * Estimates the quantile from a sorted double array.
   *
   * <p>If numValues is equal to 0 the return value will always be {@link Double#isNaN()}.
   *
   * <p>The behavior is undefined, if the array is not sorted in ascending order.
   *
   * @param p specifies the quantile, must be in [0,1], e.g. 0.5 specifies the median
   * @param sortedValues a sorted double array
   * @return the quantile estimate
   */
  default double estimateQuantile(final double p, double[] sortedValues) {
    return estimateQuantile(p, i -> sortedValues[(int) i], sortedValues.length);
  }
}
