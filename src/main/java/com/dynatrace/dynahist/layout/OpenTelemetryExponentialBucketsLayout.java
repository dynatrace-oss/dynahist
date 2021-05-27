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
package com.dynatrace.dynahist.layout;

import static com.dynatrace.dynahist.serialization.SerializationUtil.checkSerialVersion;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A tentative histogram bin layout that implements the proposal as discussed in
 * https://github.com/open-telemetry/oteps/pull/149.
 *
 * <p>BETA: This class is still subject to incompatible changes, or even removal, in a future
 * release.
 *
 * <p>This class is immutable.
 */
public final class OpenTelemetryExponentialBucketsLayout extends AbstractLayout {

  private static final byte SERIAL_VERSION_V0 = 0;

  static final int MAX_PRECISION = 10;

  private static final AtomicReferenceArray<OpenTelemetryExponentialBucketsLayout> INSTANCES =
      new AtomicReferenceArray<>(MAX_PRECISION + 1);

  private final int precision;

  private final transient int underflowBinIndex;
  private final transient int overflowBinIndex;
  private final transient long[] boundaries;
  private final transient int[] indices;
  private final transient long firstNormalValueBits;
  private final transient int indexOffset;

  /**
   * Creates a histogram bin layout with exponential buckets with given precision.
   *
   * @param precision the precision
   * @return a new {@link OpenTelemetryExponentialBucketsLayout} instance
   */
  public static OpenTelemetryExponentialBucketsLayout create(int precision) {
    checkArgument(precision >= 0);
    checkArgument(precision <= MAX_PRECISION);

    return INSTANCES.updateAndGet(
        precision,
        x -> {
          if (x != null) {
            return x;
          } else {
            return new OpenTelemetryExponentialBucketsLayout(precision);
          }
        });
  }

  static long[] calculateBoundaries(int precision) {
    int len = 1 << precision;
    long[] boundaries = new long[len + 1];
    for (int i = 0; i < len - 1; ++i) {
      boundaries[i] =
          0x000fffffffffffffL
              & Double.doubleToRawLongBits(StrictMath.pow(2., (i + 1) / (double) len));
    }
    boundaries[len - 1] = 0x0010000000000000L;
    boundaries[len] = 0x0010000000000000L;
    return boundaries;
  }

  private static int[] calculateIndices(long[] boundaries, int precision) {
    int len = 1 << precision;
    int[] indices = new int[len];
    int c = 0;
    for (int i = 0; i < len; ++i) {
      long mantissaLowerBound = ((long) i) << (52 - precision);
      while (boundaries[c] <= mantissaLowerBound) {
        c += 1;
      }
      indices[i] = c;
    }
    return indices;
  }

  OpenTelemetryExponentialBucketsLayout(int precision) {
    this.precision = precision;
    this.boundaries = calculateBoundaries(precision);
    this.indices = calculateIndices(boundaries, precision);

    int valueBits = 0;
    int index = Integer.MIN_VALUE;
    while (true) {
      int nextValueBits = valueBits + 1;
      int nextIndex = mapToBinIndexHelper(nextValueBits, indices, boundaries, precision, 0L, 0);
      if (index == nextIndex) {
        break;
      }
      valueBits = nextValueBits;
      index = nextIndex;
    }
    this.firstNormalValueBits = valueBits;
    this.indexOffset = valueBits - index;
    this.overflowBinIndex = mapToBinIndex(Double.MAX_VALUE) + 1;
    this.underflowBinIndex = -overflowBinIndex;
  }

  private static int mapToBinIndexHelper(
      long valueBits,
      int[] indices,
      long[] boundaries,
      int precision,
      long firstNormalValueBits,
      int indexOffset) {
    long mantissa = 0xfffffffffffffL & valueBits;
    int exponent = (int) ((0x7ff0000000000000L & valueBits) >> 52);
    if (exponent == 0) {
      if (mantissa < firstNormalValueBits) return (int) mantissa;
      int nlz = Long.numberOfLeadingZeros(mantissa) - 12;
      exponent -= nlz;
      mantissa <<= (nlz + 1);
      mantissa &= 0x000fffffffffffffL;
    }
    int i = indices[(int) (mantissa >>> (52 - precision))];
    int k = i + ((mantissa >= boundaries[i]) ? 1 : 0) + ((mantissa >= boundaries[i + 1]) ? 1 : 0);
    return (exponent << precision) + k + indexOffset;
  }

  @Override
  public int mapToBinIndex(double value) {
    long valueBits = Double.doubleToRawLongBits(value);
    int index =
        mapToBinIndexHelper(
            valueBits, indices, boundaries, precision, firstNormalValueBits, indexOffset);
    return (valueBits >= 0) ? index : -index;
  }

  @Override
  public int getUnderflowBinIndex() {
    return underflowBinIndex;
  }

  @Override
  public int getOverflowBinIndex() {
    return overflowBinIndex;
  }

  private double getBinLowerBoundApproximationHelper(int absBinIndex) {
    if (absBinIndex < firstNormalValueBits) {
      return Double.longBitsToDouble(absBinIndex);
    } else {
      int k = (absBinIndex - indexOffset) & (~(0xFFFFFFFF << precision));
      int exponent = (absBinIndex - indexOffset) >> precision;
      long mantissa = (k > 0) ? boundaries[k - 1] : 0;
      if (exponent <= 0) {
        int shift = 1 - exponent;
        mantissa += (~(0xffffffffffffffffL << shift));
        mantissa |= 0x0010000000000000L;
        mantissa >>>= shift;
        exponent = 0;
      }
      double lowerBound = Double.longBitsToDouble(mantissa | (((long) exponent) << 52));
      return lowerBound;
    }
  }

  @Override
  protected double getBinLowerBoundApproximation(int binIndex) {
    if (binIndex == 0) {
      return -0.;
    } else if (binIndex > 0) {
      return getBinLowerBoundApproximationHelper(binIndex);
    }
    {
      return Math.nextUp(-getBinLowerBoundApproximationHelper(-binIndex + 1));
    }
  }

  @Override
  public String toString() {
    return "OpenTelemetryExponentialBucketsLayout [" + "precision=" + precision + ']';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OpenTelemetryExponentialBucketsLayout that = (OpenTelemetryExponentialBucketsLayout) o;
    return precision == that.precision;
  }

  @Override
  public int hashCode() {
    return 31 * precision;
  }

  public void write(DataOutput dataOutput) throws IOException {
    dataOutput.writeByte(SERIAL_VERSION_V0);
    dataOutput.writeByte(precision);
  }

  public static OpenTelemetryExponentialBucketsLayout read(DataInput dataInput) throws IOException {
    checkSerialVersion(SERIAL_VERSION_V0, dataInput.readUnsignedByte());
    int tmpPrecision = dataInput.readUnsignedByte();
    return OpenTelemetryExponentialBucketsLayout.create(tmpPrecision);
  }
}
