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
    String expectedSerializedHistogramHexString =
        "00393FF00000000000004049000000000000BC0EF41380000000000800000008000010000080004001001001004010080808204208210842110891224924A494A528";
    String expectedCompressedHistogramHexString =
        "789C63B0B4FFC000060E9E107A0FDF17E106108303820518181A181C18190418191C04383838149C3814399C0439262A79AA2C99B25403001F7C09B0";

    histogram.addAscendingSequence(i -> i + 1, 50);

    byte[] serializedHistogram = SerializationUtil.write(histogram);
    byte[] compressedHistogram = SerializationUtil.writeCompressed(histogram);

    assertEquals(
        expectedSerializedHistogramHexString,
        SerializationTestUtil.byteArrayToHexString(serializedHistogram));
    assertEquals(
        expectedCompressedHistogramHexString,
        SerializationTestUtil.byteArrayToHexString(compressedHistogram));
  }
}
