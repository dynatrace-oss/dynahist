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

import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.value.ValueEstimator;
import java.io.DataInput;
import java.io.IOException;
import java.util.Locale;

final class DynamicHistogram extends AbstractMutableHistogram {

  private long[] counts;
  private byte mode; // use 2^mode bits for counting, mode is in the range {0, 1, 2, 3, 4, 5, 6}
  private byte numberOfUnusedCounts;

  private int indexOffset;

  private static int getBitOffset(final int idx, final byte mode) {
    return (idx << mode);
  }

  private static long getCountMask(final int mode) {
    return 0xFFFFFFFFFFFFFFFFL >>> (0xFFFFFFFF << mode);
  }

  private static int getArrayIndex(final int idx, final byte mode) {
    return idx >> (6 - mode);
  }

  private static long getCount(final long[] counts, final int relativeIdx, final byte mode) {
    final int arrayIdx = getArrayIndex(relativeIdx, mode);
    final long value = counts[arrayIdx];
    return (value >>> getBitOffset(relativeIdx, mode)) & getCountMask(mode);
  }

  private static void setCount(
      final long[] counts, final int relativeIdx, final byte mode, final long newValue) {
    // here newValue must be smaller than (1 << (mode+1))

    final int bitOffset = getBitOffset(relativeIdx, mode);
    final long mask = getCountMask(mode) << bitOffset;
    final long deleteMask = ~mask;
    final long setMask = newValue << bitOffset;
    final int arrayIdx = getArrayIndex(relativeIdx, mode);
    counts[arrayIdx] = (counts[arrayIdx] & deleteMask) | setMask;
  }

  private static int getLongArraySize(final int numCounters, final byte mode) {
    return ((numCounters - 1) >>> (6 - mode)) + 1;
  }

  private static int getNumCounters(
      final long[] counts, final byte numberOfUnusedCounts, final byte mode) {
    return (counts.length << (6 - mode)) - numberOfUnusedCounts;
  }

  @Override
  protected byte getMode() {
    return mode;
  }

  DynamicHistogram(final Layout layout) {
    super(requireNonNull(layout));
    this.mode = 0;
    this.indexOffset = layout.getUnderflowBinIndex() + 1;
    this.numberOfUnusedCounts = 0;
    this.counts = EMPTY_COUNTS;
  }

  public static DynamicHistogram read(final Layout layout, DataInput dataInput) throws IOException {
    requireNonNull(layout);
    requireNonNull(dataInput);

    DynamicHistogram histogram = new DynamicHistogram(layout);
    deserialize(histogram, dataInput);
    return histogram;
  }

  @Override
  public final DynamicHistogram addValue(final double value, final long count) {
    final int absoluteIndex = getLayout().mapToBinIndex(value);
    final int relativeIndex = absoluteIndex - indexOffset;
    final int arrayIdx = getArrayIndex(relativeIndex, mode);
    final int bitOffset = getBitOffset(relativeIndex, mode);
    final long mask = getCountMask(mode);
    if (count > 0) {
      if (totalCount + count >= 0) {
        totalCount += count;
        updateMinMax(value);
        if (arrayIdx >= 0 && arrayIdx < counts.length) {
          final long oldValue = counts[arrayIdx];
          final long newCount = ((oldValue >>> bitOffset) & mask) + count;
          counts[arrayIdx] += (count << bitOffset);
          if ((newCount & (~mask)) != 0l) {
            counts[arrayIdx] = oldValue;
            tryToExtendAndIncreaseCount(absoluteIndex, count, value);
          }
        } else {
          tryToExtendAndIncreaseCount(absoluteIndex, count, value);
        }
      } else {
        throw new ArithmeticException(OVERFLOW_MSG);
      }
    } else if (count < 0) {
      throw new IllegalArgumentException(String.format((Locale) null, NEGATIVE_COUNT_MSG, count));
    }
    return this;
  }

  private void tryToExtendAndIncreaseCount(
      final int absoluteIndex, final long count, final double value) {
    if (!Double.isNaN(value)) {
      increaseCount(absoluteIndex, count);
    } else {
      totalCount -= count;
      throw new IllegalArgumentException(NAN_VALUE_MSG);
    }
  }

  @Override
  protected void increaseCount(final int absoluteIndex, final long count) {

    if (absoluteIndex <= getLayout().getUnderflowBinIndex()) {
      incrementUnderflowCount(count);
    } else if (absoluteIndex >= getLayout().getOverflowBinIndex()) {
      incrementOverflowCount(count);
    } else {
      final int relativeIndex = absoluteIndex - indexOffset;
      final long newCount;
      if (relativeIndex >= 0
          && relativeIndex < getNumCounters(counts, numberOfUnusedCounts, mode)) {
        newCount = getCount(counts, relativeIndex, mode) + count;
      } else {
        newCount = count;
      }
      ensureCountArray(absoluteIndex, absoluteIndex, determineRequiredMode(newCount));
      setCount(counts, absoluteIndex - indexOffset, mode, newCount);
    }
  }

  @Override
  protected void ensureCountArray(
      final int minAbsoluteIndex, final int maxAbsoluteIndex, final byte requiredMode) {

    checkArgument(minAbsoluteIndex <= maxAbsoluteIndex);
    checkArgument(minAbsoluteIndex > getLayout().getUnderflowBinIndex());
    checkArgument(maxAbsoluteIndex < getLayout().getOverflowBinIndex());

    final int newMinAbsoluteIndex;
    final int newMaxAbsoluteIndex;
    final int currentNumCounters = getNumCounters(counts, numberOfUnusedCounts, mode);
    final int currentMinAbsoluteIndex = indexOffset;
    final int currentMaxAbsoluteIndex = indexOffset + currentNumCounters - 1;

    boolean isExpansionNecessary = false;

    if (counts.length > 0) {

      if (minAbsoluteIndex < currentMinAbsoluteIndex) {
        newMinAbsoluteIndex =
            Math.max(
                getLayout().getUnderflowBinIndex() + 1,
                Math.min(
                    minAbsoluteIndex,
                    (int) Math.ceil(currentMinAbsoluteIndex - currentNumCounters * GROW_FACTOR)));
        isExpansionNecessary = true;
      } else {
        newMinAbsoluteIndex = currentMinAbsoluteIndex;
      }

      if (maxAbsoluteIndex > currentMaxAbsoluteIndex) {
        newMaxAbsoluteIndex =
            Math.min(
                getLayout().getOverflowBinIndex() - 1,
                Math.max(
                    maxAbsoluteIndex,
                    (int) Math.ceil(currentMaxAbsoluteIndex + currentNumCounters * GROW_FACTOR)));
        isExpansionNecessary = true;
      } else {
        newMaxAbsoluteIndex = currentMaxAbsoluteIndex;
      }
    } else {
      newMinAbsoluteIndex = minAbsoluteIndex;
      newMaxAbsoluteIndex = maxAbsoluteIndex;
      isExpansionNecessary = true;
    }

    final byte newMode;
    if (requiredMode > mode) {
      isExpansionNecessary = true;
      newMode = requiredMode;
    } else {
      newMode = mode;
    }

    if (isExpansionNecessary) {
      final int newNumCounters = newMaxAbsoluteIndex - newMinAbsoluteIndex + 1;
      final int newFrom = currentMinAbsoluteIndex - newMinAbsoluteIndex;
      final long[] oldCounts = counts;
      counts = new long[getLongArraySize(newNumCounters, newMode)];
      for (int i = 0; i < currentNumCounters; ++i) {
        setCount(counts, i + newFrom, newMode, getCount(oldCounts, i, mode));
      }
      final int numberOfUnusedBits = (counts.length << 6) - (newNumCounters << newMode);
      counts[counts.length - 1] |= ~(0xffffffffffffffffl >>> numberOfUnusedBits);

      mode = newMode;
      indexOffset = newMinAbsoluteIndex;
      numberOfUnusedCounts = (byte) (numberOfUnusedBits >> mode);
    }
  }

  @Override
  public Histogram addHistogram(final Histogram histogram, ValueEstimator valueEstimator) {
    requireNonNull(histogram);
    requireNonNull(valueEstimator);

    if (histogram.isEmpty()) {
      return this;
    }

    if (getLayout().equals(histogram.getLayout())) {

      totalCount += histogram.getTotalCount();
      if (totalCount < 0) {
        totalCount -= histogram.getTotalCount();
        throw new ArithmeticException(OVERFLOW_MSG);
      }

      updateMinMax(histogram.getMin(), histogram.getMax());
      incrementUnderflowCount(histogram.getUnderflowCount());
      incrementOverflowCount(histogram.getOverflowCount());

      if (histogram.getUnderflowCount() + histogram.getOverflowCount()
          < histogram.getTotalCount()) {
        final BinIterator firstBin = histogram.getFirstNonEmptyBin();
        final BinIterator lastBin = histogram.getLastNonEmptyBin();
        if (firstBin.isUnderflowBin()) {
          firstBin.next();
        }
        if (lastBin.isOverflowBin()) {
          lastBin.previous();
        }
        {
          final byte desiredMode;
          if (histogram instanceof DynamicHistogram) {
            desiredMode = (byte) Math.max(mode, ((DynamicHistogram) histogram).mode);
          } else {
            desiredMode = mode;
          }
          ensureCountArray(firstBin.getBinIndex(), lastBin.getBinIndex(), desiredMode);
        }
        long limit = getCountMask(mode);
        while (true) {
          final int relativeIndex = firstBin.getBinIndex() - indexOffset;
          final long mergedCount = getCount(counts, relativeIndex, mode) + firstBin.getBinCount();
          if (mergedCount > limit) {
            ensureCountArray(
                firstBin.getBinIndex(), firstBin.getBinIndex(), determineRequiredMode(mergedCount));
            limit = getCountMask(mode);
          }
          setCount(counts, relativeIndex, mode, mergedCount);
          if (firstBin.getBinIndex() == lastBin.getBinIndex()) {
            break;
          }
          firstBin.next();
        }
      }
    } else {
      super.addHistogram(histogram, valueEstimator);
    }
    return this;
  }

  @Override
  public long getEstimatedFootprintInBytes() {
    return (ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES
            + ((long) counts.length) * Long.BYTES
            + ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES
            + Integer.BYTES) // counts
        + Byte.BYTES // mode
        + Byte.BYTES // numberOfUnusedCounts
        + Integer.BYTES // indexOffset
        + super.getEstimatedFootprintInBytes();
  }

  @Override
  protected int minAllocatedBinIndexInclusive() {
    return indexOffset;
  }

  @Override
  protected int maxAllocatedBinIndexExclusive() {
    return indexOffset + getNumCounters(counts, numberOfUnusedCounts, mode);
  }

  @Override
  protected long getAllocatedBinCount(final int binIndex) {
    return getCount(counts, binIndex - indexOffset, mode);
  }
}
