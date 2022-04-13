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
package com.dynatrace.dynahist.serialization;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public class BitOutput {

  private final DataOutput dataOutput;

  private long buffer;
  private int bitCount;

  public BitOutput(DataOutput dataOutput) {
    this.dataOutput = Objects.requireNonNull(dataOutput);
  }

  public void writeBits(int numBits, long value) throws IOException {
    if (numBits == 0) return;
    buffer |= (value << (-numBits)) >>> bitCount;
    bitCount += numBits;
    if (bitCount >= 64) {
      dataOutput.writeLong(buffer);
      buffer = value << 1 << ~bitCount;
      bitCount -= 64;
    }
  }

  public void finish() throws IOException {
    while (bitCount > 0) {
      dataOutput.writeByte((byte) (buffer >>> 56));
      buffer <<= 8;
      bitCount -= 8;
    }
  }
}
