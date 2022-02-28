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

import static com.dynatrace.dynahist.Constants.EXP_BUCKET_PRECISION;

import com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout;

public class DynaHistDynamicOpenTelemetryExponentialBucketRecordingSpeedBenchmark
    extends AbstractDynaHistRecordingSpeedBenchmark {
  @Override
  protected Histogram create() {
    return Histogram.createDynamic(
        OpenTelemetryExponentialBucketsLayout.create(EXP_BUCKET_PRECISION));
  }
}
