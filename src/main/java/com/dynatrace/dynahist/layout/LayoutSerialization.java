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

import static com.dynatrace.dynahist.util.Preconditions.checkArgument;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

final class LayoutSerialization {

  private LayoutSerialization() {}

  /**
   * Prevent registration of layouts with serial versions listed below. In this way the library can
   * be extended by layouts without potentially breaking custom layout serializations.
   */
  private static final HashSet<Long> RESERVED_SERIAL_VERSIONS =
      new HashSet<>(
          Arrays.asList(
              0xee3300c2e8f5499aL,
              0x1bfa94ee047b874bL,
              0xbe18431aec9dc75aL,
              0x223891975b23ed2cL,
              0x2526788dcaa5cbc3L,
              0xfb14800cbdeab19fL,
              0x3da1f941232f78d5L,
              0x798f673f19cad268L,
              0x7ebd8e2ca5d5e8f8L,
              0xe90f4adf283fd8bdL,
              0x49315914ea256847L,
              0x67a0fdcfcac8adaeL,
              0xe8c9051d7fec4813L,
              0x2966822dd3059653L,
              0x771ffd77267e093bL,
              0x3ac21d977fd66983L,
              0xe072aaeb8a78cd56L,
              0x45e4b9a63372e5a0L,
              0x45b786df0c91df63L,
              0x04717a29a3ae8880L,
              0x42de22571ad7c1abL,
              0x76beb97c8c76b6cbL,
              0x36bd063c44dd1c47L,
              0x1e3413545e2e9aa4L,
              0xb2f4232b093c1344L,
              0x3ad516c404bb339fL,
              0x0d5fdd5ccdb87d2eL,
              0x355ef46e1b222b84L,
              0x55bcebfcdecdbd6bL,
              0x2cf39955769a240fL,
              0x344b93535d597e5aL,
              0x539b205ae48839b9L,
              0x60aa5f359c34974dL,
              0x23a8ad0a51093596L,
              0x022d7b6a057e7426L,
              0x29b10e374caf5524L,
              0xd89bb40750f0b639L,
              0xcc612183ab823bb0L,
              0xe36bb5d2393522acL,
              0x220b749e65ce3926L,
              0xf94932966a8ef201L,
              0x47554432b5b68636L,
              0x971d37ae9e554d95L,
              0xa92bce46971262b3L,
              0xf50c445c4f5bc615L,
              0xe2485f42cef3685aL,
              0x3d207553b1b9e7a7L,
              0x6a9f5dd769615e93L,
              0x3e148a4afd4a0c36L,
              0xbad9df1ac314a9c5L,
              0x1c1c6feeb0c75786L,
              0xccdabd212aed7e33L,
              0x139b7acbebfd55baL,
              0xb2480b85e1963a07L,
              0x92ef3b1f4fd81c96L,
              0x456ddb8c438a95e2L,
              0x03c7f1eb167d2577L,
              0x03171ab995b64e8bL,
              0x1ad67f4b6a032331L,
              0x4c0c4551819b67baL,
              0xdc3e981d2b123e5cL,
              0x6bcf0e0aa34c2440L,
              0xa39cd039cc6aa0dbL,
              0x6a4d1f5a7ecb9a1eL,
              0x6134942188864fe8L,
              0xb0762cc0006514b8L,
              0x66eaf227554aa3ccL,
              0x9c8fec4796a43400L,
              0x5ca59771320292fbL,
              0x0a14771bfb087051L,
              0x938e4e374cc45f38L,
              0xcea5420ccc9440d7L,
              0xf6c9d75166100f38L,
              0x7c74b81edddea9f8L,
              0x13840cf38ad88cc7L,
              0xa2704e8ff2e1391cL,
              0x5ea7252040e5d5dbL,
              0xb37a491fe6012576L,
              0xa26ae9a10288adb8L,
              0xf85dd78c4404cc3eL,
              0xa5e57bcf4dea2f81L,
              0xb0545edeecc62d21L,
              0xbc7ff83b839f1468L,
              0x37ca3455f1646aaaL,
              0x7749ac894823244bL,
              0x800b649f63a23cdbL,
              0xba73b7a4362c7e17L,
              0x24026d1c4bf092beL,
              0x9c57278d440abb4cL,
              0xdfb7f6f10330d130L,
              0x49a507976a675dcaL,
              0x34d085dbbc54fa43L,
              0x981b98182d9c8820L,
              0xd2f3247856570daaL,
              0xc01e528457e0b09aL,
              0xaa8583c5bd37ff70L,
              0xac460f81599bfbecL,
              0xa1ddc1de18d263bfL));

  private static volatile Map<Long, LayoutSerializationDefinition> serialToDefinitions =
      Collections.emptyMap();
  private static volatile Map<Class<?>, LayoutSerializationDefinition> classToDefinitions =
      Collections.emptyMap();

  // new layout implementations must be registered before
  // serialization/deserialization
  static synchronized void register(LayoutSerializationDefinition[] definitions) {
    if (definitions == null) {
      return;
    }

    Map<Long, LayoutSerializationDefinition> newSerialToDefinitions =
        new HashMap<>(serialToDefinitions.size() + definitions.length);
    Map<Class<?>, LayoutSerializationDefinition> newClassToDefinitions =
        new HashMap<>(classToDefinitions.size() + definitions.length);

    newSerialToDefinitions.putAll(serialToDefinitions);
    newClassToDefinitions.putAll(classToDefinitions);

    for (LayoutSerializationDefinition definition : definitions) {
      checkArgument(!RESERVED_SERIAL_VERSIONS.contains(definition.serialVersion));
      LayoutSerializationDefinition oldDef1 =
          newSerialToDefinitions.put(definition.serialVersion, definition);
      if (oldDef1 != null) {
        checkArgument(oldDef1.clazz.equals(definition.clazz));
      }
      LayoutSerializationDefinition oldDef2 =
          newClassToDefinitions.put(definition.clazz, definition);
      if (oldDef2 != null) {
        checkArgument(oldDef2.serialVersion == definition.serialVersion);
      }
    }

    serialToDefinitions = new HashMap<>(newSerialToDefinitions);
    classToDefinitions = new HashMap<>(newClassToDefinitions);
  }

  static void write(Layout layout, DataOutput dataOutput) throws IOException {
    LayoutSerializationDefinition definition = classToDefinitions.get(layout.getClass());
    if (definition == null) {
      throw new IOException(layout.getClass() + " has not been registered for serialization!");
    }
    dataOutput.writeLong(definition.serialVersion);
    definition.writer.write(layout, dataOutput);
  }

  static Layout read(DataInput dataInput) throws IOException {
    long serializationVersion = dataInput.readLong();
    LayoutSerializationDefinition definition = serialToDefinitions.get(serializationVersion);
    if (definition == null) {
      throw new IOException(serializationVersion + " is an unknown layout serialization version!");
    }
    return definition.reader.read(dataInput);
  }

  static {
    LayoutSerialization.register(
        new LayoutSerializationDefinition[] {
          new LayoutSerializationDefinition(
              0x7f862c3808df6fcdL, CustomLayout.class, CustomLayout::write, CustomLayout::read),
          new LayoutSerializationDefinition(
              0x05d0c7e2dc0316e8L,
              LogLinearLayout.class,
              LogLinearLayout::write,
              LogLinearLayout::read),
          new LayoutSerializationDefinition(
              0x9d36115de11d38d6L,
              LogQuadraticLayout.class,
              LogQuadraticLayout::write,
              LogQuadraticLayout::read),
          new LayoutSerializationDefinition(
              0x70c0ef16c3809948L,
              LogOptimalLayout.class,
              LogOptimalLayout::write,
              LogOptimalLayout::read),
          new LayoutSerializationDefinition(
              0xf6e717a16f0a6a4aL,
              OpenTelemetryExponentialBucketsLayout.class,
              OpenTelemetryExponentialBucketsLayout::write,
              OpenTelemetryExponentialBucketsLayout::read)
        });
  }
}
