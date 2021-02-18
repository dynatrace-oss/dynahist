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
package com.dynatrace.dynahist;

import static org.junit.Assert.*;
import static org.junit.Assert.assertThrows;

import com.dynatrace.dynahist.bin.Bin;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.*;
import com.dynatrace.dynahist.quantile.SciPyQuantileEstimator;
import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import com.dynatrace.dynahist.value.ValueEstimator;
import java.io.DataInput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.Test;

public abstract class AbstractHistogramTest {

  protected abstract Histogram create(Layout layout);

  protected abstract Histogram read(Layout layout, DataInput dataInput) throws IOException;

  protected abstract Histogram addValues(Histogram histogram, double... values);

  @Test
  public final void testToString() {
    final Layout layout = new TestLayout(-100, 100);
    Histogram histogram = create(layout);
    assertEquals(
        histogram.getClass().getSimpleName()
            + " [layout="
            + layout
            + ", underFlowCount=0, overFlowCount=0, totalCount=0, min=Infinity, max=-Infinity, counts={}]",
        histogram.toString());
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());

    histogram = addValues(histogram, 0);
    assertEquals(
        histogram.getClass().getSimpleName()
            + " [layout="
            + layout
            + ", underFlowCount=0, overFlowCount=0, totalCount=1, min=0.0, max=0.0, counts={0: 1}]",
        histogram.toString());
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());

    histogram = addValues(histogram, 1);
    assertEquals(
        histogram.getClass().getSimpleName()
            + " [layout="
            + layout
            + ", underFlowCount=0, overFlowCount=0, totalCount=2, min=0.0, max=1.0, counts={0: 1, 1: 1}]",
        histogram.toString());
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  protected void testSerialization(Layout layout, Histogram histogram) {
    try {
      Histogram deserializedHistogram =
          SerializationTestUtil.testSerialization(
              histogram, Histogram::write, in -> read(layout, in));
      assertEquals(histogram, deserializedHistogram);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      Histogram deserializedHistogram =
          SerializationTestUtil.testSerialization(
              histogram, Histogram::write, in -> Histogram.readAsStatic(layout, in));
      assertEquals(histogram, deserializedHistogram);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      Histogram deserializedHistogram =
          SerializationTestUtil.testSerialization(
              histogram, Histogram::write, in -> Histogram.readAsDynamic(layout, in));
      assertEquals(histogram, deserializedHistogram);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  public final void testHashCode() {
    final Layout layout = new TestLayout(-10, 10);

    {
      Histogram histogram = create(layout);
      assertEquals(2115170828, histogram.hashCode());
    }
    {
      Histogram histogram = create(layout);
      histogram = addValues(histogram, 1);
      assertEquals(-987848916, histogram.hashCode());
    }
    {
      Histogram histogram = create(layout);
      histogram = addValues(histogram, 1, 2);
      assertEquals(-2115795891, histogram.hashCode());
    }
    {
      Histogram histogram = create(layout);
      histogram = addValues(histogram, -1, -2, 1, 2);
      assertEquals(-235792952, histogram.hashCode());
    }
    {
      Histogram histogram = create(layout);
      histogram = addValues(histogram, -3 - 2, -1, -2, 1, 2, 3);
      assertEquals(-299804540, histogram.hashCode());
    }
  }

  @Test
  public final void testSerializationOfEmptyHistogram() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    testSerialization(layout, histogram);
  }

  @Test
  public final void testSameEquals() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    assertTrue(histogram.equals(histogram));
  }

  @Test
  public final void testGetValue() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    histogram = addValues(histogram, 2, 2, 2, 2, 2);
    assertEquals(2, histogram.getValue(0), 0d);
    assertEquals(2, histogram.getValue(0, ValueEstimator.LOWER_BOUND), 0d);
    assertEquals(2, histogram.getValue(0, ValueEstimator.UPPER_BOUND), 0d);
    assertEquals(2, histogram.getValue(0, ValueEstimator.MID_POINT), 0d);
    assertEquals(2, histogram.getValue(0, ValueEstimator.UNIFORM), 0d);
  }

  @Test
  public final void testGetQuantile() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    histogram = addValues(histogram, 2, 2, 2, 2, 2);
    assertEquals(2, histogram.getQuantile(0), 0d);
    assertEquals(2, histogram.getQuantile(0, ValueEstimator.LOWER_BOUND), 0d);
    assertEquals(2, histogram.getQuantile(0, ValueEstimator.UPPER_BOUND), 0d);
    assertEquals(2, histogram.getQuantile(0, ValueEstimator.MID_POINT), 0d);
    assertEquals(2, histogram.getQuantile(0, ValueEstimator.UNIFORM), 0d);
    assertEquals(2, histogram.getQuantile(0, SciPyQuantileEstimator.create()), 0d);
    assertEquals(
        2,
        histogram.getQuantile(0, SciPyQuantileEstimator.create(), ValueEstimator.LOWER_BOUND),
        0d);
    assertEquals(
        2,
        histogram.getQuantile(0, SciPyQuantileEstimator.create(), ValueEstimator.UPPER_BOUND),
        0d);
    assertEquals(
        2, histogram.getQuantile(0, SciPyQuantileEstimator.create(), ValueEstimator.MID_POINT), 0d);
    assertEquals(
        2, histogram.getQuantile(0, SciPyQuantileEstimator.create(), ValueEstimator.UNIFORM), 0d);

    assertEquals(2, histogram.getQuantile(0.5), 0d);
    assertEquals(2, histogram.getQuantile(0.5, ValueEstimator.LOWER_BOUND), 0d);
    assertEquals(2, histogram.getQuantile(0.5, ValueEstimator.UPPER_BOUND), 0d);
    assertEquals(2, histogram.getQuantile(0.5, ValueEstimator.MID_POINT), 0d);
    assertEquals(2, histogram.getQuantile(0.5, ValueEstimator.UNIFORM), 0d);
    assertEquals(2, histogram.getQuantile(0.5, SciPyQuantileEstimator.create()), 0d);
    assertEquals(
        2,
        histogram.getQuantile(0.5, SciPyQuantileEstimator.create(), ValueEstimator.LOWER_BOUND),
        0d);
    assertEquals(
        2,
        histogram.getQuantile(0.5, SciPyQuantileEstimator.create(), ValueEstimator.UPPER_BOUND),
        0d);
    assertEquals(
        2,
        histogram.getQuantile(0.5, SciPyQuantileEstimator.create(), ValueEstimator.MID_POINT),
        0d);
    assertEquals(
        2, histogram.getQuantile(0.5, SciPyQuantileEstimator.create(), ValueEstimator.UNIFORM), 0d);

    assertEquals(2, histogram.getQuantile(1.), 0d);
    assertEquals(2, histogram.getQuantile(1., ValueEstimator.LOWER_BOUND), 0d);
    assertEquals(2, histogram.getQuantile(1., ValueEstimator.UPPER_BOUND), 0d);
    assertEquals(2, histogram.getQuantile(1., ValueEstimator.MID_POINT), 0d);
    assertEquals(2, histogram.getQuantile(1., ValueEstimator.UNIFORM), 0d);
    assertEquals(2, histogram.getQuantile(1., SciPyQuantileEstimator.create()), 0d);
    assertEquals(
        2,
        histogram.getQuantile(1., SciPyQuantileEstimator.create(), ValueEstimator.LOWER_BOUND),
        0d);
    assertEquals(
        2,
        histogram.getQuantile(1., SciPyQuantileEstimator.create(), ValueEstimator.UPPER_BOUND),
        0d);
    assertEquals(
        2,
        histogram.getQuantile(1., SciPyQuantileEstimator.create(), ValueEstimator.MID_POINT),
        0d);
    assertEquals(
        2, histogram.getQuantile(1., SciPyQuantileEstimator.create(), ValueEstimator.UNIFORM), 0d);
  }

  @Test
  public void testNonEmptyBinsAscendingIteratorForNonEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    histogram = addValues(histogram, -3, -3, -3, -3, -1, -1, 3, 3, 3, 3, 3);
    List<Bin> bins = new ArrayList<>();
    for (Bin bin : histogram.nonEmptyBinsAscending()) {
      bins.add(bin);
    }
    assertEquals(3, bins.size());

    assertEquals(4, bins.get(0).getBinCount());
    assertEquals(7, bins.get(0).getGreaterCount());
    assertEquals(0, bins.get(0).getLessCount());
    assertEquals(1, bins.get(0).getBinIndex());
    assertEquals(-3., bins.get(0).getLowerBound(), 0d);
    assertEquals(Math.nextDown(-2.), bins.get(0).getUpperBound(), 0d);

    assertEquals(2, bins.get(1).getBinCount());
    assertEquals(5, bins.get(1).getGreaterCount());
    assertEquals(4, bins.get(1).getLessCount());
    assertEquals(2, bins.get(1).getBinIndex());
    assertEquals(-2., bins.get(1).getLowerBound(), 0d);
    assertEquals(-0., bins.get(1).getUpperBound(), 0d);

    assertEquals(5, bins.get(2).getBinCount());
    assertEquals(0, bins.get(2).getGreaterCount());
    assertEquals(6, bins.get(2).getLessCount());
    assertEquals(4, bins.get(2).getBinIndex());
    assertEquals(2., bins.get(2).getLowerBound(), 0d);
    assertEquals(3., bins.get(2).getUpperBound(), 0d);
  }

  @Test
  public void testNonEmptyBinsAscendingForEachForNonEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    histogram = addValues(histogram, -3, -3, -3, -3, -1, -1, 3, 3, 3, 3, 3);
    List<Bin> bins = new ArrayList<>();
    histogram.nonEmptyBinsAscending().forEach(bins::add);

    assertEquals(3, bins.size());

    assertEquals(4, bins.get(0).getBinCount());
    assertEquals(7, bins.get(0).getGreaterCount());
    assertEquals(0, bins.get(0).getLessCount());
    assertEquals(1, bins.get(0).getBinIndex());
    assertEquals(-3., bins.get(0).getLowerBound(), 0d);
    assertEquals(Math.nextDown(-2.), bins.get(0).getUpperBound(), 0d);

    assertEquals(2, bins.get(1).getBinCount());
    assertEquals(5, bins.get(1).getGreaterCount());
    assertEquals(4, bins.get(1).getLessCount());
    assertEquals(2, bins.get(1).getBinIndex());
    assertEquals(-2., bins.get(1).getLowerBound(), 0d);
    assertEquals(-0., bins.get(1).getUpperBound(), 0d);

    assertEquals(5, bins.get(2).getBinCount());
    assertEquals(0, bins.get(2).getGreaterCount());
    assertEquals(6, bins.get(2).getLessCount());
    assertEquals(4, bins.get(2).getBinIndex());
    assertEquals(2., bins.get(2).getLowerBound(), 0d);
    assertEquals(3., bins.get(2).getUpperBound(), 0d);
  }

  @Test
  public void testNonEmptyBinsDescendingIteratorForNonEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    histogram = addValues(histogram, -3, -3, -3, -3, -1, -1, 3, 3, 3, 3, 3);
    List<Bin> bins = new ArrayList<>();
    for (Bin bin : histogram.nonEmptyBinsDescending()) {
      bins.add(bin);
    }
    assertEquals(3, bins.size());

    assertEquals(5, bins.get(0).getBinCount());
    assertEquals(0, bins.get(0).getGreaterCount());
    assertEquals(6, bins.get(0).getLessCount());
    assertEquals(4, bins.get(0).getBinIndex());
    assertEquals(2., bins.get(0).getLowerBound(), 0d);
    assertEquals(3., bins.get(0).getUpperBound(), 0d);

    assertEquals(2, bins.get(1).getBinCount());
    assertEquals(5, bins.get(1).getGreaterCount());
    assertEquals(4, bins.get(1).getLessCount());
    assertEquals(2, bins.get(1).getBinIndex());
    assertEquals(-2., bins.get(1).getLowerBound(), 0d);
    assertEquals(-0., bins.get(1).getUpperBound(), 0d);

    assertEquals(4, bins.get(2).getBinCount());
    assertEquals(7, bins.get(2).getGreaterCount());
    assertEquals(0, bins.get(2).getLessCount());
    assertEquals(1, bins.get(2).getBinIndex());
    assertEquals(-3., bins.get(2).getLowerBound(), 0d);
    assertEquals(Math.nextDown(-2.), bins.get(2).getUpperBound(), 0d);
  }

  @Test
  public void testNonEmptyBinsDescendingForEachForNonEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    histogram = addValues(histogram, -3, -3, -3, -3, -1, -1, 3, 3, 3, 3, 3);
    List<Bin> bins = new ArrayList<>();
    histogram.nonEmptyBinsDescending().forEach(bins::add);

    assertEquals(3, bins.size());

    assertEquals(5, bins.get(0).getBinCount());
    assertEquals(0, bins.get(0).getGreaterCount());
    assertEquals(6, bins.get(0).getLessCount());
    assertEquals(4, bins.get(0).getBinIndex());
    assertEquals(2., bins.get(0).getLowerBound(), 0d);
    assertEquals(3., bins.get(0).getUpperBound(), 0d);

    assertEquals(2, bins.get(1).getBinCount());
    assertEquals(5, bins.get(1).getGreaterCount());
    assertEquals(4, bins.get(1).getLessCount());
    assertEquals(2, bins.get(1).getBinIndex());
    assertEquals(-2., bins.get(1).getLowerBound(), 0d);
    assertEquals(-0., bins.get(1).getUpperBound(), 0d);

    assertEquals(4, bins.get(2).getBinCount());
    assertEquals(7, bins.get(2).getGreaterCount());
    assertEquals(0, bins.get(2).getLessCount());
    assertEquals(1, bins.get(2).getBinIndex());
    assertEquals(-3., bins.get(2).getLowerBound(), 0d);
    assertEquals(Math.nextDown(-2.), bins.get(2).getUpperBound(), 0d);
  }

  @Test
  public void testNonEmptyBinsAscendingIteratorForEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    List<Bin> bins = new ArrayList<>();
    for (Bin bin : histogram.nonEmptyBinsAscending()) {
      bins.add(bin);
    }
    assertTrue(bins.isEmpty());
  }

  @Test
  public void testNonEmptyBinsAscendingForEachForEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    List<Bin> bins = new ArrayList<>();
    histogram.nonEmptyBinsAscending().forEach(bins::add);
    assertTrue(bins.isEmpty());
  }

  @Test
  public void testNonEmptyBinsDescendingIteratorForEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    List<Bin> bins = new ArrayList<>();
    for (Bin bin : histogram.nonEmptyBinsDescending()) {
      bins.add(bin);
    }
    assertTrue(bins.isEmpty());
  }

  @Test
  public void testNonEmptyBinsDescendingForEachForEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    List<Bin> bins = new ArrayList<>();
    histogram.nonEmptyBinsDescending().forEach(bins::add);
    assertTrue(bins.isEmpty());
  }

  @Test
  public void testBinIteratorNextForLastBin() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    histogram = addValues(histogram, 5);
    BinIterator iterator = histogram.getLastNonEmptyBin();
    assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  public void testBinIteratorPreviousForFirstBin() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    histogram = addValues(histogram, 5);
    BinIterator iterator = histogram.getFirstNonEmptyBin();
    assertThrows(NoSuchElementException.class, iterator::previous);
  }

  @Test
  public void testGetBinIteratorForEmptyHistogram() {
    Histogram histogram = create(CustomLayout.create(-4, -2, 0, 2, 4));
    assertThrows(NoSuchElementException.class, histogram::getFirstNonEmptyBin);
    assertThrows(NoSuchElementException.class, histogram::getLastNonEmptyBin);
  }
}
