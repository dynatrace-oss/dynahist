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

import static com.dynatrace.dynahist.util.Algorithms.NEGATIVE_INFINITY_MAPPED_TO_LONG;
import static com.dynatrace.dynahist.util.Algorithms.POSITIVE_INFINITY_MAPPED_TO_LONG;
import static com.dynatrace.dynahist.util.Algorithms.findFirst;
import static com.dynatrace.dynahist.util.Algorithms.mapLongToDouble;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.serialization.SerializationReader;
import com.dynatrace.dynahist.serialization.SerializationWriter;
import com.dynatrace.dynahist.util.Algorithms;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A histogram bin layout, which defines the bins for a {@link Histogram}.
 *
 * <p>All implementations must be immutable.
 */
public interface Layout {

  /**
   * Maps a given value to a histogram bin index.
   *
   * <p>This function must be monotonically increasing. {@link Double#NaN} must always either return
   * an index that is smaller than or equal to {@link #getUnderflowBinIndex()} or an index that is
   * larger than or equal to {@link #getOverflowBinIndex()}.
   *
   * @param value a {@code Double} value
   * @return the index of the histogram bin to which the given value is mapped to
   */
  int mapToBinIndex(double value);

  /**
   * Returns the maximum index that is associated with the underflow bin of the histogram.
   *
   * <p>Note: {@link #getUnderflowBinIndex()} &lt; {@link #getOverflowBinIndex()} always holds.
   *
   * @return the maximum index that is associated with the underflow bin of the histogram
   */
  int getUnderflowBinIndex();

  /**
   * Returns the minimum index that is associated with the overflow bin of the histogram.
   *
   * <p>Note: {@link #getUnderflowBinIndex()} &lt; {@link #getOverflowBinIndex()} always holds.
   *
   * @return the minimum index that is associated with the overflow bin of the histogram
   */
  int getOverflowBinIndex();

  /**
   * Returns the smallest value that is mapped to the bin with given bin index.
   *
   * <p>This method is defined for all integer values. The returned value is equal to {@link
   * Double#NEGATIVE_INFINITY} for all indices smaller than or equal to {@link
   * #getUnderflowBinIndex()}. For all indices greater than or equal to {@link
   * #getOverflowBinIndex()} the same value is returned.
   *
   * @param binIndex the bin index
   * @return the lower bound of the bin
   */
  default double getBinLowerBound(int binIndex) {
    if (binIndex <= getUnderflowBinIndex()) {
      return Double.NEGATIVE_INFINITY;
    }
    int effectiveBinIndex = Math.min(getOverflowBinIndex(), binIndex);
    return mapLongToDouble(
        findFirst(
            l -> mapToBinIndex(Algorithms.mapLongToDouble(l)) >= effectiveBinIndex,
            NEGATIVE_INFINITY_MAPPED_TO_LONG,
            POSITIVE_INFINITY_MAPPED_TO_LONG));
  }

  /**
   * Returns the largest value that is mapped to the bin with given bin index.
   *
   * <p>This method is defined for all integer values. The returned value is equal to {@link
   * Double#POSITIVE_INFINITY} for all indices greater than or equal to {@link
   * #getOverflowBinIndex()}. For all indices smaller than or equal to {@link
   * #getUnderflowBinIndex()} the same value is returned.
   *
   * @param binIndex the bin index
   * @return the lower bound of the bin
   */
  default double getBinUpperBound(int binIndex) {
    if (binIndex >= getOverflowBinIndex()) {
      return Double.POSITIVE_INFINITY;
    }
    int effectiveBinIndex = Math.max(getUnderflowBinIndex(), binIndex);
    return mapLongToDouble(
        ~Algorithms.findFirst(
            l -> mapToBinIndex(mapLongToDouble(~l)) <= effectiveBinIndex,
            ~POSITIVE_INFINITY_MAPPED_TO_LONG,
            ~NEGATIVE_INFINITY_MAPPED_TO_LONG));
  }

  /**
   * Writes a {@link Layout} object.
   *
   * <p>Important: Write methods for specific implementations must be registered in {@code
   * LayoutSerialization}.
   *
   * @param dataOutput a {@link DataOutput}
   * @throws IOException if an I/O error occurs
   */
  default void writeWithTypeInfo(DataOutput dataOutput) throws IOException {
    LayoutSerialization.write(this, dataOutput);
  }

  /**
   * Reads a {@link Layout} object.
   *
   * <p>Important: Read methods for specific implementations must be registered in {@code
   * LayoutSerialization}.
   *
   * @param dataInput a {@link DataInput}
   * @return the read layout
   * @throws IOException if an I/O error occurs
   */
  static Layout readWithTypeInfo(DataInput dataInput) throws IOException {
    return LayoutSerialization.read(dataInput);
  }

  /**
   * Returns the smallest value which can be mapped into a regular bin.
   *
   * @return the smallest value which can be mapped into a regular bin
   */
  default double getNormalRangeLowerBound() {
    return getBinLowerBound(getUnderflowBinIndex() + 1);
  }

  /**
   * Returns the largest value which can be mapped into a regular bin.
   *
   * @return the largest value which can be mapped into a regular bin
   */
  default double getNormalRangeUpperBound() {
    return getBinUpperBound(getOverflowBinIndex() - 1);
  }

  /**
   * Defines the serialization of a new layout that can then be registered using {@link
   * #register(LayoutSerializationDefinition...)}.
   *
   * @param <T> a {@code Layout} type
   * @param serialVersion a unique serial version (choose some long constant that has been generated
   *     randomly)
   * @param clazz the type of the layout
   * @param writer defines the serialization of the layout
   * @param reader defines the deserialization of the layout
   * @return a new @link {@link LayoutSerializationDefinition}
   */
  static <T extends Layout> LayoutSerializationDefinition defineSerialization(
      long serialVersion,
      Class<T> clazz,
      SerializationWriter<T> writer,
      SerializationReader<T> reader) {
    return new LayoutSerializationDefinition(serialVersion, clazz, writer, reader);
  }

  /**
   * Registers the given layout serialization definitions such that they are known by subsequent
   * calls of {@link #writeWithTypeInfo(DataOutput)} and {@link #readWithTypeInfo(DataInput)}.
   *
   * @param definitions the layout serializations to register
   */
  static void register(LayoutSerializationDefinition... definitions) {
    LayoutSerialization.register(definitions);
  }
}
