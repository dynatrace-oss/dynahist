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
package com.dynatrace.dynahist.layout;

import static java.util.Objects.requireNonNull;

import com.dynatrace.dynahist.serialization.SerializationReader;
import com.dynatrace.dynahist.serialization.SerializationWriter;

/** Represents the serialization definition for some {@link Layout}. */
public final class LayoutSerializationDefinition {

  final long serialVersion;
  final Class<?> clazz;
  final SerializationWriter<Layout> writer;
  final SerializationReader<Layout> reader;

  @SuppressWarnings("unchecked")
  <T extends Layout> LayoutSerializationDefinition(
      long serialVersion,
      Class<T> clazz,
      SerializationWriter<T> writer,
      SerializationReader<T> reader) {
    this.serialVersion = serialVersion;
    this.clazz = requireNonNull(clazz);
    this.writer = (SerializationWriter<Layout>) requireNonNull(writer);
    this.reader = (SerializationReader<Layout>) requireNonNull(reader);
  }
}
