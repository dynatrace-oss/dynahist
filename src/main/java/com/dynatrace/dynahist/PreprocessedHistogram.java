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

import com.dynatrace.dynahist.bin.AbstractBin;
import com.dynatrace.dynahist.bin.Bin;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.value.ValueEstimator;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.NoSuchElementException;
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

    checkArgument(rank >= 0L);
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

  private void checkIfElementExists() {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }
  }

  @Override
  public BinIterator getFirstNonEmptyBin() {
    checkIfElementExists();
    return new BinIteratorImpl(0);
  }

  @Override
  public BinIterator getLastNonEmptyBin() {
    checkIfElementExists();
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

  private abstract class AbstractBinExtended extends AbstractBin {

    protected abstract int getNonEmptyIndex();

    @Override
    public long getBinCount() {
      return getCountOfNonEmptyBin(getNonEmptyIndex());
    }

    @Override
    public long getLessCount() {
      return (getNonEmptyIndex() > 0) ? accumulatedCounts[getNonEmptyIndex() - 1] : 0;
    }

    @Override
    public long getGreaterCount() {
      return getTotalCount() - accumulatedCounts[getNonEmptyIndex()];
    }

    @Override
    public int getBinIndex() {
      return nonEmptyBinIndices[getNonEmptyIndex()];
    }

    @Override
    protected Histogram getHistogram() {
      return PreprocessedHistogram.this;
    }
  }

  private final class BinCopyImpl extends AbstractBinExtended {
    private final int nonEmptyBinIndex;

    private BinCopyImpl(int nonEmptyBinIndex) {
      this.nonEmptyBinIndex = nonEmptyBinIndex;
    }

    @Override
    protected int getNonEmptyIndex() {
      return nonEmptyBinIndex;
    }
  }

  private class BinIteratorImpl extends AbstractBinExtended implements BinIterator {

    private int nonEmptyBinIndex;

    private BinIteratorImpl(final int nonEmptyBinIndex) {
      this.nonEmptyBinIndex = nonEmptyBinIndex;
    }

    @Override
    protected int getNonEmptyIndex() {
      return nonEmptyBinIndex;
    }

    @Override
    public void next() {
      if (nonEmptyBinIndex + 1 >= accumulatedCounts.length) {
        throw new NoSuchElementException();
      }
      nonEmptyBinIndex += 1;
    }

    @Override
    public void previous() {
      if (nonEmptyBinIndex <= 0) {
        throw new NoSuchElementException();
      }
      nonEmptyBinIndex -= 1;
    }

    @Override
    public Bin getBinCopy() {
      return new BinCopyImpl(nonEmptyBinIndex);
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
  public Histogram addHistogram(Histogram histogram, ValueEstimator valueEstimator) {
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
    return 2L * Double.BYTES // min, max
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
