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
package com.dynatrace.dynahist.layout;

import static com.dynatrace.dynahist.util.Algorithms.NEGATIVE_INFINITY_MAPPED_TO_LONG;
import static com.dynatrace.dynahist.util.Algorithms.POSITIVE_INFINITY_MAPPED_TO_LONG;
import static com.dynatrace.dynahist.util.Algorithms.findFirst;
import static com.dynatrace.dynahist.util.Algorithms.mapDoubleToLong;
import static com.dynatrace.dynahist.util.Algorithms.mapLongToDouble;

// TODO java doc

abstract class AbstractErrorLimitingLayout implements Layout {

  @Override
  public final double getBinLowerBound(int binIndex) {
    if (binIndex <= getUnderflowBinIndex()) {
      return Double.NEGATIVE_INFINITY;
    }
    final int effectiveBinIndex = Math.min(getOverflowBinIndex(), binIndex);
    final double approximateBinLowerBound = getIndexLowerBound(effectiveBinIndex);
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
    final double approximateBinUpperBound = getIndexUpperBound(effectiveBinIndex);
    return mapLongToDouble(
        ~findFirst(
            l -> mapToBinIndex(mapLongToDouble(~l)) <= effectiveBinIndex,
            ~POSITIVE_INFINITY_MAPPED_TO_LONG,
            ~NEGATIVE_INFINITY_MAPPED_TO_LONG,
            ~mapDoubleToLong(approximateBinUpperBound)));
  }

  protected abstract double getTransition(final int idx);

  protected final double getIndexLowerBound(final int idx) {
    if (idx >= 0) {
      return getTransition(idx);
    } else {
      return -getTransition(-idx);
    }
  }

  protected final double getIndexUpperBound(final int idx) {
    if (idx >= 0) {
      return getTransition(idx + 1);
    } else {
      return -getTransition(-(idx + 1));
    }
  }
}
