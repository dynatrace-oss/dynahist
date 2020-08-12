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
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A layout for {@link Histogram} that is able to approximate any values within a given range with a
 * maximum error that either satisfies the given absolute or the given relative error bounds.
 *
 * <p>This class is immutable.
 */
public final class ErrorLimitingLayout2 extends AbstractLayout {

  protected static final byte SERIAL_VERSION_V0 = 0;

  private final double absoluteError;
  private final double relativeError;

  private final int underflowBinIndex;
  private final int overflowBinIndex;

  private final transient double factorNormal;
  private final transient double factorSubnormal;

  private final transient double offset;
  private final transient int firstNormalIdx;

  /**
   * Creates a {@link Layout} that guarantees a given error over a given interval.
   *
   * <p>The allowed error is given by the maximum of relative and absolute error.
   *
   * @param absoluteError the allowed absolute error
   * @param relativeError the allowed relative error
   * @param minValue the minimum value that can be recorded with given error guarantees
   * @param maxValue the maximum value that can be recorded with given error guarantees
   */
  public static ErrorLimitingLayout2 create(
      final double absoluteError,
      final double relativeError,
      final double minValue,
      final double maxValue) {

    checkArgument(!Double.isInfinite(maxValue));
    checkArgument(!Double.isInfinite(minValue));
    checkArgument(!Double.isNaN(maxValue));
    checkArgument(!Double.isNaN(minValue));
    checkArgument(maxValue >= minValue);

    final int firstNormalIdx = calculateFirstNormalIndex(relativeError);
    final double factorNormal = calculateFactorNormal(relativeError);
    final double factorSubnormal = calculateFactorSubNormal(absoluteError);
    final double offset = calculateOffset(absoluteError, firstNormalIdx);

    final int minValueBinIndex =
        mapToBinIndex(minValue, factorNormal, factorSubnormal, firstNormalIdx, offset);
    final int maxValueBinIndex =
        mapToBinIndex(maxValue, factorNormal, factorSubnormal, firstNormalIdx, offset);

    checkArgument(minValueBinIndex > Integer.MIN_VALUE);
    checkArgument(maxValueBinIndex < Integer.MAX_VALUE);

    final int underflowBinIndex = minValueBinIndex - 1;
    final int overflowBinIndex = maxValueBinIndex + 1;

    checkArgument(
        overflowBinIndex - underflowBinIndex - 1 <= Integer.MAX_VALUE); // TODO always true
    checkArgument(overflowBinIndex - underflowBinIndex - 1 >= 0);

    return new ErrorLimitingLayout2(
        absoluteError,
        relativeError,
        underflowBinIndex,
        overflowBinIndex,
        factorNormal,
        factorSubnormal,
        offset,
        firstNormalIdx);
  }

  private ErrorLimitingLayout2(
      double absoluteError,
      double relativeError,
      int underflowBinIndex,
      int overflowBinIndex,
      double factorNormal,
      double factorSubnormal,
      double offset,
      int firstNormalIdx) {

    this.absoluteError = absoluteError;
    this.relativeError = relativeError;
    this.underflowBinIndex = underflowBinIndex;
    this.overflowBinIndex = overflowBinIndex;
    this.factorNormal = factorNormal;
    this.factorSubnormal = factorSubnormal;
    this.offset = offset;
    this.firstNormalIdx = firstNormalIdx;
  }

  /**
   * For unsigned values the return value is in the range [0, 6144].
   *
   * <p>It can be shown that this function is monotonically increasing for all non-negative
   * arguments.
   *
   * @param unsignedValueBits
   * @return
   */
  static double calculateOffset(final long unsignedValueBits) {
    final long exponent = unsignedValueBits >>> 52;
    final double exponentMul3 = exponent + (exponent << 1);
    final double mantissaPlus1 =
        Double.longBitsToDouble((unsignedValueBits & 0x000fffffffffffffL) | 0x3ff0000000000000L);
    return ((mantissaPlus1 - 1d) * (5d - mantissaPlus1) + exponentMul3);
  }

  private static int mapToBinIndex(
      final double value,
      final double factorNormal,
      final double factorSubnormal,
      final int firstNormalIdx,
      final double offset) {
    final long valueBits = Double.doubleToRawLongBits(value);
    final long unsignedValueBits = valueBits & 0x7fffffffffffffffL;
    double a = calculateOffset(unsignedValueBits) - offset;
    final int idx;
    if (a >= 0) {
      idx = (int) (factorNormal * a) + firstNormalIdx;
    } else {
      idx = (int) (factorSubnormal * Double.longBitsToDouble(unsignedValueBits));
    }
    return (valueBits >= 0) ? idx : ~idx;
  }

  @Override
  public final int mapToBinIndex(final double value) {
    return mapToBinIndex(value, factorNormal, factorSubnormal, firstNormalIdx, offset);
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

  public static ErrorLimitingLayout2 read(DataInput dataInput) throws IOException {
    checkSerialVersion(SERIAL_VERSION_V0, dataInput.readUnsignedByte());
    double absoluteErrorTmp = dataInput.readDouble();
    double relativeErrorTmp = dataInput.readDouble();
    int underflowBinIndexTmp = SerializationUtil.readSignedVarInt(dataInput);
    int overflowBinIndexTmp = SerializationUtil.readSignedVarInt(dataInput);

    final int firstNormalIdxTmp = calculateFirstNormalIndex(relativeErrorTmp);
    final double factorNormalTmp = calculateFactorNormal(relativeErrorTmp);
    final double factorSubnormalTmp = calculateFactorSubNormal(absoluteErrorTmp);
    final double offsetTmp = calculateOffset(absoluteErrorTmp, firstNormalIdxTmp);

    return new ErrorLimitingLayout2(
        absoluteErrorTmp,
        relativeErrorTmp,
        underflowBinIndexTmp,
        overflowBinIndexTmp,
        factorNormalTmp,
        factorSubnormalTmp,
        offsetTmp,
        firstNormalIdxTmp);
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
    ErrorLimitingLayout2 other = (ErrorLimitingLayout2) obj;
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
      return getApproximateBoundHelper(binIndex);
    } else {
      return -getApproximateBoundHelper(-binIndex);
    }
  }

  private double getApproximateBoundHelper(final int idx) {
    if (idx <= firstNormalIdx) {
      return idx * absoluteError;
    } else {
      final double s = (idx - firstNormalIdx) / factorNormal + offset;
      final int exponent = ((int) Math.floor(s)) / 3;
      final int exponentMul3Plus4 = exponent + (exponent << 1) + 4;
      final double mantissaPlus1 =
          3. - Math.sqrt(exponentMul3Plus4 - s); // mantissaPlus1 is in the range [1, 2)
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

  private static int calculateFirstNormalIndex(double relativeError) {
    checkArgument(relativeError >= 0d);
    return (int) StrictMath.ceil(1. / relativeError + 0.5);
  }

  private static double calculateFactorNormal(double relativeError) {
    return 0.25 / StrictMath.log1p(relativeError);
  }

  private static double calculateFactorSubNormal(double absoluteError) {
    checkArgument(absoluteError >= Double.MIN_NORMAL);
    return 1d / absoluteError;
  }

  private static double calculateOffset(double absoluteError, int firstNormalIdx) {
    final double subNormalLimit =
        (firstNormalIdx != Integer.MAX_VALUE)
            ? absoluteError * firstNormalIdx
            : Double.POSITIVE_INFINITY;
    checkArgument(subNormalLimit >= Double.MIN_NORMAL);
    return calculateOffset(Double.doubleToLongBits(subNormalLimit));
  }
}
