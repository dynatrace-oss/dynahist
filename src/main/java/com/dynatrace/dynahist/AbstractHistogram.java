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
package com.dynatrace.dynahist;

import static com.dynatrace.dynahist.util.Algorithms.interpolate;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.quantile.QuantileEstimator;
import com.dynatrace.dynahist.quantile.SciPyQuantileEstimator;

abstract class AbstractHistogram implements Histogram {

  private static final QuantileEstimator DEFAULT_QUANTILE_ESTIMATOR =
      new SciPyQuantileEstimator(0.5, 0.5);

  protected static final int ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES = 4;
  protected static final int ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES = 12;

  private final Layout layout;

  protected AbstractHistogram(final Layout layout) {
    this.layout = requireNonNull(layout);
  }

  private String formatCounts() {
    if (getTotalCount() == 0L) {
      return "{}";
    }
    final StringBuilder builder = new StringBuilder("{");
    final BinIterator bin = getFirstNonEmptyBin();
    while (bin.getGreaterCount() > 0L) {
      builder.append(bin.getBinIndex()).append(": ").append(bin.getBinCount()).append(", ");
      bin.next();
    }
    builder.append(bin.getBinIndex()).append(": ").append(bin.getBinCount()).append("}");
    return builder.toString();
  }

  @Override
  public final String toString() {
    return getClass().getSimpleName()
        + " [layout="
        + getLayout()
        + ", underFlowCount="
        + getUnderflowCount()
        + ", overFlowCount="
        + getOverflowCount()
        + ", totalCount="
        + getTotalCount()
        + ", min="
        + getMin()
        + ", max="
        + getMax()
        + ", counts="
        + formatCounts()
        + "]";
  }

  @Override
  public final int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + getLayout().hashCode();
    long temp;
    temp = Double.doubleToLongBits(getMax());
    result = prime * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(getMin());
    result = prime * result + (int) (temp ^ (temp >>> 32));

    // hash only count values together with bin indices that are larger than 0
    if (getTotalCount() > 0L) {
      final BinIterator binIterator = getFirstNonEmptyBin();
      while (true) {
        temp = binIterator.getBinCount();
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + binIterator.getBinIndex();
        if (binIterator.getGreaterCount() == 0L) {
          break;
        }
        binIterator.next();
      }
    }
    return result;
  }

  @Override
  public final boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Histogram)) {
      return false;
    }
    final Histogram other = (Histogram) obj;
    if (!getLayout().equals(other.getLayout())
        || getTotalCount() != other.getTotalCount()
        || getUnderflowCount() != other.getUnderflowCount()
        || getOverflowCount() != other.getOverflowCount()
        || Double.compare(getMin(), other.getMin()) != 0
        || Double.compare(getMax(), other.getMax()) != 0) {
      return false;
    }

    if (getTotalCount() > 0) {
      final BinIterator binIterator = getFirstNonEmptyBin();
      final BinIterator otherBinIterator = other.getFirstNonEmptyBin();
      while (true) {
        if (binIterator.getBinIndex() != otherBinIterator.getBinIndex()
            || binIterator.getBinCount() != otherBinIterator.getBinCount()) {
          return false;
        }
        if (binIterator.getGreaterCount() == 0L) {
          break;
        }
        binIterator.next();
        otherBinIterator.next();
      }
    }
    return true;
  }

  @Override
  public final Layout getLayout() {
    return layout;
  }

  @Override
  public BinIterator getBinByOrder(long order) {
    final long totalCount = getTotalCount();

    checkArgument(order >= 0L);
    checkArgument(order < totalCount);

    final BinIterator binIterator;
    if (order < (totalCount >>> 1)) {
      binIterator = getFirstNonEmptyBin();
      while (binIterator.getGreaterCount() >= totalCount - order) {
        binIterator.next();
      }
    } else {
      binIterator = getLastNonEmptyBin();
      while (binIterator.getLessCount() > order) {
        binIterator.previous();
      }
    }
    return binIterator;
  }

  @Override
  public boolean isEmpty() {
    return getTotalCount() == 0;
  }

  @Override
  public double getValueEstimate(long order) {

    final long totalCount = getTotalCount();

    checkArgument(order >= 0L);
    checkArgument(order < totalCount);

    final Bin bin = getBinByOrder(order);
    long effectiveBinCount = bin.getBinCount();
    long effectiveLessCount = bin.getLessCount();
    if (bin.isFirstNonEmptyBin()) {
      if (order == 0) {
        return getMin();
      }
      effectiveLessCount += 1;
      effectiveBinCount -= 1;
    }
    if (bin.isLastNonEmptyBin()) {
      if (order == totalCount - 1) {
        return getMax();
      }
      effectiveBinCount -= 1;
    }

    return interpolate(
        order - effectiveLessCount,
        -0.5,
        bin.getLowerBound(),
        effectiveBinCount - 0.5,
        bin.getUpperBound());
  }

  @Override
  public Histogram getPreprocessedCopy() {
    return PreprocessedHistogram.of(this);
  }

  @Override
  public double getQuantileEstimate(double p, QuantileEstimator quantileEstimator) {
    return quantileEstimator.estimateQuantile(p, this::getValueEstimate, getTotalCount());
  }

  @Override
  public double getQuantileEstimate(double p) {
    return getQuantileEstimate(p, DEFAULT_QUANTILE_ESTIMATOR);
  }

  @Override
  public long getEstimatedFootprintInBytes() {
    return ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES // layout
        + ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES; // object header for this object
  }
}
