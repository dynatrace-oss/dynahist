/*
 * Copyright 2020-2022 Dynatrace LLC
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

import static com.dynatrace.dynahist.serialization.SerializationUtil.writeSignedVarInt;
import static com.dynatrace.dynahist.serialization.SerializationUtil.writeUnsignedVarLong;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.bin.Bin;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.quantile.QuantileEstimator;
import com.dynatrace.dynahist.quantile.SciPyQuantileEstimator;
import com.dynatrace.dynahist.value.ValueEstimator;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;

abstract class AbstractHistogram implements Histogram {

  protected static final byte SERIAL_VERSION_V0 = 0;

  private static final QuantileEstimator DEFAULT_QUANTILE_ESTIMATOR =
      SciPyQuantileEstimator.create();

  private static final ValueEstimator DEFAULT_VALUE_ESTIMATOR = ValueEstimator.UNIFORM;

  protected static final long ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES = 4;
  protected static final long ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES = 12;

  private final Layout layout;

  protected AbstractHistogram(final Layout layout) {
    this.layout = requireNonNull(layout);
  }

  /**
   * Returns the mode which is a value from {0, 1, 2, 3, 4, 5, 6} indicating if 2^mode bits are
   * sufficient to represent all regular bin counts (excluding under and overflow bins).
   *
   * @return the mode
   */
  protected abstract byte getMode();

  protected static byte determineRequiredMode(final long value) {
    if (value > 0xFFFFFFFFL) {
      return 6;
    } else if (value > 0xFFFFL) {
      return 5;
    } else if (value > 0xFFL) {
      return 4;
    } else if (value > 0xFL) {
      return 3;
    } else if (value > 0x3L) {
      return 2;
    } else if (value > 0x1L) {
      return 1;
    } else {
      return 0;
    }
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

  private abstract static class AbstractNonEmptyBinsIterable implements Iterable<Bin> {

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

  @Override
  public final void write(final DataOutput dataOutput) throws IOException {
    requireNonNull(dataOutput);

    // 0. write serial version and mode
    dataOutput.writeByte(SERIAL_VERSION_V0);

    // info byte definition:
    // bit 1 - 3:
    // 0: special mode total count is 0 or 1
    // 1: mode == 0
    // 2: mode == 1
    // 3: mode == 2
    // 4: mode == 3
    // 5: mode == 4
    // 6: mode == 5
    // 7: mode == 6
    // bit 4:
    // 0: min == max (or special mode and total count is 0)
    // 1: min < max (or special mode and total count is 1)
    // bit 5 - 6:
    // 0: effective regular count == 0 (or special mode)
    // 1: effective regular count == 1
    // 2: effective regular count == 2
    // 3: effective regular count >= 3
    // bit 7
    // 0: effective underflow count == 0 (or special mode)
    // 1: effective underflow count >= 1
    // bit 8
    // 0: effective overflow count == 0 (or special mode)
    // 1: effective overflow count >= 1

    final double min = getMin();
    final long totalCount = getTotalCount();

    if (totalCount <= 1) {
      // special mode
      if (isEmpty()) {
        int infoByte = 0x00;
        dataOutput.writeByte(infoByte);
      } else {
        int infoByte = 0x08;
        dataOutput.writeByte(infoByte);
        dataOutput.writeDouble(min);
      }
      return;
    }

    final Layout layout = getLayout();

    // since the minimum and maximum values are explicitly serialized, we can drop
    // them from the corresponding bins, which reduces
    // the corresponding counts, the "effective" bin counts represent the bin counts
    // after removing the minimum and the maximum
    final long underflowCount = getUnderflowCount();
    final long overflowCount = getOverflowCount();
    final long effectiveUnderFlowCount =
        underflowCount - (underflowCount > 0 ? 1 : 0) - (underflowCount == totalCount ? 1 : 0);
    final long effectiveOverFlowCount =
        overflowCount - (overflowCount > 0 ? 1 : 0) - (overflowCount == totalCount ? 1 : 0);
    final long effectiveTotalCount = totalCount - 2;
    final long effectiveRegularTotalCount =
        effectiveTotalCount
            - effectiveUnderFlowCount
            - effectiveOverFlowCount; // effective count in normal range

    final byte mode = getMode();
    final double max = getMax();
    final boolean isMinSmallerThanMax = Double.compare(min, max) < 0;

    // 1. write info byte
    int infoByte = mode + 1;
    if (isMinSmallerThanMax) {
      infoByte |= 0x08; // bit 4
    }
    infoByte |= ((int) Math.min(effectiveRegularTotalCount, 3)) << 4; // bit 5 and 6
    if (effectiveUnderFlowCount > 0) {
      infoByte |= 0x40; // bit 7
    }
    if (effectiveOverFlowCount > 0) {
      infoByte |= 0x80; // bit 8
    }
    dataOutput.writeByte(infoByte);

    // 2. write minimum and maximum, if necessary
    dataOutput.writeDouble(min);
    if (isMinSmallerThanMax) {
      dataOutput.writeDouble(max);
    }

    // 3. write effective under and over flow counts, if necessary
    if (effectiveUnderFlowCount >= 1) {
      writeUnsignedVarLong(effectiveUnderFlowCount - 1, dataOutput);
    }
    if (effectiveOverFlowCount >= 1) {
      writeUnsignedVarLong(effectiveOverFlowCount - 1, dataOutput);
    }

    if (effectiveRegularTotalCount >= 1) {

      final int minBinIndex = layout.mapToBinIndex(min);
      final int maxBinIndex = layout.mapToBinIndex(max);

      // 4. write first regular effectively non-zero bin index
      BinIterator binIteratorStart = getFirstNonEmptyBin();
      if (binIteratorStart.isUnderflowBin()) {
        binIteratorStart.next();
      }
      while (binIteratorStart.getLessCount() + binIteratorStart.getBinCount() <= 1) {
        binIteratorStart.next();
      }
      int binIndex = binIteratorStart.getBinIndex();
      writeSignedVarInt(binIndex, dataOutput);

      if (effectiveRegularTotalCount >= 2) {

        // 5. write last regular effectively non-zero bin index
        BinIterator binIteratorEnd = getLastNonEmptyBin();
        if (binIteratorEnd.isOverflowBin()) {
          binIteratorEnd.previous();
        }
        while (binIteratorEnd.getGreaterCount() + binIteratorEnd.getBinCount() <= 1) {
          binIteratorEnd.previous();
        }
        final int endBinIndex = binIteratorEnd.getBinIndex();
        writeSignedVarInt(endBinIndex, dataOutput);

        if (effectiveRegularTotalCount >= 3) {

          // 6. write counts if effective regular total counts >= 3, otherwise the counts
          // can be derived from first and last regular effectively non-zero bin indices written in
          // step 4 and 5
          if (mode <= 2) {
            final int countsPerByte = (1 << (3 - mode));
            final int bitsPerCount = (1 << mode);
            final int bitMask = (1 << bitsPerCount) - 1;

            while (binIndex <= endBinIndex) {
              int b = 0;
              for (int i = 0; i < countsPerByte; ++i) {
                b <<= bitsPerCount;
                if (binIndex <= endBinIndex) {
                  if (binIndex == binIteratorStart.getBinIndex()) {
                    long binCount = binIteratorStart.getBinCount();
                    if (binIteratorStart.isFirstNonEmptyBin()) binCount -= 1;
                    if (binIteratorStart.isLastNonEmptyBin()) {
                      binCount -= 1;
                    } else {
                      binIteratorStart.next();
                    }
                    b |= ((int) binCount) & bitMask;
                  }
                  binIndex += 1;
                }
              }
              dataOutput.writeByte(b);
            }
          } else {
            final int bytePerCount = 1 << (mode - 3);
            while (binIndex <= endBinIndex) {
              long binCount = 0;
              if (binIndex == binIteratorStart.getBinIndex()) {
                binCount = binIteratorStart.getBinCount();
                if (binIteratorStart.isFirstNonEmptyBin()) binCount -= 1;
                if (binIteratorStart.isLastNonEmptyBin()) {
                  binCount -= 1;
                } else {
                  binIteratorStart.next();
                }
              }
              binIndex += 1;
              for (int i = bytePerCount - 1; i >= 0; i--) {
                dataOutput.writeByte((int) (binCount >>> (i << 3)));
              }
            }
          }
        }
      }
    }
  }
}
