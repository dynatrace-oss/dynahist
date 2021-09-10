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
package com.dynatrace.dynahist;

import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.bin.Bin;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.quantile.QuantileEstimator;
import com.dynatrace.dynahist.quantile.SciPyQuantileEstimator;
import com.dynatrace.dynahist.value.ValueEstimator;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;

abstract class AbstractHistogram implements Histogram {

  private static final QuantileEstimator DEFAULT_QUANTILE_ESTIMATOR =
      SciPyQuantileEstimator.create();

  private static final ValueEstimator DEFAULT_VALUE_ESTIMATOR = ValueEstimator.UNIFORM;

  protected static final long ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES = 4;
  protected static final long ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES = 12;

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
  public BinIterator getBinByRank(long rank) {
    final long totalCount = getTotalCount();

    checkArgument(rank >= 0L);
    checkArgument(rank < totalCount);

    final BinIterator binIterator;
    if (rank < (totalCount >>> 1)) {
      binIterator = getFirstNonEmptyBin();
      while (binIterator.getGreaterCount() >= totalCount - rank) {
        binIterator.next();
      }
    } else {
      binIterator = getLastNonEmptyBin();
      while (binIterator.getLessCount() > rank) {
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
  public double getValue(long rank, ValueEstimator valueEstimator) {
    requireNonNull(valueEstimator);
    return valueEstimator.getValueEstimate(this, rank);
  }

  @Override
  public double getValue(long rank) {
    return getValue(rank, DEFAULT_VALUE_ESTIMATOR);
  }

  @Override
  public Histogram getPreprocessedCopy() {
    return PreprocessedHistogram.of(this);
  }

  @Override
  public double getQuantile(
      double p, QuantileEstimator quantileEstimator, ValueEstimator valueEstimator) {
    return quantileEstimator.estimateQuantile(
        p, rank -> getValue(rank, valueEstimator), getTotalCount());
  }

  @Override
  public double getQuantile(double p, ValueEstimator valueEstimator) {
    return getQuantile(p, DEFAULT_QUANTILE_ESTIMATOR, valueEstimator);
  }

  @Override
  public double getQuantile(double p, QuantileEstimator quantileEstimator) {
    return getQuantile(p, quantileEstimator, DEFAULT_VALUE_ESTIMATOR);
  }

  @Override
  public double getQuantile(double p) {
    return getQuantile(p, DEFAULT_QUANTILE_ESTIMATOR);
  }

  @Override
  public long getEstimatedFootprintInBytes() {
    return ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES // layout
        + ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES; // object header for this object
  }

  @Override
  public Histogram addHistogram(Histogram histogram) {
    return addHistogram(histogram, DEFAULT_VALUE_ESTIMATOR);
  }

  private abstract class AbstractNonEmptyBinsIterable implements Iterable<Bin> {

    protected abstract BinIterator getStart();

    protected abstract void advanceBinIterator(BinIterator binIterator);

    protected abstract boolean isAtEnd(BinIterator binIterator);

    @Override
    public Iterator<Bin> iterator() {
      return new Iterator<Bin>() {
        private BinIterator it = null;

        @Override
        public boolean hasNext() {
          return it == null || !isAtEnd(it);
        }

        @Override
        public Bin next() {
          if (it != null) {
            advanceBinIterator(it);
          } else {
            it = getStart();
          }
          return it.getBinCopy();
        }
      };
    }

    @Override
    public void forEach(Consumer<? super Bin> action) {
      BinIterator it = getStart();
      action.accept(it.getBinCopy());
      while (!isAtEnd(it)) {
        advanceBinIterator(it);
        action.accept(it.getBinCopy());
      }
    }
  }

  @Override
  public Iterable<Bin> nonEmptyBinsAscending() {
    if (isEmpty()) return Collections.emptyList();
    return new AbstractNonEmptyBinsIterable() {
      @Override
      protected BinIterator getStart() {
        return getFirstNonEmptyBin();
      }

      @Override
      protected void advanceBinIterator(BinIterator binIterator) {
        binIterator.next();
      }

      @Override
      protected boolean isAtEnd(BinIterator binIterator) {
        return binIterator.isLastNonEmptyBin();
      }
    };
  }

  @Override
  public Iterable<Bin> nonEmptyBinsDescending() {
    if (isEmpty()) return Collections.emptyList();
    return new AbstractNonEmptyBinsIterable() {
      @Override
      protected BinIterator getStart() {
        return getLastNonEmptyBin();
      }

      @Override
      protected void advanceBinIterator(BinIterator binIterator) {
        binIterator.previous();
      }

      @Override
      protected boolean isAtEnd(BinIterator binIterator) {
        return binIterator.isFirstNonEmptyBin();
      }
    };
  }
}
