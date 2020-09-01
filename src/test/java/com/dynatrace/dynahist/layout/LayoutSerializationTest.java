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
    double absoluteBinWidthLimit = 0.01;
    double relativeBinWidthLimit = 0.05;
    double valueRangeLowerBound = 10;
    double valueRangeUpperBound = 1000;

    Layout layout1 =
        LogLinearLayout.create(
            absoluteBinWidthLimit,
            relativeBinWidthLimit,
            valueRangeLowerBound,
            valueRangeUpperBound);
    Layout layout2 =
        LogQuadraticLayout.create(
            absoluteBinWidthLimit,
            relativeBinWidthLimit,
            valueRangeLowerBound,
            valueRangeUpperBound);

    assertNotEquals(layout1, layout2);

    Layout deserializedLayout1 =
        SerializationTestUtil.testSerialization(
            layout1,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "05D0C7E2DC0316E8003F847AE147AE147B3FA999999999999A8C02A404");
    Layout deserializedLayout2 =
        SerializationTestUtil.testSerialization(
            layout2,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "9D36115DE11D38D6003F847AE147AE147B3FA999999999999AD201A203");

    assertEquals(layout1, deserializedLayout1);
    assertEquals(layout2, deserializedLayout2);
  }

  private class BaseTestLayout implements Layout {
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

  @Test
  public void testLayoutRegistration() {

    class TestLayout1 extends BaseTestLayout {}
    class TestLayout2 extends BaseTestLayout {}

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
    class TestLayout extends BaseTestLayout {}

    long serialVersion = 0x3e148a4afd4a0c36L; // this serial version is among the reserved list
    LayoutSerializationDefinition def =
        Layout.defineSerialization(
            serialVersion,
            TestLayout.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout());
    assertThrows(IllegalArgumentException.class, () -> Layout.register(def));
  }

  @Test
  public void testLayoutRegistrationWithNullArgument() {
    LayoutSerialization.register(null);
  }

  @Test
  public void testLayoutRegistrationWithSameSerialsAndDifferentTypes() {
    class TestLayout1 extends BaseTestLayout {}
    class TestLayout2 extends BaseTestLayout {}

    long serialVersion = 0xd7937e3e7c687bcdL;
    LayoutSerializationDefinition def1 =
        Layout.defineSerialization(
            serialVersion,
            TestLayout1.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout1());
    LayoutSerializationDefinition def2 =
        Layout.defineSerialization(
            serialVersion,
            TestLayout2.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout2());
    assertThrows(IllegalArgumentException.class, () -> Layout.register(def1, def2));
  }

  @Test
  public void testLayoutRegistrationWithSameSerialsAndSameTypes() {

    class TestLayout extends BaseTestLayout {}
    long serialVersion = 0x7c5456827a2e71caL;
    LayoutSerializationDefinition def1 =
        Layout.defineSerialization(
            serialVersion,
            TestLayout.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout());
    LayoutSerializationDefinition def2 =
        Layout.defineSerialization(
            serialVersion,
            TestLayout.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout());
    Layout.register(def1, def2);
  }

  @Test
  public void testLayoutRegistrationWithDifferentSerialsAndSameTypes() {
    class TestLayout extends BaseTestLayout {}

    long serialVersion1 = 0x794bdbf8691c97aeL;
    long serialVersion2 = 0x9e1069916e5fd9c9L;
    LayoutSerializationDefinition def1 =
        Layout.defineSerialization(
            serialVersion1,
            TestLayout.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout());
    LayoutSerializationDefinition def2 =
        Layout.defineSerialization(
            serialVersion2,
            TestLayout.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout());
    assertThrows(IllegalArgumentException.class, () -> Layout.register(def1, def2));
  }

  @Test
  public void testLayoutRegistrationWithDifferentSerialsAndDifferenTypes() {
    class TestLayout1 extends BaseTestLayout {}
    class TestLayout2 extends BaseTestLayout {}

    long serialVersion1 = 0x84bc993bcfbfc331L;
    long serialVersion2 = 0x467ce723a53f6415L;
    LayoutSerializationDefinition def1 =
        Layout.defineSerialization(
            serialVersion1,
            TestLayout1.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout1());
    LayoutSerializationDefinition def2 =
        Layout.defineSerialization(
            serialVersion2,
            TestLayout2.class,
            (data, dataOutput) -> {},
            dataInput -> new TestLayout2());
    Layout.register(def1, def2);
  }

  @Test
  public void testWriteWithTypeInfoForUnregisteredLayout() {
    class TestLayout extends BaseTestLayout {}

    Layout layout = new TestLayout();
    assertThrows(
        IOException.class,
        () -> SerializationTestUtil.toByteArray(Layout::writeWithTypeInfo, layout));
  }

  @Test
  public void testReadWithTypeInfoForUnregisteredLayout() {
    byte[] data = SerializationTestUtil.hexStringToByteArray("3ECCC0D9B0D7A08B");
    assertThrows(
        IOException.class,
        () -> SerializationTestUtil.fromByteArray(Layout::readWithTypeInfo, data));
  }
}
