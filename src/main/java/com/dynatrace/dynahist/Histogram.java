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

import com.dynatrace.dynahist.layout.ErrorLimitingLayout1;
import com.dynatrace.dynahist.layout.ErrorLimitingLayout2;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.quantile.QuantileEstimator;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.LongToDoubleFunction;

public interface Histogram {

  /**
   * Returns the underlying {@link Layout} of the histogram.
   *
   * @return the underlying {@link Layout} of the histogram
   */
  Layout getLayout();

  /**
   * Returns a {@link BinIterator} representing the first non-empty bin.
   *
   * <p>Must not be called if the histogram is empty.
   *
   * @return a {@link BinIterator} representing the first non-empty bin.
   */
  BinIterator getFirstNonEmptyBin();

  /**
   * Returns a {@link BinIterator} representing the last non-empty bin.
   *
   * <p>Must not be called if the histogram is empty.
   *
   * @return a {@link BinIterator} representing last non-empty bin.
   */
  BinIterator getLastNonEmptyBin();

  /**
   * Returns a bin iterator, representing the bin containing the value with given rank (0-based)
   *
   * <p>The runtime of this method may be O(N) where N is the number of bins. Therefore, if this
   * function is called many times, it is recommended to transform the histogram using {@link
   * #getPreprocessedCopy()} into a @link {@link PreprocessedHistogram} first (which is an O(N)
   * operation), whose implementation has a worst case complexity of O(log N).
   *
   * @param rank, must be greater than or equal to 0 and smaller than {@link #getTotalCount()}
   * @return bin iterator, representing the bin containing the value with given order (0-based)
   */
  BinIterator getBinByRank(long rank);

  /**
   * Returns the number of added values greater than {@link Layout#getNormalRangeUpperBound()}.
   *
   * @return the number of added values greater than {@link Layout#getNormalRangeUpperBound()}
   */
  default long getOverflowCount() {
    if (!isEmpty()) {
      BinIterator it = getLastNonEmptyBin();
      if (it.isOverflowBin()) {
        return it.getBinCount();
      }
    }
    return 0;
  }

  /**
   * Returns the number of added values less than {@link Layout#getNormalRangeLowerBound()}.
   *
   * @return the number of added values less than {@link Layout#getNormalRangeLowerBound()}
   */
  default long getUnderflowCount() {
    if (!isEmpty()) {
      BinIterator it = getFirstNonEmptyBin();
      if (it.isUnderflowBin()) {
        return it.getBinCount();
      }
    }
    return 0;
  }

  /**
   * Returns the total number of added values.
   *
   * @return the total number of added values
   */
  long getTotalCount();

  /**
   * Returns the minimum of all added values.
   *
   * @return the minimum of all added values
   */
  double getMin();

  /**
   * Returns the maximum of all added values.
   *
   * @return the maximum of all added values
   */
  double getMax();

  /**
   * Returns the number of values added to histogram bin with given index.
   *
   * @param binIndex the histogram bin index
   * @return the number of values added to histogram bin with given index
   */
  long getCount(int binIndex);

  /**
   * Returns {@code true} if this histogram is empty.
   *
   * @return {@code true} if this histogram is empty
   */
  boolean isEmpty();

  /**
   * Returns an estimation for the value with given (0-based) rank.
   *
   * <p>The values within a histogram bin are assumed to be uniformly distributed. If the bin count
   * is N, the interval spanned by the bin is divided into N subintervals. The values are
   * approximated by the midpoints of these intervals. In case the bin is the first or the last
   * non-empty bin, the minimum and the maximum given by {@link #getMin()} and {@link #getMax()} are
   * incorporated, respectively. It is guaranteed that the estimated values returned by this
   * function are never less than {@link #getMin()} or greater than {@link #getMax()}. Furthermore,
   * the estimated values will map to the same bin again, if the mapping defined by the layout of
   * this histogram is. Therefore, starting from an empty histogram with the same layout and adding
   * all estimated values once will result in an equal copy of the histogram.
   *
   * <p>Example: If rank is equal to 1, an approximation for the second smallest value will be
   * returned.
   *
   * <p>The runtime of this method may be O(N) where N is the number of bins. Therefore, if this
   * function is called many times, it is recommended to transform the histogram using {@link
   * #getPreprocessedCopy()} into a @link {@link PreprocessedHistogram} first (which is an O(N)
   * operation), whose implementation has a worst case complexity of O(log N).
   *
   * @param rank the 0-based rank, must be nonnegative and less than {@link #getTotalCount()}
   * @return an approximation for the value with given rank
   */
  double getValueEstimate(long rank);

  /**
   * Returns an estimate for the quantile value using the estimated values as given by {@link
   * #getValueEstimate(long)} and the given {@link QuantileEstimator}.
   *
   * <p>The runtime of this method may be O(N) where N is the number of bins. Therefore, if this
   * function is called many times, it is recommended to transform the histogram using {@link
   * #getPreprocessedCopy()} into a @link {@link PreprocessedHistogram} first (which is an O(N)
   * operation), whose implementation has a worst case complexity of O(log N).
   *
   * @param p the p-value in range [0,1]
   * @param quantileEstimator the quantile estimator
   * @return an estimate for the p-quantile
   */
  double getQuantileEstimate(double p, QuantileEstimator quantileEstimator);

  /**
   * Returns an estimate for the quantile value using the estimated values as given by {@link
   * #getValueEstimate(long)} and the default quantile estimation method. The default behavior might
   * change in future. Therefore, if well-defined behavior is wanted, use {@link
   * #getQuantileEstimate(double, QuantileEstimator)}.
   *
   * <p>The runtime of this method may be O(N) where N is the number of bins. Therefore, if this
   * function is called many times, it is recommended to transform the histogram using {@link
   * #getPreprocessedCopy()} into a @link {@link PreprocessedHistogram} first (which is an O(N)
   * operation), whose implementation has a worst case complexity of O(log N).
   *
   * @param p the p-value in range [0,1]
   * @return an estimate for the p-quantile
   */
  double getQuantileEstimate(double p);

  /**
   * Returns an estimate for the quantile value using the estimated values as given by {@link
   * #getValueEstimate(long)} using the default quantile estimator.
   *
   * <p>Preprocessing is recommended, if many calls of {@link #getBinByRank(long)} or {@link
   * #getValueEstimate(long)} are expected.
   *
   * @return an immutable copy of this histogram
   */
  Histogram getPreprocessedCopy();

  /**
   * Adds a given value to the histogram.
   *
   * <p>
   *
   * @param value the value to be added to the histogram
   * @return a reference to this
   * @throws IllegalArgumentException if value is equal to {@link Double#NaN}
   * @throws ArithmeticException if the total count of the histogram would overflow
   * @throws UnsupportedOperationException if modifications are not supported
   */
  Histogram addValue(double value);

  /**
   * Adds a given value to the histogram with a given multiplicity.
   *
   * <p>
   *
   * @param value the value to be added to the histogram
   * @param count
   * @return a reference to this
   * @throws IllegalArgumentException if value is equal to {@link Double#NaN} or count is negative
   * @throws ArithmeticException if the total count of the histogram would overflow
   * @throws UnsupportedOperationException if modifications are not supported
   */
  Histogram addValue(double value, long count);

  /**
   * Adds a given histogram to the histogram.
   *
   * <p>If the given histogram has a different layout than this histogram, this operation may lead
   * to unwanted loss of precision. In this case the operation is equivalent to adding all estimated
   * values as obtained by {@link #getValueEstimate(long)}.
   *
   * <p>In particular the error limits of {@link ErrorLimitingLayout1} or {@link
   * ErrorLimitingLayout2} might be violated.
   *
   * @param histogram
   * @return a reference to this
   * @throws ArithmeticException if the total count of the histogram would overflow
   * @throws UnsupportedOperationException if modifications are not supported
   */
  Histogram addHistogram(Histogram histogram);

  /**
   * Adds an ordered sequence to the histogram.
   *
   * <p>The function {@code orderedSequence} must be defined for
   *
   * @param orderedSequence
   * @param length
   * @return a reference to this
   * @throws UnsupportedOperationException if modifications are not supported
   */
  Histogram addOrderedSequence(LongToDoubleFunction orderedSequence, long length);

  /**
   * Writes this histogram to a given {@link DataOutput}.
   *
   * <p>The {@link Layout} information will not be written. Therefore, it is necessary to provide
   * the layout when reading using {@link #readAsDynamic(Layout, DataInput)} or {@link
   * #readAsStatic(Layout, DataInput)}.
   *
   * @param dataOutput
   * @throws IOException
   */
  void write(DataOutput dataOutput) throws IOException;

  /**
   * Provide an estimate of the histogram's total footprint in bytes
   *
   * @return estimate of the histogram's total footprint in bytes
   */
  long getEstimatedFootprintInBytes();

  /**
   * Creates an empty {@link Histogram} that allocates internal arrays for bin counts dynamically.
   *
   * <p>Choose this, if memory efficiency is more important than speed.
   *
   * @param layout the {@link Layout} of the histogram
   * @return an empty {@link Histogram}
   */
  static Histogram createDynamic(Layout layout) {
    return new DynamicHistogram(layout);
  }

  /**
   * Creates an empty {@link Histogram} that allocates internal arrays for bin counts statically.
   *
   * <p>Choose this, if speed is more efficient than memory efficiency.
   *
   * @param layout the {@link Layout} of the histogram
   * @return an empty {@link Histogram}
   */
  static Histogram createStatic(Layout layout) {
    return new StaticHistogram(layout);
  }

  /**
   * Reads a histogram from a given {@link DataInput}.
   *
   * <p>The returned histogram will allocate internal arrays for bin counts dynamically. The
   * behavior is undefined if the given layout does not match the layout before serialization.
   *
   * @param layout
   * @param dataInput
   * @return the deserialized histogram
   * @throws IOException
   */
  static Histogram readAsDynamic(Layout layout, DataInput dataInput) throws IOException {
    return DynamicHistogram.read(layout, dataInput);
  }

  /**
   * Reads a histogram from a given {@link DataInput}.
   *
   * <p>The returned histogram will allocate that allocate internal arrays for bin counts
   * statically. The behavior is undefined if the given layout does not match the layout before
   * serialization.
   *
   * @param layout
   * @param dataInput
   * @return the deserialized histogram
   * @throws IOException
   */
  static Histogram readAsStatic(Layout layout, DataInput dataInput) throws IOException {
    return StaticHistogram.read(layout, dataInput);
  }

  /**
   * Reads a histogram from a given {@link DataInput}.
   *
   * <p>The returned histogram will be immutable and preprocessed in order to support fast queries.
   * The behavior is undefined if the given layout does not match the layout before serialization.
   *
   * @param layout
   * @param dataInput
   * @return the deserialized histogram
   * @throws IOException
   */
  static Histogram readAsPreprocessed(Layout layout, DataInput dataInput) throws IOException {
    return DynamicHistogram.read(layout, dataInput).getPreprocessedCopy();
  }
}
