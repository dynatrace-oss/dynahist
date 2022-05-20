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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

public class BitOutputInputTest {

  private static final int MAX_LENGTH = 200;

  private static void testRandomCase(SplittableRandom random) {
    int numBits = random.nextInt(MAX_LENGTH);

    BitSet bitSet = new BitSet(numBits);
    byte[] expected = Arrays.copyOf(bitSet.toByteArray(), (numBits + 7) / 8);
    List<Long> bitChunksTemp = new ArrayList<>();
    List<Integer> chunkLengthsTemp = new ArrayList<>();

    int bitCounter = 0;
    while (bitCounter < numBits) {
      final int chunkBitSize = random.nextInt(65);
      int chunkBitCounter = 0;
      long value = 0;
      while (bitCounter < numBits && chunkBitCounter < chunkBitSize) {
        if (bitSet.get(bitCounter)) {
          value |= (1L << chunkBitCounter);
        }
        bitCounter += 1;
        chunkBitCounter += 1;
      }
      bitChunksTemp.add(value);
      chunkLengthsTemp.add(chunkBitCounter);
    }
    long[] bitChunks = bitChunksTemp.stream().mapToLong(Long::longValue).toArray();
    int[] chunkLengths = chunkLengthsTemp.stream().mapToInt(Integer::intValue).toArray();
    testCase(expected, bitChunks, chunkLengths);
  }

  private static void testCase(byte[] expected, long[] bitChunks, int[] chunkLengths) {

    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
      BitOutput bitOutput = new BitOutput(dataOutputStream);

      for (int i = 0; i < bitChunks.length; ++i) {
        bitOutput.writeBits(chunkLengths[i], bitChunks[i]);
      }
      bitOutput.finish();
      byte[] actual = byteArrayOutputStream.toByteArray();
      assertThat(actual).isEqualTo(expected);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(expected);
        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream)) {
      BitInput bitInput = new BitInput(dataInputStream);

      for (int i = 0; i < bitChunks.length; ++i) {
        long mask;
        if (chunkLengths[i] > 0) {
          mask = 0xFFFFFFFFFFFFFFFFL >>> -chunkLengths[i];
        } else {
          mask = 0;
        }
        assertThat(bitInput.readBits(chunkLengths[i])).isEqualTo(bitChunks[i] & mask);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testRandomCases() {
    SplittableRandom random = new SplittableRandom(0);
    int numCycles = 10000;
    for (int i = 0; i < numCycles; ++i) {
      testRandomCase(random);
    }
  }

  @Test
  public void testSomeExplicitCases() {
    testCase(new byte[] {(byte) 0x00}, new long[] {0}, new int[] {1});
    testCase(new byte[] {(byte) 0x80}, new long[] {1}, new int[] {1});
    testCase(new byte[] {(byte) 0x00}, new long[] {0, 0}, new int[] {1, 1});
    testCase(new byte[] {(byte) 0x40}, new long[] {0, 1}, new int[] {1, 1});
    testCase(new byte[] {(byte) 0x80}, new long[] {1, 0}, new int[] {1, 1});
    testCase(new byte[] {(byte) 0xc0}, new long[] {1, 1}, new int[] {1, 1});
    testCase(
        new byte[] {
          (byte) 0x01,
          (byte) 0x23,
          (byte) 0x45,
          (byte) 0x67,
          (byte) 0x89,
          (byte) 0xab,
          (byte) 0xcd,
          (byte) 0xef
        },
        new long[] {0x0123456789abcdefL},
        new int[] {64});
    testCase(
        new byte[] {
          (byte) 0x01,
          (byte) 0x23,
          (byte) 0x45,
          (byte) 0x67,
          (byte) 0x89,
          (byte) 0xab,
          (byte) 0xcd,
          (byte) 0xef,
          (byte) 0xfe,
          (byte) 0xdc,
          (byte) 0xba,
          (byte) 0x98,
          (byte) 0x76,
          (byte) 0x54,
          (byte) 0x32,
          (byte) 0x10
        },
        new long[] {0x0123456789abcdefL, 0xfedcba9876543210L},
        new int[] {64, 64});
    testCase(
        new byte[] {
          (byte) 0x89,
          (byte) 0xab,
          (byte) 0xcd,
          (byte) 0xef,
          (byte) 0xfe,
          (byte) 0xdc,
          (byte) 0xba,
          (byte) 0x98,
          (byte) 0x76,
          (byte) 0x54,
          (byte) 0x32,
          (byte) 0x10
        },
        new long[] {0x0123456789abcdefL, 0xfedcba9876543210L},
        new int[] {32, 64});
    testCase(
        new byte[] {
          (byte) 0x89,
          (byte) 0xab,
          (byte) 0xcd,
          (byte) 0xef,
          (byte) 0x98,
          (byte) 0x76,
          (byte) 0x54,
          (byte) 0x32,
          (byte) 0x10
        },
        new long[] {0x0123456789abcdefL, 0xfedcba9876543210L},
        new int[] {32, 40});
    testCase(
        new byte[] {
          (byte) 0x89,
          (byte) 0xab,
          (byte) 0xcd,
          (byte) 0xef,
          (byte) 0x87,
          (byte) 0x65,
          (byte) 0x43,
          (byte) 0x21,
          (byte) 0x00
        },
        new long[] {0x0123456789abcdefL, 0xfedcba9876543210L},
        new int[] {32, 36});
  }
}
