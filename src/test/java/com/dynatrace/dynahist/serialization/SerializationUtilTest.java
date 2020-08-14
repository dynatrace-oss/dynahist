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
package com.dynatrace.dynahist.serialization;

import static org.junit.Assert.*;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.layout.ErrorLimitingLayout2;
import com.dynatrace.dynahist.layout.Layout;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import org.junit.Test;

public class SerializationUtilTest {

  @Test
  public void testReadUnsignedVarInt() {
    byte[] array = new byte[] {-1, -2, -3, -4, -5, -6, -7, -8, -9};
    assertThrows(
        IOException.class,
        () ->
            SerializationUtil.readUnsignedVarInt(
                new DataInputStream(new ByteArrayInputStream(array))));
  }

  @Test
  public void testReadUnsignedVarLong() {
    byte[] array = new byte[] {-1, -2, -3, -4, -5, -6, -7, -8, -9, -10};
    assertThrows(
        IOException.class,
        () ->
            SerializationUtil.readUnsignedVarLong(
                new DataInputStream(new ByteArrayInputStream(array))));
  }

  @Test
  public void testSerialization() throws IOException, DataFormatException {
    Layout layout = ErrorLimitingLayout2.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogramDynamic = Histogram.createDynamic(layout);
    Histogram histogramStatic = Histogram.createDynamic(layout);
    Histogram histogramPreprocessed = Histogram.createDynamic(layout);
    histogramDynamic.addAscendingSequence(i -> i + 1, 1000000000);
    histogramPreprocessed.addAscendingSequence(i -> i + 1, 1000000000);
    histogramStatic.addAscendingSequence(i -> i + 1, 1000000000);

    {
      byte[] serializedHistogram;
      byte[] compressedHistogram;
      Histogram decompressedHistogram;
      Histogram deserializedHistogram;
      serializedHistogram = SerializationUtil.write(histogramDynamic);
      compressedHistogram = SerializationUtil.writeCompressed(histogramDynamic);

      deserializedHistogram = SerializationUtil.readAsDynamic(layout, serializedHistogram);
      decompressedHistogram =
          SerializationUtil.readCompressedAsDynamic(layout, compressedHistogram);
      assertEquals(histogramDynamic, deserializedHistogram);
      assertEquals(histogramDynamic, decompressedHistogram);
      assertEquals(histogramDynamic.hashCode(), deserializedHistogram.hashCode());
      assertEquals(histogramDynamic.hashCode(), decompressedHistogram.hashCode());
    }

    {
      byte[] serializedHistogram;
      byte[] compressedHistogram;
      Histogram decompressedHistogram;
      Histogram deserializedHistogram;
      serializedHistogram = SerializationUtil.write(histogramDynamic);
      compressedHistogram = SerializationUtil.writeCompressed(histogramDynamic);
      deserializedHistogram = SerializationUtil.readAsDynamic(layout, serializedHistogram);
      decompressedHistogram =
          SerializationUtil.readCompressedAsDynamic(layout, compressedHistogram);
      assertEquals(histogramDynamic, deserializedHistogram);
      assertEquals(histogramDynamic, decompressedHistogram);
      assertEquals(histogramDynamic.hashCode(), deserializedHistogram.hashCode());
      assertEquals(histogramDynamic.hashCode(), decompressedHistogram.hashCode());
    }

    {
      byte[] serializedHistogram;
      byte[] compressedHistogram;
      Histogram decompressedHistogram;
      Histogram deserializedHistogram;
      serializedHistogram = SerializationUtil.write(histogramStatic);
      compressedHistogram = SerializationUtil.writeCompressed(histogramStatic);
      deserializedHistogram = SerializationUtil.readAsStatic(layout, serializedHistogram);
      decompressedHistogram = SerializationUtil.readCompressedAsStatic(layout, compressedHistogram);
      assertEquals(histogramStatic, deserializedHistogram);
      assertEquals(histogramStatic, decompressedHistogram);
      assertEquals(histogramStatic.hashCode(), deserializedHistogram.hashCode());
      assertEquals(histogramStatic.hashCode(), decompressedHistogram.hashCode());
    }

    {
      byte[] serializedHistogram;
      byte[] compressedHistogram;
      Histogram decompressedHistogram;
      Histogram deserializedHistogram;
      serializedHistogram = SerializationUtil.write(histogramPreprocessed);
      compressedHistogram = SerializationUtil.writeCompressed(histogramPreprocessed);
      deserializedHistogram = SerializationUtil.readAsPreprocessed(layout, serializedHistogram);
      decompressedHistogram =
          SerializationUtil.readCompressedAsPreprocessed(layout, compressedHistogram);

      assertEquals(histogramPreprocessed, deserializedHistogram);
      assertEquals(histogramPreprocessed, decompressedHistogram);
      assertEquals(histogramPreprocessed.hashCode(), deserializedHistogram.hashCode());
      assertEquals(histogramPreprocessed.hashCode(), decompressedHistogram.hashCode());
    }
  }

  @Test
  public void testWriteAndWriteCompressed() throws IOException {
    Layout layout = ErrorLimitingLayout2.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    byte[] expectedSerializedHistogram =
        new byte[] {
          0, 57, 63, -16, 0, 0, 0, 0, 0, 0, 64, 73, 0, 0, 0, 0, 0, 0, -68, 14, -12, 19, -128, 0, 0,
          0, 0, 8, 0, 0, 0, 8, 0, 0, 16, 0, 0, -128, 0, 64, 1, 0, 16, 1, 0, 64, 16, 8, 8, 8, 32, 66,
          8, 33, 8, 66, 17, 8, -111, 34, 73, 36, -92, -108, -91, 40,
        };
    byte[] expectedCompressedHistogram =
        new byte[] {
          120, -100, 99, -80, -76, -1, -64, 0, 6, 14, -98, 16, 122, 15, -33, 23, -31, 6, 16, -125,
          3, -126, 5, 24, 24, 26, 24, 28, 24, 25, 4, 24, 25, 28, 4, 56, 56, 56, 20, -100, 56, 20,
          57, -100, 4, 57, 38, 42, 121, -86, 44, -103, -78, 84, 3, 0, 31, 124, 9, -80,
        };

    histogram.addAscendingSequence(i -> i + 1, 50);

    byte[] serializedHistogram = SerializationUtil.write(histogram);
    byte[] compressedHistogram = SerializationUtil.writeCompressed(histogram);

    assertArrayEquals(expectedSerializedHistogram, serializedHistogram);
    assertArrayEquals(expectedCompressedHistogram, compressedHistogram);
  }
}
