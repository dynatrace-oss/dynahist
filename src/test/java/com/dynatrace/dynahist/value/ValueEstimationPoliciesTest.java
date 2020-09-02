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
package com.dynatrace.dynahist.value;

import static org.junit.Assert.assertEquals;

import com.dynatrace.dynahist.bin.Bin;
import org.junit.Test;

public class ValueEstimationPoliciesTest {

  private static final Bin BIN1 =
      new Bin() {

        @Override
        public boolean isUnderflowBin() {
          return false;
        }

        @Override
        public boolean isOverflowBin() {
          return false;
        }

        @Override
        public double getUpperBound() {
          return 8;
        }

        @Override
        public double getLowerBound() {
          return 3;
        }

        @Override
        public long getLessCount() {
          return 10;
        }

        @Override
        public long getGreaterCount() {
          return 20;
        }

        @Override
        public int getBinIndex() {
          return 77;
        }

        @Override
        public long getBinCount() {
          return 5;
        }
      };

  private static final Bin BIN2 =
      new Bin() {

        @Override
        public boolean isUnderflowBin() {
          return false;
        }

        @Override
        public boolean isOverflowBin() {
          return false;
        }

        @Override
        public double getUpperBound() {
          return 8;
        }

        @Override
        public double getLowerBound() {
          return 3;
        }

        @Override
        public long getLessCount() {
          return 0;
        }

        @Override
        public long getGreaterCount() {
          return 30;
        }

        @Override
        public int getBinIndex() {
          return 77;
        }

        @Override
        public long getBinCount() {
          return 5;
        }
      };

  private static final Bin BIN3 =
      new Bin() {

        @Override
        public boolean isUnderflowBin() {
          return false;
        }

        @Override
        public boolean isOverflowBin() {
          return false;
        }

        @Override
        public double getUpperBound() {
          return 8;
        }

        @Override
        public double getLowerBound() {
          return 3;
        }

        @Override
        public long getLessCount() {
          return 30;
        }

        @Override
        public long getGreaterCount() {
          return 0;
        }

        @Override
        public int getBinIndex() {
          return 77;
        }

        @Override
        public long getBinCount() {
          return 5;
        }
      };

  private static final Bin BIN4 =
      new Bin() {

        @Override
        public boolean isUnderflowBin() {
          return false;
        }

        @Override
        public boolean isOverflowBin() {
          return false;
        }

        @Override
        public double getUpperBound() {
          return 8;
        }

        @Override
        public double getLowerBound() {
          return 3;
        }

        @Override
        public long getLessCount() {
          return 0;
        }

        @Override
        public long getGreaterCount() {
          return 0;
        }

        @Override
        public int getBinIndex() {
          return 77;
        }

        @Override
        public long getBinCount() {
          return 5;
        }
      };

  @Test
  public void testLowerBoundValueEstimationPolicy() {
    assertEquals(
        BIN1.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN1, 10), 0d);
    assertEquals(
        BIN1.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN1, 11), 0d);
    assertEquals(
        BIN1.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN1, 12), 0d);
    assertEquals(
        BIN1.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN1, 13), 0d);
    assertEquals(
        BIN1.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN1, 14), 0d);

    assertEquals(
        BIN2.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN2, 1), 0d);
    assertEquals(
        BIN2.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN2, 2), 0d);
    assertEquals(
        BIN2.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN2, 3), 0d);
    assertEquals(
        BIN2.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN2, 4), 0d);

    assertEquals(
        BIN3.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN3, 30), 0d);
    assertEquals(
        BIN3.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN3, 31), 0d);
    assertEquals(
        BIN3.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN3, 32), 0d);
    assertEquals(
        BIN3.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN3, 33), 0d);

    assertEquals(
        BIN4.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN4, 1), 0d);
    assertEquals(
        BIN4.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN4, 2), 0d);
    assertEquals(
        BIN4.getLowerBound(), ValueEstimatorImpls.LOWER_BOUND.getSampleFromBin(BIN4, 3), 0d);
  }

  @Test
  public void testUpperBoundValueEstimationPolicy() {
    assertEquals(
        BIN1.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN1, 10), 0d);
    assertEquals(
        BIN1.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN1, 11), 0d);
    assertEquals(
        BIN1.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN1, 12), 0d);
    assertEquals(
        BIN1.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN1, 13), 0d);
    assertEquals(
        BIN1.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN1, 14), 0d);

    assertEquals(
        BIN2.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN2, 1), 0d);
    assertEquals(
        BIN2.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN2, 2), 0d);
    assertEquals(
        BIN2.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN2, 3), 0d);
    assertEquals(
        BIN2.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN2, 4), 0d);

    assertEquals(
        BIN3.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN3, 30), 0d);
    assertEquals(
        BIN3.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN3, 31), 0d);
    assertEquals(
        BIN3.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN3, 32), 0d);
    assertEquals(
        BIN3.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN3, 33), 0d);

    assertEquals(
        BIN4.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN4, 1), 0d);
    assertEquals(
        BIN4.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN4, 2), 0d);
    assertEquals(
        BIN4.getUpperBound(), ValueEstimatorImpls.UPPER_BOUND.getSampleFromBin(BIN4, 3), 0d);
  }

  @Test
  public void testMidPointValueEstimationPolicy() {
    assertEquals(
        0.5 * (BIN1.getLowerBound() + BIN1.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN1, 10),
        0d);
    assertEquals(
        0.5 * (BIN1.getLowerBound() + BIN1.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN1, 11),
        0d);
    assertEquals(
        0.5 * (BIN1.getLowerBound() + BIN1.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN1, 12),
        0d);
    assertEquals(
        0.5 * (BIN1.getLowerBound() + BIN1.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN1, 13),
        0d);
    assertEquals(
        0.5 * (BIN1.getLowerBound() + BIN1.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN1, 14),
        0d);

    assertEquals(
        0.5 * (BIN2.getLowerBound() + BIN2.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN2, 1),
        0d);
    assertEquals(
        0.5 * (BIN2.getLowerBound() + BIN2.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN2, 2),
        0d);
    assertEquals(
        0.5 * (BIN2.getLowerBound() + BIN2.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN2, 3),
        0d);
    assertEquals(
        0.5 * (BIN2.getLowerBound() + BIN2.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN2, 4),
        0d);

    assertEquals(
        0.5 * (BIN3.getLowerBound() + BIN3.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN3, 30),
        0d);
    assertEquals(
        0.5 * (BIN3.getLowerBound() + BIN3.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN3, 31),
        0d);
    assertEquals(
        0.5 * (BIN3.getLowerBound() + BIN3.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN3, 32),
        0d);
    assertEquals(
        0.5 * (BIN3.getLowerBound() + BIN3.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN3, 33),
        0d);

    assertEquals(
        0.5 * (BIN4.getLowerBound() + BIN4.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN4, 1),
        0d);
    assertEquals(
        0.5 * (BIN4.getLowerBound() + BIN4.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN4, 2),
        0d);
    assertEquals(
        0.5 * (BIN4.getLowerBound() + BIN4.getUpperBound()),
        ValueEstimatorImpls.MID_POINT.getSampleFromBin(BIN4, 3),
        0d);
  }

  @Test
  public void testUniformValueEstimationPolicy() {
    assertEquals(3.5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN1, 10), 0d);
    assertEquals(4.5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN1, 11), 0d);
    assertEquals(5.5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN1, 12), 0d);
    assertEquals(6.5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN1, 13), 0d);
    assertEquals(7.5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN1, 14), 0d);

    assertEquals(3 + 2. / 9 * 5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN2, 1), 0d);
    assertEquals(3 + 4. / 9 * 5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN2, 2), 0d);
    assertEquals(3 + 6. / 9 * 5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN2, 3), 1e-9);
    assertEquals(3 + 8. / 9 * 5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN2, 4), 0d);

    assertEquals(3 + 1. / 9 * 5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN3, 30), 0d);
    assertEquals(3 + 3. / 9 * 5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN3, 31), 0d);
    assertEquals(3 + 5. / 9 * 5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN3, 32), 0d);
    assertEquals(3 + 7. / 9 * 5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN3, 33), 0d);

    assertEquals(4.25, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN4, 1), 1e-3);
    assertEquals(5.5, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN4, 2), 1e-3);
    assertEquals(6.75, ValueEstimatorImpls.UNIFORM.getSampleFromBin(BIN4, 3), 1e-3);
  }
}
