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

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.layout.Layout;
import java.io.*;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class SerializationUtil {

  private SerializationUtil() {}

  private static final String ENCOUNTERED_UNEXPECTED_DATA_MSG = "Encountered unexpected data!";

  private static final String INCOMPATIBLE_SERIAL_VERSION_MSG =
      "Incompatible serial versions! Expected version %d but was %d.";

  public static void checkSerialVersion(byte expectedSerialVersion, int currentSerialVersion)
      throws IOException {
    if (expectedSerialVersion != currentSerialVersion) {
      throw new IOException(
          String.format(
              (Locale) null,
              INCOMPATIBLE_SERIAL_VERSION_MSG,
              expectedSerialVersion,
              currentSerialVersion));
    }
  }

  /**
   * Writes a {@code long} to the given {@link DataOutput} using variable-length encoding.
   *
   * @param value the {@code long} value
   * @param dataOutput the {@link DataOutput}
   * @throws IOException if an I/O error occurs
   */
  public static void writeUnsignedVarLong(long value, final DataOutput dataOutput)
      throws IOException {
    while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
      dataOutput.writeByte(((int) value & 0x7F) | 0x80);
      value >>>= 7;
    }
    dataOutput.writeByte((int) value & 0x7F);
  }

  /**
   * Writes an {@code int} to the given {@link DataOutput} using variable-length and zigzag
   * encoding.
   *
   * @param value the {@code int} value
   * @param dataOutput the {@link DataOutput}
   * @throws IOException if an I/O error occurs
   */
  public static void writeSignedVarInt(final int value, final DataOutput dataOutput)
      throws IOException {
    writeUnsignedVarInt((value << 1) ^ (value >> 31), dataOutput);
  }

  /**
   * Writes an {@code int} to the given {@link DataOutput} using variable-length encoding.
   *
   * @param value the {@code int} value
   * @param dataOutput the {@link DataOutput}
   * @throws IOException if an I/O error occurs
   */
  public static void writeUnsignedVarInt(int value, final DataOutput dataOutput)
      throws IOException {
    while ((value & 0xFFFFFF80) != 0L) {
      dataOutput.writeByte((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    dataOutput.writeByte(value & 0x7F);
  }

  /**
   * Reads a variable-length encoded {@code long} from the given {@link DataInput}.
   *
   * @param dataInput the {@link DataInput}
   * @return the read {@code long} value
   * @throws IOException if an I/O error occurs
   */
  public static long readUnsignedVarLong(final DataInput dataInput) throws IOException {
    long value = 0L;
    int i = 0;
    long b;
    while (((b = dataInput.readByte()) & 0x80L) != 0) {
      value |= (b & 0x7F) << i;
      i += 7;
      if (i > 63) {
        throw new IOException(ENCOUNTERED_UNEXPECTED_DATA_MSG);
      }
    }
    return value | (b << i);
  }

  /**
   * Reads a variable-length and zigzag encoded {@code long} from the given {@link DataInput}.
   *
   * @param dataInput the {@link DataInput}
   * @return the read {@code long} value
   * @throws IOException if an I/O error occurs
   */
  public static int readSignedVarInt(final DataInput dataInput) throws IOException {
    final int raw = readUnsignedVarInt(dataInput);
    final int temp = (((raw << 31) >> 31) ^ raw) >> 1;
    return temp ^ (raw & (1 << 31));
  }

  /**
   * Reads a variable-length encoded {@code int} from the given {@link DataInput}.
   *
   * @param dataInput the {@link DataInput}
   * @return the read {@code int} value
   * @throws IOException if an I/O error occurs
   */
  public static int readUnsignedVarInt(final DataInput dataInput) throws IOException {
    int value = 0;
    int i = 0;
    int b;
    while (((b = dataInput.readByte()) & 0x80) != 0) {
      value |= (b & 0x7F) << i;
      i += 7;
      if (i > 35) {
        throw new IOException(ENCOUNTERED_UNEXPECTED_DATA_MSG);
      }
    }
    return value | (b << i);
  }

  public static byte[] write(Histogram histogram) throws IOException {
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream); ) {
      histogram.write(dataOutputStream);
      return byteArrayOutputStream.toByteArray();
    }
  }

  public static Histogram readAsStatic(Layout layout, byte[] serializedHistogram)
      throws IOException {
    try (ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(serializedHistogram); ) {
      return Histogram.readAsStatic(layout, new DataInputStream(byteArrayInputStream));
    }
  }

  public static Histogram readAsDynamic(Layout layout, byte[] serializedHistogram)
      throws IOException {
    try (ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(serializedHistogram); ) {
      return Histogram.readAsDynamic(layout, new DataInputStream(byteArrayInputStream));
    }
  }

  public static Histogram readAsPreprocessed(Layout layout, byte[] serializedHistogram)
      throws IOException {
    try (ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(serializedHistogram); ) {
      return Histogram.readAsPreprocessed(layout, new DataInputStream(byteArrayInputStream));
    }
  }

  public static byte[] writeCompressed(Histogram histogram) throws IOException {
    return compressHistogram(write(histogram));
  }

  public static Histogram readCompressedAsStatic(Layout layout, byte[] serializedHistogram)
      throws DataFormatException, IOException {
    return readAsStatic(layout, decompressHistogram(serializedHistogram));
  }

  public static Histogram readCompressedAsDynamic(Layout layout, byte[] serializedHistogram)
      throws IOException, DataFormatException {
    return readAsDynamic(layout, decompressHistogram(serializedHistogram));
  }

  public static Histogram readCompressedAsPreprocessed(Layout layout, byte[] serializedHistogram)
      throws DataFormatException, IOException {
    return readAsPreprocessed(layout, decompressHistogram(serializedHistogram));
  }

  private static byte[] compressHistogram(byte[] serializedHistogram) throws IOException {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ) {
      Deflater deflater = new Deflater();
      deflater.setInput(serializedHistogram);
      deflater.finish();
      byte[] buffer = new byte[1024];
      while (!deflater.finished()) {
        outputStream.write(buffer, 0, deflater.deflate(buffer));
      }
      return outputStream.toByteArray();
    }
  }

  private static byte[] decompressHistogram(byte[] serializedHistogram)
      throws DataFormatException, IOException {
    try (ByteArrayOutputStream outputStream =
        new ByteArrayOutputStream(serializedHistogram.length); ) {
      Inflater inflater = new Inflater();
      inflater.setInput(serializedHistogram);
      byte[] buffer = new byte[1024];
      while (!inflater.finished()) {
        outputStream.write(buffer, 0, inflater.inflate(buffer));
      }
      return outputStream.toByteArray();
    }
  }
}
