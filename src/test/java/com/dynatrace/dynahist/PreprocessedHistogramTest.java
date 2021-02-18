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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import java.io.*;
import org.junit.Test;

public class PreprocessedHistogramTest extends AbstractHistogramTest {

  @Override
  protected Histogram create(Layout layout) {
    return Histogram.createDynamic(layout).getPreprocessedCopy();
  }

  @Override
  protected Histogram read(Layout layout, DataInput dataInput) throws IOException {
    return Histogram.readAsPreprocessed(layout, dataInput);
  }

  @Override
  protected Histogram addValues(Histogram histogram, double... values) {
    if (values == null) return histogram;

    Histogram mutableHistogram = Histogram.createStatic(histogram.getLayout());
    mutableHistogram.addHistogram(histogram);
    for (double x : values) {
      mutableHistogram.addValue(x);
    }
    return mutableHistogram.getPreprocessedCopy();
  }

  @Test
  public void testGetEstimatedFootprintInByte() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram preprocessedHistogram = Histogram.createDynamic(layout).getPreprocessedCopy();
    assertEquals(72, preprocessedHistogram.getEstimatedFootprintInBytes());
  }

  @Test
  public void testExceptions() {
    Layout layout = LogQuadraticLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    histogram.addValue(-5.5);
    Histogram preprocessedHistogram = histogram.getPreprocessedCopy();
    histogram.addValue(-4.4);
    BinIterator iterator = preprocessedHistogram.getFirstNonEmptyBin();

    assertThrows(UnsupportedOperationException.class, () -> preprocessedHistogram.addValue(-5.5));
    assertThrows(
        UnsupportedOperationException.class, () -> preprocessedHistogram.addValue(-5.5, 5));
    assertThrows(
        UnsupportedOperationException.class, () -> preprocessedHistogram.addHistogram(histogram));
    assertThrows(
        UnsupportedOperationException.class,
        () -> preprocessedHistogram.addAscendingSequence(j -> 100, 10));
    assertThrows(IllegalArgumentException.class, () -> preprocessedHistogram.getBinByRank(-1));
    assertThrows(IllegalArgumentException.class, () -> preprocessedHistogram.getBinByRank(1));
  }

  @Test
  public void testReadAsPreprocessed() throws IOException {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    histogram.addValue(-5.5);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
    histogram.write(dataOutputStream);
    byte[] serializedHistogram = byteArrayOutputStream.toByteArray();

    DataInputStream dataInputStream =
        new DataInputStream(new ByteArrayInputStream(serializedHistogram));
    Histogram deserializedHistogram = Histogram.readAsPreprocessed(layout, dataInputStream);

    assertEquals(histogram, deserializedHistogram);
    assertEquals(histogram.hashCode(), deserializedHistogram.hashCode());
  }

  @Test
  public void testIsMutable() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout).getPreprocessedCopy();
    assertFalse(histogram.isMutable());
  }
}
