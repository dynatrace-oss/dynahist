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
package com.dynatrace.dynahist.demo;

import static org.junit.Assert.assertEquals;

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.Test;

/** Simple example of using DynaHist */
public class HistogramUsage {

  /**
   * The {@link Layout} defines the bins for a {@link Histogram} and maps a given value to a
   * histogram bin index. {@link LogLinearLayout#create(double, double, double, double)} creates a
   * {@link Layout} Choose {@link LogLinearLayout}, if speed is more important than memory
   * efficiency. {@link LogQuadraticLayout#create(double, double, double, double)} creates a {@link
   * Layout} Choose {@link LogQuadraticLayout}, if memory efficiency is more important than speed.
   * LogLinearLayout and LogQuadraticLayout guarantee that the bins cover a given interval and that
   * the bin widths either satisfy an absolute bin width limit or a relative bin width limit.
   *
   * <p>{@link Histogram#createDynamic(Layout)} creates a dynamic {@link Histogram} {@link
   * Histogram#createStatic(Layout)} creates a static {@link Histogram}.
   */
  @Test
  public void createHistogram() {
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);

    assertEquals(
        histogram.getClass().getSimpleName()
            + " [layout="
            + layout
            + ", underFlowCount=0, overFlowCount=0, totalCount=0, min=Infinity, max=-Infinity, counts={}]",
        histogram.toString());
  }

  /** Add values using {@link Histogram#addValue(double)} adds a given value to the histogram. */
  @Test
  public void addSingleValue() {
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);

    histogram.addValue(-5.5);
    assertEquals(
        PrintUtil.print(histogram),
        "-5.50000000000000000E+00 - -5.50000000000000000E+00 :                   1\n");
  }

  /**
   * Add values with multiplicity using {@link Histogram#addValue(double, long) } adds a given value
   * to the histogram with a given multiplicity.
   */
  @Test
  public void addValueWithMultiplicity() {
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);

    histogram.addValue(-5.5, 5);

    assertEquals(
        PrintUtil.print(histogram),
        "-5.50000000000000000E+00 - -5.50000000000000000E+00 :                   5\n");
  }

  /**
   * Get quantile values using {@link Histogram#getQuantile(double)} returns an estimate for the
   * quantile value. p = 0.5 returns median.
   */
  @Test
  public void getMedianSingleValue() {
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);

    histogram.addValue(5.5);

    double median = histogram.getQuantile(0.5);
    assertEquals(5.5, median, Math.max(1e-5, 5.5 * 1e-2));
  }

  @Test
  public void getMedianMultipleValues() {
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    for (int i = 0; i <= 100; i++) {
      histogram.addValue(i, 5);
    }
    assertEquals(50, histogram.getQuantile(0.5), Math.max(1e-5, 50 * 1e-2));
  }

  /**
   * Merge histograms using {@link Histogram#addHistogram(Histogram)}. If the given histograms have
   * a different layout, this operation may lead to an unwanted loss of precision.
   */
  @Test
  public void mergeHistogram() {
    Layout layout1 = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Layout layout2 = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram1 = Histogram.createDynamic(layout1);
    Histogram histogram2 = Histogram.createDynamic(layout2);
    Histogram histogramTotal = Histogram.createDynamic(layout1);
    histogram1.addValue(-55.5);
    histogram1.addValue(100);
    histogram2.addValue(5);
    histogram2.addValue(-7.5);
    histogramTotal.addValue(-55.5);
    histogramTotal.addValue(100);
    histogramTotal.addValue(5);
    histogramTotal.addValue(-7.5);

    histogram1.addHistogram(histogram2);

    assertEquals(histogramTotal, histogram1);
    assertEquals(histogramTotal.hashCode(), histogram1.hashCode());
  }

  /**
   * Write the histograms to to a given {@link DataOutput} using {@link
   * Histogram#write(DataOutput)}. The {@link Layout} information will not be written. Therefore, it
   * is necessary to provide the layout when reading using {@link Histogram#readAsDynamic(Layout,
   * DataInput)} or {@link Histogram#readAsStatic(Layout, DataInput)}.
   */
  @Test
  public void serializeAndDeserializeHistogram() {
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, -1e6, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    histogram.addValue(-5.5);

    // serialization
    byte[] serializedHistogram = null;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
      histogram.write(dataOutputStream);
      serializedHistogram = byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      e.printStackTrace();
    }

    // deserialization
    Histogram deserializedHistogram = null;
    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedHistogram);
        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream)) {
      deserializedHistogram = Histogram.readAsDynamic(layout, dataInputStream);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertEquals(histogram, deserializedHistogram);
    assertEquals(histogram.hashCode(), deserializedHistogram.hashCode());
  }
}
