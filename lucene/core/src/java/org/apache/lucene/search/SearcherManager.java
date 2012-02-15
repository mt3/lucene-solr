package org.apache.lucene.search;

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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;

/**
 * Utility class to safely share {@link IndexSearcher} instances across multiple
 * threads, while periodically reopening. This class ensures each searcher is
 * closed only once all threads have finished using it.
 * 
 * <p>
 * Use {@link #acquire} to obtain the current searcher, and {@link #release} to
 * release it, like this:
 * 
 * <pre class="prettyprint">
 * IndexSearcher s = manager.acquire();
 * try {
 *   // Do searching, doc retrieval, etc. with s
 * } finally {
 *   manager.release(s);
 * }
 * // Do not use s after this!
 * s = null;
 * </pre>
 * 
 * <p>
 * In addition you should periodically call {@link #maybeRefresh}. While it's
 * possible to call this just before running each query, this is discouraged
 * since it penalizes the unlucky queries that do the reopen. It's better to use
 * a separate background thread, that periodically calls maybeReopen. Finally,
 * be sure to call {@link #close} once you are done.
 * 
 * @see SearcherFactory
 * 
 * @lucene.experimental
 */
public final class SearcherManager extends ReferenceManager<IndexSearcher> {

  private final SearcherFactory searcherFactory;

  /**
   * Creates and returns a new SearcherManager from the given
   * {@link IndexWriter}.
   * 
   * @param writer
   *          the IndexWriter to open the IndexReader from.
   * @param applyAllDeletes
   *          If <code>true</code>, all buffered deletes will be applied (made
   *          visible) in the {@link IndexSearcher} / {@link DirectoryReader}.
   *          If <code>false</code>, the deletes may or may not be applied, but
   *          remain buffered (in IndexWriter) so that they will be applied in
   *          the future. Applying deletes can be costly, so if your app can
   *          tolerate deleted documents being returned you might gain some
   *          performance by passing <code>false</code>. See
   *          {@link DirectoryReader#openIfChanged(DirectoryReader, IndexWriter, boolean)}.
   * @param searcherFactory
   *          An optional {@link SearcherFactory}. Pass <code>null</code> if you
   *          don't require the searcher to be warmed before going live or other
   *          custom behavior.
   * 
   * @throws IOException
   */
  public SearcherManager(IndexWriter writer, boolean applyAllDeletes, SearcherFactory searcherFactory) throws IOException {
    if (searcherFactory == null) {
      searcherFactory = new SearcherFactory();
    }
    this.searcherFactory = searcherFactory;
    current = searcherFactory.newSearcher(DirectoryReader.open(writer, applyAllDeletes));
  }
  
  @Override
  protected void decRef(IndexSearcher reference) throws IOException {
    reference.getIndexReader().decRef();
  }
  
  @Override
  protected IndexSearcher refreshIfNeeded(IndexSearcher referenceToRefresh) throws IOException {
    final IndexReader r = referenceToRefresh.getIndexReader();
    final IndexReader newReader = (r instanceof DirectoryReader) ?
      DirectoryReader.openIfChanged((DirectoryReader) r) : null;
    if (newReader == null) {
      return null;
    } else {
      return searcherFactory.newSearcher(newReader);
    }
  }
  
  @Override
  protected boolean tryIncRef(IndexSearcher reference) {
    return reference.getIndexReader().tryIncRef();
  }

  /**
   * Creates and returns a new SearcherManager from the given {@link Directory}. 
   * @param dir the directory to open the DirectoryReader on.
   * @param searcherFactory An optional {@link SearcherFactory}. Pass
   *        <code>null</code> if you don't require the searcher to be warmed
   *        before going live or other custom behavior.
   *        
   * @throws IOException
   */
  public SearcherManager(Directory dir, SearcherFactory searcherFactory) throws IOException {
    if (searcherFactory == null) {
      searcherFactory = new SearcherFactory();
    }
    this.searcherFactory = searcherFactory;
    current = searcherFactory.newSearcher(DirectoryReader.open(dir));
  }

  /**
   * Returns <code>true</code> if no changes have occured since this searcher
   * ie. reader was opened, otherwise <code>false</code>.
   * @see DirectoryReader#isCurrent() 
   */
  public boolean isSearcherCurrent() throws IOException {
    final IndexSearcher searcher = acquire();
    try {
      final IndexReader r = searcher.getIndexReader();
      return r instanceof DirectoryReader ?
        ((DirectoryReader ) r).isCurrent() :
        true;
    } finally {
      release(searcher);
    }
  }
  
}