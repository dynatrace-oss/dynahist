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
package com.dynatrace.dynahist.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Locale;
import org.junit.Test;

public class PreconditionsTest {

  private static final String MSG = "msg";
  private static final String FORMAT_STRING = "value = %s";
  private static final long VALUE = 123;

  @Test
  public void testCheckArgumentFalse() {
    assertThrows(IllegalArgumentException.class, () -> Preconditions.checkArgument(false));
  }

  @Test
  public void testCheckArgumentTrue() {
    Preconditions.checkArgument(true);
  }

  @Test
  public void testCheckArgumentWithMessageFalse() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> Preconditions.checkArgument(false, MSG));
    assertEquals(MSG, e.getMessage());
  }

  @Test
  public void testCheckArgumentWithMessageAndValueTrue() {
    Preconditions.checkArgument(true, FORMAT_STRING, VALUE);
  }

  @Test
  public void testCheckArgumentWithMessageAndValueFalse() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> Preconditions.checkArgument(false, FORMAT_STRING, VALUE));
    assertEquals(String.format((Locale) null, FORMAT_STRING, VALUE), e.getMessage());
  }

  @Test
  public void testCheckArgumentWithMessageTrue() {
    Preconditions.checkArgument(true, MSG);
  }
}
