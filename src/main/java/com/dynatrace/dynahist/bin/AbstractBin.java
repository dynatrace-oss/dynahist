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
package com.dynatrace.dynahist.bin;

import com.dynatrace.dynahist.Histogram;

public abstract class AbstractBin implements Bin {

  protected abstract Histogram getHistogram();

  @Override
  public String toString() {
    return "Bin [binIndex="
        + getBinIndex()
        + ", lowerBound="
        + getLowerBound()
        + ", upperBound="
        + getUpperBound()
        + ", binCount="
        + getBinCount()
        + ", lessCount="
        + getLessCount()
        + ", greaterCount="
        + getGreaterCount()
        + ", isUnderflowBin="
        + isUnderflowBin()
        + ", isOverflowBin="
        + isOverflowBin()
        + "]";
  }

  @Override
  public boolean isUnderflowBin() {
    return getBinIndex() == getHistogram().getLayout().getUnderflowBinIndex();
  }

  @Override
  public boolean isOverflowBin() {
    return getBinIndex() == getHistogram().getLayout().getOverflowBinIndex();
  }

  @Override
  public double getLowerBound() {
    final Histogram histogram = getHistogram();
    return Math.max(histogram.getMin(), histogram.getLayout().getBinLowerBound(getBinIndex()));
  }

  @Override
  public double getUpperBound() {
    final Histogram histogram = getHistogram();
    return Math.min(histogram.getMax(), histogram.getLayout().getBinUpperBound(getBinIndex()));
  }
}
