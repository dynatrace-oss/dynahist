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

import com.newrelic.nrsketch.ComboNrSketch;
import com.newrelic.nrsketch.NrSketch;
import com.newrelic.nrsketch.indexer.IndexerOption;

public class NrSketchComboSubBucketLogIndexerRecordingSpeedBenchmark
    extends AbstractNrSketchRecordingSpeedBenchmark {
  @Override
  protected NrSketch create() {
    return new ComboNrSketch(
        Constants.NR_NUM_BUCKETS,
        Constants.EXP_BUCKET_PRECISION,
        IndexerOption.SUB_BUCKET_LOG_INDEXER);
  }
}
