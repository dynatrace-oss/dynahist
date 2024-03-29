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
package com.dynatrace.dynahist.bin;

import java.util.NoSuchElementException;

/**
 * A bin iterator over a histogram.
 *
 * <p>Allows iterating over all non-empty bins in ascending or descending order. The behavior of the
 * iterator is undefined, if the underlying histogram is modified at the same time.
 */
public interface BinIterator extends Bin {

  /**
   * Advances to the next non-empty bin.
   *
   * <p>Throws a {@link NoSuchElementException}, if bin iterator represents the last non-empty bin,
   * that is if {@link #isLastNonEmptyBin()} returns {@code true}.
   *
   * @throws NoSuchElementException if this bin is the last non-empty bin
   */
  void next();

  /**
   * Advances to the last non-empty bin.
   *
   * <p>Throws a {@link NoSuchElementException}, if bin iterator represents the first non-empty bin,
   * that is if {@link #isFirstNonEmptyBin()} returns {@code true}.
   *
   * @throws NoSuchElementException if this bin is the first non-empty bin
   */
  void previous();

  /**
   * Creates a new {@link Bin} instance representing the current position of this bin iterator
   *
   * <p>The returned bin is immutable with respect to this bin iterator. However, the behavior of
   * the returned bin will be undefined, if the underlying histogram gets modified.
   *
   * @deprecated Use {@link #copy()} instead.
   * @return a new bin instance
   */
  @Deprecated
  Bin getBinCopy();

  /**
   * Creates a copy of this {@link BinIterator} instance
   *
   * <p>The returned copy is immutable with respect to this bin iterator. However, the behavior of
   * the returned bin iterator will be undefined, if the underlying histogram gets modified.
   *
   * @return a new bin iterator instance
   */
  BinIterator copy();
}
