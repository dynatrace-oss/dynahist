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

import static com.dynatrace.dynahist.util.Preconditions.checkArgument;

public class TestLayout implements Layout {

  private final int underflowIndex;
  private final int overflowIndex;

  public TestLayout(final int underflowIndex, final int overflowIndex) {
    checkArgument(underflowIndex < overflowIndex);
    this.underflowIndex = underflowIndex;
    this.overflowIndex = overflowIndex;
  }

  @Override
  public int mapToBinIndex(final double value) {
    if (Double.isNaN(value)) {
      return Integer.MAX_VALUE;
    }
    if (value >= 0) {
      return (int) (value + 0.5);
    } else {
      return (int) (value - 0.5);
    }
  }

  @Override
  public int getUnderflowBinIndex() {
    return underflowIndex;
  }

  @Override
  public int getOverflowBinIndex() {
    return overflowIndex;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + " [underflowIndex="
        + underflowIndex
        + ", overflowIndex="
        + overflowIndex
        + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + overflowIndex;
    result = prime * result + underflowIndex;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TestLayout other = (TestLayout) obj;
    if (overflowIndex != other.overflowIndex) {
      return false;
    }
    if (underflowIndex != other.underflowIndex) {
      return false;
    }
    return true;
  }
}
