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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import java.io.IOException;
import org.junit.Test;

public class LayoutSerializationTest {

  @Test
  public void test() throws IOException {
    double absoluteError = 0.01;
    double relativeError = 0.05;
    double minValue = 10;
    double maxValue = 1000;

    Layout layout1 = ErrorLimitingLayout1.create(absoluteError, relativeError, minValue, maxValue);
    Layout layout2 = ErrorLimitingLayout2.create(absoluteError, relativeError, minValue, maxValue);

    assertNotEquals(layout1, layout2);

    Layout deserializedLayout1 =
        SerializationTestUtil.testSerialization(
            layout1,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "686F93824A6AA99A003F847AE147AE147B3FA999999999999A8C02A404");
    Layout deserializedLayout2 =
        SerializationTestUtil.testSerialization(
            layout2,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "2C48F5ABE116B423003F847AE147AE147B3FA999999999999AD201A203");

    assertEquals(layout1, deserializedLayout1);
    assertEquals(layout2, deserializedLayout2);
  }

  public class BaseTestLayout implements Layout {
    @Override
    public int mapToBinIndex(double value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getUnderflowBinIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getOverflowBinIndex() {
      throw new UnsupportedOperationException();
    }
  }

  public class TestLayout1 extends BaseTestLayout {}

  public class TestLayout2 extends BaseTestLayout {}

  @Test
  public void testLayoutRegistration() {
    long serialVersion = 0xfd6be2444812868eL;
    LayoutSerializationDefinition def1 =
        Layout.defineSerialization(
            serialVersion,
            TestLayout1.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout1());
    Layout.register(def1);
    LayoutSerializationDefinition def2 =
        Layout.defineSerialization(
            serialVersion,
            TestLayout2.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout2());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Layout.register(
                def2)); // registration of another serialization using the same serial version must
    // fail
  }

  @Test
  public void testLayoutRegistrationWithReservedSerialVersion() {
    long serialVersion = 0x3e148a4afd4a0c36L; // this serial version is among the reserved list
    LayoutSerializationDefinition def =
        Layout.defineSerialization(
            serialVersion,
            TestLayout2.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout2());
    assertThrows(IllegalArgumentException.class, () -> Layout.register(def));
  }
}
