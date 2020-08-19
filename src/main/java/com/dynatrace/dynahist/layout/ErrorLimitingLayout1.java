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
package com.dynatrace.dynahist.layout;

import static com.dynatrace.dynahist.serialization.SerializationUtil.checkSerialVersion;
import static com.dynatrace.dynahist.serialization.SerializationUtil.writeSignedVarInt;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.serialization.SerializationUtil;
import com.dynatrace.dynahist.util.Algorithms;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A layout for {@link Histogram} that is able to approximate any values within a given range with a
 * maximum error that either satisfies the given absolute or the given relative error bounds.
 *
 * <p>This class is immutable.
 */
public final class ErrorLimitingLayout1 extends AbstractLayout {

  protected static final byte SERIAL_VERSION_V0 = 0;

  private final double absoluteError;
  private final double relativeError;

  private final int underflowBinIndex;
  private final int overflowBinIndex;

  private final transient double factorNormal;
  private final transient double factorSubnormal;

  private final transient double offset;
  private final transient long unsignedValueBitsNormalLimit;

  /**
   * Creates a {@link Layout} that guarantees a given error over a given interval.
   *
   * <p>The allowed error is given by the maximum of relative and absolute error.
   *
   * @param absoluteError the allowed absolute error
   * @param relativeError the allowed relative error
   * @param minValue the minimum value that can be recorded with given error guarantees
   * @param maxValue the maximum value that can be recorded with given error guarantees
   * @return a new {@link ErrorLimitingLayout1} instance
   */
  public static ErrorLimitingLayout1 create(
      final double absoluteError,
      final double relativeError,
      final double minValue,
      final double maxValue) {

    checkArgument(Double.isFinite(maxValue));
    checkArgument(Double.isFinite(minValue));
    checkArgument(maxValue >= minValue);

    checkArgument(absoluteError >= Double.MIN_NORMAL);
    checkArgument(absoluteError <= Double.MAX_VALUE);
    checkArgument(relativeError >= 0);
    checkArgument(relativeError <= Double.MAX_VALUE);

    final int firstNormalIdx =
        calculateFirstNormalIndex(
            relativeError); // will always be >= 1 because 0 <= relativeError <= Double.MAX_VALUE

    final double factorNormal = calculateFactorNormal(relativeError);
    final double factorSubnormal = calculateFactorSubNormal(absoluteError);

    final long unsignedValueBitsNormalLimit =
        calculateUnsignedValueBitsNormalLimit(factorSubnormal, firstNormalIdx);

    final double offset =
        calculateOffset(unsignedValueBitsNormalLimit, factorNormal, firstNormalIdx);

    final int minValueBinIndex =
        mapToBinIndex(
            minValue, factorNormal, factorSubnormal, unsignedValueBitsNormalLimit, offset);
    final int maxValueBinIndex =
        mapToBinIndex(
            maxValue, factorNormal, factorSubnormal, unsignedValueBitsNormalLimit, offset);

    checkArgument(minValueBinIndex > Integer.MIN_VALUE);
    checkArgument(maxValueBinIndex < Integer.MAX_VALUE);

    final int underflowBinIndex = minValueBinIndex - 1;
    final int overflowBinIndex = maxValueBinIndex + 1;

    checkArgument(
        (long) overflowBinIndex - (long) underflowBinIndex - 1l <= (long) Integer.MAX_VALUE);

    return new ErrorLimitingLayout1(
        absoluteError,
        relativeError,
        underflowBinIndex,
        overflowBinIndex,
        factorNormal,
        factorSubnormal,
        offset,
        unsignedValueBitsNormalLimit);
  }

  private ErrorLimitingLayout1(
      double absoluteError,
      double relativeError,
      int underflowBinIndex,
      int overflowBinIndex,
      double factorNormal,
      double factorSubnormal,
      double offset,
      long unsignedValueBitsNormalLimit) {

    this.absoluteError = absoluteError;
    this.relativeError = relativeError;
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

  static strictfp int calculateFirstNormalIndex(double relativeError) {
    return (int) StrictMath.ceil(1. / relativeError);
  }

  static strictfp double calculateFactorNormal(double relativeError) {
    return 1. / StrictMath.log1p(relativeError);
  }

  static strictfp double calculateFactorSubNormal(double absoluteError) {
    return 1d / absoluteError;
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
   *
   * @param unsignedValueBits
   * @return
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
  // keyword was used for this method and all called methods.
  // Due to a performance penalty (see
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
    dataOutput.writeDouble(absoluteError);
    dataOutput.writeDouble(relativeError);
    writeSignedVarInt(underflowBinIndex, dataOutput);
    writeSignedVarInt(overflowBinIndex, dataOutput);
  }

  public static ErrorLimitingLayout1 read(DataInput dataInput) throws IOException {
    checkSerialVersion(SERIAL_VERSION_V0, dataInput.readUnsignedByte());
    double absoluteErrorTmp = dataInput.readDouble();
    double relativeErrorTmp = dataInput.readDouble();
    int underflowBinIndexTmp = SerializationUtil.readSignedVarInt(dataInput);
    int overflowBinIndexTmp = SerializationUtil.readSignedVarInt(dataInput);

    final int firstNormalIdxTmp = calculateFirstNormalIndex(relativeErrorTmp);
    final double factorNormalTmp = calculateFactorNormal(relativeErrorTmp);
    final double factorSubnormalTmp = calculateFactorSubNormal(absoluteErrorTmp);

    final long unsignedValueBitsNormalLimitTmp =
        calculateUnsignedValueBitsNormalLimit(factorSubnormalTmp, firstNormalIdxTmp);

    final double offsetTmp =
        calculateOffset(unsignedValueBitsNormalLimitTmp, factorNormalTmp, firstNormalIdxTmp);

    return new ErrorLimitingLayout1(
        absoluteErrorTmp,
        relativeErrorTmp,
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
    temp = Double.doubleToLongBits(absoluteError);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    result = prime * result + overflowBinIndex;
    temp = Double.doubleToLongBits(relativeError);
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
    ErrorLimitingLayout1 other = (ErrorLimitingLayout1) obj;
    if (Double.doubleToLongBits(absoluteError) != Double.doubleToLongBits(other.absoluteError)) {
      return false;
    }
    if (overflowBinIndex != other.overflowBinIndex) {
      return false;
    }
    if (Double.doubleToLongBits(relativeError) != Double.doubleToLongBits(other.relativeError)) {
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
    double x = idx * absoluteError;
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
        + " [absoluteError="
        + absoluteError
        + ", relativeError="
        + relativeError
        + ", underflowBinIndex="
        + underflowBinIndex
        + ", overflowBinIndex="
        + overflowBinIndex
        + "]";
  }
}
