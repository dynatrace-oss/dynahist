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
    int precision = 6;

    Layout logLinearLayout =
        LogLinearLayout.create(
            absoluteBinWidthLimit,
            relativeBinWidthLimit,
            valueRangeLowerBound,
            valueRangeUpperBound);
    Layout logQuadraticLayout =
        LogQuadraticLayout.create(
            absoluteBinWidthLimit,
            relativeBinWidthLimit,
            valueRangeLowerBound,
            valueRangeUpperBound);
    Layout logOptimalLayout =
        LogOptimalLayout.create(
            absoluteBinWidthLimit,
            relativeBinWidthLimit,
            valueRangeLowerBound,
            valueRangeUpperBound);
    Layout otelExpBucketLayout = OpenTelemetryExponentialBucketsLayout.create(precision);
    Layout customLayout = CustomLayout.create(-1, 1, 2, 3);

    // check if layouts are pairwise different
    assertNotEquals(customLayout, logLinearLayout);
    assertNotEquals(customLayout, logQuadraticLayout);
    assertNotEquals(customLayout, logOptimalLayout);
    assertNotEquals(customLayout, otelExpBucketLayout);
    assertNotEquals(logLinearLayout, logQuadraticLayout);
    assertNotEquals(logLinearLayout, logOptimalLayout);
    assertNotEquals(logLinearLayout, otelExpBucketLayout);
    assertNotEquals(logQuadraticLayout, logOptimalLayout);
    assertNotEquals(logQuadraticLayout, otelExpBucketLayout);
    assertNotEquals(logOptimalLayout, otelExpBucketLayout);

    Layout deserializedLogLinearLayout =
        SerializationTestUtil.testSerialization(
            logLinearLayout,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "05D0C7E2DC0316E8003F847AE147AE147B3FA999999999999A8C02A404");
    Layout deserializedLogQuadraticLayout =
        SerializationTestUtil.testSerialization(
            logQuadraticLayout,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "9D36115DE11D38D6003F847AE147AE147B3FA999999999999AD201A203");
    Layout deserializedLogOptimalLayout =
        SerializationTestUtil.testSerialization(
            logOptimalLayout,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "70C0EF16C3809948003F847AE147AE147B3FA999999999999AC6018603");
    Layout deserializedOtelExpBucketLayout =
        SerializationTestUtil.testSerialization(
            otelExpBucketLayout,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "F6E717A16F0A6A4A0006");
    Layout deserializedCustomLayout =
        SerializationTestUtil.testSerialization(
            customLayout,
            Layout::writeWithTypeInfo,
            Layout::readWithTypeInfo,
            "7F862C3808DF6FCD0004BFF00000000000003FF000000000000040000000000000004008000000000000");

    assertEquals(logLinearLayout, deserializedLogLinearLayout);
    assertEquals(logQuadraticLayout, deserializedLogQuadraticLayout);
    assertEquals(logOptimalLayout, deserializedLogOptimalLayout);
    assertEquals(otelExpBucketLayout, deserializedOtelExpBucketLayout);
    assertEquals(customLayout, deserializedCustomLayout);
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
