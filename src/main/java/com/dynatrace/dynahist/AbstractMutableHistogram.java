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

import static com.dynatrace.dynahist.serialization.SerializationUtil.readSignedVarInt;
import static com.dynatrace.dynahist.serialization.SerializationUtil.readUnsignedVarLong;
import static com.dynatrace.dynahist.util.Algorithms.findFirst;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.bin.AbstractBin;
import com.dynatrace.dynahist.bin.Bin;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.serialization.SerializationUtil;
import com.dynatrace.dynahist.util.Algorithms;
import com.dynatrace.dynahist.value.ValueEstimator;
import java.io.DataInput;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.function.LongToDoubleFunction;

abstract class AbstractMutableHistogram extends AbstractHistogram implements Histogram {

  protected static final double GROW_FACTOR = 0.25;

  protected static final String OVERFLOW_MSG = "Overflow occurred!";
  protected static final String NAN_VALUE_MSG = "Value was not a number (NaN)!";
  protected static final String NEGATIVE_COUNT_MSG = "Count must be non-negative, but was %d!";
  protected static final String INCOMPATIBLE_SERIAL_VERSION_MSG =
      "Incompatible serial versions! Expected version " + SERIAL_VERSION_V0 + " but was %d.";

  protected static final long[] EMPTY_COUNTS = {};

  private long underflowCount = 0;
  private long overflowCount = 0;
  protected long totalCount = 0;
  private double min = Double.POSITIVE_INFINITY;
  private double max = Double.NEGATIVE_INFINITY;

  protected AbstractMutableHistogram(final Layout layout) {
    super(layout);
  }

  protected void incrementUnderflowCount(final long count) {
    underflowCount += count;
  }

  protected void incrementOverflowCount(final long count) {
    overflowCount += count;
  }

  protected void incrementTotalCount(final long count) {
    totalCount += count;
  }

  protected void updateMinMax(final double value) {
    updateMinMax(value, value);
  }

  protected void updateMinMax(final double min, final double max) {
    if (min <= this.min
        && (min < this.min || (Double.doubleToRawLongBits(min) == 0x8000000000000000L))) {
      this.min = min;
    }
    if (max >= this.max
        && (max > this.max || (Double.doubleToRawLongBits(max) == 0x0000000000000000L))) {
      this.max = max;
    }
  }

  @Override
  public Histogram addHistogram(Histogram histogram, ValueEstimator valueEstimator) {

    requireNonNull(histogram);
    requireNonNull(valueEstimator);

    if (histogram.isEmpty()) {
      return this;
    }
    if (histogram.getTotalCount() > Long.MAX_VALUE - getTotalCount()) {
      throw new ArithmeticException(OVERFLOW_MSG);
    }

    Layout layout = histogram.getLayout();
    if (getLayout().equals(layout)) {

      final BinIterator firstNonEmptyBin = histogram.getFirstNonEmptyBin();
      final BinIterator lastNonEmptyBin = histogram.getLastNonEmptyBin();

      if (firstNonEmptyBin.getBinIndex() == lastNonEmptyBin.getBinIndex()) {
        addValue(histogram.getMin(), histogram.getTotalCount() - 1);
        addValue(histogram.getMax());
      } else {
        addValue(lastNonEmptyBin.getUpperBound(), lastNonEmptyBin.getBinCount());
        for (BinIterator binIterator = firstNonEmptyBin;
            !binIterator.isLastNonEmptyBin();
            binIterator.next()) {
          addValue(binIterator.getLowerBound(), binIterator.getBinCount());
        }
      }
      return this;
    } else {
      // preprocess histogram to get a copy that allows faster random access to
      // approximated values
      final Histogram preprocessedHistogram = histogram.getPreprocessedCopy();
      return addAscendingSequence(
          rank -> preprocessedHistogram.getValue(rank, valueEstimator),
          preprocessedHistogram.getTotalCount());
    }
  }

  @Override
  public long getEstimatedFootprintInBytes() {
    return 3L * Long.BYTES // underFlowCount, overFlowCount, totalCount
        + 2L * Double.BYTES // min, max
        + super.getEstimatedFootprintInBytes();
  }

  /**
   * Return value must be greater than or equal to {@link #maxAllocatedBinIndexExclusive()} if
   * histogram is empty.
   */
  protected abstract int minAllocatedBinIndexInclusive();

  /**
   * Return value must be less than or equal to {@link #minAllocatedBinIndexInclusive()} if
   * histogram is empty.
   */
  protected abstract int maxAllocatedBinIndexExclusive();

  protected abstract long getAllocatedBinCount(int binIndex);

  @Override
  public long getCount(final int binIndex) {
    if (binIndex <= getLayout().getUnderflowBinIndex()) {
      return getUnderflowCount();
    } else if (binIndex < minAllocatedBinIndexInclusive()) {
      return 0L;
    } else if (binIndex < maxAllocatedBinIndexExclusive()) {
      return getAllocatedBinCount(binIndex);
    } else if (binIndex < getLayout().getOverflowBinIndex()) {
      return 0L;
    } else {
      return getOverflowCount();
    }
  }

  private class BinCopyImpl extends AbstractBin {
    private final long binCount;
    private final long lessCount;
    private final long greaterCount;
    private final int binIndex;

    private BinCopyImpl(long binCount, long lessCount, long greaterCount, int binIndex) {
      this.binCount = binCount;
      this.lessCount = lessCount;
      this.greaterCount = greaterCount;
      this.binIndex = binIndex;
    }

    @Override
    protected Histogram getHistogram() {
      return AbstractMutableHistogram.this;
    }

    @Override
    public long getBinCount() {
      return binCount;
    }

    @Override
    public long getLessCount() {
      return lessCount;
    }

    @Override
    public long getGreaterCount() {
      return greaterCount;
    }

    @Override
    public int getBinIndex() {
      return binIndex;
    }
  }

  protected class BinIteratorImpl extends AbstractBin implements BinIterator {

    private int binIndex;
    private long lessCount;
    private long greaterCount;
    private long count;

    protected BinIteratorImpl(
        final int binIndex, final long lessCount, final long greaterCount, final long count) {
      this.binIndex = binIndex;
      this.lessCount = lessCount;
      this.greaterCount = greaterCount;
      this.count = count;
    }

    @Override
    public long getBinCount() {
      return count;
    }

    @Override
    public long getLessCount() {
      return lessCount;
    }

    @Override
    public long getGreaterCount() {
      return greaterCount;
    }

    @Override
    public void next() {
      if (greaterCount <= 0) {
        throw new NoSuchElementException();
      }
      lessCount += count;
      if (greaterCount != getOverflowCount()) {
        if (binIndex == getLayout().getUnderflowBinIndex()) {
          binIndex = minAllocatedBinIndexInclusive() - 1;
        }
        do {
          binIndex += 1;
          count = getAllocatedBinCount(binIndex);
        } while (count == 0L);
        greaterCount -= count;
      } else {
        binIndex = getLayout().getOverflowBinIndex();
        count = greaterCount;
        greaterCount = 0L;
      }
    }

    @Override
    public void previous() {
      if (lessCount <= 0) {
        throw new NoSuchElementException();
      }
      greaterCount += count;
      if (lessCount != getUnderflowCount()) {
        if (binIndex == getLayout().getOverflowBinIndex()) {
          binIndex = maxAllocatedBinIndexExclusive();
        }
        do {
          binIndex -= 1;
          count = getAllocatedBinCount(binIndex);
        } while (count == 0L);
        lessCount -= count;
      } else {
        binIndex = getLayout().getUnderflowBinIndex();
        count = lessCount;
        lessCount = 0L;
      }
    }

    @Override
    public Bin getBinCopy() {
      return new BinCopyImpl(count, lessCount, greaterCount, binIndex);
    }

    @Override
    public int getBinIndex() {
      return binIndex;
    }

    @Override
    protected Histogram getHistogram() {
      return AbstractMutableHistogram.this;
    }
  }

  protected abstract void ensureCountArray(
      int minNonEmptyBinIndex, int maxNonEmptyBinIndex, byte mode);

  protected static <T extends AbstractMutableHistogram> void deserialize(
      final T histogram, final DataInput dataInput) throws IOException {

    requireNonNull(histogram);
    requireNonNull(dataInput);
    checkArgument(histogram.isEmpty());

    final Layout layout = histogram.getLayout();

    // 0. write serial version and mode
    SerializationUtil.checkSerialVersion(SERIAL_VERSION_V0, dataInput.readUnsignedByte());

    // 1. read info byte
    final int infoByte = dataInput.readUnsignedByte();

    if ((infoByte & 0x07) == 0) {
      // special mode
      if ((infoByte & 0x08) > 0) {
        histogram.addValue(dataInput.readDouble());
      }
      return;
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
        histogram.ensureCountArray(minAllocatedBinIndex, maxAllocatedBinIndex, mode);
      }

      if (effectiveRegularTotalCount >= 3) {

        // 6. read counts

        if (mode <= 2) {
          final int bitsPerCount = (1 << mode);
          final int bitMask = (1 << bitsPerCount) - 1;
          int availableBitCount = 0;
          int readBits = 0;
          for (int binIndex = firstRegularEffectivelyNonZeroBinIndex;
              binIndex <= lastRegularEffectivelyNonZeroBinIndex;
              ++binIndex) {
            if (availableBitCount == 0) {
              readBits = dataInput.readUnsignedByte();
              availableBitCount = 8;
            }
            availableBitCount -= bitsPerCount;
            long binCount = (readBits >>> availableBitCount) & bitMask;
            histogram.increaseCount(binIndex, binCount);
            totalCount += binCount;
          }
        } else {
          final int bytePerCount = 1 << (mode - 3);
          for (int binIndex = firstRegularEffectivelyNonZeroBinIndex;
              binIndex <= lastRegularEffectivelyNonZeroBinIndex;
              ++binIndex) {
            long binCount = 0;
            for (int i = 0; i < bytePerCount; ++i) {
              binCount <<= 8;
              binCount += dataInput.readUnsignedByte();
            }
            histogram.increaseCount(binIndex, binCount);
            totalCount += binCount;
          }
        }
      } else {
        histogram.increaseCount(firstRegularEffectivelyNonZeroBinIndex, 1);
        totalCount += 1;
        if (effectiveRegularTotalCount == 2) {
          histogram.increaseCount(lastRegularEffectivelyNonZeroBinIndex, 1);
          totalCount += 1;
        }
      }
    }

    histogram.updateMinMax(min, max);
    histogram.increaseCount(minBinIndex, 1);
    histogram.increaseCount(maxBinIndex, 1);
    histogram.incrementUnderflowCount(effectiveUnderFlowCount);
    histogram.incrementOverflowCount(effectiveOverFlowCount);
    histogram.incrementTotalCount(totalCount);
  }

  protected abstract void increaseCount(final int absoluteIndex, final long count);

  @Override
  public BinIterator getFirstNonEmptyBin() {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }

    final int absoluteIndex;
    final long lessCount = 0L;
    final long greaterCount;
    final long count;
    if (getUnderflowCount() > 0L) {
      absoluteIndex = getLayout().getUnderflowBinIndex();
      count = getUnderflowCount();
      greaterCount = getTotalCount() - getUnderflowCount();
    } else if (getOverflowCount() == getTotalCount()) {
      absoluteIndex = getLayout().getOverflowBinIndex();
      count = getOverflowCount();
      greaterCount = 0L;
    } else {
      long c;
      int idx = minAllocatedBinIndexInclusive() - 1;
      do {
        idx += 1;
        c = getAllocatedBinCount(idx);
      } while (c == 0L);
      absoluteIndex = idx;
      count = c;
      greaterCount = getTotalCount() - c;
    }
    return new BinIteratorImpl(absoluteIndex, lessCount, greaterCount, count);
  }

  @Override
  public BinIterator getLastNonEmptyBin() {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }

    final int absoluteIndex;
    final long lessCount;
    final long greaterCount = 0L;
    final long count;
    if (getOverflowCount() > 0L) {
      absoluteIndex = getLayout().getOverflowBinIndex();
      count = getOverflowCount();
      lessCount = getTotalCount() - getOverflowCount();
    } else if (getTotalCount() == getUnderflowCount()) {
      absoluteIndex = getLayout().getUnderflowBinIndex();
      count = getUnderflowCount();
      lessCount = 0L;
    } else {
      long c;
      int idx = maxAllocatedBinIndexExclusive();
      do {
        idx -= 1;
        c = getAllocatedBinCount(idx);
      } while (c == 0L);
      absoluteIndex = idx;
      count = c;
      lessCount = getTotalCount() - c;
    }
    return new BinIteratorImpl(absoluteIndex, lessCount, greaterCount, count);
  }

  @Override
  public long getUnderflowCount() {
    return underflowCount;
  }

  @Override
  public long getOverflowCount() {
    return overflowCount;
  }

  @Override
  public long getTotalCount() {
    return totalCount;
  }

  @Override
  public double getMin() {
    return min;
  }

  @Override
  public double getMax() {
    return max;
  }

  private int mapToBinIndex(double value) {
    final Layout layout = getLayout();
    int idx = layout.mapToBinIndex(value);
    int underFlowBinIndex = layout.getUnderflowBinIndex();
    if (idx <= underFlowBinIndex) {
      return underFlowBinIndex;
    }
    int overFlowBinIndex = layout.getOverflowBinIndex();
    if (idx >= overFlowBinIndex) {
      return overFlowBinIndex;
    }
    return idx;
  }

  @Override
  public Histogram addAscendingSequence(LongToDoubleFunction ascendingSequence, long length) {
    requireNonNull(ascendingSequence);

    if (length == 0) {
      return this;
    }

    checkArgument(length >= 0);
    if (length > Long.MAX_VALUE - getTotalCount()) {
      throw new ArithmeticException(OVERFLOW_MSG);
    }

    // add last value to update maximum
    final double lastValue = ascendingSequence.applyAsDouble(length - 1);
    addValue(lastValue);

    // add remaining values in ascending order
    final long lengthWithoutLast = length - 1;
    long valIndex = 0;
    while (valIndex != lengthWithoutLast) {
      final double value = ascendingSequence.applyAsDouble(valIndex);
      final int binIndex = mapToBinIndex(value);
      long nextValIndex =
          findFirst(
              i ->
                  i == lengthWithoutLast
                      || mapToBinIndex(ascendingSequence.applyAsDouble(i)) > binIndex,
              valIndex + 1,
              lengthWithoutLast,
              valIndex + 1);
      addValue(value, nextValIndex - valIndex);
      valIndex = nextValIndex;
    }
    return this;
  }

  @Override
  public boolean isMutable() {
    return true;
  }
}
