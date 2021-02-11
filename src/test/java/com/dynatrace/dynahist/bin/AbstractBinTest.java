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
package com.dynatrace.dynahist.bin;

import static org.junit.Assert.assertEquals;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.TestLayout;
import org.junit.Test;

public class AbstractBinTest {

  @Test
  public void testToString() {

    long lessCount = 2343;
    long greaterCount = 42304;
    long binCount = 423489324;
    int binIndex = 434;

    Layout layout = new TestLayout(-5, 7);
    Histogram histogram = Histogram.createDynamic(layout);

    Bin bin =
        new AbstractBin() {

          @Override
          public long getLessCount() {
            return lessCount;
          }

          @Override
          public long getGreaterCount() {
            return greaterCount;
          }

          @Override
          public int getBinIndex() {
            return binIndex;
          }

          @Override
          public long getBinCount() {
            return binCount;
          }

          @Override
          protected Histogram getHistogram() {
            return histogram;
          }
        };

    assertEquals(
        "Bin [binIndex=434, lowerBound=Infinity, upperBound=-Infinity, binCount=423489324, lessCount=2343, greaterCount=42304, isUnderflowBin=false, isOverflowBin=false]",
        bin.toString());
  }
}
