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

import com.dynatrace.dynahist.layout.Layout;
import java.io.DataInput;
import java.io.IOException;
import java.util.Locale;

final class StaticHistogram extends AbstractMutableHistogram {

  private final long[] counts;

  StaticHistogram(final Layout layout) {
    super(layout);
    final int countsArraySize = layout.getOverflowBinIndex() - layout.getUnderflowBinIndex() - 1;
    checkArgument(countsArraySize >= 0);
    this.counts = new long[countsArraySize];
  }

  @Override
  public Histogram addValue(final double value, final long count) {
    if (count > 0) {
      if (totalCount + count >= 0) {
        totalCount += count;
        updateMinMax(value);
        final int arrayIdx =
            getLayout().mapToBinIndex(value) - getLayout().getUnderflowBinIndex() - 1;
        if (arrayIdx >= 0 && arrayIdx < counts.length) {
          counts[arrayIdx] += count;
        } else {
          if (!Double.isNaN(value)) {
            if (arrayIdx < 0) {
              incrementUnderflowCount(count);
            } else {
              incrementOverflowCount(count);
            }
          } else {
            totalCount -= count;
            throw new IllegalArgumentException(NAN_VALUE_MSG);
          }
        }
      } else {
        throw new ArithmeticException(OVERFLOW_MSG);
      }
    } else if (count < 0) {
      throw new IllegalArgumentException(String.format((Locale) null, NEGATIVE_COUNT_MSG, count));
    }
    return this;
  }

  @Override
  public long getEstimatedFootprintInBytes() {
    return (((long) counts.length * Long.BYTES)
            + ESTIMATED_OBJECT_HEADER_FOOTPRINT_IN_BYTES
            + ESTIMATED_REFERENCE_FOOTPRINT_IN_BYTES
            + Integer.BYTES) // counts
        + super.getEstimatedFootprintInBytes();
  }

  @Override
  protected int minAllocatedBinIndexInclusive() {
    return getLayout().getUnderflowBinIndex() + 1;
  }

  @Override
  protected int maxAllocatedBinIndexExclusive() {
    return getLayout().getOverflowBinIndex();
  }

  @Override
  protected long getAllocatedBinCount(final int binIndex) {
    return counts[binIndex - minAllocatedBinIndexInclusive()];
  }

  @Override
  protected byte getMode() {
    long c = 0;
    for (int i = 0; i < counts.length; ++i) {
      c |= counts[i];
    }
    return determineRequiredMode(c);
  }

  @Override
  protected void ensureCountArray(int minNonEmptyBinIndex, int maxNonEmptyBinIndex, byte mode) {
    // not necessary because of static allocation
  }

  @Override
  protected void increaseCount(int absoluteIndex, long count) {
    if (absoluteIndex <= getLayout().getUnderflowBinIndex()) {
      incrementUnderflowCount(count);
    } else if (absoluteIndex >= getLayout().getOverflowBinIndex()) {
      incrementOverflowCount(count);
    } else {
      counts[absoluteIndex - getLayout().getUnderflowBinIndex() - 1] += count;
    }
  }

  public static StaticHistogram read(final Layout layout, final DataInput dataInput)
      throws IOException {
    requireNonNull(layout);
    requireNonNull(dataInput);

    StaticHistogram histogram = new StaticHistogram(layout);
    deserialize(histogram, dataInput);
    return histogram;
  }
}
