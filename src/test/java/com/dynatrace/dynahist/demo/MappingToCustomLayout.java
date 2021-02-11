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
import com.dynatrace.dynahist.layout.CustomLayout;
import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogQuadraticLayout;
import java.util.Random;
import org.junit.Test;

public class MappingToCustomLayout {

  @Test
  public void recordValuesAndMapToCustomLayout1() {
    Layout layout = LogLinearLayout.create(1e-5, 1e-2, 0, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    final Random random = new Random(0);

    for (int i = 0; i < 1000000; i += 20000) {
      histogram.addValue(random.nextDouble() * i);
    }

    Layout resultLayout = CustomLayout.create(0, 1, 10, 100, 1000, 10000, 1000000);
    Histogram resultHistogram = Histogram.createDynamic(resultLayout);

    resultHistogram.addHistogram(histogram);
    assertEquals(
        ""
            + " 0.00000000000000000E+00 -  9.99999999999999900E-01 : *\n"
            + " 1.00000000000000000E+03 -  9.99999999999999800E+03 : *****\n"
            + " 1.00000000000000000E+04 -  7.77237591081370300E+05 : **************************************************\n",
        PrintUtil.prettyPrint(resultHistogram));
  }

  @Test
  public void recordValuesAndMapToCustomLayout2() {
    Layout layout = LogQuadraticLayout.create(1e-5, 1e-2, 0, 1e6);
    Histogram histogram = Histogram.createDynamic(layout);
    final Random random = new Random(0);

    for (int i = 0; i < 1000000; ++i) {
      histogram.addValue(random.nextDouble() * i);
    }

    Layout resultLayout = CustomLayout.create(0, 1, 10, 100, 1000, 10000, 1000000);
    Histogram resultHistogram = Histogram.createDynamic(resultLayout);

    resultHistogram.addHistogram(histogram);
    assertEquals(
        ""
            + " 0.00000000000000000E+00 -  9.99999999999999900E-01 :                  14\n"
            + " 1.00000000000000000E+00 -  9.99999999999999800E+00 :                 114\n"
            + " 1.00000000000000000E+01 -  9.99999999999999900E+01 :                 925\n"
            + " 1.00000000000000000E+02 -  9.99999999999999900E+02 :                6971\n"
            + " 1.00000000000000000E+03 -  9.99999999999999800E+03 :               47863\n"
            + " 1.00000000000000000E+04 -  9.98000950924521900E+05 :              944113\n",
        PrintUtil.print(resultHistogram));
  }
}
