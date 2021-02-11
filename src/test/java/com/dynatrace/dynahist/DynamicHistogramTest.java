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
package com.dynatrace.dynahist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import java.io.DataInput;
import java.io.IOException;
import org.junit.Test;

public class DynamicHistogramTest extends AbstractMutableHistogramTest {

  @Override
  protected Histogram create(final Layout layout) {
    return Histogram.createDynamic(layout);
  }

  @Override
  protected Histogram read(Layout layout, DataInput dataInput) throws IOException {
    return Histogram.readAsDynamic(layout, dataInput);
  }

  @Override
  public void testGetEstimatedFootprintInByte() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    assertEquals(82, histogram.getEstimatedFootprintInBytes());
  }

  @Test
  public void testEnsureCountArrayArgumentChecks() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    DynamicHistogram histogram = new DynamicHistogram(layout);

    assertThrows(IllegalArgumentException.class, () -> histogram.ensureCountArray(2, -2, (byte) 3));
    assertThrows(
        IllegalArgumentException.class,
        () -> histogram.ensureCountArray(layout.getUnderflowBinIndex(), 0, (byte) 3));
    assertThrows(
        IllegalArgumentException.class,
        () -> histogram.ensureCountArray(0, layout.getOverflowBinIndex(), (byte) 3));
  }
}
