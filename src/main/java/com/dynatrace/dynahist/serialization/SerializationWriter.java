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
package com.dynatrace.dynahist.serialization;

import java.io.DataOutput;
import java.io.IOException;

/**
 * A serialization writer for a given type.
 *
 * @param <T> the type to be serialized
 */
@FunctionalInterface
public interface SerializationWriter<T> {

  /**
   * Serializes a given object by writing to a given {@link DataOutput}.
   *
   * @param data the object to be serialized
   * @param dataOutput the data output
   * @throws IOException if an I/O error occurs.
   */
  void write(T data, DataOutput dataOutput) throws IOException;
}
