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
import static com.dynatrace.dynahist.serialization.SerializationUtil.writeSignedVarInt;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;

import com.dynatrace.dynahist.serialization.SerializationUtil;
import com.dynatrace.dynahist.util.Algorithms;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A histogram bin layout where all bins covering the given range have a width that is either
 * smaller than a given absolute bin width limit or a given relative bin width limit. This layout
 * uses a piecewise-linear function to map values to bin indices.
 *
 * <p>This class is immutable.
 */
public final class LogLinearLayout extends AbstractLayout {

  private static final byte SERIAL_VERSION_V0 = 0;

  private final double absoluteBinWidthLimit;
  private final double relativeBinWidthLimit;

  private final int underflowBinIndex;
  private final int overflowBinIndex;

  private final transient double factorNormal;
  private final transient double factorSubnormal;

  private final transient double offset;
  private final transient long unsignedValueBitsNormalLimit;

  /**
   * Creates a histogram bin layout covering a given range and with bins that have absolute and
   * relative width limitations.
   *
   * <p>The maximum bin width is either bounded by an absolute or a relative bin width limit.
   *
   * @param absoluteBinWidthLimit the absolute bin width limit
   * @param relativeBinWidthLimit the relative bin width limit
   * @param valueRangeLowerBound the range lower bound
   * @param valueRangeUpperBound the range upper bound
   * @return a new {@link LogLinearLayout} instance
   */
  public static LogLinearLayout create(
      final double absoluteBinWidthLimit,
      final double relativeBinWidthLimit,
      final double valueRangeLowerBound,
      final double valueRangeUpperBound) {

    checkArgument(Double.isFinite(valueRangeUpperBound));
    checkArgument(Double.isFinite(valueRangeLowerBound));
    checkArgument(valueRangeUpperBound >= valueRangeLowerBound);

    checkArgument(absoluteBinWidthLimit >= Double.MIN_NORMAL);
    checkArgument(absoluteBinWidthLimit <= Double.MAX_VALUE);
    checkArgument(relativeBinWidthLimit >= 0);
    checkArgument(relativeBinWidthLimit <= Double.MAX_VALUE);

    final int firstNormalIdx = calculateFirstNormalIndex(relativeBinWidthLimit);
    // will always be >= 1 because 0 <= relativeBinWidthLimit <= Double.MAX_VALUE

    final double factorNormal = calculateFactorNormal(relativeBinWidthLimit);
    final double factorSubnormal = calculateFactorSubNormal(absoluteBinWidthLimit);

    final long unsignedValueBitsNormalLimit =
        calculateUnsignedValueBitsNormalLimit(factorSubnormal, firstNormalIdx);

    final double offset =
        calculateOffset(unsignedValueBitsNormalLimit, factorNormal, firstNormalIdx);

    final int valueRangeLowerBoundBinIndex =
        mapToBinIndex(
            valueRangeLowerBound,
            factorNormal,
            factorSubnormal,
            unsignedValueBitsNormalLimit,
            offset);
    final int valueRangeUpperBoundBinIndex =
        mapToBinIndex(
            valueRangeUpperBound,
            factorNormal,
            factorSubnormal,
            unsignedValueBitsNormalLimit,
            offset);

    checkArgument(valueRangeLowerBoundBinIndex > Integer.MIN_VALUE);
    checkArgument(valueRangeUpperBoundBinIndex < Integer.MAX_VALUE);

    final int underflowBinIndex = valueRangeLowerBoundBinIndex - 1;
    final int overflowBinIndex = valueRangeUpperBoundBinIndex + 1;

    checkArgument(
        (long) overflowBinIndex - (long) underflowBinIndex - 1L <= (long) Integer.MAX_VALUE);

    return new LogLinearLayout(
        absoluteBinWidthLimit,
        relativeBinWidthLimit,
        underflowBinIndex,
        overflowBinIndex,
        factorNormal,
        factorSubnormal,
        offset,
        unsignedValueBitsNormalLimit);
  }

  private LogLinearLayout(
      double absoluteBinWidthLimit,
      double relativeBinWidthLimit,
      int underflowBinIndex,
      int overflowBinIndex,
      double factorNormal,
      double factorSubnormal,
      double offset,
      long unsignedValueBitsNormalLimit) {

    this.absoluteBinWidthLimit = absoluteBinWidthLimit;
    this.relativeBinWidthLimit = relativeBinWidthLimit;
    this.underflowBinIndex = underflowBinIndex;
    this.overflowBinIndex = overflowBinIndex;
    this.factorNormal = factorNormal;
    this.factorSubnormal = factorSubnormal;
    this.offset = offset;
    this.unsignedValueBitsNormalLimit = unsignedValueBitsNormalLimit;
  }

  static long calculateUnsignedValueBitsNormalLimit(double factorSubnormal, int firstNormalIdx) {

    return Algorithms.findFirst(
        l -> calculateSubNormalIdx(l, factorSubnormal) >= firstNormalIdx,
        0,
        Double.doubleToRawLongBits(Double.POSITIVE_INFINITY),
        calculateUnsignedValueBitsNormalLimitApproximate(factorSubnormal, firstNormalIdx));
  }

  static long calculateUnsignedValueBitsNormalLimitApproximate(
      double factorSubnormal, int firstNormalIdx) {
    return Algorithms.mapDoubleToLong(firstNormalIdx / factorSubnormal);
  }

  static strictfp int calculateFirstNormalIndex(double relativeBinWidthLimit) {
    return (int) StrictMath.ceil(1. / relativeBinWidthLimit);
  }

  static strictfp double calculateFactorNormal(double relativeBinWidthLimit) {
    return 1. / StrictMath.log1p(relativeBinWidthLimit);
  }

  static strictfp double calculateFactorSubNormal(double absoluteBinWidthLimit) {
    return 1d / absoluteBinWidthLimit;
  }

  static double calculateOffset(
      long unsignedValueBitsNormalLimit, double factorNormal, int firstNormalIdx) {

    return Algorithms.mapLongToDouble(
        Algorithms.findFirst(
            l -> {
              double offsetCandidate = Algorithms.mapLongToDouble(l);
              int binIndex =
                  calculateNormalIdx(unsignedValueBitsNormalLimit, factorNormal, offsetCandidate);
              return binIndex >= firstNormalIdx;
            },
            Algorithms.NEGATIVE_INFINITY_MAPPED_TO_LONG,
            Algorithms.POSITIVE_INFINITY_MAPPED_TO_LONG,
            Algorithms.mapDoubleToLong(
                calculateOffsetApproximate(
                    unsignedValueBitsNormalLimit, factorNormal, firstNormalIdx))));
  }

  static double calculateOffsetApproximate(
      long unsignedValueBitsNormalLimit, double factorNormal, int firstNormalIdx) {
    return firstNormalIdx - factorNormal * mapToBinIndexHelper(unsignedValueBitsNormalLimit);
  }

  /**
   * For unsigned values the return value is in the range [0, 2049].
   *
   * <p>It can be shown that this function is monotonically increasing for all non-negative
   * arguments.
   */
  static double mapToBinIndexHelper(final long unsignedValueBits) {
    final long exponent = unsignedValueBits >>> 52;
    final double mantissaPlus1 =
        Double.longBitsToDouble((unsignedValueBits & 0x000fffffffffffffL) | 0x3ff0000000000000L);
    return mantissaPlus1 + exponent;
  }

  private static int calculateNormalIdx(
      final long unsignedValueBits, final double factorNormal, final double offset) {
    return (int) (factorNormal * mapToBinIndexHelper(unsignedValueBits) + offset);
  }

  private static int calculateSubNormalIdx(
      final long unsignedValueBits, final double factorSubnormal) {
    return (int) (factorSubnormal * Double.longBitsToDouble(unsignedValueBits));
  }

  // Unfortunately this mapping is not platform-independent. It would be independent if the strictfp
  // keyword was used for this method and all called methods. Due to a performance penalty (see
  // https://bugs.openjdk.java.net/browse/JDK-8136414) of strictfp, which is hopefully fixed in Java
  // 15, we have omitted strictfp here in the meantime.
  private static int mapToBinIndex(
      final double value,
      final double factorNormal,
      final double factorSubnormal,
      final long unsignedValueBitsNormalLimit,
      final double offset) {
    final long valueBits = Double.doubleToRawLongBits(value);
    final long unsignedValueBits = valueBits & 0x7fffffffffffffffL;
    final int idx;
    if (unsignedValueBits >= unsignedValueBitsNormalLimit) {
      idx = calculateNormalIdx(unsignedValueBits, factorNormal, offset);
    } else {
      idx = calculateSubNormalIdx(unsignedValueBits, factorSubnormal);
    }
    return (valueBits >= 0) ? idx : ~idx;
  }

  @Override
  public final int mapToBinIndex(final double value) {
    return mapToBinIndex(
        value, factorNormal, factorSubnormal, unsignedValueBitsNormalLimit, offset);
  }

  @Override
  public int getUnderflowBinIndex() {
    return underflowBinIndex;
  }

  @Override
  public int getOverflowBinIndex() {
    return overflowBinIndex;
  }

  public void write(DataOutput dataOutput) throws IOException {
    dataOutput.writeByte(SERIAL_VERSION_V0);
    dataOutput.writeDouble(absoluteBinWidthLimit);
    dataOutput.writeDouble(relativeBinWidthLimit);
    writeSignedVarInt(underflowBinIndex, dataOutput);
    writeSignedVarInt(overflowBinIndex, dataOutput);
  }

  public static LogLinearLayout read(DataInput dataInput) throws IOException {
    checkSerialVersion(SERIAL_VERSION_V0, dataInput.readUnsignedByte());
    double absoluteBinWidthLimitTmp = dataInput.readDouble();
    double relativeBinWidthLimitTmp = dataInput.readDouble();
    int underflowBinIndexTmp = SerializationUtil.readSignedVarInt(dataInput);
    int overflowBinIndexTmp = SerializationUtil.readSignedVarInt(dataInput);

    final int firstNormalIdxTmp = calculateFirstNormalIndex(relativeBinWidthLimitTmp);
    final double factorNormalTmp = calculateFactorNormal(relativeBinWidthLimitTmp);
    final double factorSubnormalTmp = calculateFactorSubNormal(absoluteBinWidthLimitTmp);

    final long unsignedValueBitsNormalLimitTmp =
        calculateUnsignedValueBitsNormalLimit(factorSubnormalTmp, firstNormalIdxTmp);

    final double offsetTmp =
        calculateOffset(unsignedValueBitsNormalLimitTmp, factorNormalTmp, firstNormalIdxTmp);

    return new LogLinearLayout(
        absoluteBinWidthLimitTmp,
        relativeBinWidthLimitTmp,
        underflowBinIndexTmp,
        overflowBinIndexTmp,
        factorNormalTmp,
        factorSubnormalTmp,
        offsetTmp,
        unsignedValueBitsNormalLimitTmp);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    long temp;
    temp = Double.doubleToLongBits(absoluteBinWidthLimit);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    result = prime * result + overflowBinIndex;
    temp = Double.doubleToLongBits(relativeBinWidthLimit);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    result = prime * result + underflowBinIndex;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    LogLinearLayout other = (LogLinearLayout) obj;
    if (Double.doubleToLongBits(absoluteBinWidthLimit)
        != Double.doubleToLongBits(other.absoluteBinWidthLimit)) {
      return false;
    }
    if (overflowBinIndex != other.overflowBinIndex) {
      return false;
    }
    if (Double.doubleToLongBits(relativeBinWidthLimit)
        != Double.doubleToLongBits(other.relativeBinWidthLimit)) {
      return false;
    }
    if (underflowBinIndex != other.underflowBinIndex) {
      return false;
    }
    return true;
  }

  @Override
  protected double getBinLowerBoundApproximation(final int binIndex) {
    if (binIndex >= 0) {
      return getBinLowerBoundApproximationHelper(binIndex);
    } else {
      return -getBinLowerBoundApproximationHelper(-binIndex);
    }
  }

  private double getBinLowerBoundApproximationHelper(final int idx) {
    double x = idx * absoluteBinWidthLimit;
    if (x < Double.longBitsToDouble(unsignedValueBitsNormalLimit)) {
      return x;
    } else {
      final double s = (idx - offset) / factorNormal;
      final int exponent = ((int) Math.floor(s)) - 1;
      final double mantissaPlus1 = s - exponent;
      return Math.scalb(mantissaPlus1, exponent - 1023);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + " [absoluteBinWidthLimit="
        + absoluteBinWidthLimit
        + ", relativeBinWidthLimit="
        + relativeBinWidthLimit
        + ", underflowBinIndex="
        + underflowBinIndex
        + ", overflowBinIndex="
        + overflowBinIndex
        + "]";
  }
}
