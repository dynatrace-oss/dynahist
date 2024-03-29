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

interface HistogramDeserializationBuilder {

  void setMinValue(double minValue);

  void setMaxValue(double maxValue);

  void allocateRegularCounts(
      int minBinIndex,
      int maxBinIndex,
      int bitsPerCount); // TODO add hint for number of nonempty buckets and total count

  void incrementRegularCount(int binIndex, long increment); // TODO rename to setRegularCount

  // due to numerical inaccuracies the bucket capacity could be violated
  void incrementRegularCountSafe(int binIndex);

  void incrementOverflowCount(long increment);

  void incrementUnderflowCount(long increment);

  void incrementTotalCount(long increment);

  // if this method is called without calling any other method before, an empty histogram will be
  // created
  Histogram build();
}
