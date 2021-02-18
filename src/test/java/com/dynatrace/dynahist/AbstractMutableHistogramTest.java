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

import static com.dynatrace.dynahist.serialization.SerializationTestUtil.byteArrayToHexString;
import static com.dynatrace.dynahist.serialization.SerializationTestUtil.toByteArray;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.CustomLayout;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import com.dynatrace.dynahist.layout.TestLayout;
import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import com.dynatrace.dynahist.serialization.SerializationUtil;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import org.junit.Test;

public abstract class AbstractMutableHistogramTest extends AbstractHistogramTest {

  @Override
  protected Histogram addValues(Histogram histogram, double... values) {
    if (values != null) {
      for (double x : values) {
        histogram.addValue(x);
      }
    }
    return histogram;
  }

  @Test
  public void testCountOverflow() {
    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = create(layout);
    histogram.addValue(10., Long.MAX_VALUE);
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
    assertThrows(ArithmeticException.class, () -> histogram.addValue(90.));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  public void testNaN() {
    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = create(layout);
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
    assertThrows(IllegalArgumentException.class, () -> histogram.addValue(Double.NaN));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  public void testPositiveInfinity() {
    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = create(layout);
    histogram.addValue(Double.POSITIVE_INFINITY);
    assertEquals(1, histogram.getTotalCount());
    assertEquals(Double.POSITIVE_INFINITY, histogram.getMin(), 0.);
    assertEquals(Double.POSITIVE_INFINITY, histogram.getMax(), 0.);
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  public void testNegativeInfinity() {
    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = create(layout);
    histogram.addValue(Double.NEGATIVE_INFINITY);
    assertEquals(1, histogram.getTotalCount());
    assertEquals(Double.NEGATIVE_INFINITY, histogram.getMin(), 0.);
    assertEquals(Double.NEGATIVE_INFINITY, histogram.getMax(), 0.);
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  public void testGetBinByRank() {

    final long K = 57;
    final long Z = 5;

    final Layout layout = LogQuadraticLayout.create(1., 0., 0., K);

    final Histogram histogram = create(layout);

    for (long k = 0; k < K; ++k) {
      histogram.addValue(k, Z);
    }

    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());

    for (int k = 0; k < Z * K; ++k) {
      final BinIterator bin = histogram.getBinByRank(k);
      assertEquals(Z, bin.getBinCount());
      assertEquals((k / Z) * Z, bin.getLessCount());
      assertEquals(Z * K - (k / Z) * Z - Z, bin.getGreaterCount());
    }
  }

  @Test
  public void testSingleValueHistogramNormal() {
    final Histogram histogram = create(new TestLayout(-100, 100)).addValue(5);
    assertEquals(1, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  public void testSingleValueHistogramUnderflow() {
    final Histogram histogram = create(new TestLayout(-100, 100)).addValue(1000);
    assertEquals(1, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  public void testSingleValueHistogramOverflow() {
    final Histogram histogram = create(new TestLayout(-100, 100)).addValue(-1000);
    assertEquals(1, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  public void testTwoValuesHistogramUnderflowAndOverflow() {
    final Histogram histogram = create(new TestLayout(-100, 100)).addValue(-1000).addValue(1000);
    assertEquals(2, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  public void testGeneral() {

    final int N = 10_000;

    final Random random = new Random(0);

    for (int i = 0; i < N; ++i) {

      final Layout layout = new TestLayout(-1000, 1000);

      final Histogram histogram = create(layout);

      final int N_Data = random.nextInt(50);

      final long countBase = 1L << random.nextInt(33);

      long totalCount = 0L;
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (int j = 0; j < N_Data; ++j) {
        final long count = countBase + random.nextInt(10);
        final double value = random.nextDouble() * 200. - 100.;
        histogram.addValue(value, count);
        totalCount += count;
        min = Math.min(min, value);
        max = Math.max(max, value);
      }

      // verify total count, min, and max
      assertEquals(totalCount, histogram.getTotalCount());
      assertEquals(min, histogram.getMin(), 0.0);
      assertEquals(max, histogram.getMax(), 0.0);
      if (totalCount > 0) {
        assertEquals(min, histogram.getValue(0), 0.0);
        assertEquals(max, histogram.getValue(totalCount - 1), 0.0);
      }
      HistogramTestUtil.checkHistogramDataConsistency(histogram);
      HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
      testSerialization(layout, histogram);
    }
  }

  @Test
  public void testGetPreprocessedData() {
    Histogram histogram = create(new TestLayout(-100, 100));
    histogram.addValue(-101, 3);
    histogram.addValue(-53, 2);
    histogram.addValue(3, 4);
    histogram.addValue(106, 1);
    Histogram preprocessedHistogram = histogram.getPreprocessedCopy();

    assertEquals(histogram, preprocessedHistogram);

    HistogramTestUtil.checkHistogramDataConsistency(preprocessedHistogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram);

    assertEquals(106, preprocessedHistogram.getMax(), 0);
    assertEquals(-101, preprocessedHistogram.getMin(), 0);
    assertEquals(10, preprocessedHistogram.getTotalCount());

    assertEquals(-100, preprocessedHistogram.getBinByRank(0).getBinIndex());
    assertEquals(0, preprocessedHistogram.getBinByRank(0).getLessCount());
    assertEquals(3, preprocessedHistogram.getBinByRank(0).getBinCount());
    assertEquals(7, preprocessedHistogram.getBinByRank(0).getGreaterCount());

    assertEquals(-100, preprocessedHistogram.getFirstNonEmptyBin().getBinIndex());
    assertEquals(0, preprocessedHistogram.getFirstNonEmptyBin().getLessCount());
    assertEquals(3, preprocessedHistogram.getFirstNonEmptyBin().getBinCount());
    assertEquals(7, preprocessedHistogram.getFirstNonEmptyBin().getGreaterCount());

    assertEquals(-100, preprocessedHistogram.getBinByRank(1).getBinIndex());
    assertEquals(0, preprocessedHistogram.getBinByRank(1).getLessCount());
    assertEquals(3, preprocessedHistogram.getBinByRank(1).getBinCount());
    assertEquals(7, preprocessedHistogram.getBinByRank(1).getGreaterCount());

    assertEquals(-100, preprocessedHistogram.getBinByRank(2).getBinIndex());
    assertEquals(0, preprocessedHistogram.getBinByRank(2).getLessCount());
    assertEquals(3, preprocessedHistogram.getBinByRank(2).getBinCount());
    assertEquals(7, preprocessedHistogram.getBinByRank(2).getGreaterCount());

    assertEquals(-53, preprocessedHistogram.getBinByRank(3).getBinIndex());
    assertEquals(3, preprocessedHistogram.getBinByRank(3).getLessCount());
    assertEquals(2, preprocessedHistogram.getBinByRank(3).getBinCount());
    assertEquals(5, preprocessedHistogram.getBinByRank(3).getGreaterCount());

    assertEquals(-53, preprocessedHistogram.getBinByRank(4).getBinIndex());
    assertEquals(3, preprocessedHistogram.getBinByRank(4).getLessCount());
    assertEquals(2, preprocessedHistogram.getBinByRank(4).getBinCount());
    assertEquals(5, preprocessedHistogram.getBinByRank(4).getGreaterCount());

    assertEquals(3, preprocessedHistogram.getBinByRank(5).getBinIndex());
    assertEquals(5, preprocessedHistogram.getBinByRank(5).getLessCount());
    assertEquals(4, preprocessedHistogram.getBinByRank(5).getBinCount());
    assertEquals(1, preprocessedHistogram.getBinByRank(5).getGreaterCount());

    assertEquals(3, preprocessedHistogram.getBinByRank(6).getBinIndex());
    assertEquals(5, preprocessedHistogram.getBinByRank(6).getLessCount());
    assertEquals(4, preprocessedHistogram.getBinByRank(6).getBinCount());
    assertEquals(1, preprocessedHistogram.getBinByRank(6).getGreaterCount());

    assertEquals(3, preprocessedHistogram.getBinByRank(7).getBinIndex());
    assertEquals(5, preprocessedHistogram.getBinByRank(7).getLessCount());
    assertEquals(4, preprocessedHistogram.getBinByRank(7).getBinCount());
    assertEquals(1, preprocessedHistogram.getBinByRank(7).getGreaterCount());

    assertEquals(3, preprocessedHistogram.getBinByRank(8).getBinIndex());
    assertEquals(5, preprocessedHistogram.getBinByRank(8).getLessCount());
    assertEquals(4, preprocessedHistogram.getBinByRank(8).getBinCount());
    assertEquals(1, preprocessedHistogram.getBinByRank(8).getGreaterCount());

    assertEquals(100, preprocessedHistogram.getBinByRank(9).getBinIndex());
    assertEquals(9, preprocessedHistogram.getBinByRank(9).getLessCount());
    assertEquals(1, preprocessedHistogram.getBinByRank(9).getBinCount());
    assertEquals(0, preprocessedHistogram.getBinByRank(9).getGreaterCount());

    assertEquals(100, preprocessedHistogram.getLastNonEmptyBin().getBinIndex());
    assertEquals(9, preprocessedHistogram.getLastNonEmptyBin().getLessCount());
    assertEquals(1, preprocessedHistogram.getLastNonEmptyBin().getBinCount());
    assertEquals(0, preprocessedHistogram.getLastNonEmptyBin().getGreaterCount());
  }

  @Test
  public void testAddAscendingSequence() {

    TestLayout layout = new TestLayout(-5, 5);

    int numCycles = 10000;
    Random rnd = new Random(0);

    for (int i = 0; i < numCycles; ++i) {
      double[] values = rnd.doubles(rnd.nextInt(100)).map(d -> d * 12 - 6).toArray();
      Arrays.sort(values);

      Histogram histogram1 = create(layout);
      Histogram histogram2 = create(layout);

      for (double v : values) {
        histogram1.addValue(v);
      }
      histogram2.addAscendingSequence(j -> values[(int) j], values.length);

      assertEquals(histogram1, histogram2);
      assertEquals(histogram1.getPreprocessedCopy(), histogram2.getPreprocessedCopy());
    }
  }

  @Test
  public void testAddAscendingSequenceUniqueValue() {

    TestLayout layout = new TestLayout(-5, 5);

    double[] values = {Double.NEGATIVE_INFINITY, -5.5, -0.1, 5.3, Double.POSITIVE_INFINITY};
    for (double value : values) {
      Histogram histogram1 = create(layout);
      Histogram histogram2 = create(layout);
      histogram1.addValue(value, Long.MAX_VALUE);
      histogram2.addAscendingSequence(j -> value, Long.MAX_VALUE);
      assertEquals(histogram1, histogram2);
    }
  }

  @Test
  public void testAddAscendingSequenceInvalidLength() {
    TestLayout layout = new TestLayout(-5, 5);
    Histogram histogram = create(layout);

    double[] values = {Double.NEGATIVE_INFINITY, -5.5, -0.1, 5.3, Double.POSITIVE_INFINITY};
    assertThrows(
        IllegalArgumentException.class,
        () -> histogram.addAscendingSequence(j -> values[(int) j], -1));
    histogram.addValue(1, Long.MAX_VALUE);
    assertThrows(
        ArithmeticException.class,
        () -> histogram.addAscendingSequence(j -> values[(int) j], Long.MAX_VALUE));
  }

  @Test
  public void testHistogram() {

    TestLayout layout = new TestLayout(-2, 2);
    Histogram histogram = create(layout);

    histogram.addValue(Double.NEGATIVE_INFINITY);
    histogram.addValue(-2, 3);
    histogram.addValue(-1, 4);

    histogram.addValue(0, 1);
    histogram.addValue(1, 3);
    histogram.addValue(2, 2);

    assertEquals(14, histogram.getTotalCount());
    assertEquals(Double.NEGATIVE_INFINITY, histogram.getValue(0), 0d);
    assertEquals(Double.NEGATIVE_INFINITY, histogram.getValue(1), 0d);
    assertEquals(Double.NEGATIVE_INFINITY, histogram.getValue(2), 0d);
    assertEquals(Double.NEGATIVE_INFINITY, histogram.getValue(3), 0d);
    assertEquals(-1.3749999999999998, histogram.getValue(4), 0d);
    assertEquals(-1.5 + 3. / 8., histogram.getValue(5), 0d);
    assertEquals(-0.8749999999999998, histogram.getValue(6), 0d);
    assertEquals(-1.5 + 7. / 8., histogram.getValue(7), 0d);
    assertEquals(0, histogram.getValue(8), 0d);
    assertEquals(0.6666666666666665, histogram.getValue(9), 0d);
    assertEquals(0.9999999999999998, histogram.getValue(10), 0d);
    assertEquals(4. / 3, histogram.getValue(11), 1e-14);
    assertEquals(1.6666666666666667, histogram.getValue(12), 0d);
    assertEquals(2, histogram.getValue(13), 0d);

    testSerialization(layout, histogram);
  }

  @Test
  public void testAddHistogramEqualLayout() {

    final int cycles = 1000;

    final Random random = new Random(0);

    final Layout layout = new TestLayout(-100, 100);

    for (int cycleCounter = 0; cycleCounter < cycles; ++cycleCounter) {

      final int numValues1 = random.nextInt(1000);
      final int numValues2 = random.nextInt(1000);

      final Histogram histogram1 = create(layout);
      final Histogram histogram2 = create(layout);
      final Histogram histogramTotal = create(layout);

      for (int i = 0; i < numValues1; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        histogram1.addValue(value);
        histogramTotal.addValue(value);
      }

      for (int i = 0; i < numValues2; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        histogram2.addValue(value);
        histogramTotal.addValue(value);
      }

      final Histogram histogramMerged = histogram1.addHistogram(histogram2);

      assertEquals(histogramTotal, histogramMerged);
      assertEquals(histogramTotal.hashCode(), histogramMerged.hashCode());
    }
  }

  @Test
  public void testAddHistogramNonEqualLayout() {

    final int cycles = 1000;

    final Random random = new Random(0);

    final Layout layout1 = new TestLayout(-100, 100);
    final Layout layout2 = new TestLayout(-100, 101);
    final Layout layoutTotal = layout1;

    for (int cycleCounter = 0; cycleCounter < cycles; ++cycleCounter) {

      final int numValues1 = random.nextInt(1000);
      final int numValues2 = random.nextInt(1000);

      final Histogram histogram1 = create(layout1);
      final Histogram histogram2 = create(layout2);
      final Histogram histogramTotal = create(layoutTotal);

      for (int i = 0; i < numValues1; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        histogram1.addValue(value);
        histogramTotal.addValue(value);
      }

      for (int i = 0; i < numValues2; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        histogram2.addValue(value);
        histogramTotal.addValue(value);
      }

      final Histogram histogramMerged = histogram1.addHistogram(histogram2);

      assertEquals(histogramTotal, histogramMerged);
      assertEquals(histogramTotal.hashCode(), histogramMerged.hashCode());
    }
  }

  @Test
  public void testAddEmptyHistogram() {

    final int cycles = 100;

    final Random random = new Random(0);

    final Layout layout = new TestLayout(-100, 100);

    for (int cycleCounter = 0; cycleCounter < cycles; ++cycleCounter) {

      final int numValues = random.nextInt(10);

      final Histogram histogram1 = create(layout);
      final Histogram histogram2 = create(layout);

      for (int i = 0; i < numValues; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        histogram1.addValue(value);
        histogram2.addValue(value);
      }

      histogram2.addHistogram(create(layout));

      assertEquals(histogram1, histogram2);
      assertEquals(histogram1.hashCode(), histogram2.hashCode());
    }
  }

  @Test
  public void testAddNegativeCount() {

    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = create(layout);

    assertThrows(IllegalArgumentException.class, () -> histogram.addValue(2.4, -1));
  }

  @Test
  public void testAddZeroCount() {

    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = create(layout);

    histogram.addValue(2.4, 0);

    assertTrue(histogram.isEmpty());

    testSerialization(layout, histogram);
  }

  @Test
  public void testQuantileOfSingleValue() {

    double value = 5.4;

    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = create(layout);

    histogram.addValue(value);

    assertEquals(value, histogram.getQuantile(0), 0d);
    assertEquals(value, histogram.getQuantile(0.2), 0d);
    assertEquals(value, histogram.getQuantile(0.5), 0d);
    assertEquals(value, histogram.getQuantile(0.7), 0d);
    assertEquals(value, histogram.getQuantile(1), 0d);

    testSerialization(layout, histogram);
  }

  @Test
  public void testNonEmptyBins() {

    final int K = 1000;

    final Layout layout = LogQuadraticLayout.create(1., 0., 0., K);

    final int numCycles = 100;

    final Random random = new Random(0);

    for (int i = 0; i < numCycles; ++i) {

      final Histogram histogram = create(layout);

      long count = 0;
      int nonEmptyBinsCount = 0;

      for (int k = 0; k < K; ++k) {
        if (random.nextBoolean()) {
          final int n = random.nextInt(1_000_000);

          nonEmptyBinsCount += 1;
          count += n;
          histogram.addValue(k, n);
        }
      }

      assertEquals(count, histogram.getTotalCount());
      assertEquals(nonEmptyBinsCount, HistogramTestUtil.numberOfNonEmptyBins(histogram));
      testSerialization(layout, histogram);
    }
  }

  @Test
  public void testEmptyHistogram() {
    final Layout layout = LogQuadraticLayout.create(1e-3, 0., 0., 10.);
    final Histogram histogram = create(layout);
    assertEquals(0, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    testSerialization(layout, histogram);
  }

  @Test
  public void testSerialization1() {
    Random rnd = new Random(0);
    int numCycles = 10000;

    for (int cycle = 0; cycle < numCycles; ++cycle) {

      final Layout layout = new TestLayout(-10 + rnd.nextInt(10), rnd.nextInt(10));
      final Histogram histogram = create(layout);

      int numValues = rnd.nextInt(20);

      for (int i = 0; i < numValues; ++i) {
        histogram.addValue(-11 + rnd.nextInt(24));
      }
      testSerialization(layout, histogram);
    }
  }

  @Test
  public void testSerialization2() {
    Random rnd = new Random(0);
    int numCycles = 10000;

    for (int cycle = 0; cycle < numCycles; ++cycle) {

      final Layout layout = new TestLayout(-10 + rnd.nextInt(10), rnd.nextInt(10));
      final Histogram histogram = create(layout);

      int numValues = rnd.nextInt(20);

      for (int i = 0; i < numValues; ++i) {
        histogram.addValue(-11 + rnd.nextInt(24), 1L << rnd.nextInt(34));
      }
      testSerialization(layout, histogram);
    }
  }

  @Test
  public void testMinimalLayout() {
    Layout layout = new TestLayout(-1, 0);

    final Histogram histogram = create(layout);

    histogram.addValue(1000);
    histogram.addValue(-1000);

    testSerialization(layout, histogram);

    assertEquals(-1000, histogram.getMin(), 0d);
    assertEquals(1000, histogram.getMax(), 0d);

    assertEquals(-1000, histogram.getValue(0), 0d);
    assertEquals(1000, histogram.getValue(1), 0d);

    assertEquals(-1000, histogram.getQuantile(0), 0d);
    assertEquals(1000, histogram.getQuantile(1), 0d);
    assertEquals(0, histogram.getQuantile(0.5), 0d);
  }

  @Test
  public void testVerySmallEffectiveBin() {
    Layout layout = new TestLayout(-100, 100);

    double[] xValues = {-12143.43, -12, 34.535, 21314234};
    long c1 = 432;
    long c2 = 331;

    for (double min : xValues) {
      double max = Math.nextUp(min);
      final Histogram histogram = create(layout);
      histogram.addValue(min, c1);
      histogram.addValue(max, c2);
      assertEquals(c1 + c2, histogram.getTotalCount());
      double previous = histogram.getValue(0);
      assertThat(previous).isGreaterThanOrEqualTo(min);
      for (long i = 1; i < c1 + c2; ++i) {
        double current = histogram.getValue(i);
        assertThat(previous).isLessThanOrEqualTo(current);
        previous = current;
      }
      assertThat(previous).isLessThanOrEqualTo(max);
    }
  }

  @Test
  public void testNegativeZero() {
    Layout layout = new TestLayout(-1, 1);

    {
      final Histogram histogram = create(layout);
      histogram.addValue(0d);
      histogram.addValue(-0d);
      assertEquals(Double.doubleToRawLongBits(-0d), Double.doubleToRawLongBits(histogram.getMin()));
      assertEquals(Double.doubleToRawLongBits(0d), Double.doubleToRawLongBits(histogram.getMax()));
    }
    {
      final Histogram histogram = create(layout);
      histogram.addValue(-0d);
      histogram.addValue(0d);
      assertEquals(Double.doubleToRawLongBits(-0d), Double.doubleToRawLongBits(histogram.getMin()));
      assertEquals(Double.doubleToRawLongBits(0), Double.doubleToRawLongBits(histogram.getMax()));
    }
    {
      final Histogram histogram = create(layout);
      histogram.addValue(0d);
      histogram.addValue(0d);
      assertEquals(Double.doubleToRawLongBits(0d), Double.doubleToRawLongBits(histogram.getMin()));
      assertEquals(Double.doubleToRawLongBits(0d), Double.doubleToRawLongBits(histogram.getMax()));
    }
    {
      final Histogram histogram = create(layout);
      histogram.addValue(-0d);
      histogram.addValue(-0d);
      assertEquals(Double.doubleToRawLongBits(-0d), Double.doubleToRawLongBits(histogram.getMin()));
      assertEquals(Double.doubleToRawLongBits(-0d), Double.doubleToRawLongBits(histogram.getMax()));
    }
  }

  @Test
  public void testGetEstimatedFootprintInByte() {}

  @Test
  public void testAddHistogramFirstNonEmptyBinEqualsLastNonEmptyBin() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram1 = create(layout);
    Histogram histogram2 = create(layout);
    Histogram totalHistogram = create(layout);
    histogram1.addValue(5);
    totalHistogram.addValue(5);
    totalHistogram.addValue(-5);
    totalHistogram.addValue(5.5, 5);
    histogram1.addValue(-5);
    histogram2.addValue(5.5, 5);
    assertEquals(totalHistogram, histogram1.addHistogram(histogram2));
  }

  @Test
  public void testAddHistogramOverflow() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram1 = create(layout);
    Histogram histogram2 = create(layout);
    histogram1.addValue(5, 1000000);
    histogram2.addValue(5, Long.MAX_VALUE);

    assertThrows(ArithmeticException.class, () -> histogram1.addHistogram(histogram2));
  }

  @Test
  public void testDeserializeInvalidSerialVersion() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(new byte[] {1}));
    assertThrows(IOException.class, () -> Histogram.readAsDynamic(layout, dataInputStream));
  }

  @Test
  public void testGetValueEstimateInvalidOrder() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    histogram.addValue(5);

    assertThrows(IllegalArgumentException.class, () -> histogram.getValue(-1));
    assertThrows(IllegalArgumentException.class, () -> histogram.getValue(1));
  }

  @Test
  public void testGetBinByRankInvalidOrder() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    histogram.addValue(5);

    assertThrows(IllegalArgumentException.class, () -> histogram.getBinByRank(-1));
    assertThrows(IllegalArgumentException.class, () -> histogram.getBinByRank(1));
  }

  @Test
  public void testEquals() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    Histogram otherHistogram = create(layout);

    assertFalse(histogram.equals(null));
    assertNotEquals(histogram, create(LogLinearLayout.create(1e-8, 1e-2, -1e5, 1e5)));
    histogram.addValue(1e4);
    assertNotEquals(histogram, otherHistogram);
    otherHistogram.addValue(-1e7 * 2);
    assertNotEquals(histogram, otherHistogram);
    histogram.addValue(-1e7);
    otherHistogram.addValue(1e7);
    assertNotEquals(histogram, otherHistogram);
    histogram.addValue(1e7 * 2);
    otherHistogram.addValue(1e6);
    assertNotEquals(histogram, otherHistogram);
    histogram.addValue(-1e7 * 2);
    otherHistogram.addValue(-1e7);
    assertNotEquals(histogram, otherHistogram);
    otherHistogram.addValue(1e7 * 2);
    histogram.addValue(1e2);
    histogram.addValue(1e7);
    otherHistogram.addValue(1e4);
    assertNotEquals(histogram, otherHistogram);
    histogram.addValue(1e2);
    otherHistogram.addValue(1e2);
    assertNotEquals(histogram, otherHistogram);
  }

  @Test
  public void testTotalCountOverflow() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    histogram.addValue(1, Long.MAX_VALUE);
    assertThrows(ArithmeticException.class, () -> histogram.addValue(2));
  }

  private static void testAddHistogramHelper(
      Function<Layout, Histogram> histogramFactory1,
      Function<Layout, Histogram> histogramFactory2) {

    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);

    SplittableRandom random = new SplittableRandom(0);
    long numValues1 = 1000;
    long numValues2 = 2000;

    Histogram histogram1 = histogramFactory1.apply(layout);
    Histogram histogram2 = histogramFactory2.apply(layout);

    Histogram histogramTotal = histogramFactory1.apply(layout);

    DoubleStream.generate(random::nextDouble)
        .limit(numValues1)
        .forEach(
            x -> {
              histogram1.addValue(x);
              histogramTotal.addValue(x);
            });

    DoubleStream.generate(random::nextDouble)
        .limit(numValues2)
        .forEach(
            x -> {
              histogram2.addValue(x);
              histogramTotal.addValue(x);
            });

    histogram1.addHistogram(histogram2);

    assertEquals(histogramTotal, histogram1);
  }

  @Test
  public void testAddHistogram() {
    testAddHistogramHelper(this::create, Histogram::createDynamic);
    testAddHistogramHelper(this::create, Histogram::createStatic);
  }

  @Test
  public void testAddHistogramWithStatic() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);

    SplittableRandom random = new SplittableRandom(0);
    long numValues1 = 1000;
    long numValues2 = 2000;

    Histogram histogram1 = create(layout);

    Histogram histogram2 = Histogram.createDynamic(layout);
    Histogram histogramTotal = create(layout);

    DoubleStream.generate(random::nextDouble)
        .limit(numValues1)
        .forEach(
            x -> {
              histogram1.addValue(x);
              histogramTotal.addValue(x);
            });

    DoubleStream.generate(random::nextDouble)
        .limit(numValues2)
        .forEach(
            x -> {
              histogram2.addValue(x);
              histogramTotal.addValue(x);
            });

    histogram1.addHistogram(histogram2);

    assertEquals(histogramTotal, histogram1);
  }

  @Test
  public void testAddHistogramWithUnderAndOverFlowCountsOnly() {
    Layout layout = new TestLayout(-1, 1);

    Histogram histogram1 = create(layout);
    Histogram histogram2 = Histogram.createDynamic(layout);
    Histogram histogramTotal = create(layout);

    histogram1.addValue(-2, 1000L);
    histogramTotal.addValue(-2, 1000L);
    histogram1.addValue(2, 2000L);
    histogramTotal.addValue(2, 2000L);

    histogram2.addValue(-2, 4000L);
    histogramTotal.addValue(-2, 4000L);
    histogram2.addValue(2, 8000L);
    histogramTotal.addValue(2, 8000L);

    histogram1.addHistogram(histogram2);

    assertEquals(histogramTotal, histogram1);
  }

  @Test
  public void testIsMutable() {
    Layout layout = new TestLayout(-1, 1);
    Histogram histogram = create(layout);
    assertTrue(histogram.isMutable());
  }

  @Test
  public final void testDeserializationUsingWrongLayout() throws IOException {

    List<Layout> layouts =
        Arrays.asList(
            LogLinearLayout.create(1e-1, 1e-1, -5, 5),
            LogQuadraticLayout.create(1e-1, 1e-1, -5, 5),
            LogLinearLayout.create(1.1e-1, 1e-1, -5, 5),
            LogQuadraticLayout.create(1.1e-1, 1e-1, -5, 5),
            LogLinearLayout.create(1e-1, 1.1e-1, -5, 5),
            LogQuadraticLayout.create(1e-1, 1.1e-1, -5, 5),
            CustomLayout.create(-2, 4, 5),
            CustomLayout.create(-2),
            CustomLayout.create(1));

    long numIterations = 10000;

    SplittableRandom random = new SplittableRandom(0);

    for (int i = 0; i < numIterations; ++i) {
      for (Layout writeLayout : layouts) {
        for (Layout readLayout : layouts) {
          Histogram histogram = create(writeLayout);
          long numValues = random.nextLong(100);
          for (long j = 0; j < numValues; ++j) {
            histogram.addValue(random.nextDouble(-6, 6));
          }

          Histogram deserializedHistogram =
              SerializationTestUtil.testSerialization(
                  histogram, Histogram::write, in -> read(readLayout, in));

          assertEquals(histogram.getTotalCount(), deserializedHistogram.getTotalCount());
          assertEquals(histogram.getMin(), deserializedHistogram.getMin(), 0d);
          assertEquals(histogram.getMax(), deserializedHistogram.getMax(), 0d);
        }
      }
    }
  }

  @Test
  public final void testDeserializationSpecial() throws IOException {

    double min = -100;
    double max = 120;

    int minRegularIdx = -30;
    int maxRegularIdx = 40;
    long underflowCount = 2000;
    long overflowCount = 1000;
    long totalCount = 0;
    totalCount += underflowCount;
    totalCount += overflowCount;

    StringBuilder sb = new StringBuilder();
    sb.append("00"); // serial version
    sb.append("FF"); // info byte
    sb.append(byteArrayToHexString(toByteArray((v, d) -> d.writeDouble(v), min))); // minimum
    sb.append(byteArrayToHexString(toByteArray((v, d) -> d.writeDouble(v), max))); // maximum
    sb.append(
        byteArrayToHexString(
            toByteArray(
                SerializationUtil::writeUnsignedVarLong, underflowCount))); // underflow count
    sb.append(
        byteArrayToHexString(
            toByteArray(SerializationUtil::writeUnsignedVarLong, overflowCount))); // overflow count

    sb.append(
        byteArrayToHexString(
            toByteArray(SerializationUtil::writeSignedVarInt, minRegularIdx))); // regular min index
    sb.append(
        byteArrayToHexString(
            toByteArray(SerializationUtil::writeSignedVarInt, maxRegularIdx))); // regular max index

    for (int idx = minRegularIdx; idx <= maxRegularIdx; ++idx) {
      sb.append(byteArrayToHexString(toByteArray((i, d) -> d.writeLong(i), 1L)));
      totalCount += 1;
    }

    totalCount += 4; // for min and max and first and last regular bin

    Layout layout = new TestLayout(-2, 2);

    Histogram histogram = SerializationTestUtil.testReading(in -> read(layout, in), sb.toString());

    assertEquals(totalCount, histogram.getTotalCount());
    assertEquals(min, histogram.getMin(), 0d);
    assertEquals(max, histogram.getMax(), 0d);
  }
}
