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
package com.dynatrace.dynahist;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.dynatrace.dynahist.bin.Bin;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.layout.*;
import com.dynatrace.dynahist.quantile.SciPyQuantileEstimator;
import com.dynatrace.dynahist.serialization.SerializationReader;
import com.dynatrace.dynahist.serialization.SerializationTestUtil;
import com.dynatrace.dynahist.serialization.SerializationWriter;
import com.dynatrace.dynahist.value.ValueEstimator;
import java.io.DataInput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.LongToDoubleFunction;
import java.util.stream.DoubleStream;
import org.junit.jupiter.api.Test;

public abstract class AbstractHistogramTest {

  private static final class SerializationConfig {
    private final SerializationWriter<Histogram> writer;
    private final SerializationReader<Histogram> reader;

    public SerializationConfig(
        SerializationWriter<Histogram> writer, SerializationReader<Histogram> reader) {
      this.writer = requireNonNull(writer);
      this.reader = requireNonNull(reader);
    }

    public SerializationWriter<Histogram> getWriter() {
      return writer;
    }

    public SerializationReader<Histogram> getReader() {
      return reader;
    }
  }

  private static final List<SerializationConfig> getSerializationConfigs(Layout layout) {
    return Arrays.asList(
        new SerializationConfig(Histogram::write, in -> Histogram.readAsDynamic(layout, in)),
        new SerializationConfig(Histogram::write, in -> Histogram.readAsStatic(layout, in)),
        new SerializationConfig(Histogram::write, in -> Histogram.readAsPreprocessed(layout, in)),
        new SerializationConfig(
            (h, out) -> ((AbstractHistogram) h).writeSerialVersion0(out),
            in -> Histogram.readAsDynamic(layout, in)),
        new SerializationConfig(
            (h, out) -> ((AbstractHistogram) h).writeSerialVersion0(out),
            in -> Histogram.readAsStatic(layout, in)),
        new SerializationConfig(
            (h, out) -> ((AbstractHistogram) h).writeSerialVersion0(out),
            in -> Histogram.readAsPreprocessed(layout, in)),
        new SerializationConfig(
            (h, out) -> ((AbstractHistogram) h).writeSerialVersion1(out),
            in -> Histogram.readAsDynamic(layout, in)),
        new SerializationConfig(
            (h, out) -> ((AbstractHistogram) h).writeSerialVersion1(out),
            in -> Histogram.readAsStatic(layout, in)),
        new SerializationConfig(
            (h, out) -> ((AbstractHistogram) h).writeSerialVersion1(out),
            in -> Histogram.readAsPreprocessed(layout, in)));
  }

  protected abstract Histogram create(Layout layout);

  protected abstract Histogram read(Layout layout, DataInput dataInput) throws IOException;

  protected final Histogram addValues(Histogram histogram, double... values) {
    Builder builder = modify(histogram);
    if (values != null) {
      for (double v : values) {
        builder.addValue(v);
      }
    }
    return builder.done();
  }

  protected static Builder modify(Histogram histogram) {
    return new Builder(histogram);
  }

  // histogram1 will get modified
  protected static Histogram merge(Histogram histogram1, Histogram histogram2) {
    return modify(histogram1).addHistogram(histogram2).done();
  }

  private static class Builder {

    protected final Histogram mutableHistogram;
    protected final boolean isPreprocessed;

    protected Builder(Histogram histogram) {
      this.isPreprocessed = histogram instanceof PreprocessedHistogram;
      if (this.isPreprocessed) {
        this.mutableHistogram =
            Histogram.createDynamic(histogram.getLayout()).addHistogram(histogram);
      } else {
        this.mutableHistogram = histogram;
      }
    }

    public Builder addValue(double value) {
      return addValue(value, 1);
    }

    public Builder addValue(double value, long count) {
      mutableHistogram.addValue(value, count);
      return this;
    }

    public Histogram done() {
      if (isPreprocessed) {
        return mutableHistogram.getPreprocessedCopy();
      } else {
        return mutableHistogram;
      }
    }

    public Builder addHistogram(Histogram histogram) {
      this.mutableHistogram.addHistogram(histogram);
      return this;
    }

    public Builder addAscendingSequence(LongToDoubleFunction ascendingSequence, long length) {
      mutableHistogram.addAscendingSequence(ascendingSequence, length);
      return this;
    }
  }

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
    for (SerializationConfig config : getSerializationConfigs(layout)) {
      testSerialization(histogram, config.getWriter(), config.getReader());
    }
  }

  protected void testSerialization(
      Histogram histogram,
      SerializationWriter<Histogram> writer,
      SerializationReader<Histogram> reader) {
    try {
      assertEquals(histogram, SerializationTestUtil.testSerialization(histogram, writer, reader));
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

  @Test
  void testDeserializationUsingWrongLayout() throws IOException {

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

    long numIterations = 1000;

    SplittableRandom random = new SplittableRandom(0);

    for (int i = 0; i < numIterations; ++i) {
      for (Layout writeLayout : layouts) {
        for (Layout readLayout : layouts) {
          Histogram histogram = create(writeLayout);
          long numValues = random.nextLong(100);
          double[] values =
              DoubleStream.generate(() -> random.nextDouble(-6, 6)).limit(numValues).toArray();
          histogram = addValues(histogram, values);

          for (SerializationConfig config : getSerializationConfigs(readLayout)) {
            Histogram deserializedHistogram =
                SerializationTestUtil.testSerialization(
                    histogram, config.getWriter(), config.getReader());

            assertEquals(histogram.getTotalCount(), deserializedHistogram.getTotalCount());
            assertEquals(histogram.getMin(), deserializedHistogram.getMin(), 0d);
            assertEquals(histogram.getMax(), deserializedHistogram.getMax(), 0d);
          }
        }
      }
    }
  }

  @Test
  void testNaN() {
    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = create(layout);
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    assertThrows(IllegalArgumentException.class, () -> addValues(histogram, Double.NaN));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
  }

  @Test
  void testPositiveInfinity() {
    final Layout layout = new TestLayout(-100, 100);
    Histogram histogram = create(layout);
    histogram = addValues(histogram, Double.POSITIVE_INFINITY);
    assertEquals(1, histogram.getTotalCount());
    assertEquals(Double.POSITIVE_INFINITY, histogram.getMin(), 0.);
    assertEquals(Double.POSITIVE_INFINITY, histogram.getMax(), 0.);
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
  }

  @Test
  void testNegativeInfinity() {
    final Layout layout = new TestLayout(-100, 100);
    Histogram histogram = create(layout);
    histogram = addValues(histogram, Double.NEGATIVE_INFINITY);
    assertEquals(1, histogram.getTotalCount());
    assertEquals(Double.NEGATIVE_INFINITY, histogram.getMin(), 0.);
    assertEquals(Double.NEGATIVE_INFINITY, histogram.getMax(), 0.);
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
  }

  @Test
  void testIsMutable() {
    Layout layout = new TestLayout(-1, 1);
    Histogram histogram = create(layout);
    if (histogram.isMutable()) {
      assertDoesNotThrow(() -> histogram.addValue(0));
    } else {
      assertThrows(UnsupportedOperationException.class, () -> histogram.addValue(0));
    }
  }

  @Test
  void testTwoValuesHistogramUnderflowAndOverflow() {
    final Histogram histogram = addValues(create(new TestLayout(-100, 100)), -1000, 1000);
    assertEquals(2, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  void testSingleValueHistogramNormal() {
    final Histogram histogram = addValues(create(new TestLayout(-100, 100)), 5);
    assertEquals(1, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  void testSingleValueHistogramUnderflow() {
    final Histogram histogram = addValues(create(new TestLayout(-100, 100)), 1000);
    assertEquals(1, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  void testSingleValueHistogramOverflow() {
    final Histogram histogram = addValues(create(new TestLayout(-100, 100)), -1000);
    assertEquals(1, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  void testNegativeZero() {
    Layout layout = new TestLayout(-1, 1);
    {
      final Histogram histogram = addValues(create(layout), 0d, -0d);
      assertEquals(Double.doubleToRawLongBits(-0d), Double.doubleToRawLongBits(histogram.getMin()));
      assertEquals(Double.doubleToRawLongBits(0d), Double.doubleToRawLongBits(histogram.getMax()));
    }
    {
      final Histogram histogram = addValues(create(layout), -0d, 0d);
      assertEquals(Double.doubleToRawLongBits(-0d), Double.doubleToRawLongBits(histogram.getMin()));
      assertEquals(Double.doubleToRawLongBits(0), Double.doubleToRawLongBits(histogram.getMax()));
    }
    {
      final Histogram histogram = addValues(create(layout), 0d, 0d);
      assertEquals(Double.doubleToRawLongBits(0d), Double.doubleToRawLongBits(histogram.getMin()));
      assertEquals(Double.doubleToRawLongBits(0d), Double.doubleToRawLongBits(histogram.getMax()));
    }
    {
      final Histogram histogram = addValues(create(layout), -0d, -0d);
      assertEquals(Double.doubleToRawLongBits(-0d), Double.doubleToRawLongBits(histogram.getMin()));
      assertEquals(Double.doubleToRawLongBits(-0d), Double.doubleToRawLongBits(histogram.getMax()));
    }
  }

  @Test
  void testMinimalLayout1() {
    Layout layout = new TestLayout(-1, 0);

    final Histogram histogram = addValues(create(layout), 1000, -1000);

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
  void testMinimalLayout2() {
    Layout layout = new TestLayout(-1, 0);

    final Histogram histogram = addValues(create(layout), 1000, 1000, -1000, -1000);

    testSerialization(layout, histogram);

    assertEquals(-1000, histogram.getMin(), 0d);
    assertEquals(1000, histogram.getMax(), 0d);

    assertEquals(-1000, histogram.getValue(0), 0d);
    assertEquals(-1001. / 3., histogram.getValue(1), 1e-9);
    assertEquals(999. / 3., histogram.getValue(2), 1e-9);
    assertEquals(1000, histogram.getValue(3), 0d);

    assertEquals(-1000, histogram.getQuantile(0), 0d);
    assertEquals(1000, histogram.getQuantile(1), 0d);
    assertEquals(-1. / 3., histogram.getQuantile(0.5), 1e-9);
  }

  @Test
  void testSingleRegularBinLayout() {
    Layout layout = new TestLayout(-1, 1);

    final Histogram histogram = addValues(create(layout), 1000, 0, -1000);

    testSerialization(layout, histogram);

    assertEquals(-1000, histogram.getMin(), 0d);
    assertEquals(1000, histogram.getMax(), 0d);

    assertEquals(-1000, histogram.getValue(0), 0d);
    assertEquals(0, histogram.getValue(1), 0d);
    assertEquals(1000, histogram.getValue(2), 0d);

    assertEquals(-1000, histogram.getQuantile(0), 0d);
    assertEquals(1000, histogram.getQuantile(1), 0d);
    assertEquals(0, histogram.getQuantile(0.5), 0d);
  }

  @Test
  void testEmptyHistogram() {
    final Layout layout = LogQuadraticLayout.create(1e-3, 0., 0., 10.);
    final Histogram histogram = create(layout);
    assertEquals(0, HistogramTestUtil.numberOfNonEmptyBins(histogram));
    testSerialization(layout, histogram);
  }

  @Test
  void testQuantileOfSingleValue() {

    double value = 5.4;

    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = addValues(create(layout), value);

    assertEquals(value, histogram.getQuantile(0), 0d);
    assertEquals(value, histogram.getQuantile(0.2), 0d);
    assertEquals(value, histogram.getQuantile(0.5), 0d);
    assertEquals(value, histogram.getQuantile(0.7), 0d);
    assertEquals(value, histogram.getQuantile(1), 0d);

    testSerialization(layout, histogram);
  }

  @Test
  void testCountOverflow() {
    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = modify(create(layout)).addValue(10., Long.MAX_VALUE).done();
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
    assertThrows(ArithmeticException.class, () -> modify(histogram).addValue(90.).done());
    HistogramTestUtil.checkHistogramDataConsistency(histogram);
    HistogramTestUtil.checkHistogramDataConsistency(histogram.getPreprocessedCopy());
  }

  @Test
  void testAddHistogramWithUnderAndOverFlowCountsOnly() {
    Layout layout = new TestLayout(-1, 1);

    Histogram histogram1 = modify(create(layout)).addValue(-2, 1000L).addValue(2, 2000L).done();
    Histogram histogram2 = modify(create(layout)).addValue(-2, 4000L).addValue(2, 8000L).done();
    Histogram histogramTotal =
        modify(create(layout))
            .addValue(-2, 1000L)
            .addValue(2, 2000L)
            .addValue(-2, 4000L)
            .addValue(2, 8000L)
            .done();
    histogram1 = modify(histogram1).addHistogram(histogram2).done();
    assertEquals(histogramTotal, histogram1);
  }

  @Test
  void testTotalCountOverflow() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = modify(create(layout)).addValue(1, Long.MAX_VALUE).done();
    assertThrows(ArithmeticException.class, () -> modify(histogram).addValue(2).done());
  }

  @Test
  void testGetBinByRank() {

    final long K = 57;
    final long Z = 5;

    final Layout layout = LogQuadraticLayout.create(1., 0., 0., K);

    final Builder builder = modify(create(layout));

    for (long k = 0; k < K; ++k) {
      builder.addValue(k, Z);
    }
    Histogram histogram = builder.done();

    HistogramTestUtil.checkHistogramDataConsistency(histogram);

    for (int k = 0; k < Z * K; ++k) {
      final BinIterator bin = histogram.getBinByRank(k);
      assertEquals(Z, bin.getBinCount());
      assertEquals((k / Z) * Z, bin.getLessCount());
      assertEquals(Z * K - (k / Z) * Z - Z, bin.getGreaterCount());
    }
  }

  @Test
  void testGeneral() {

    final int N = 10_000;

    final SplittableRandom random = new SplittableRandom(0);

    for (int i = 0; i < N; ++i) {

      final Layout layout = new TestLayout(-1000, 1000);

      Builder builder = modify(create(layout));

      final int N_Data = random.nextInt(50);

      final long countBase = 1L << random.nextInt(33);

      long totalCount = 0L;
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (int j = 0; j < N_Data; ++j) {
        final long count = countBase + random.nextInt(10);
        final double value = random.nextDouble() * 200. - 100.;
        builder.addValue(value, count);
        totalCount += count;
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
      Histogram histogram = builder.done();

      // verify total count, min, and max
      assertEquals(totalCount, histogram.getTotalCount());
      assertEquals(min, histogram.getMin(), 0.0);
      assertEquals(max, histogram.getMax(), 0.0);
      if (totalCount > 0) {
        assertEquals(min, histogram.getValue(0), 0.0);
        assertEquals(max, histogram.getValue(totalCount - 1), 0.0);
      }
      HistogramTestUtil.checkHistogramDataConsistency(histogram);
      testSerialization(layout, histogram);
    }
  }

  @Test
  void testHistogram() {

    TestLayout layout = new TestLayout(-2, 2);
    Histogram histogram =
        modify(create(layout))
            .addValue(Double.NEGATIVE_INFINITY)
            .addValue(-2, 3)
            .addValue(-1, 4)
            .addValue(0, 1)
            .addValue(1, 3)
            .addValue(2, 2)
            .done();

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
  void testAddZeroCount() {

    final Layout layout = new TestLayout(-100, 100);
    final Histogram histogram = modify(create(layout)).addValue(2.4, 0).done();

    assertTrue(histogram.isEmpty());

    testSerialization(layout, histogram);
  }

  @Test
  void testEquals() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = create(layout);
    Histogram otherHistogram = create(layout);

    assertFalse(histogram.equals(null));
    assertNotEquals(histogram, create(LogLinearLayout.create(1e-8, 1e-2, -1e5, 1e5)));
    histogram = addValues(histogram, 1e4);
    assertNotEquals(histogram, otherHistogram);
    addValues(otherHistogram, -1e7 * 2);
    assertNotEquals(histogram, otherHistogram);
    histogram = addValues(histogram, -1e7);
    otherHistogram = addValues(otherHistogram, 1e7);
    assertNotEquals(histogram, otherHistogram);
    histogram = addValues(histogram, 1e7 * 2);
    otherHistogram = addValues(otherHistogram, 1e6);
    assertNotEquals(histogram, otherHistogram);
    histogram = addValues(histogram, -1e7 * 2);
    otherHistogram = addValues(otherHistogram, -1e7);
    assertNotEquals(histogram, otherHistogram);
    addValues(otherHistogram, 1e7 * 2);
    histogram = addValues(histogram, 1e2);
    histogram = addValues(histogram, 1e7);
    otherHistogram = addValues(otherHistogram, 1e4);
    assertNotEquals(histogram, otherHistogram);
    histogram = addValues(histogram, 1e2);
    otherHistogram = addValues(otherHistogram, 1e2);
    assertNotEquals(histogram, otherHistogram);
  }

  @Test
  void testGetPreprocessedData() {
    Histogram histogram =
        modify(create(new TestLayout(-100, 100)))
            .addValue(-101, 3)
            .addValue(-53, 2)
            .addValue(3, 4)
            .addValue(106, 1)
            .done();
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
  void testAddAscendingSequence() {

    TestLayout layout = new TestLayout(-5, 5);

    int numCycles = 10000;
    SplittableRandom rnd = new SplittableRandom(0);

    for (int i = 0; i < numCycles; ++i) {
      double[] values = rnd.doubles(rnd.nextInt(100)).map(d -> d * 12 - 6).toArray();
      Arrays.sort(values);

      Histogram histogram1 = addValues(create(layout), values);
      Histogram histogram2 =
          modify(create(layout)).addAscendingSequence(j -> values[(int) j], values.length).done();

      assertEquals(histogram1, histogram2);
      assertEquals(histogram1.getPreprocessedCopy(), histogram2.getPreprocessedCopy());
    }
  }

  @Test
  void testAddAscendingSequenceUniqueValue() {

    TestLayout layout = new TestLayout(-5, 5);

    double[] values = {Double.NEGATIVE_INFINITY, -5.5, -0.1, 5.3, Double.POSITIVE_INFINITY};
    for (double value : values) {
      Histogram histogram1 = modify(create(layout)).addValue(value, Long.MAX_VALUE).done();
      Histogram histogram2 =
          modify(create(layout)).addAscendingSequence(j -> value, Long.MAX_VALUE).done();
      assertEquals(histogram1, histogram2);
    }
  }

  @Test
  void testAddAscendingSequenceInvalidLength() {
    TestLayout layout = new TestLayout(-5, 5);
    Histogram[] histogram = new Histogram[] {create(layout)};

    double[] values = {Double.NEGATIVE_INFINITY, -5.5, -0.1, 5.3, Double.POSITIVE_INFINITY};
    assertThrows(
        IllegalArgumentException.class,
        () ->
            histogram[0] =
                modify(histogram[0]).addAscendingSequence(j -> values[(int) j], -1).done());
    histogram[0] = modify(histogram[0]).addValue(1, Long.MAX_VALUE).done();
    assertThrows(
        ArithmeticException.class,
        () ->
            histogram[0] =
                modify(histogram[0])
                    .addAscendingSequence(j -> values[(int) j], Long.MAX_VALUE)
                    .done());
  }

  @Test
  void testAddHistogramEqualLayout() {

    final int cycles = 1000;

    final SplittableRandom random = new SplittableRandom(0);

    final Layout layout = new TestLayout(-100, 100);

    for (int cycleCounter = 0; cycleCounter < cycles; ++cycleCounter) {

      final int numValues1 = random.nextInt(1000);
      final int numValues2 = random.nextInt(1000);

      final Builder builder1 = modify(create(layout));
      final Builder builder2 = modify(create(layout));
      final Builder builderTotal = modify(create(layout));

      for (int i = 0; i < numValues1; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        builder1.addValue(value);
        builderTotal.addValue(value);
      }

      for (int i = 0; i < numValues2; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        builder2.addValue(value);
        builderTotal.addValue(value);
      }
      Histogram histogram1 = builder1.done();
      Histogram histogram2 = builder2.done();
      Histogram histogramTotal = builderTotal.done();

      final Histogram histogramMerged = merge(histogram1, histogram2);

      assertEquals(histogramTotal, histogramMerged);
      assertEquals(histogramTotal.hashCode(), histogramMerged.hashCode());
    }
  }

  @Test
  void testAddHistogramNonEqualLayout() {

    final int cycles = 1000;

    final SplittableRandom random = new SplittableRandom(0);

    final Layout layout1 = new TestLayout(-100, 100);
    final Layout layout2 = new TestLayout(-100, 101);
    final Layout layoutTotal = layout1;

    for (int cycleCounter = 0; cycleCounter < cycles; ++cycleCounter) {

      final int numValues1 = random.nextInt(1000);
      final int numValues2 = random.nextInt(1000);

      final Builder builder1 = modify(create(layout1));
      final Builder builder2 = modify(create(layout2));
      final Builder builderTotal = modify(create(layoutTotal));

      for (int i = 0; i < numValues1; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        builder1.addValue(value);
        builderTotal.addValue(value);
      }

      for (int i = 0; i < numValues2; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        builder2.addValue(value);
        builderTotal.addValue(value);
      }
      Histogram histogram1 = builder1.done();
      Histogram histogram2 = builder2.done();
      Histogram histogramTotal = builderTotal.done();
      final Histogram histogramMerged = merge(histogram1, histogram2);

      assertEquals(histogramTotal, histogramMerged);
      assertEquals(histogramTotal.hashCode(), histogramMerged.hashCode());
    }
  }

  @Test
  void testAddEmptyHistogram() {

    final int cycles = 100;

    final SplittableRandom random = new SplittableRandom(0);

    final Layout layout = new TestLayout(-100, 100);

    for (int cycleCounter = 0; cycleCounter < cycles; ++cycleCounter) {

      final int numValues = random.nextInt(10);

      final Builder builder1 = modify(create(layout));
      final Builder builder2 = modify(create(layout));

      for (int i = 0; i < numValues; ++i) {
        final double value = -101. + random.nextDouble() * 202.;
        builder1.addValue(value);
        builder2.addValue(value);
      }
      Histogram histogram1 = builder1.done();
      Histogram histogram2 = builder2.done();
      histogram2 = merge(histogram2, create(layout));

      assertEquals(histogram1, histogram2);
      assertEquals(histogram1.hashCode(), histogram2.hashCode());
    }
  }

  @Test
  void testAddNegativeCount() {
    final Layout layout = new TestLayout(-100, 100);
    assertThrows(
        IllegalArgumentException.class, () -> modify(create(layout)).addValue(2.4, -1).done());
  }

  @Test
  void testNonEmptyBins() {

    final int K = 1000;

    final Layout layout = LogQuadraticLayout.create(1., 0., 0., K);

    final int numCycles = 100;

    final SplittableRandom random = new SplittableRandom(0);

    for (int i = 0; i < numCycles; ++i) {

      final Builder builder = modify(create(layout));

      long count = 0;
      int nonEmptyBinsCount = 0;

      for (int k = 0; k < K; ++k) {
        if (random.nextBoolean()) {
          final int n = random.nextInt(1_000_000);

          nonEmptyBinsCount += 1;
          count += n;
          builder.addValue(k, n);
        }
      }

      Histogram histogram = builder.done();
      assertEquals(count, histogram.getTotalCount());
      assertEquals(nonEmptyBinsCount, HistogramTestUtil.numberOfNonEmptyBins(histogram));
      testSerialization(layout, histogram);
    }
  }

  @Test
  void testSerialization1() {
    SplittableRandom rnd = new SplittableRandom(0);
    int numCycles = 10000;

    for (int cycle = 0; cycle < numCycles; ++cycle) {

      final Layout layout = new TestLayout(-10 + rnd.nextInt(10), rnd.nextInt(10));
      Builder builder = modify(create(layout));

      int numValues = rnd.nextInt(20);

      for (int i = 0; i < numValues; ++i) {
        builder.addValue(-11 + rnd.nextInt(24));
      }
      Histogram histogram = builder.done();
      testSerialization(layout, histogram);
    }
  }

  @Test
  void testSerialization2() {
    SplittableRandom rnd = new SplittableRandom(0);
    int numCycles = 10000;

    for (int cycle = 0; cycle < numCycles; ++cycle) {

      final Layout layout = new TestLayout(-10 + rnd.nextInt(10), rnd.nextInt(10));
      Builder builder = modify(create(layout));

      int numValues = rnd.nextInt(20);

      for (int i = 0; i < numValues; ++i) {
        builder.addValue(-11 + rnd.nextInt(24), 1L << rnd.nextInt(34));
      }
      Histogram histogram = builder.done();
      testSerialization(layout, histogram);
    }
  }

  @Test
  void testVerySmallEffectiveBin() {
    Layout layout = new TestLayout(-100, 100);

    double[] xValues = {-12143.43, -12, 34.535, 21314234};
    long c1 = 432;
    long c2 = 331;

    for (double min : xValues) {
      double max = Math.nextUp(min);
      final Builder builder = modify(create(layout));
      builder.addValue(min, c1);
      builder.addValue(max, c2);
      Histogram histogram = builder.done();
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
  void testAddHistogramFirstNonEmptyBinEqualsLastNonEmptyBin() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram1 = modify(create(layout)).addValue(5).addValue(-5).done();
    Histogram histogram2 = modify(create(layout)).addValue(5.5, 5).done();
    Histogram histogramTotal =
        modify(create(layout)).addValue(5).addValue(-5).addValue(5.5, 5).done();

    assertEquals(histogramTotal, merge(histogram1, histogram2));
  }

  @Test
  void testAddHistogramOverflow() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram1 = modify(create(layout)).addValue(5, 1000000).done();
    Histogram histogram2 = modify(create(layout)).addValue(5, Long.MAX_VALUE).done();

    assertThrows(ArithmeticException.class, () -> merge(histogram1, histogram2));
  }

  @Test
  void testGetValueEstimateInvalidOrder() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = modify(create(layout)).addValue(5).done();

    assertThrows(IllegalArgumentException.class, () -> histogram.getValue(-1));
    assertThrows(IllegalArgumentException.class, () -> histogram.getValue(1));
  }

  @Test
  void testGetBinByRankInvalidOrder() {
    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);
    Histogram histogram = modify(create(layout)).addValue(5).done();

    assertThrows(IllegalArgumentException.class, () -> histogram.getBinByRank(-1));
    assertThrows(IllegalArgumentException.class, () -> histogram.getBinByRank(1));
  }

  private static void testAddHistogramHelper(
      Function<Layout, Histogram> histogramFactory1,
      Function<Layout, Histogram> histogramFactory2) {

    Layout layout = LogLinearLayout.create(1e-8, 1e-2, -1e6, 1e6);

    SplittableRandom random = new SplittableRandom(0);
    long numValues1 = 1000;
    long numValues2 = 2000;

    Builder builder1 = modify(histogramFactory1.apply(layout));
    Builder builder2 = modify(histogramFactory2.apply(layout));

    Builder builderTotal = modify(histogramFactory1.apply(layout));

    DoubleStream.generate(random::nextDouble)
        .limit(numValues1)
        .forEach(
            x -> {
              builder1.addValue(x);
              builderTotal.addValue(x);
            });

    DoubleStream.generate(random::nextDouble)
        .limit(numValues2)
        .forEach(
            x -> {
              builder2.addValue(x);
              builderTotal.addValue(x);
            });

    Histogram histogram1 = builder1.done();
    Histogram histogram2 = builder2.done();
    Histogram histogramTotal = builderTotal.done();

    histogram1 = merge(histogram1, histogram2);

    assertEquals(histogramTotal, histogram1);
  }

  @Test
  void testAddHistogram() {
    testAddHistogramHelper(this::create, Histogram::createDynamic);
    testAddHistogramHelper(this::create, Histogram::createStatic);
  }

  @Test
  void testDeprecatedGetBinCopy() {
    Histogram histogram =
        modify(create(new TestLayout(-1, 1))).addValue(-1, 4).addValue(0, 2).addValue(1, 3).done();
    BinIterator iterator = histogram.getFirstNonEmptyBin();
    assertEquals(4, iterator.getBinCopy().getBinCount());
  }
}
