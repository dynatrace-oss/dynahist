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

import static com.dynatrace.dynahist.Constants.*;

import com.datadoghq.sketch.ddsketch.DDSketch;
import com.datadoghq.sketch.ddsketch.mapping.LogarithmicMapping;
import com.datadoghq.sketch.ddsketch.store.UnboundedSizeDenseStore;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import com.dynatrace.dynahist.serialization.SerializationUtil;
import com.newrelic.nrsketch.NrSketch;
import com.newrelic.nrsketch.NrSketchSerializer;
import com.newrelic.nrsketch.SimpleNrSketch;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import org.HdrHistogram.DoubleHistogram;

public class Comparison {

  private static void assertCondition(boolean condition, String description) {
    if (!condition) {
      System.out.println(description);
    }
  }

  private static void assertException(Runnable runnable, String description) {
    try {
      runnable.run();
      System.out.println(description);
    } catch (RuntimeException e) {
      // expect that exception is thrown
    }
  }

  interface Sketch {
    String getDescription();

    double getMin();

    double getMax();

    BigDecimal getCount();

    void add(double value);

    void add(double value, long increment);

    byte[] serialize() throws IOException;
  }

  private static Sketch createDDSketch() {
    return new Sketch() {
      private final DDSketch sketch =
          new DDSketch(
              new LogarithmicMapping(DD_SKETCH_RELATIVE_ACCURACY), UnboundedSizeDenseStore::new);

      @Override
      public String getDescription() {
        return "DDSketch";
      }

      @Override
      public double getMin() {
        return sketch.getMinValue();
      }

      @Override
      public double getMax() {
        return sketch.getMaxValue();
      }

      @Override
      public BigDecimal getCount() {
        return BigDecimal.valueOf(sketch.getCount());
      }

      @Override
      public void add(double value) {
        sketch.accept(value);
      }

      @Override
      public void add(double value, long increment) {
        sketch.accept(value, increment);
      }

      @Override
      public byte[] serialize() {
        return sketch.serialize().array();
      }

      @Override
      public String toString() {
        return "DDSketch (count = " + sketch.getCount() + ")";
      }
    };
  }

  private static Sketch createDynaHist() {
    return new Sketch() {
      private final Histogram histogram =
          Histogram.createDynamic(LogQuadraticLayout.create(ABSOLUTE_ERROR, PRECISION, 0, MAX));

      @Override
      public String getDescription() {
        return "DynaHist";
      }

      @Override
      public double getMin() {
        return histogram.getMin();
      }

      @Override
      public double getMax() {
        return histogram.getMax();
      }

      @Override
      public BigDecimal getCount() {
        return BigDecimal.valueOf(histogram.getTotalCount());
      }

      @Override
      public void add(double value) {
        histogram.addValue(value);
      }

      @Override
      public void add(double value, long increment) {
        histogram.addValue(value, increment);
      }

      @Override
      public byte[] serialize() throws IOException {
        return SerializationUtil.write(histogram);
      }

      @Override
      public String toString() {
        return histogram.toString();
      }
    };
  }

  private static Sketch createHdrHistogram() {
    return new Sketch() {
      private final DoubleHistogram histogram = new DoubleHistogram(RANGE, PRECISION_DIGITS);

      @Override
      public String getDescription() {
        return "HdrDoubleHistogram";
      }

      @Override
      public double getMin() {
        return histogram.getMinValue();
      }

      @Override
      public double getMax() {
        return histogram.getMaxValue();
      }

      @Override
      public BigDecimal getCount() {
        return BigDecimal.valueOf(histogram.getTotalCount());
      }

      @Override
      public void add(double value) {
        histogram.recordValue(value);
      }

      @Override
      public void add(double value, long increment) {
        histogram.recordValueWithCount(value, increment);
      }

      @Override
      public byte[] serialize() throws IOException {
        byte[] result = new byte[histogram.getNeededByteBufferCapacity()];
        ByteBuffer byteBuffer = ByteBuffer.wrap(result);
        histogram.encodeIntoByteBuffer(byteBuffer);
        return result;
      }

      @Override
      public String toString() {
        return "HdrHistogram.DoubleHistogram (count = " + histogram.getTotalCount() + ")";
      }
    };
  }

  private static Sketch createNrSketch() {
    return new Sketch() {
      private final NrSketch nrSketch = new SimpleNrSketch();

      @Override
      public String getDescription() {
        return "NrSketch";
      }

      @Override
      public double getMin() {
        return nrSketch.getMin();
      }

      @Override
      public double getMax() {
        return nrSketch.getMax();
      }

      @Override
      public BigDecimal getCount() {
        return BigDecimal.valueOf(nrSketch.getCount());
      }

      @Override
      public void add(double value) {
        nrSketch.insert(value);
      }

      @Override
      public void add(double value, long increment) {
        nrSketch.insert(value, increment);
      }

      @Override
      public byte[] serialize() throws IOException {
        return NrSketchSerializer.serializeNrSketch(nrSketch).array();
      }

      @Override
      public String toString() {
        return nrSketch.toString();
      }
    };
  }

  private static void testRecordingOfExtremeValues(Supplier<Sketch> sketchSupplier) {
    final double value = 2.5362386543;

    Sketch sketch = sketchSupplier.get();
    sketch.add(value);
    assertCondition(
        sketch.getMin() == value,
        sketch.getDescription()
            + " does not keep track of the exact minimum value! (expected = "
            + value
            + ", actual = "
            + sketch.getMin()
            + ")");
    assertCondition(
        sketch.getMax() == value,
        sketch.getDescription()
            + " does not keep track of the exact maximum value! (expected = "
            + value
            + ", actual = "
            + sketch.getMax()
            + ")");
  }

  private static void testOverflow(Supplier<Sketch> sketchSupplier) {
    final double value = 2.5362386543;
    Sketch sketch = sketchSupplier.get();
    sketch.add(value);
    assertException(
        () -> sketch.add(value, Long.MAX_VALUE),
        sketch.getDescription()
            + " swallows overflows silently! (expected an exception, actual total count = "
            + sketch.getCount()
            + ")");
  }

  private static void testExactnessOfCounts(Supplier<Sketch> sketchSupplier) {
    final double value = 2.5362386543;
    Sketch sketch = sketchSupplier.get();
    long trueCount = Long.MAX_VALUE;
    sketch.add(value, trueCount);
    assertCondition(
        BigDecimal.valueOf(trueCount).equals(sketch.getCount()),
        sketch.getDescription()
            + " does not count exactly! (expected = "
            + trueCount
            + ", actual = "
            + sketch.getCount()
            + ")");
  }

  private static void testRecordingOfSpecialValues(Supplier<Sketch> sketchSupplier) {
    Sketch sketch = sketchSupplier.get();
    assertException(
        () -> sketch.add(Double.NaN),
        sketch.getDescription()
            + " silently ignores NaN values! (expected an exception, sketch = "
            + sketch
            + ")");
  }

  private static void testNegativeIncrements(Supplier<Sketch> sketchSupplier) {
    final double value = 2.5362386543;
    {
      Sketch sketch = sketchSupplier.get();
      assertException(
          () -> sketch.add(value, -1),
          sketch.getDescription()
              + " does not throw an exception for negative counts! (expected an exception, sketch = "
              + sketch
              + ")");
    }
    {
      Sketch sketch = sketchSupplier.get();
      assertException(
          () -> sketch.add(value, Long.MIN_VALUE),
          sketch.getDescription()
              + " does not throw an exception for negative counts! (expected an exception, sketch = "
              + sketch
              + ")");
    }
    {
      Sketch sketch = sketchSupplier.get();
      sketch.add(value);
      assertException(
          () -> sketch.add(value, -1),
          sketch.getDescription()
              + " does not throw an exception for negative counts! (expected an exception, sketch = "
              + sketch
              + ")");
    }
    {
      Sketch sketch = sketchSupplier.get();
      sketch.add(value, 2);
      assertException(
          () -> sketch.add(value, -1),
          sketch.getDescription()
              + " does not throw an exception for negative counts! (expected an exception, sketch = "
              + sketch
              + ")");
    }
  }

  /**
   * Demonstrates differences between HdrHistogram, DDSketch, NrSketch, and DynaHist that must be
   * taken into account when comparing the benchmark results.
   */
  public static void main(String[] args) {

    System.out.println("\nRecording of extreme values:");
    testRecordingOfExtremeValues(Comparison::createDDSketch);
    testRecordingOfExtremeValues(Comparison::createNrSketch);
    testRecordingOfExtremeValues(Comparison::createDynaHist);
    testRecordingOfExtremeValues(Comparison::createHdrHistogram);

    System.out.println("\nOverflow handling:");
    testOverflow(Comparison::createDDSketch);
    testOverflow(Comparison::createNrSketch);
    testOverflow(Comparison::createDynaHist);
    testOverflow(Comparison::createHdrHistogram);

    System.out.println("\nExactness of counts:");
    testExactnessOfCounts(Comparison::createDDSketch);
    testExactnessOfCounts(Comparison::createNrSketch);
    testExactnessOfCounts(Comparison::createDynaHist);
    testExactnessOfCounts(Comparison::createHdrHistogram);

    System.out.println("\nNegative increments:");
    testNegativeIncrements(Comparison::createDDSketch);
    testNegativeIncrements(Comparison::createNrSketch);
    testNegativeIncrements(Comparison::createDynaHist);
    testNegativeIncrements(Comparison::createHdrHistogram);

    System.out.println("\nRecording of special values:");
    testRecordingOfSpecialValues(Comparison::createDDSketch);
    testRecordingOfSpecialValues(Comparison::createNrSketch);
    testRecordingOfSpecialValues(Comparison::createDynaHist);
    testRecordingOfSpecialValues(Comparison::createHdrHistogram);
  }
}
