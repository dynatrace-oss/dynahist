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

import static org.junit.Assert.assertArrayEquals;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.layout.ErrorLimitingLayout2;
import com.dynatrace.dynahist.layout.Layout;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.Test;

public class SerializationWriterTest {

  @Test
  public void testToByteArray() throws IOException {
    Layout layout = ErrorLimitingLayout2.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    SerializationWriter<Histogram> serializationWriter =
        (data, dataOutput) -> histogram.write(dataOutput);
    byte[] serializedHistogram = null;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream); ) {
      histogram.write(dataOutputStream);
      serializedHistogram = byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertArrayEquals(serializedHistogram, serializationWriter.toByteArray(histogram));
  }
}
