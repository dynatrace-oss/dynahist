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

import static com.dynatrace.dynahist.util.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.*;

public final class SerializationTestUtil {

  private SerializationTestUtil() {}

  public static <T> T testSerialization(
      final T data, final SerializationWriter<T> writer, final SerializationReader<T> reader)
      throws IOException {
    final byte[] bytes = toByteArray(writer, data);
    return fromByteArray(reader, bytes);
  }

  public static <T> T testSerialization(
      final T data,
      final SerializationWriter<T> writer,
      final SerializationReader<T> reader,
      String expectedHexSerialization)
      throws IOException {
    String hexSerialization = byteArrayToHexString(toByteArray(writer, data));
    assertThat(hexSerialization).isEqualTo(expectedHexSerialization);
    return fromByteArray(reader, hexStringToByteArray(hexSerialization));
  }

  public static <T> T testReading(
      final SerializationReader<T> reader, final String hexSerialization) throws IOException {
    return fromByteArray(reader, hexStringToByteArray(hexSerialization));
  }

  public static <T> byte[] toByteArray(final SerializationWriter<T> writer, T data)
      throws IOException {
    byte[] bytes = toByteArrayHelper(writer, data);

    // repeat serialization multiple times to see if output is the same
    int repetitions = 5;
    for (int i = 0; i < repetitions; ++i) {
      assertThat(toByteArrayHelper(writer, data)).isEqualTo(bytes);
    }

    return bytes;
  }

  private static <T> byte[] toByteArrayHelper(final SerializationWriter<T> writer, T data)
      throws IOException {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    final DataOutputStream dos = new DataOutputStream(bos);
    writer.write(data, dos);
    return bos.toByteArray();
  }

  public static <T> T fromByteArray(final SerializationReader<T> reader, byte[] bytes)
      throws IOException {
    final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    final DataInputStream dis = new DataInputStream(bis);
    final T deserializedData = reader.read(dis);

    assertThrows(EOFException.class, dis::readByte);
    return deserializedData;
  }

  public static byte[] hexStringToByteArray(final String s) {
    final int len = s.length();
    checkArgument(len % 2 == 0);
    final byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      final int char1 = Character.digit(s.charAt(i + 0), 16);
      final int char2 = Character.digit(s.charAt(i + 1), 16);
      data[i / 2] = (byte) ((char1 << 4) + char2);
    }
    return data;
  }

  private static final char[] HEX_UPPER_CASE_CHARACTERS = "0123456789ABCDEF".toCharArray();

  public static String byteArrayToHexString(final byte[] bytes) {

    checkArgument(bytes.length <= Integer.MAX_VALUE >>> 1);

    final char[] hexChars = new char[bytes.length << 1];
    for (int j = 0; j < bytes.length; j++) {
      final int v = bytes[j] & 0xFF;
      hexChars[(j << 1) + 0] = HEX_UPPER_CASE_CHARACTERS[v >>> 4];
      hexChars[(j << 1) + 1] = HEX_UPPER_CASE_CHARACTERS[v & 0x0F];
    }
    return new String(hexChars);
  }
}
