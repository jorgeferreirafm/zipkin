/**
 * Copyright 2015-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.storage.elasticsearch.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin.internal.Util;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.SPAN;
import static zipkin.storage.elasticsearch.http.TestResponses.SERVICE_NAMES;
import static zipkin.storage.elasticsearch.http.TestResponses.SPAN_NAMES;

public class ElasticsearchHttpSpanStoreTest {
  @Rule public MockWebServer es = new MockWebServer();

  ElasticsearchHttpStorage storage = ElasticsearchHttpStorage.builder()
    .hosts(asList(es.url("").toString()))
    .build();
  ElasticsearchHttpSpanStore spanStore = new ElasticsearchHttpSpanStore(storage);

  @After public void close() throws IOException {
    storage.close();
  }

  @Test public void serviceNames_defaultsTo24HrsAgo_6x() throws Exception {
    es.enqueue(new MockResponse().setBody(SERVICE_NAMES));
    spanStore.getServiceNames().execute();

    requestLimitedTo2DaysOfIndices_singleTypeIndex();
  }

  @Test public void spanNames_defaultsTo24HrsAgo_6x() throws Exception {
    es.enqueue(new MockResponse().setBody(SPAN_NAMES));
    spanStore.getSpanNames("foo").execute();

    requestLimitedTo2DaysOfIndices_singleTypeIndex();
  }

  private void requestLimitedTo2DaysOfIndices_singleTypeIndex() throws Exception {
    long today = Util.midnightUTC(System.currentTimeMillis());
    long yesterday = today - TimeUnit.DAYS.toMillis(1);

    // 24 hrs ago always will fall into 2 days (ex. if it is 4:00pm, 24hrs ago is a different day)
    String indexesToSearch = ""
      + storage.indexNameFormatter().formatTypeAndTimestamp(SPAN, yesterday)
      + ","
      + storage.indexNameFormatter().formatTypeAndTimestamp(SPAN, today);

    RecordedRequest request = es.takeRequest();
    assertThat(request.getPath())
      .startsWith("/" + indexesToSearch + "/_search");
  }
}
