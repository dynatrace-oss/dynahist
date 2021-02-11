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

import static com.dynatrace.dynahist.util.Algorithms.NEGATIVE_INFINITY_MAPPED_TO_LONG;
import static com.dynatrace.dynahist.util.Algorithms.POSITIVE_INFINITY_MAPPED_TO_LONG;
import static com.dynatrace.dynahist.util.Algorithms.findFirst;
import static com.dynatrace.dynahist.util.Algorithms.mapDoubleToLong;
import static com.dynatrace.dynahist.util.Algorithms.mapLongToDouble;

/**
 * An abstract {@link Layout} class that can be used if there is an approximate formula for the
 * reverse mapping (from bin index to bin boundaries).
 */
abstract class AbstractLayout implements Layout {

  @Override
  public final double getBinLowerBound(int binIndex) {
    if (binIndex <= getUnderflowBinIndex()) {
      return Double.NEGATIVE_INFINITY;
    }
    final int effectiveBinIndex = Math.min(getOverflowBinIndex(), binIndex);
    final double approximateBinLowerBound = getBinLowerBoundApproximation(effectiveBinIndex);
    return mapLongToDouble(
        findFirst(
            l -> mapToBinIndex(mapLongToDouble(l)) >= effectiveBinIndex,
            NEGATIVE_INFINITY_MAPPED_TO_LONG,
            POSITIVE_INFINITY_MAPPED_TO_LONG,
            mapDoubleToLong(approximateBinLowerBound)));
  }

  @Override
  public final double getBinUpperBound(int binIndex) {
    if (binIndex >= getOverflowBinIndex()) {
      return Double.POSITIVE_INFINITY;
    }
    final int effectiveBinIndex = Math.max(getUnderflowBinIndex(), binIndex);
    final double approximateBinUpperBound = getBinLowerBoundApproximation(effectiveBinIndex + 1);
    return mapLongToDouble(
        ~findFirst(
            l -> mapToBinIndex(mapLongToDouble(~l)) <= effectiveBinIndex,
            ~POSITIVE_INFINITY_MAPPED_TO_LONG,
            ~NEGATIVE_INFINITY_MAPPED_TO_LONG,
            ~mapDoubleToLong(approximateBinUpperBound)));
  }

  /**
   * Gives an approximation of the lower bound of bin with given bin index.
   *
   * <p>The method must be defined for all values greater than {@link #getUnderflowBinIndex()} and
   * smaller than or equal to {@link #getOverflowBinIndex()}.
   *
   * <p>The return value must not be {@link Double#NaN}.
   *
   * @param binIndex the bin index
   * @return an approximation of the lower bound
   */
  protected abstract double getBinLowerBoundApproximation(final int binIndex);
}
