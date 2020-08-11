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

import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
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
}
