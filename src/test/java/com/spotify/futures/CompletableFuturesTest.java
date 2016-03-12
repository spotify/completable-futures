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
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static com.spotify.futures.CompletableFutures.allAsList;
import static com.spotify.futures.CompletableFutures.dereference;
import static com.spotify.futures.CompletableFutures.exceptionallyCompletedFuture;
import static com.spotify.futures.CompletableFutures.exceptionallyCompose;
import static com.spotify.futures.CompletableFutures.getCompleted;
import static com.spotify.futures.CompletableFutures.handleCompose;
import static com.spotify.futures.CompletableFutures.joinList;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class CompletableFuturesTest {

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void allAsList_empty() throws Exception {
    final List<CompletionStage<String>> input = emptyList();
    assertThat(allAsList(input).get(), is(emptyList()));
  }

  @Test
  public void allAsList_one() throws Exception {
    final String value = "a";
    final List<CompletionStage<String>> input = singletonList(completedFuture(value));
    assertThat(allAsList(input).get(), is(singletonList(value)));
  }

  @Test
  public void allAsList_multiple() throws Exception {
    final List<String> values = asList("a", "b", "c");
    final List<CompletableFuture<String>> input = values.stream()
        .map(CompletableFuture::completedFuture)
        .collect(toList());
    assertThat(allAsList(input).get(), is(values));
  }

  @Test
  public void allAsList_exceptional() throws Exception {
    final RuntimeException ex = new RuntimeException("boom");
    final List<CompletionStage<String>> input = asList(
        completedFuture("a"),
        exceptionallyCompletedFuture(ex),
        completedFuture("b")
    );

    exception.expectCause(is(ex));
    allAsList(input).get();
  }

  @Test
  public void allAsList_null() throws Exception {
    exception.expect(NullPointerException.class);
    allAsList(null);
  }

  @Test
  public void allAsList_containsNull() throws Exception {
    final List<CompletionStage<String>> input = asList(
        completedFuture("a"),
        null,
        completedFuture("b")
    );

    exception.expect(NullPointerException.class);
    allAsList(input);
  }

  @Test
  public void getCompleted_done() throws Exception {
    final CompletionStage<String> future = completedFuture("hello");
    assertEquals("hello", getCompleted(future));
  }

  @Test
  public void getCompleted_exceptional() throws Exception {
    final Exception ex = new Exception("boom");
    final CompletionStage<String> future = exceptionallyCompletedFuture(ex);
    exception.expectCause(is(ex));
    getCompleted(future);
  }

  @Test
  public void getCompleted_pending() throws Exception {
    final CompletionStage<String> future = new CompletableFuture<>();

    exception.expect(IllegalStateException.class);
    getCompleted(future);
  }

  @Test
  public void exceptionallyCompletedFuture_completed() throws Exception {
    final CompletableFuture<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    assertThat(future.isCompletedExceptionally(), is(true));
  }

  @Test
  public void exceptionallyCompletedFuture_throws() throws Exception {
    final Exception ex = new Exception("boom");
    final CompletableFuture<String> future = exceptionallyCompletedFuture(ex);

    exception.expectCause(is(ex));
    future.get();
  }

  @Test
  public void exceptionallyCompletedFuture_null() throws Exception {
    exception.expect(NullPointerException.class);
    exceptionallyCompletedFuture(null);
  }

  @Test
  public void joinList_empty() throws Exception {
    final List<String> result = Stream.<CompletableFuture<String>>of()
        .collect(joinList())
        .get();

    assertThat(result, not(nullValue()));
    assertThat(result, hasSize(0));
  }

  @Test
  public void joinList_one() throws Exception {
    final List<String> result = Stream.of(completedFuture("a"))
        .collect(joinList())
        .get();

    assertThat(result, hasSize(1));
    assertThat(result, contains("a"));
  }

  @Test
  public void joinList_two() throws Exception {
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = completedFuture("world");

    final List<String> result = Stream.of(a, b)
        .collect(joinList())
        .get();
    assertThat(result, contains("hello", "world"));
  }

  @Test
  public void joinList_mixedStageTypes() throws Exception {
    // Note that a and b use different subclasses of CompletionStage
    final CompletionStage<String> a = completedFuture("hello");
    final CompletableFuture<String> b = completedFuture("world");

    final List<String> result = Stream.of(a, b)
        .collect(joinList())
        .get();
    assertThat(result, contains("hello", "world"));
  }

  @Test
  public void joinList_mixedValueTypes() throws Exception {
    // Note that a and b have different result types
    final CompletionStage<Integer> a = completedFuture(3);
    final CompletableFuture<Long> b = completedFuture(4L);

    final List<? extends Number> result = Stream.of(a, b)
        .collect(joinList())
        .get();
    assertThat(result, contains(3, 4L));
  }

  @Test
  public void joinList_exceptional() throws Exception {
    final RuntimeException ex = new RuntimeException("boom");
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = exceptionallyCompletedFuture(ex);

    final CompletableFuture<List<String>> result = Stream.of(a, b).collect(joinList());

    exception.expectCause(is(ex));
    result.get();
  }

  @Test
  public void joinList_containsNull() throws Exception {
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = null;

    exception.expect(NullPointerException.class);
    Stream.of(a, b).collect(joinList());
  }

  @Test
  public void dereference_completed() throws Exception {
    final CompletionStage<String> future = completedFuture("hello");
    final CompletionStage<String> dereferenced = dereference(completedFuture(future));

    assertThat(dereferenced, is(future));
  }

  @Test
  public void dereference_exceptional() throws Exception {
    final IllegalArgumentException ex = new IllegalArgumentException();
    final CompletionStage<Object> future = exceptionallyCompletedFuture(ex);
    final CompletionStage<Object> dereferenced = dereference(completedFuture(future));

    exception.expectCause(is(ex));
    getCompleted(dereferenced);
  }

  @Test
  public void dereference_null() throws Exception {
    final CompletionStage<Object> dereferenced = dereference(completedFuture(null));

    exception.expectCause(isA(NullPointerException.class));
    getCompleted(dereferenced);
  }

  @Test
  public void exceptionallyCompose_complete() throws Exception {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final CompletableFuture<String> fallback = completedFuture("hello");

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> fallback);

    assertThat(composed, is(fallback));
  }

  @Test
  public void exceptionallyCompose_exceptional() throws Exception {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final IllegalStateException fallbackException = new IllegalStateException();
    final CompletableFuture<String> fallback = exceptionallyCompletedFuture(fallbackException);

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> fallback);

    exception.expectCause(is(fallbackException));
    getCompleted(composed);
  }

  @Test
  public void exceptionallyCompose_unused() throws Exception {
    final CompletionStage<String> future = completedFuture("hello");
    final IllegalStateException fallbackException = new IllegalStateException();
    final CompletableFuture<String> fallback = exceptionallyCompletedFuture(fallbackException);

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> fallback);
    assertThat(getCompleted(composed), is("hello"));
  }

  @Test
  public void exceptionallyCompose_throws() throws Exception {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final IllegalStateException ex = new IllegalStateException();

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> {
      throw ex;
    });

    exception.expectCause(is(ex));
    getCompleted(composed);
  }

  @Test
  public void exceptionallyCompose_returnsNull() throws Exception {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> null);

    exception.expectCause(isA(NullPointerException.class));
    getCompleted(composed);
  }

  @Test
  public void handleCompose_completed() throws Exception {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));

    final CompletionStage<String> composed =
        handleCompose(future, (s, t) -> completedFuture("hello"));

    assertEquals("hello", getCompleted(composed));

  }

  @Test
  public void handleCompose_failure() throws Exception {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final IllegalStateException ex = new IllegalStateException();

    final CompletionStage<String> composed =
        handleCompose(future, (s, t) -> exceptionallyCompletedFuture(ex));

    exception.expectCause(is(ex));
    getCompleted(composed);
  }

  @Test
  public void handleCompose_throws() throws Exception {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final IllegalStateException ex = new IllegalStateException();

    final CompletionStage<String> composed = handleCompose(future, (s, throwable) -> { throw ex; });

    exception.expectCause(is(ex));
    getCompleted(composed);
  }

  @Test
  public void handleCompose_returnsNull() throws Exception {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final CompletionStage<String> composed = handleCompose(future, (s, throwable) -> null);

    exception.expectCause(isA(NullPointerException.class));
    getCompleted(composed);
  }

  @Test
  public void testCombine3() throws Exception {
    CompletionStage<String> future = CompletableFutures.combine3(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        (a, b, c) -> a + b + c);
    assertEquals("abc", getCompleted(future));
  }

  @Test
  public void testCombine3Fails() throws Exception {
    CompletionStage<String> future = CompletableFutures.combine3(
        completedFuture("a"), completedFuture("b"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b, c) -> a + b + c);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void testCombine4() throws Exception {
    CompletionStage<String> future = CompletableFutures.combine4(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"),
        (a, b, c, d) -> a + b + c + d);
    assertEquals("abcd", getCompleted(future));
  }

  @Test
  public void testCombine4Fails() throws Exception {
    CompletionStage<String> future = CompletableFutures.combine4(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d)-> a + b + c + d);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void testCombine5() throws Exception {
    CompletionStage<String> future = CompletableFutures.combine5(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"), completedFuture("e"),
        (a, b, c, d, e) -> a + b + c + d + e);
    assertEquals("abcde", getCompleted(future));
  }

  @Test
  public void testCombine5Fails() throws Exception {
    CompletionStage<String> future = CompletableFutures.combine5(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"),
        exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d, e)-> a + b + c + d + e);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }
}
