package org.apache.lucene.index.values;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

import org.apache.lucene.index.values.IntsImpl.IntsReader;
import org.apache.lucene.index.values.IntsImpl.IntsWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.Counter;

/**
 * @lucene.experimental
 */
public class Ints {
  // TODO - add bulk copy where possible

  private Ints() {
  }

  public static Writer getWriter(Directory dir, String id,
      Counter bytesUsed, ValueType type, IOContext context) throws IOException {
    return new IntsWriter(dir, id, bytesUsed, type, context);
  }

  public static IndexDocValues getValues(Directory dir, String id,
      int numDocs, IOContext context) throws IOException {
    return new IntsReader(dir, id, numDocs, context);
  }
}