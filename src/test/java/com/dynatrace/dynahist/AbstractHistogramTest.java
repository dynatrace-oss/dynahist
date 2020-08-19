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

import com.dynatrace.dynahist.layout.ErrorLimitingLayout2;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.TestLayout;
import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import java.io.DataInput;
import java.io.IOException;
import java.io.UncheckedIOException;
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
              histogram, (h, out) -> h.write(out), in -> read(layout, in));
      assertEquals(histogram, deserializedHistogram);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      Histogram deserializedHistogram =
          SerializationTestUtil.testSerialization(
              histogram, (h, out) -> h.write(out), in -> Histogram.readAsStatic(layout, in));
      assertEquals(histogram, deserializedHistogram);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      Histogram deserializedHistogram =
          SerializationTestUtil.testSerialization(
              histogram, (h, out) -> h.write(out), in -> Histogram.readAsDynamic(layout, in));
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
    Layout layout = ErrorLimitingLayout2.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram preprocessedHistogram = create(layout);
    testSerialization(layout, preprocessedHistogram);
  }
}
