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
import static com.dynatrace.dynahist.util.Preconditions.checkState;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.LongToDoubleFunction;

/** A preprocessed and immutable histogram that allows fast order statistic queries. */
final class PreprocessedHistogram extends AbstractHistogram {

  private static final int[] EMPTY_BIN_INDICES = {};
  private static final long[] EMPTY_ACCUMULATED_COUNTS = {};

  private final double min;
  private final double max;
  private final long[] accumulatedCounts;
  private final int[] nonEmptyBinIndices;

  static Histogram of(Histogram histogram) {
    if (histogram instanceof PreprocessedHistogram) {
      return histogram;
    } else {
      return new PreprocessedHistogram(histogram);
    }
  }

  private PreprocessedHistogram(Histogram histogram) {
    super(histogram.getLayout());

    min = histogram.getMin();
    max = histogram.getMax();

    if (histogram.isEmpty()) {
      nonEmptyBinIndices = EMPTY_BIN_INDICES;
      accumulatedCounts = EMPTY_ACCUMULATED_COUNTS;
    } else {

      final BinIterator firstNonEmptyBin = histogram.getFirstNonEmptyBin();
      final BinIterator lastNonEmptyBin = histogram.getLastNonEmptyBin();

      final int firstNonEmptyBinIndex = firstNonEmptyBin.getBinIndex();
      final int lastNonEmptyBinIndex = lastNonEmptyBin.getBinIndex();
      final int binIndexRange = lastNonEmptyBinIndex - firstNonEmptyBinIndex + 1;

      final int[] nonEmptyBinIndicesTmp = new int[binIndexRange];
      final long[] accumulatedCountsTmp = new long[binIndexRange];

      BinIterator binIterator = firstNonEmptyBin;

      nonEmptyBinIndicesTmp[0] = binIterator.getBinIndex();
      accumulatedCountsTmp[0] = binIterator.getBinCount();
      int nonEmptyBinCounter = 1;

      while (!binIterator.isLastNonEmptyBin()) {
        binIterator.next();
        nonEmptyBinIndicesTmp[nonEmptyBinCounter] = binIterator.getBinIndex();
        accumulatedCountsTmp[nonEmptyBinCounter] =
            accumulatedCountsTmp[nonEmptyBinCounter - 1] + binIterator.getBinCount();
        nonEmptyBinCounter += 1;
      }

      nonEmptyBinIndices = Arrays.copyOf(nonEmptyBinIndicesTmp, nonEmptyBinCounter);
      accumulatedCounts = Arrays.copyOf(accumulatedCountsTmp, nonEmptyBinCounter);
    }
  }

  @Override
  public BinIterator getBinByRank(long rank) {
    final long totalCount = getTotalCount();

    checkArgument(rank >= 0l);
    checkArgument(rank < totalCount);

    final int i = Arrays.binarySearch(accumulatedCounts, rank + 1);
    return new BinIteratorImpl((i >= 0) ? i : -(i + 1));
  }

  private long getCountOfNonEmptyBin(int nonEmptyBinIndex) {
    if (nonEmptyBinIndex > 0) {
      return accumulatedCounts[nonEmptyBinIndex] - accumulatedCounts[nonEmptyBinIndex - 1];
    } else {
      return accumulatedCounts[nonEmptyBinIndex];
    }
  }

  @Override
  public BinIterator getFirstNonEmptyBin() {
    return new BinIteratorImpl(0);
  }

  @Override
  public BinIterator getLastNonEmptyBin() {
    return new BinIteratorImpl(nonEmptyBinIndices.length - 1);
  }

  @Override
  public long getTotalCount() {
    return (accumulatedCounts.length > 0) ? accumulatedCounts[accumulatedCounts.length - 1] : 0;
  }

  @Override
  public double getMin() {
    return min;
  }

  @Override
  public double getMax() {
    return max;
  }

  @Override
  public long getCount(int binIndex) {
    int nonEmptyBinIndex = Arrays.binarySearch(nonEmptyBinIndices, binIndex);
    if (nonEmptyBinIndex >= 0) {
      return getCountOfNonEmptyBin(nonEmptyBinIndex);
    } else {
      return 0;
    }
  }

  private class BinIteratorImpl extends AbstractBin implements BinIterator {

    private int nonEmptyBinIndex;

    private BinIteratorImpl(final int nonEmptyBinIndex) {
      this.nonEmptyBinIndex = nonEmptyBinIndex;
    }

    @Override
    public long getBinCount() {
      return getCountOfNonEmptyBin(nonEmptyBinIndex);
    }

    @Override
    public long getLessCount() {
      return (nonEmptyBinIndex > 0) ? accumulatedCounts[nonEmptyBinIndex - 1] : 0;
    }

    @Override
    public long getGreaterCount() {
      return getTotalCount() - accumulatedCounts[nonEmptyBinIndex];
    }

    @Override
    public void next() {
      checkState(nonEmptyBinIndex + 1 < accumulatedCounts.length);
      nonEmptyBinIndex += 1;
    }

    @Override
    public void previous() {
      checkState(nonEmptyBinIndex > 0);
      nonEmptyBinIndex -= 1;
    }

    @Override
    public int getBinIndex() {
      return nonEmptyBinIndices[nonEmptyBinIndex];
    }

    @Override
    public boolean isUnderflowBin() {
      return getBinIndex() == getLayout().getUnderflowBinIndex();
    }

    @Override
    public boolean isOverflowBin() {
      return getBinIndex() == getLayout().getOverflowBinIndex();
    }

    @Override
    public double getLowerBound() {
      return Math.max(getMin(), getLayout().getBinLowerBound(getBinIndex()));
    }

    @Override
    public double getUpperBound() {
      return Math.min(getMax(), getLayout().getBinUpperBound(getBinIndex()));
    }
  }

  @Override
  public Histogram addValue(double value, long count) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Histogram addValue(double value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Histogram addHistogram(Histogram histogram) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Histogram addAscendingSequence(LongToDoubleFunction ascendingSequence, long length) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void write(DataOutput dataOutput) throws IOException {
    Histogram.createDynamic(getLayout()).addHistogram(this).write(dataOutput);
  }

  @Override
  public long getEstimatedFootprintInBytes() {
    return 2 * Double.BYTES // min, max
        + (ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES
            + ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES
            + Integer.BYTES
            + ((long) accumulatedCounts.length) * Long.BYTES) // accumulatedCounts
        + (ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES
            + ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES
            + Integer.BYTES
            + ((long) nonEmptyBinIndices.length) * Integer.BYTES) // nonEmptyBinIndices
        + super.getEstimatedFootprintInBytes();
  }

  @Override
  public boolean isMutable() {
    return false;
  }
}
