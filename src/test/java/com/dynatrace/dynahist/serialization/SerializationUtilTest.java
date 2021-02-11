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
package com.dynatrace.dynahist.serialization;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
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
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    String expectedSerializedHistogramHexString =
        "00393FF00000000000004049000000000000BC0EF413800000000008000000080000100000800040010010010040100808082041082108221108912249249494A528";
    String expectedCompressedHistogramHexString =
        "789C63B0B4FFC000060E9E107A0FDF17E106108303820518181A181C18190418191C04383838141C391439940439262A79AA4C99B25403001DCD097F";

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

  @Test
  public void testFromByteArray() throws IOException {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    SerializationReader<Histogram> serializationReader =
        dataInput -> Histogram.readAsDynamic(layout, dataInput);

    byte[] serializedHistogram = null;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
      histogram.write(dataOutputStream);
      serializedHistogram = byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertEquals(
        histogram, SerializationUtil.fromByteArray(serializationReader, serializedHistogram));
  }

  @Test
  public void testToByteArray() throws IOException {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    SerializationWriter<Histogram> serializationWriter =
        (data, dataOutput) -> histogram.write(dataOutput);
    byte[] serializedHistogram = null;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
      histogram.write(dataOutputStream);
      serializedHistogram = byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertArrayEquals(
        serializedHistogram, SerializationUtil.toByteArray(serializationWriter, histogram));
  }
}
