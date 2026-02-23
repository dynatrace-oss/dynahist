/*
 * Copyright 2020-2026 Dynatrace LLC
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
 * A histogram bin layout with bin boundaries defined according to {@code (2^(2^(-scale)))^i} where
 * i is an integer.
 *
 * <p>Zero values have its own bucket with index 0. For positive values the lower bound is inclusive
 * and the upper bound is exclusive. Positive bins are sequentially numbered starting with 1. bins
 * are only defined for ranges that contains at least 1 value that is representable as a double. In
 * addition, a bucket is defined for positive infinity which is also the last bucket. The bins on
 * the negative side are obtained by mirroring the bins on the positive side at zero. Thus, for
 * negative values the lower bound is exclusive and the upper bound is inclusive. Negative bins are
 * sequentially numbered starting with -1 and decreasing.
 *
 * <p>This layout is similar to the layout of the OpenTelemetry exponential histogram (see <a
 * href="https://opentelemetry.io/docs/specs/otel/metrics/data-model/#exponentialhistogram">OpenTelemetry
 * specification</a>). However, the lower instead of the upper bounds are inclusive. This means that
 * values that are exact powers of 2 are mapped differently. The advantages of this layout are the
 * slightly simpler mapping of values to bins (due to less branching in the mapping function) and
 * that fewer bins are needed for scale 0 to cover the whole range of double precision values.
 *
 * <p>The implementation of this layout is exact, meaning that values are mapped to the bins without
 * numerical errors. Furthermore, the mapping only requires simple bit manipulations and array
 * lookups, which makes it very fast. Also, the reverse mapping from bin indices to bucket
 *
 * <p>This class is immutable.
 */
public final class ExponentialHistogramSmallInclusiveLayout
    extends AbstractExponentialHistogramLayout {

  private static final byte SERIAL_VERSION_V0 = 0;

  private static final AtomicReferenceArray<ExponentialHistogramSmallInclusiveLayout> INSTANCES =
      new AtomicReferenceArray<>(MAX_SCALE + 1);

  private final transient int underflowBinIndex;
  private final transient int overflowBinIndex;
  private final transient long[] boundaries;
  private final transient int[] indices;
  private final transient long firstNormalValueBits;
  private final transient int indexOffset;

  /**
   * Creates a histogram bin layout with exponential buckets with given scale.
   *
   * @param scale the scale
   * @return a new {@link ExponentialHistogramSmallInclusiveLayout} instance
   */
  public static ExponentialHistogramSmallInclusiveLayout create(int scale) {
    checkArgument(scale >= 0);
    checkArgument(scale <= MAX_SCALE);

    return INSTANCES.updateAndGet(
        scale,
        x -> {
          if (x != null) {
            return x;
          } else {
            return new ExponentialHistogramSmallInclusiveLayout(scale);
          }
        });
  }

  ExponentialHistogramSmallInclusiveLayout(int scale) {
    super(scale);
    this.boundaries = calculateBoundaries(scale);
    this.indices = calculateIndices(boundaries, scale);

    int valueBits = 0;
    int index = Integer.MIN_VALUE;
    while (true) {
      int nextValueBits = valueBits + 1;
      int nextIndex = mapToBinIndexHelper(nextValueBits, indices, boundaries, scale, 0L, 0);
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
      int scale,
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
    int i = indices[(int) (mantissa >>> (52 - scale))];
    int k = i + ((mantissa >= boundaries[i]) ? 1 : 0) + ((mantissa >= boundaries[i + 1]) ? 1 : 0);
    return (exponent << scale) + k + indexOffset;
  }

  @Override
  public int mapToBinIndex(double value) {
    long valueBits = Double.doubleToRawLongBits(value);
    int index =
        mapToBinIndexHelper(
            valueBits, indices, boundaries, scale, firstNormalValueBits, indexOffset);
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
      return Double.longBitsToDouble((long) absBinIndex);
    } else {
      int k = (absBinIndex - indexOffset) & ~(0xFFFFFFFF << scale);
      int exponent = (absBinIndex - indexOffset) >> scale;
      long mantissa = (k > 0) ? boundaries[k - 1] : 0;
      if (exponent <= 0) {
        int shift = 1 - exponent;
        mantissa += ~(0xffffffffffffffffL << shift);
        mantissa |= 0x0010000000000000L;
        mantissa >>>= shift;
        exponent = 0;
      }
      return Double.longBitsToDouble(mantissa | (((long) exponent) << 52));
    }
  }

  @Override
  protected double getBinLowerBoundApproximation(int binIndex) {
    // this function implements returns the exact lower bound of the bin with given index
    if (binIndex == 0) {
      return -0.;
    } else if (binIndex > 0) {
      return getBinLowerBoundApproximationHelper(binIndex);
    }
    {
      return Math.nextUp(-getBinLowerBoundApproximationHelper(-binIndex + 1));
    }
  }

  /**
   * Writes this layout to the given {@link DataOutput}.
   *
   * @param dataOutput the data output to write to
   * @throws IOException if an I/O error occurs
   */
  public void write(DataOutput dataOutput) throws IOException {
    dataOutput.writeByte(SERIAL_VERSION_V0);
    dataOutput.writeByte(scale);
  }

  /**
   * Reads an {@code OpenTelemetryExponentialBucketsLayout} from the given {@link DataInput}.
   *
   * @param dataInput the data input to read from
   * @return the deserialized layout
   * @throws IOException if an I/O error occurs
   */
  public static ExponentialHistogramSmallInclusiveLayout read(DataInput dataInput)
      throws IOException {
    checkSerialVersion(SERIAL_VERSION_V0, dataInput.readByte());
    int tmpScale = dataInput.readUnsignedByte();
    return ExponentialHistogramSmallInclusiveLayout.create(tmpScale);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExponentialHistogramSmallInclusiveLayout)) return false;
    ExponentialHistogramSmallInclusiveLayout that = (ExponentialHistogramSmallInclusiveLayout) o;
    return scale == that.scale;
  }

  @Override
  public int hashCode() {
    return 31 * scale;
  }
}
