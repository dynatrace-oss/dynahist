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

import java.io.DataInput;
import java.io.IOException;

/**
 * A deserializer for a given type.
 *
 * @param <T> the type to be deserialized
 */
@FunctionalInterface
public interface SerializationReader<T> {

  /**
   * Deserializes an object by reading from a given {@link DataInput}.
   *
   * <p>Implementations should never return {@code null} except for the case {@code null} was really
   * the serialized value. Returning {@code null} in all other cases like for deprecated
   * serialization formats is very dangerous, especially if not all serialized bytes are consumed
   * within the {@link #read} method. If the caller chooses to continue with deserialization from
   * the same {@link DataInput}, wrong data may be deserialized due to the resulting misalignment.
   * This may lead to severe problems like huge unwanted allocations, if for example the wrong array
   * length was read first. Consider throwing an {@link IOException} instead, which must be handled
   * by the caller anyway, and which effectively prevents callers from continuing with
   * deserialization.
   *
   * @param dataInput the data input
   * @return the deserialized object
   * @throws IOException if an I/O error occurs.
   */
  T read(DataInput dataInput) throws IOException;
}
