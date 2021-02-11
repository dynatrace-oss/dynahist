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

import com.dynatrace.dynahist.layout.Layout;

/** A bin of the histogram. */
public interface Bin {

  /**
   * Returns the number of values belonging to the current bin position.
   *
   * @return the number of values belonging to the current bin position
   */
  long getBinCount();

  /**
   * Returns a lower bound for all values in this bin.
   *
   * <p>The returned value is always greater than or equal to the minimum value of the underlying
   * histogram.
   *
   * @return a lower bound for all values in this bin
   */
  double getLowerBound();

  /**
   * Returns an upper bound for all values in this bin.
   *
   * <p>The returned value is always less than or equal to the maximum value of the underlying
   * histogram.
   *
   * @return an upper bound for all values in this bin
   */
  double getUpperBound();

  /**
   * Returns the width of the current bin.
   *
   * @return the width of the bin
   */
  default double getWidth() {
    return getUpperBound() - getLowerBound();
  }

  /**
   * Returns the number of values less than the lower bound of the the current bin.
   *
   * @return the number of values less than the lower bound of the the current bin
   */
  long getLessCount();

  /**
   * Returns the number of values greater than the upper bound of the the current bin.
   *
   * @return the number of values greater than the upper bound of the the current bin
   */
  long getGreaterCount();

  /**
   * Returns the bin index as defined by the {@link Layout}.
   *
   * @return the bin index as defined by the {@link Layout}
   */
  int getBinIndex();

  /**
   * Returns {@code true} if this bin corresponds to the first non-empty bin.
   *
   * @return {@code true} if this bin corresponds to the first non-empty bin
   */
  default boolean isFirstNonEmptyBin() {
    return getLessCount() == 0;
  }

  /**
   * Returns {@code true} if this bin corresponds to the last non-empty bin.
   *
   * @return {@code true} if this bin corresponds to the last non-empty bin
   */
  default boolean isLastNonEmptyBin() {
    return getGreaterCount() == 0;
  }

  /**
   * Returns {@code true} if this bin corresponds to the underflow bin.
   *
   * @return {@code true} if this bin corresponds to the underflow bin
   */
  boolean isUnderflowBin();

  /**
   * Returns {@code true} if this bin corresponds to the overflow bin.
   *
   * @return {@code true} if this bin corresponds to the overflow bin
   */
  boolean isOverflowBin();
}
