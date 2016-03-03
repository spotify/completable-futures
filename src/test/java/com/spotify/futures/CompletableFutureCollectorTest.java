/*
 * Copyright (c) 2014-2016 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.futures;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.spotify.futures.CompletableFutures.exceptionallyCompletedFuture;
import static com.spotify.futures.CompletableFutures.joinAll;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

public class CompletableFutureCollectorTest {

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void empty() throws Exception {
    final List<String> result = Stream.<CompletableFuture<String>>of()
        .collect(joinAll())
        .get();

    assertThat(result, not(nullValue()));
    assertThat(result, hasSize(0));
  }

  @Test
  public void one() throws Exception {
    final List<String> result = Stream.of(completedFuture("a"))
        .collect(joinAll())
        .get();

    assertThat(result, hasSize(1));
    assertThat(result, contains("a"));
  }

  @Test
  public void two() throws Exception {
    CompletableFuture<String> a = completedFuture("hello");
    CompletableFuture<String> b = completedFuture("world");

    final List<String> result = Stream.of(a, b)
        .collect(joinAll())
        .get();
    assertThat(result, contains("hello", "world"));
  }

  @Test
  public void exceptional() throws Exception {
    final RuntimeException ex = new RuntimeException("boom");

    CompletableFuture<String> a = completedFuture("hello");
    CompletableFuture<String> b = exceptionallyCompletedFuture(ex);

    final CompletableFuture<List<String>> result = Stream.of(a, b).collect(joinAll());

    exception.expectCause(is(ex));
    result.get();
  }

  @Test
  public void containsNull() throws Exception {
    CompletableFuture<String> a = completedFuture("hello");
    CompletableFuture<String> b = null;

    exception.expect(NullPointerException.class);
    Stream.of(a, b).collect(joinAll());
  }

}
