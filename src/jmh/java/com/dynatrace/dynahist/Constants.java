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

public interface Constants {
  long RANGE = 1_000_000_000;
  long MIN = 1000;
  long MAX = MIN * RANGE;
  int PRECISION_DIGITS = 2;
  double PRECISION = Math.pow(10., -PRECISION_DIGITS);
  int EXP_BUCKET_PRECISION =
      6; // parameter for exponential buckets, corresponds to relative error of 1.0109
  double DD_SKETCH_RELATIVE_ACCURACY =
      PRECISION * 0.5; // parameter for DDSketch to have comparable relative bin widths
  double ABSOLUTE_ERROR = MIN * PRECISION;
}
