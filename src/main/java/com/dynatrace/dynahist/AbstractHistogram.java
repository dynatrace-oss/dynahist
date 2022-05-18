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

import static com.dynatrace.dynahist.serialization.SerializationUtil.*;
import static com.dynatrace.dynahist.serialization.SerializationUtil.readSignedVarInt;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.bin.Bin;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.quantile.QuantileEstimator;
import com.dynatrace.dynahist.quantile.SciPyQuantileEstimator;
import com.dynatrace.dynahist.serialization.BitInput;
import com.dynatrace.dynahist.serialization.BitOutput;
import com.dynatrace.dynahist.util.Algorithms;
import com.dynatrace.dynahist.value.ValueEstimator;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.Consumer;

abstract class AbstractHistogram implements Histogram {

  private static final String UNKNOWN_SERIAL_VERSION_MSG = "Unknown serial version %d!";

  protected static final byte SERIAL_VERSION_V0 = 0;
  protected static final byte SERIAL_VERSION_V1 = 1;

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
          return it.copy();
        }
      };
    }

    @Override
    public void forEach(Consumer<? super Bin> action) {
      BinIterator it = getStart();
      action.accept(it.copy());
      while (!isAtEnd(it)) {
        advanceBinIterator(it);
        action.accept(it.copy());
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
    writeSerialVersion1(dataOutput);
  }

  protected static Histogram deserialize(
      final Layout layout, final HistogramDeserializationBuilder builder, final DataInput dataInput)
      throws IOException {

    requireNonNull(layout);
    requireNonNull(builder);
    requireNonNull(dataInput);

    byte serialVersion = dataInput.readByte();
    if (serialVersion == SERIAL_VERSION_V1) {
      return deserializeVersion1(layout, builder, dataInput);
    } else if (serialVersion == SERIAL_VERSION_V0) {
      return deserializeVersion0(layout, builder, dataInput);
    } else {
      throw new IOException(String.format(Locale.ROOT, UNKNOWN_SERIAL_VERSION_MSG, serialVersion));
    }
  }

  private BinIterator getFirstNonEmptyRegularBin() {
    BinIterator binIterator = getFirstNonEmptyBin();
    if (binIterator.isUnderflowBin()) {
      binIterator.next();
    }
    while (binIterator.getLessCount() + binIterator.getBinCount() <= 1) {
      binIterator.next();
    }
    return binIterator;
  }

  private static long getEffectiveRegularCount(BinIterator binIterator) {
    long binCount = binIterator.getBinCount();
    if (binIterator.isFirstNonEmptyBin()) binCount -= 1;
    if (binIterator.isLastNonEmptyBin()) binCount -= 1;
    return binCount;
  }

  private BinIterator getLastNonEmptyRegularBin() {
    BinIterator binIteratorEnd = getLastNonEmptyBin();
    if (binIteratorEnd.isOverflowBin()) {
      binIteratorEnd.previous();
    }
    while (binIteratorEnd.getGreaterCount() + binIteratorEnd.getBinCount() <= 1) {
      binIteratorEnd.previous();
    }
    return binIteratorEnd;
  }

  // visible for testing
  void writeSerialVersion0(final DataOutput dataOutput) throws IOException {

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

      // 4. write first regular effectively non-zero bin index
      BinIterator binIteratorStart = getFirstNonEmptyRegularBin();
      final int startBinIndex = binIteratorStart.getBinIndex();
      writeSignedVarInt(startBinIndex, dataOutput);

      if (effectiveRegularTotalCount >= 2) {

        // 5. write last regular effectively non-zero bin index
        BinIterator binIteratorEnd = getLastNonEmptyRegularBin();
        final int endBinIndex = binIteratorEnd.getBinIndex();
        writeSignedVarInt(endBinIndex, dataOutput);

        if (effectiveRegularTotalCount >= 3) {

          // 6. write counts if effective regular total counts >= 3, otherwise the counts
          // can be derived from first and last regular effectively non-zero bin indices written in
          // step 4 and 5
          final int bitsPerCount = 1 << mode;
          BitOutput bitOutput = new BitOutput(dataOutput);
          bitOutput.writeBits(bitsPerCount, getEffectiveRegularCount(binIteratorStart));
          int binIndex = binIteratorStart.getBinIndex();
          while (binIndex < endBinIndex) {
            int lastBinIndex = binIndex;
            binIteratorStart.next();
            binIndex = binIteratorStart.getBinIndex();
            for (int i = lastBinIndex + 1; i < binIndex; ++i) {
              bitOutput.writeBits(bitsPerCount, 0L);
            }
            bitOutput.writeBits(bitsPerCount, getEffectiveRegularCount(binIteratorStart));
          }
          bitOutput.finish();
        }
      }
    }
  }

  private static void setSingleValue(
      final Layout layout, HistogramDeserializationBuilder builder, double value) {
    builder.setMinValue(value);
    builder.setMaxValue(value);
    int binIndex = layout.mapToBinIndex(value);
    if (binIndex <= layout.getUnderflowBinIndex()) {
      builder.incrementUnderflowCount(1);
    } else if (binIndex >= layout.getOverflowBinIndex()) {
      builder.incrementOverflowCount(1);
    } else {
      builder.allocateRegularCounts(binIndex, binIndex, 1);
      builder.incrementRegularCount(binIndex, 1);
    }
    builder.incrementTotalCount(1);
  }

  protected static Histogram deserializeVersion0(
      final Layout layout, final HistogramDeserializationBuilder builder, final DataInput dataInput)
      throws IOException {

    // 1. read info byte
    final int infoByte = dataInput.readUnsignedByte();
    if ((infoByte & 0x07) == 0) {
      // special mode
      if ((infoByte & 0x08) > 0) {
        setSingleValue(layout, builder, dataInput.readDouble());
      }
      return builder.build();
    }

    final byte mode = (byte) ((infoByte & 0x07) - 1);
    final boolean isMinSmallerThanMax = (infoByte & 0x08) > 0;

    final long effectiveRegularTotalCount = (infoByte >>> 4) & 0x03;
    long effectiveUnderFlowCount = (infoByte >>> 6) & 0x01;
    long effectiveOverFlowCount = (infoByte >>> 7) & 0x01;

    // 2. read minimum and maximum, if necessary
    final double min = dataInput.readDouble();
    final int minBinIndex = layout.mapToBinIndex(min);
    final double max;
    final int maxBinIndex;
    if (isMinSmallerThanMax) {
      max = dataInput.readDouble();
      maxBinIndex = layout.mapToBinIndex(max);
    } else {
      max = min;
      maxBinIndex = minBinIndex;
    }

    // 3. read effective under and over flow counts, if necessary
    if (effectiveUnderFlowCount == 1) {
      effectiveUnderFlowCount += readUnsignedVarLong(dataInput);
    }
    if (effectiveOverFlowCount == 1) {
      effectiveOverFlowCount += readUnsignedVarLong(dataInput);
    }

    long totalCount = 2 + effectiveOverFlowCount + effectiveUnderFlowCount;

    if (effectiveRegularTotalCount >= 1) {
      // 4. read first regular effectively non-zero bin index
      final int firstRegularEffectivelyNonZeroBinIndex = readSignedVarInt(dataInput);

      final int lastRegularEffectivelyNonZeroBinIndex;
      if (effectiveRegularTotalCount >= 2) {
        // 5. read last regular effectively non-zero bin index
        lastRegularEffectivelyNonZeroBinIndex = readSignedVarInt(dataInput);
      } else {
        lastRegularEffectivelyNonZeroBinIndex = firstRegularEffectivelyNonZeroBinIndex;
      }

      builder.setMinValue(min);
      builder.setMaxValue(max);

      if (layout.getUnderflowBinIndex() + 1 < layout.getOverflowBinIndex()) {
        final int minAllocatedBinIndexUnclipped;
        if (minBinIndex <= layout.getUnderflowBinIndex()) {
          minAllocatedBinIndexUnclipped = firstRegularEffectivelyNonZeroBinIndex;
        } else {
          minAllocatedBinIndexUnclipped =
              Math.min(minBinIndex, firstRegularEffectivelyNonZeroBinIndex);
        }

        final int maxAllocatedBinIndexUnclipped;
        if (maxBinIndex >= layout.getOverflowBinIndex()) {
          maxAllocatedBinIndexUnclipped = lastRegularEffectivelyNonZeroBinIndex;
        } else {
          maxAllocatedBinIndexUnclipped =
              Math.max(maxBinIndex, lastRegularEffectivelyNonZeroBinIndex);
        }
        final int minAllocatedBinIndex =
            Algorithms.clip(
                minAllocatedBinIndexUnclipped,
                layout.getUnderflowBinIndex() + 1,
                layout.getOverflowBinIndex() - 1);
        final int maxAllocatedBinIndex =
            Algorithms.clip(
                maxAllocatedBinIndexUnclipped,
                layout.getUnderflowBinIndex() + 1,
                layout.getOverflowBinIndex() - 1);
        builder.allocateRegularCounts(minAllocatedBinIndex, maxAllocatedBinIndex, 1 << mode);
      }

      if (effectiveRegularTotalCount >= 3) {

        // 6. read counts
        BitInput bitInput = new BitInput(dataInput);
        final int bitsPerCount = (1 << mode);
        for (int binIndex = firstRegularEffectivelyNonZeroBinIndex;
            binIndex <= lastRegularEffectivelyNonZeroBinIndex;
            ++binIndex) {
          long binCount = bitInput.readBits(bitsPerCount);
          builder.incrementRegularCount(binIndex, binCount);
          totalCount += binCount;
        }
      } else {
        builder.incrementRegularCount(firstRegularEffectivelyNonZeroBinIndex, 1);
        totalCount += 1;
        if (effectiveRegularTotalCount == 2) {
          builder.incrementRegularCount(lastRegularEffectivelyNonZeroBinIndex, 1);
          totalCount += 1;
        }
      }
    } else {
      builder.setMinValue(min);
      builder.setMaxValue(max);
    }

    if (minBinIndex <= layout.getUnderflowBinIndex()) {
      builder.incrementUnderflowCount(1);
    } else if (minBinIndex >= layout.getOverflowBinIndex()) {
      builder.incrementOverflowCount(1);
    } else {
      builder.incrementRegularCountSafe(minBinIndex);
    }

    if (maxBinIndex <= layout.getUnderflowBinIndex()) {
      builder.incrementUnderflowCount(1);
    } else if (maxBinIndex >= layout.getOverflowBinIndex()) {
      builder.incrementOverflowCount(1);
    } else {
      builder.incrementRegularCountSafe(maxBinIndex);
    }

    builder.incrementUnderflowCount(effectiveUnderFlowCount);
    builder.incrementOverflowCount(effectiveOverFlowCount);
    builder.incrementTotalCount(totalCount);
    return builder.build();
  }

  // visible for testing
  void writeSerialVersion1(final DataOutput dataOutput) throws IOException {

    dataOutput.writeByte(SERIAL_VERSION_V1);

    // info byte definition:
    //   bit 1:
    //     0: special mode <=> total count is 0 or 1
    //     1: normal mode <=> total count >= 2
    //   bit 2:
    //     0: min == max (or special mode and total count is 0)
    //     1: min < max (or special mode and total count is 1)
    //   bit 3-4:
    //     0: effective regular count == 0 (or special mode)
    //     1: effective regular count == 1
    //     2: effective regular count == 2
    //     3: effective regular count >= 3
    //   bit 5-6:
    //     0: effective underflow count == 0 (or special mode)
    //     1: effective underflow count == 1
    //     2: effective underflow count == 2
    //     3: effective underflow count >= 3
    //   bit 7-8:
    //     0: effective overflow count == 0 (or special mode)
    //     1: effective overflow count == 1
    //     2: effective overflow count == 2
    //     3: effective overflow count >= 3

    final double min = getMin();
    final long totalCount = getTotalCount();

    if (totalCount <= 1) {
      // special mode
      if (isEmpty()) {
        int infoByte = 0x00;
        dataOutput.writeByte(infoByte);
      } else {
        int infoByte = 0x02;
        dataOutput.writeByte(infoByte);
        dataOutput.writeDouble(min);
      }
      return;
    }

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

    final double max = getMax();
    final boolean isMinSmallerThanMax = Double.compare(min, max) < 0;

    // 1. write info byte
    int infoByte = 1;
    if (isMinSmallerThanMax) {
      infoByte |= 0x02; // bit 2
    }
    infoByte |= Math.min(3, effectiveRegularTotalCount) << 2; // bit 3 and 4
    infoByte |= Math.min(3, effectiveUnderFlowCount) << 4; // bit 5 and 6
    infoByte |= Math.min(3, effectiveOverFlowCount) << 6; // bit 7 and 8
    dataOutput.writeByte(infoByte);

    // 2. write minimum and maximum, if necessary
    dataOutput.writeDouble(min);
    if (isMinSmallerThanMax) {
      dataOutput.writeDouble(max);
    }

    // 3. write effective under and over flow counts, if necessary
    if (effectiveUnderFlowCount >= 3) {
      writeUnsignedVarLong(effectiveUnderFlowCount - 3, dataOutput);
    }
    if (effectiveOverFlowCount >= 3) {
      writeUnsignedVarLong(effectiveOverFlowCount - 3, dataOutput);
    }

    if (effectiveRegularTotalCount >= 1) {

      // 4. write first regular effectively non-zero bin index
      BinIterator binIteratorStart = getFirstNonEmptyRegularBin();
      final int startBinIndex = binIteratorStart.getBinIndex();

      if (effectiveRegularTotalCount == 2) {
        // 5. write last regular effectively non-zero bin index
        BinIterator binIteratorEnd = getLastNonEmptyRegularBin();
        final int endBinIndex = binIteratorEnd.getBinIndex();

        writeSignedVarInt(startBinIndex, dataOutput);
        writeSignedVarInt(endBinIndex, dataOutput);
      } else if (effectiveRegularTotalCount >= 3) {

        BinIterator binIteratorEnd = getLastNonEmptyRegularBin();
        final int endBinIndex = binIteratorEnd.getBinIndex();

        final int bitsPerCount;
        final int bitsForDiffIndex;
        final boolean denseSerialization;
        int numberOfNonZeroRegularBins;
        {
          BinIterator it = binIteratorStart.copy();
          long maxCountMask = it.getBinCount();
          int diffIndexMask = 0;
          numberOfNonZeroRegularBins = 1;
          int binIndex = it.getBinIndex();
          while (binIndex < endBinIndex) {
            int lastBinIndex = binIndex;
            it.next();
            binIndex = it.getBinIndex();
            diffIndexMask |= binIndex - lastBinIndex - 1;
            maxCountMask |= it.getBinCount();
            numberOfNonZeroRegularBins += 1;
          }
          int numberOfBins = endBinIndex - startBinIndex + 1;

          bitsPerCount = Long.SIZE - Long.numberOfLeadingZeros(maxCountMask);
          bitsForDiffIndex = Integer.SIZE - Integer.numberOfLeadingZeros(diffIndexMask);

          long bitsForVariant1 = 6 + numberOfBins * (long) bitsPerCount;
          long bitsForVariant2 =
              6
                  + 5
                  + numberOfNonZeroRegularBins * (long) bitsPerCount
                  + (numberOfNonZeroRegularBins - 1) * (long) bitsForDiffIndex;
          denseSerialization = bitsForVariant1 <= bitsForVariant2;
        }

        if (denseSerialization) {
          writeSignedVarInt(startBinIndex, dataOutput);
          writeSignedVarInt(endBinIndex, dataOutput);
        } else {
          writeSignedVarInt(endBinIndex, dataOutput);
          writeSignedVarInt(startBinIndex, dataOutput);
        }
        BitOutput bitOutput = new BitOutput(dataOutput);
        bitOutput.writeBits(6, bitsPerCount);

        if (denseSerialization) {
          bitOutput.writeBits(bitsPerCount, getEffectiveRegularCount(binIteratorStart));
          int binIndex = binIteratorStart.getBinIndex();
          while (binIndex < endBinIndex) {
            int lastBinIndex = binIndex;
            binIteratorStart.next();
            binIndex = binIteratorStart.getBinIndex();
            for (int i = lastBinIndex + 1; i < binIndex; ++i) {
              bitOutput.writeBits(bitsPerCount, 0L);
            }
            bitOutput.writeBits(bitsPerCount, getEffectiveRegularCount(binIteratorStart));
          }
        } else {
          bitOutput.writeBits(5, bitsForDiffIndex);
          bitOutput.writeBits(bitsPerCount, getEffectiveRegularCount(binIteratorStart));
          int binIndex = binIteratorStart.getBinIndex();
          while (binIndex < endBinIndex) {
            int lastBinIndex = binIndex;
            binIteratorStart.next();
            binIndex = binIteratorStart.getBinIndex();
            bitOutput.writeBits(bitsForDiffIndex, binIndex - lastBinIndex - 1L);
            bitOutput.writeBits(bitsPerCount, getEffectiveRegularCount(binIteratorStart));
          }
        }
        bitOutput.finish();
      } else {
        writeSignedVarInt(startBinIndex, dataOutput);
      }
    }
  }

  protected static Histogram deserializeVersion1(
      final Layout layout, final HistogramDeserializationBuilder builder, final DataInput dataInput)
      throws IOException {

    // 1. read info byte
    final int infoByte = dataInput.readUnsignedByte();
    if ((infoByte & 0x01) == 0) {
      // special mode
      if ((infoByte & 0x02) > 0) {
        setSingleValue(layout, builder, dataInput.readDouble());
      }
      return builder.build();
    }

    final boolean isMinSmallerThanMax = (infoByte & 0x02) > 0;

    final long effectiveRegularTotalCount = (infoByte >>> 2) & 0x03;
    long effectiveUnderFlowCount = (infoByte >>> 4) & 0x03;
    long effectiveOverFlowCount = (infoByte >>> 6) & 0x03;

    // 2. read minimum and maximum, if necessary
    final double min = dataInput.readDouble();
    final int minBinIndex = layout.mapToBinIndex(min);
    final double max;
    final int maxBinIndex;
    if (isMinSmallerThanMax) {
      max = dataInput.readDouble();
      maxBinIndex = layout.mapToBinIndex(max);
    } else {
      max = min;
      maxBinIndex = minBinIndex;
    }

    // 3. read effective under and overflow counts, if necessary
    if (effectiveUnderFlowCount == 3) {
      effectiveUnderFlowCount += readUnsignedVarLong(dataInput);
    }
    if (effectiveOverFlowCount == 3) {
      effectiveOverFlowCount += readUnsignedVarLong(dataInput);
    }

    long totalCount = 2 + effectiveOverFlowCount + effectiveUnderFlowCount;

    if (effectiveRegularTotalCount >= 1) {
      // 4. read first regular effectively non-zero bin index
      int firstRegularEffectivelyNonZeroBinIndex = readSignedVarInt(dataInput);

      int lastRegularEffectivelyNonZeroBinIndex;
      boolean denseSerialization = true;
      if (effectiveRegularTotalCount >= 2) {
        // 5. read last regular effectively non-zero bin index
        lastRegularEffectivelyNonZeroBinIndex = readSignedVarInt(dataInput);

        if (firstRegularEffectivelyNonZeroBinIndex > lastRegularEffectivelyNonZeroBinIndex) {
          denseSerialization = false;
          int t = firstRegularEffectivelyNonZeroBinIndex;
          firstRegularEffectivelyNonZeroBinIndex = lastRegularEffectivelyNonZeroBinIndex;
          lastRegularEffectivelyNonZeroBinIndex = t;
        }

      } else {
        lastRegularEffectivelyNonZeroBinIndex = firstRegularEffectivelyNonZeroBinIndex;
      }

      builder.setMinValue(min);
      builder.setMaxValue(max);

      if (effectiveRegularTotalCount >= 3) {

        // 6. read counts
        BitInput bitInput = new BitInput(dataInput);
        final int bitsPerCount = (int) bitInput.readBits(6);

        if (layout.getUnderflowBinIndex() + 1 < layout.getOverflowBinIndex()) {
          final int minAllocatedBinIndexUnclipped;
          if (minBinIndex <= layout.getUnderflowBinIndex()) {
            minAllocatedBinIndexUnclipped = firstRegularEffectivelyNonZeroBinIndex;
          } else {
            minAllocatedBinIndexUnclipped =
                Math.min(minBinIndex, firstRegularEffectivelyNonZeroBinIndex);
          }

          final int maxAllocatedBinIndexUnclipped;
          if (maxBinIndex >= layout.getOverflowBinIndex()) {
            maxAllocatedBinIndexUnclipped = lastRegularEffectivelyNonZeroBinIndex;
          } else {
            maxAllocatedBinIndexUnclipped =
                Math.max(maxBinIndex, lastRegularEffectivelyNonZeroBinIndex);
          }
          final int minAllocatedBinIndex =
              Algorithms.clip(
                  minAllocatedBinIndexUnclipped,
                  layout.getUnderflowBinIndex() + 1,
                  layout.getOverflowBinIndex() - 1);
          final int maxAllocatedBinIndex =
              Algorithms.clip(
                  maxAllocatedBinIndexUnclipped,
                  layout.getUnderflowBinIndex() + 1,
                  layout.getOverflowBinIndex() - 1);
          builder.allocateRegularCounts(minAllocatedBinIndex, maxAllocatedBinIndex, bitsPerCount);
        }

        if (denseSerialization) {
          for (int binIndex = firstRegularEffectivelyNonZeroBinIndex;
              binIndex <= lastRegularEffectivelyNonZeroBinIndex;
              ++binIndex) {
            long binCount = bitInput.readBits(bitsPerCount);
            builder.incrementRegularCount(binIndex, binCount);
            totalCount += binCount;
          }
        } else {
          final int bitsForDiffIndex = (int) bitInput.readBits(5);
          int binIndex = firstRegularEffectivelyNonZeroBinIndex;
          while (true) {
            long binCount = bitInput.readBits(bitsPerCount);
            builder.incrementRegularCount(binIndex, binCount);
            totalCount += binCount;
            if (binIndex >= lastRegularEffectivelyNonZeroBinIndex) break;
            binIndex += bitInput.readBits(bitsForDiffIndex) + 1;
          }
        }
      } else {
        builder.incrementRegularCount(firstRegularEffectivelyNonZeroBinIndex, 1);
        totalCount += 1;
        if (effectiveRegularTotalCount == 2) {
          builder.incrementRegularCount(lastRegularEffectivelyNonZeroBinIndex, 1);
          totalCount += 1;
        }
      }
    } else {
      builder.setMinValue(min);
      builder.setMaxValue(max);
    }

    if (minBinIndex <= layout.getUnderflowBinIndex()) {
      builder.incrementUnderflowCount(1);
    } else if (minBinIndex >= layout.getOverflowBinIndex()) {
      builder.incrementOverflowCount(1);
    } else {
      builder.incrementRegularCountSafe(minBinIndex);
    }

    if (maxBinIndex <= layout.getUnderflowBinIndex()) {
      builder.incrementUnderflowCount(1);
    } else if (maxBinIndex >= layout.getOverflowBinIndex()) {
      builder.incrementOverflowCount(1);
    } else {
      builder.incrementRegularCountSafe(maxBinIndex);
    }

    builder.incrementUnderflowCount(effectiveUnderFlowCount);
    builder.incrementOverflowCount(effectiveOverFlowCount);
    builder.incrementTotalCount(totalCount);
    return builder.build();
  }
}
