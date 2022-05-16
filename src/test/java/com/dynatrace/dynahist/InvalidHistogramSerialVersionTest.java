/*
 * Copyright 2022 Dynatrace LLC
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
package com.dynatrace.dynahist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dynatrace.dynahist.layout.CustomLayout;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.serialization.SerializationReader;
import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class InvalidHistogramSerialVersionTest {

  @Test
  public void invalidSerialVersions() {
    byte[] validSerialVersions = {
      AbstractHistogram.SERIAL_VERSION_V0, AbstractHistogram.SERIAL_VERSION_V1
    };
    Arrays.sort(validSerialVersions);
    int skippedVersions = 0;
    Layout layout = CustomLayout.create(0);
    for (int i = 0; i < 256; ++i) {
      byte version = (byte) i;
      if (Arrays.binarySearch(validSerialVersions, version) < 0) {
        assertException(version, in -> Histogram.readAsPreprocessed(layout, in));
        assertException(version, in -> Histogram.readAsStatic(layout, in));
        assertException(version, in -> Histogram.readAsDynamic(layout, in));

      } else {
        skippedVersions += 1;
      }
    }

    assertEquals(validSerialVersions.length, skippedVersions);
  }

  private static void assertException(byte version, SerializationReader<Histogram> reader) {
    assertThrows(
            IOException.class,
            () ->
                SerializationTestUtil.testReading(
                    reader, SerializationTestUtil.byteArrayToHexString(new byte[] {version})))
        .getCause();
  }
}
