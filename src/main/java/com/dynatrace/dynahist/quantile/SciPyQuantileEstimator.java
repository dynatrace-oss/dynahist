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

import static com.dynatrace.dynahist.util.Preconditions.checkArgument;

import com.dynatrace.dynahist.util.Algorithms;
import java.util.function.LongToDoubleFunction;

/**
 * A quantile estimator implementation based on the definition used by the {@code
 * scipy.stats.mstats.mquantiles} method in the SciPy Python library.
 *
 * <p>This class is immutable.
 *
 * @see <a
 *     href="https://docs.scipy.org/doc/scipy-1.5.2/reference/generated/scipy.stats.mstats.mquantiles.html">SciPy
 *     reference for scipy.stats.mstats.mquantiles</a>
 */
public final class SciPyQuantileEstimator implements QuantileEstimator {

  private static final QuantileEstimator DEFAULT =
      create(0.4, 0.4); // this is also the default in SciPy version 1.5.2

  private final double alphap;
  private final double betap;

  /**
   * Returns a {@link QuantileEstimator} instance that uses the SciPy quantile definition with given
   * plotting positions parameters.
   *
   * @param alphap plotting positions parameter
   * @param betap plotting positions parameter
   * @return a {@link QuantileEstimator} instance
   */
  public static QuantileEstimator create(double alphap, double betap) {
    return new SciPyQuantileEstimator(alphap, betap);
  }

  /**
   * Returns a {@link QuantileEstimator} instance that uses the SciPy quantile definition with
   * default parameters.
   *
   * @return a {@link QuantileEstimator} instance
   */
  public static QuantileEstimator create() {
    return DEFAULT;
  }

  private SciPyQuantileEstimator(double alphap, double betap) {
    checkArgument(alphap >= 0);
    checkArgument(alphap <= 1);
    checkArgument(betap >= 0);
    checkArgument(betap <= 1);

    this.alphap = alphap;
    this.betap = betap;
  }

  @Override
  public double estimateQuantile(
      double p, LongToDoubleFunction sortedValueFunction, long numValues) {

    if (numValues == 0) {
      return Double.NaN;
    }
    if (numValues == 1) {
      return sortedValueFunction.applyAsDouble(0);
    }

    double z = Algorithms.interpolate(p, 0, alphap - 1, 1, numValues - betap);
    if (z <= 0) {
      return sortedValueFunction.applyAsDouble(0);
    }
    if (z >= numValues - 1) {
      return sortedValueFunction.applyAsDouble(numValues - 1);
    }
    long zIntPart = (long) z;
    double zFractionPart = z - zIntPart;
    if (zFractionPart == 0) {
      return sortedValueFunction.applyAsDouble(zIntPart);
    }
    double y1 = sortedValueFunction.applyAsDouble(zIntPart);
    double y2 = sortedValueFunction.applyAsDouble(zIntPart + 1);
    return Algorithms.interpolate(zFractionPart, 0, y1, 1, y2);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " [alphap=" + alphap + ", betap=" + betap + "]";
  }
}
