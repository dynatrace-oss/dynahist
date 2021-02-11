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
package com.dynatrace.dynahist.value;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;

/** Estimates recorded values from a histogram. */
public interface ValueEstimator {

  /**
   * Estimates a recorded value with given zero-based rank from the given histogram.
   *
   * <p>The estimated value must always be in the value range of the bin it belongs to.
   *
   * <p>If rank == 0, {@link Histogram#getMin()} is returned. If rank == {@link
   * Histogram#getTotalCount()} - 1, {@link Histogram#getMax()} is returned.
   *
   * @param histogram the histogram
   * @param rank the zero-based rank
   * @return the estimated value
   * @throws IllegalArgumentException if 0 &le; rank &lt; {@link Histogram#getTotalCount()} does not
   *     hold
   */
  double getValueEstimate(Histogram histogram, long rank);

  /**
   * Distributes the values of a bin uniformly over the bin's interval. The distance between two
   * values is kept constant. Let X be the distance between two points. The distance of the first
   * value to the bin lower bound will be X/2 unless the bin boundary represents the minimum
   * recorded value. The distance of the last value to the bin upper bound will be X/2 unless the
   * bin boundary represents the maximum recorded value.
   *
   * <p>If this estimator is used for bins of a {@link LogLinearLayout} or {@link
   * LogQuadraticLayout}, the maximum absolute and relative estimation errors will be bounded by the
   * absolute and relative bin width limits, respectively.
   */
  final ValueEstimator UNIFORM = ValueEstimatorImpls.UNIFORM;

  /**
   * Places all values of the bin at its lower bound except for the maximum recorded value.
   *
   * <p>It is guaranteed that the estimated value is smaller than or equal to the original value.
   *
   * <p>If this estimator is used for bins of a {@link LogLinearLayout} or {@link
   * LogQuadraticLayout}, the maximum absolute and relative estimation errors will be bounded by the
   * absolute and relative bin width limits, respectively.
   */
  final ValueEstimator LOWER_BOUND = ValueEstimatorImpls.LOWER_BOUND;

  /**
   * Places all values of the bin at its upper bound except for the minimum recorded value.
   *
   * <p>It is guaranteed that the estimated value is greater than or equal to the original value.
   *
   * <p>If this estimator is used for bins of a {@link LogLinearLayout} or {@link
   * LogQuadraticLayout}, the maximum absolute and relative estimation errors will be bounded by the
   * absolute and relative bin width limits, respectively.
   */
  final ValueEstimator UPPER_BOUND = ValueEstimatorImpls.UPPER_BOUND;

  /**
   * Places all values at the mid point of the bin except for the minimum and maximum recorded
   * values.
   *
   * <p>If this estimator is used for bins of a {@link LogLinearLayout} or {@link
   * LogQuadraticLayout}, the maximum absolute and relative estimation errors will be bounded by
   * half of the absolute and relative bin width limits, respectively.
   */
  final ValueEstimator MID_POINT = ValueEstimatorImpls.MID_POINT;
}
