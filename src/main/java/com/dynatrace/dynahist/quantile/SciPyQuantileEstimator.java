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
package com.dynatrace.dynahist.quantile;

import static com.dynatrace.dynahist.util.Preconditions.checkArgument;

import com.dynatrace.dynahist.util.Algorithms;
import java.util.function.LongToDoubleFunction;

// TODO java-doc

public class SciPyQuantileEstimator implements QuantileEstimator {

  private final double alphap;
  private final double betap;

  public SciPyQuantileEstimator() {
    this(0.4, 0.4);
  }

  public SciPyQuantileEstimator(double alphap, double betap) {
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
}
