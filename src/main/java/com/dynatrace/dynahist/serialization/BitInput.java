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

import static java.util.Objects.requireNonNull;

import java.io.DataInput;
import java.io.IOException;

public class BitInput {

  private final DataInput dataInput;

  private long buffer;
  private int bitCount;

  public BitInput(DataInput dataInput) {
    this.dataInput = requireNonNull(dataInput);
  }

  public long readBits(int numBits) throws IOException {
    if (numBits == 0) return 0;
    long result = 0;
    if (bitCount < numBits) {
      result = buffer;
      numBits -= bitCount;
      while (numBits > 8) {
        result <<= 8;
        result |= dataInput.readUnsignedByte();
        numBits -= 8;
      }
      result <<= numBits;
      buffer = dataInput.readUnsignedByte();
      bitCount = 8;
    }
    bitCount -= numBits;
    result |= buffer >>> bitCount;
    buffer &= (1L << bitCount) - 1;
    return result;
  }
}
