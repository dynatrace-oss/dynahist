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
package com.dynatrace.dynahist;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AbstractBinTest {

  @Test
  public void testToString() {

    boolean isUnderFlowBin = false;
    boolean isOverFlowBin = true;
    double upperBound = 1e6;
    double lowerBound = -1e3;
    long lessCount = 2343;
    long greaterCount = 42304;
    long binCount = 423489324;
    int binIndex = 434;

    Bin bin =
        new AbstractBin() {

          @Override
          public boolean isUnderflowBin() {
            return isUnderFlowBin;
          }

          @Override
          public boolean isOverflowBin() {
            return isOverFlowBin;
          }

          @Override
          public double getUpperBound() {
            return upperBound;
          }

          @Override
          public double getLowerBound() {
            return lowerBound;
          }

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
        };

    assertEquals(
        "Bin [getBinIndex()=434, getLowerBound()=-1000.0, getUpperBound()=1000000.0, getBinCount()=423489324, getLessCount()=2343, getGreaterCount()=42304, isUnderflowBin()=false, isOverflowBin()=true]",
        bin.toString());
  }
}
