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

import static org.junit.Assert.*;

import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import java.io.IOException;
import org.junit.Test;

public class CustomLayoutTest {

  @Test
  public void testConsistency() {
    LayoutTestUtil.assertConsistency(CustomLayout.create(Double.POSITIVE_INFINITY));
    LayoutTestUtil.assertConsistency(CustomLayout.create(-1, 1));
    LayoutTestUtil.assertConsistency(CustomLayout.create(-1, 0, 1));
    LayoutTestUtil.assertConsistency(CustomLayout.create(-0., 0.));
    LayoutTestUtil.assertConsistency(CustomLayout.create(Math.nextUp(Double.NEGATIVE_INFINITY)));
    LayoutTestUtil.assertConsistency(CustomLayout.create(Math.nextUp(Double.NEGATIVE_INFINITY)));
    LayoutTestUtil.assertConsistency(
        CustomLayout.create(Math.nextUp(Double.NEGATIVE_INFINITY), Double.POSITIVE_INFINITY));
    LayoutTestUtil.assertConsistency(CustomLayout.create(-3, -1.5, 234, 4324234));
    LayoutTestUtil.assertConsistency(CustomLayout.create(-34234, -3, -1.5, 234, 4324234));
  }

  @Test
  public void testSerialization() throws IOException {
    CustomLayout layout = CustomLayout.create(-3, -1.5, 234, 4324234);
    CustomLayout deserializedLayout =
        SerializationTestUtil.testSerialization(
            layout,
            CustomLayout::write,
            CustomLayout::read,
            "0004C008000000000000BFF8000000000000406D40000000000041507EE280000000");

    assertEquals(deserializedLayout, layout);
  }

  @Test
  public void testHashCode() {
    CustomLayout layout = CustomLayout.create(-3, -1.5, 234, 4324234);
    assertEquals(327767682, layout.hashCode());
  }

  @Test
  public void testToString() {
    Layout layout = CustomLayout.create(-3, -1.5, 234, 4324234);
    assertEquals(
        "CustomLayout [sortedBinBoundaries=[-3.0, -1.5, 234.0, 4324234.0]]", layout.toString());
  }

  @Test
  public void testEquals() {
    Layout layout = CustomLayout.create(-3, -1.5, 234, 4324234);
    assertFalse(layout.equals(null));
    assertEquals(layout, layout);
    assertNotEquals(layout, LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6));
    assertNotEquals(layout, CustomLayout.create(-3, -1.5, 234, 353, 4324234));
  }

  @Test
  public void testCreate() {
    assertThrows(IllegalArgumentException.class, CustomLayout::create);
    assertThrows(
        IllegalArgumentException.class, () -> CustomLayout.create(Double.NEGATIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> CustomLayout.create(1, 0));
  }
}
