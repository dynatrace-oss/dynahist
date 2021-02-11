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

import com.dynatrace.dynahist.Histogram;
import com.dynatrace.dynahist.bin.BinIterator;
import com.dynatrace.dynahist.util.Preconditions;
import java.util.Locale;

public final class PrintUtil {

  private PrintUtil() {}

  public static String print(Histogram histogram) {

    Preconditions.checkArgument(histogram != null);
    Preconditions.checkArgument(histogram.getTotalCount() != 0);

    BinIterator iterator = histogram.getFirstNonEmptyBin();
    StringBuilder result =
        new StringBuilder(
            String.format(
                (Locale) null,
                "%24.17E - %24.17E : %19d\n",
                iterator.getLowerBound(),
                iterator.getUpperBound(),
                iterator.getBinCount()));
    while (!iterator.isLastNonEmptyBin()) {
      iterator.next();
      result.append(
          String.format(
              (Locale) null,
              "%24.17E - %24.17E : %19d\n",
              iterator.getLowerBound(),
              iterator.getUpperBound(),
              iterator.getBinCount()));
    }
    return result.toString();
  }

  public static String prettyPrint(Histogram histogram) {
    Preconditions.checkArgument(histogram != null);
    Preconditions.checkArgument(histogram.getTotalCount() != 0);

    BinIterator iterator = histogram.getFirstNonEmptyBin();
    StringBuilder temp = new StringBuilder();
    for (int i = 0; i < iterator.getBinCount(); ++i) {
      temp.append('*');
    }
    StringBuilder result =
        new StringBuilder(
            String.format(
                (Locale) null,
                "%24.17E - %24.17E : %s\n",
                iterator.getLowerBound(),
                iterator.getUpperBound(),
                temp));
    while (!iterator.isLastNonEmptyBin()) {
      iterator.next();
      for (int i = 0; i < iterator.getBinCount(); ++i) {
        temp.append('*');
      }
      result.append(
          String.format(
              (Locale) null,
              "%24.17E - %24.17E : %s\n",
              iterator.getLowerBound(),
              iterator.getUpperBound(),
              temp));
    }
    return result.toString();
  }
}
