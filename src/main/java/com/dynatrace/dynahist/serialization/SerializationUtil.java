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

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
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

  public static void writeUnsignedVarLong(long value, final DataOutput dataOutput)
      throws IOException {
    while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
      dataOutput.writeByte(((int) value & 0x7F) | 0x80);
      value >>>= 7;
    }
    dataOutput.writeByte((int) value & 0x7F);
  }

  public static void writeSignedVarInt(final int value, final DataOutput dataOutput)
      throws IOException {
    writeUnsignedVarInt((value << 1) ^ (value >> 31), dataOutput);
  }

  public static void writeUnsignedVarInt(int value, final DataOutput dataOutput)
      throws IOException {
    while ((value & 0xFFFFFF80) != 0L) {
      dataOutput.writeByte((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    dataOutput.writeByte(value & 0x7F);
  }

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

  public static int readSignedVarInt(final DataInput dataInput) throws IOException {
    final int raw = readUnsignedVarInt(dataInput);
    final int temp = (((raw << 31) >> 31) ^ raw) >> 1;
    return temp ^ (raw & (1 << 31));
  }

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

  public static byte[] compress(byte[] data) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Deflater deflater = new Deflater();
    deflater.setInput(data);
    deflater.finish();
    byte[] buffer = new byte[1024];
    while (!deflater.finished()) {
      outputStream.write(buffer, 0, deflater.deflate(buffer));
    }
    return outputStream.toByteArray();
  }

  public static byte[] decompress(byte[] data) throws DataFormatException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
    Inflater inflater = new Inflater();
    inflater.setInput(data);
    byte[] buffer = new byte[1024];
    while (!inflater.finished()) {
      outputStream.write(buffer, 0, inflater.inflate(buffer));
    }
    return outputStream.toByteArray();
  }
}
