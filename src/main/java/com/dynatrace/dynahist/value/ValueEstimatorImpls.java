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

import static com.dynatrace.dynahist.util.Algorithms.interpolate;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.bin.Bin;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;

/** Value estimators. */
enum ValueEstimatorImpls implements ValueEstimator {
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
  UNIFORM {
    @Override
    protected double getEstimateFromBin(Bin bin, long rank) {
      final long relativeRank = rank - bin.getLessCount();

      return interpolate(
          (relativeRank - (bin.getBinCount() - relativeRank - 1L)),
          -bin.getBinCount() + ((bin.isFirstNonEmptyBin()) ? 1L : 0L),
          bin.getLowerBound(),
          bin.getBinCount() - ((bin.isLastNonEmptyBin()) ? 1L : 0L),
          bin.getUpperBound());
    }
  },
  /**
   * Places all values of the bin at its lower bound except for the maximum recorded value.
   *
   * <p>It is guaranteed that the estimated value is smaller than or equal to the original value.
   *
   * <p>If this estimator is used for bins of a {@link LogLinearLayout} or {@link
   * LogQuadraticLayout}, the maximum absolute and relative estimation errors will be bounded by the
   * absolute and relative bin width limits, respectively.
   */
  LOWER_BOUND {
    @Override
    protected double getEstimateFromBin(Bin bin, long rank) {
      return bin.getLowerBound();
    }
  },
  /**
   * Places all values of the bin at its upper bound except for the minimum recorded value.
   *
   * <p>It is guaranteed that the estimated value is greater than or equal to the original value.
   *
   * <p>If this estimator is used for bins of a {@link LogLinearLayout} or {@link
   * LogQuadraticLayout}, the maximum absolute and relative estimation errors will be bounded by the
   * absolute and relative bin width limits, respectively.
   */
  UPPER_BOUND {
    @Override
    protected double getEstimateFromBin(Bin bin, long rank) {
      return bin.getUpperBound();
    }
  },
  /**
   * Places all values at the mid point of the bin except for the minimum and maximum recorded
   * values.
   *
   * <p>If this estimator is used for bins of a {@link LogLinearLayout} or {@link
   * LogQuadraticLayout}, the maximum absolute and relative estimation errors will be bounded by
   * half of the absolute and relative bin width limits, respectively.
   */
  MID_POINT {
    @Override
    protected double getEstimateFromBin(Bin bin, long rank) {
      return Math.max(
          bin.getLowerBound(),
          Math.min(bin.getUpperBound(), (bin.getLowerBound() + bin.getUpperBound()) * 0.5));
    }
  };

  /**
   * Estimates the value with given zero-based rank and the given bin.
   *
   * <p>It can be assumed that the value of given rank was mapped into the given bin. Furthermore,
   * this function will never be called for ranks corresponding to the minimum and maximum recorded
   * value, because they are both stored explicitly by the histogram.
   *
   * @param bin the bin
   * @param rank the zero-based rank
   * @return the estimated value
   */
  protected abstract double getEstimateFromBin(Bin bin, long rank);

  @Override
  public double getValueEstimate(Histogram histogram, long rank) {

    requireNonNull(histogram);

    final long totalCount = histogram.getTotalCount();

    checkArgument(rank >= 0L);
    checkArgument(rank < totalCount);

    if (rank <= 0) {
      return histogram.getMin();
    }
    if (rank + 1 == totalCount) {
      return histogram.getMax();
    }

    final Bin bin = histogram.getBinByRank(rank);

    return getEstimateFromBin(bin, rank);
  }
}
