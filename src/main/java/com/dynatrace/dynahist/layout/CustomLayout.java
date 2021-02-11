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
import static com.dynatrace.dynahist.serialization.SerializationUtil.readUnsignedVarInt;
import static com.dynatrace.dynahist.serialization.SerializationUtil.writeUnsignedVarInt;
import static com.dynatrace.dynahist.util.Algorithms.mapDoubleToLong;
import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.util.Algorithms;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/** A custom histogram bin layout. */
public class CustomLayout implements Layout {

  private static final byte SERIAL_VERSION_V0 = 0;

  private final double[] sortedBinBoundaries;

  private CustomLayout(double[] sortedBinBoundaries) {
    requireNonNull(sortedBinBoundaries);
    this.sortedBinBoundaries = sortedBinBoundaries;
  }

  public static CustomLayout create(double... sortedBinBoundaries) {
    requireNonNull(sortedBinBoundaries);
    checkArgument(sortedBinBoundaries.length > 0);
    checkArgument(sortedBinBoundaries[0] > Double.NEGATIVE_INFINITY);
    for (int i = 1; i < sortedBinBoundaries.length; ++i) {
      checkArgument(
          mapDoubleToLong(sortedBinBoundaries[i - 1]) < mapDoubleToLong(sortedBinBoundaries[i]));
    }
    return new CustomLayout(Arrays.copyOf(sortedBinBoundaries, sortedBinBoundaries.length));
  }

  @Override
  public int mapToBinIndex(double value) {
    long mappedValue = Algorithms.mapDoubleToLong(value);
    return (int)
        Algorithms.findFirst(
            l ->
                l == sortedBinBoundaries.length
                    || Algorithms.mapDoubleToLong(sortedBinBoundaries[(int) l]) > mappedValue,
            0,
            sortedBinBoundaries.length);
  }

  @Override
  public double getBinLowerBound(int binIndex) {
    if (binIndex > 0) {
      return sortedBinBoundaries[Math.min(binIndex, sortedBinBoundaries.length) - 1];
    } else {
      return Double.NEGATIVE_INFINITY;
    }
  }

  @Override
  public double getBinUpperBound(int binIndex) {
    if (binIndex < sortedBinBoundaries.length) {
      return Algorithms.mapLongToDouble(
          Algorithms.mapDoubleToLong(sortedBinBoundaries[Math.max(0, binIndex)]) - 1);
    } else {
      return Double.POSITIVE_INFINITY;
    }
  }

  @Override
  public int getUnderflowBinIndex() {
    return 0;
  }

  @Override
  public int getOverflowBinIndex() {
    return sortedBinBoundaries.length;
  }

  public void write(DataOutput dataOutput) throws IOException {
    dataOutput.writeByte(SERIAL_VERSION_V0);
    writeUnsignedVarInt(sortedBinBoundaries.length, dataOutput);
    for (double boundary : sortedBinBoundaries) {
      dataOutput.writeDouble(boundary);
    }
  }

  public static CustomLayout read(DataInput dataInput) throws IOException {
    checkSerialVersion(SERIAL_VERSION_V0, dataInput.readUnsignedByte());
    int len = readUnsignedVarInt(dataInput);
    double[] sortedBinBoundaries = new double[len];
    for (int i = 0; i < len; ++i) {
      sortedBinBoundaries[i] = dataInput.readDouble();
    }
    return new CustomLayout(sortedBinBoundaries);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(sortedBinBoundaries);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    CustomLayout other = (CustomLayout) obj;
    if (!Arrays.equals(sortedBinBoundaries, other.sortedBinBoundaries)) return false;
    return true;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + " [sortedBinBoundaries="
        + Arrays.toString(sortedBinBoundaries)
        + "]";
  }
}
