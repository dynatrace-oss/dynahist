/*
 * Copyright 2022 Dynatrace LLC
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

import com.datadoghq.sketch.ddsketch.DDSketch;
import com.datadoghq.sketch.ddsketch.mapping.IndexMapping;
import com.datadoghq.sketch.ddsketch.store.Store;
import org.openjdk.jmh.infra.Blackhole;

public abstract class AbstractDDSketchRecordingSpeedBenchmark
    extends AbstractRecordingSpeedBenchmark<DDSketch> {

  @Override
  protected final DDSketch create() {
    return new DDSketch(createMapping(), this::createStore);
  }

  protected abstract IndexMapping createMapping();

  protected abstract Store createStore();

  @Override
  protected final void add(DDSketch histogram, double value) {
    histogram.accept(value);
  }

  @Override
  protected final void consume(DDSketch histogram, Blackhole blackhole) {
    blackhole.consume(histogram.getCount());
    blackhole.consume(histogram.getMinValue());
    blackhole.consume(histogram.getMaxValue());
  }
}
