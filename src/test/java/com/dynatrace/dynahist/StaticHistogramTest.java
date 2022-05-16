/*
 * Copyright 2020-2022 Dynatrace LLC
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

import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import java.io.DataInput;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class StaticHistogramTest extends AbstractHistogramTest {

  @Override
  protected Histogram create(final Layout layout) {
    return Histogram.createStatic(layout);
  }

  @Override
  protected Histogram read(Layout layout, DataInput dataInput) throws IOException {
    return Histogram.readAsStatic(layout, dataInput);
  }

  @Test
  void testGetEstimatedFootprintInByte() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createStatic(layout);
    assertEquals(49772, histogram.getEstimatedFootprintInBytes());
  }

  @Override
  void testAddHistogramEqualLayout() {
    super.testAddHistogramEqualLayout();
  }

  @Test
  void testStaticHistogramConstructor() {
    Layout layout =
        new Layout() {
          @Override
          public int mapToBinIndex(double value) {
            return 0;
          }

          @Override
          public int getUnderflowBinIndex() {
            return 1;
          }

          @Override
          public int getOverflowBinIndex() {
            return -1;
          }
        };
    assertThrows(IllegalArgumentException.class, () -> Histogram.createStatic(layout));
  }
}
