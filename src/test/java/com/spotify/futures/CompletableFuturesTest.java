/*-
 * -\-\-
 * completable-futures
 * --
 * Copyright (C) 2016 - 2023 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.futures;

import static com.spotify.futures.CompletableFutures.allAsList;
import static com.spotify.futures.CompletableFutures.allAsMap;
import static com.spotify.futures.CompletableFutures.combine;
import static com.spotify.futures.CompletableFutures.combineFutures;
import static com.spotify.futures.CompletableFutures.dereference;
import static com.spotify.futures.CompletableFutures.exceptionallyCompletedFuture;
import static com.spotify.futures.CompletableFutures.exceptionallyCompose;
import static com.spotify.futures.CompletableFutures.getCompleted;
import static com.spotify.futures.CompletableFutures.getException;
import static com.spotify.futures.CompletableFutures.handleCompose;
import static com.spotify.futures.CompletableFutures.joinList;
import static com.spotify.futures.CompletableFutures.joinMap;
import static com.spotify.futures.CompletableFutures.poll;
import static com.spotify.futures.CompletableFutures.successfulAsList;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Matcher;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CompletableFuturesTest {

  private DeterministicScheduler executor;

  @BeforeEach
  public void setUp() {
    executor = new DeterministicScheduler();
  }

  @Test
  public void allAsList_empty() {
    final List<CompletionStage<String>> input = emptyList();
    assertThat(allAsList(input), completesTo(emptyList()));
  }

  @Test
  public void allAsList_one() {
    final String value = "a";
    final List<CompletionStage<String>> input = singletonList(completedFuture(value));
    assertThat(allAsList(input), completesTo(singletonList(value)));
  }

  @Test
  public void allAsList_multiple() {
    final List<String> values = asList("a", "b", "c");
    final List<CompletableFuture<String>> input =
        values.stream().map(CompletableFuture::completedFuture).collect(toList());
    assertThat(allAsList(input), completesTo(values));
  }

  @Test
  public void allAsList_exceptional() {
    final RuntimeException boom = new RuntimeException("boom");
    final List<CompletionStage<String>> input =
        asList(completedFuture("a"), exceptionallyCompletedFuture(boom), completedFuture("b"));

    final ExecutionException e = assertThrows(ExecutionException.class, allAsList(input)::get);
    assertThat(e.getCause(), is(equalTo(boom)));
  }

  @Test
  public void allAsList_exceptional_failFast() {
    final CompletableFuture<String> incomplete = incompleteFuture();
    final CompletableFuture<String> failed = exceptionallyCompletedFuture(new TimeoutException());
    final List<CompletionStage<String>> input = asList(incomplete, failed);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> allAsList(input).join());
    assertThat(e.getCause(), instanceOf(TimeoutException.class));
  }

  @Test
  public void allAsList_null() {
    assertThrows(NullPointerException.class, () -> allAsList(null));
  }

  @Test
  public void allAsList_containsNull() {
    final List<CompletionStage<String>> input =
        asList(completedFuture("a"), null, completedFuture("b"));

    assertThrows(NullPointerException.class, () -> allAsList(input));
  }

  @Test
  public void allAsMap_empty() {
    final Map<String, CompletionStage<String>> input = emptyMap();
    assertThat(allAsMap(input), completesTo(emptyMap()));
  }

  @Test
  public void allAsMap_one() {
    final String key = "1";
    final String value = "a";
    final Map<String, CompletionStage<String>> input = singletonMap(key, completedFuture(value));
    assertThat(allAsMap(input), completesTo(singletonMap(key, value)));
  }

  @Test
  public void allAsMap_multiple() {
    final List<String> keys = asList("1", "2", "3");
    final List<String> values = asList("a", "b", "c");
    final List<CompletableFuture<String>> stagedValues =
        values.stream().map(CompletableFuture::completedFuture).collect(toList());
    final Map<String, CompletableFuture<String>> input = asMap(keys, stagedValues);
    assertThat(allAsMap(input), completesTo(asMap(keys, values)));
  }

  @Test
  public void allAsMap_exceptional() throws Exception {
    final List<String> keys = asList("1", "2", "3");
    final RuntimeException boom = new RuntimeException("boom");
    final List<CompletionStage<String>> values =
        asList(completedFuture("a"), exceptionallyCompletedFuture(boom), completedFuture("b"));
    Map<String, CompletionStage<String>> input = asMap(keys, values);
    final ExecutionException e = assertThrows(ExecutionException.class, allAsMap(input)::get);
    assertThat(e.getCause(), is(equalTo(boom)));
  }

  @Test
  public void allAsMap_null() {
    assertThrows(NullPointerException.class, () -> allAsMap(null));
  }

  @Test
  public void allAsMap_valueContainsNull() {
    final List<String> keys = asList("1", "2", "3");
    final List<CompletionStage<String>> values =
        asList(completedFuture("a"), null, completedFuture("b"));

    final Map<String, CompletionStage<String>> input = asMap(keys, values);
    assertThrows(NullPointerException.class, () -> allAsMap(input));
  }

  @Test
  public void allAsMap_keyContainsNull() {
    final List<String> keys = asList("1", null, "3");
    final List<String> values = asList("a", "b", "c");
    final List<CompletableFuture<String>> stagedValues =
        values.stream().map(CompletableFuture::completedFuture).collect(toList());
    ;

    final Map<String, CompletableFuture<String>> input = asMap(keys, stagedValues);
    assertThat(allAsMap(input), completesTo(asMap(keys, values)));
  }

  @Test
  public void successfulAsList_exceptionalAndNull() {
    final List<CompletableFuture<String>> input =
        asList(
            completedFuture("a"),
            exceptionallyCompletedFuture(new RuntimeException("boom")),
            completedFuture(null),
            completedFuture("d"));
    final List<String> expected = asList("a", "default", null, "d");
    assertThat(successfulAsList(input, t -> "default"), completesTo(expected));
  }

  @Test
  public void getCompleted_done() {
    final CompletionStage<String> future = completedFuture("hello");
    assertThat(getCompleted(future), is("hello"));
  }

  @Test
  public void getCompleted_exceptional() {
    final Exception ex = new Exception("boom");
    final CompletionStage<String> future = exceptionallyCompletedFuture(ex);
    final Exception e = assertThrows(Exception.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void getCompleted_nilResult() {
    final CompletableFuture<Void> future = completedFuture(null);
    assertNull(getCompleted(future));
  }

  @Test
  public void getCompleted_pending() {
    final CompletionStage<String> future = new CompletableFuture<>();

    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void getException_completedExceptionally() {
    final Exception ex = new Exception("boom");
    final CompletionStage<String> future = exceptionallyCompletedFuture(ex);
    assertThat(getException(future), is(ex));
  }

  @Test
  public void getException_completedNormally() {
    final CompletionStage<String> future = completedFuture("hello");
    assertThrows(IllegalStateException.class, () -> getException(future));
  }

  @Test
  public void getException_pending() {
    final CompletionStage<String> future = new CompletableFuture<>();
    assertThrows(IllegalStateException.class, () -> getException(future));
  }

  @Test
  public void getException_cancelled() {
    final CompletionStage<String> future = new CompletableFuture<>();
    future.toCompletableFuture().cancel(true);
    assertThrows(CancellationException.class, () -> getException(future));
  }

  @Test
  public void getException_returnsNullIfImplementationDoesNotThrow() {
    final CompletableFuture<Void> future = new NonThrowingFuture<>();
    future.completeExceptionally(new NullPointerException());
    assertNull(getException(future));
  }

  @Test
  public void exceptionallyCompletedFuture_completed() {
    final CompletableFuture<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    assertThat(future.isCompletedExceptionally(), is(true));
  }

  @Test
  public void exceptionallyCompletedFuture_throws() throws Exception {
    final Exception ex = new Exception("boom");
    final CompletableFuture<String> future = exceptionallyCompletedFuture(ex);

    final ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void exceptionallyCompletedFuture_null() {
    assertThrows(NullPointerException.class, () -> exceptionallyCompletedFuture(null));
  }

  @Test
  public void joinList_empty() throws Exception {
    final List<String> result = Stream.<CompletableFuture<String>>of().collect(joinList()).get();

    assertThat(result, not(nullValue()));
    assertThat(result, hasSize(0));
  }

  @Test
  public void joinList_one() throws Exception {
    final List<String> result = Stream.of(completedFuture("a")).collect(joinList()).get();

    assertThat(result, hasSize(1));
    assertThat(result, contains("a"));
  }

  @Test
  public void joinList_two() throws Exception {
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = completedFuture("world");

    final List<String> result = Stream.of(a, b).collect(joinList()).get();
    assertThat(result, contains("hello", "world"));
  }

  @Test
  public void joinList_mixedStageTypes() throws Exception {
    // Note that a and b use different subclasses of CompletionStage
    final CompletionStage<String> a = completedFuture("hello");
    final CompletableFuture<String> b = completedFuture("world");

    final List<String> result = Stream.of(a, b).collect(joinList()).get();
    assertThat(result, contains("hello", "world"));
  }

  @Test
  public void joinList_mixedValueTypes() throws Exception {
    // Note that a and b have different result types
    final CompletionStage<Integer> a = completedFuture(3);
    final CompletableFuture<Long> b = completedFuture(4L);

    final Stream<? extends CompletionStage<? extends Number>> s = Stream.of(a, b);

    final List<? extends Number> result = s.collect(joinList()).get();
    assertThat(result, contains(3, 4L));
  }

  @Test
  public void joinList_exceptional() throws Exception {
    final RuntimeException ex = new RuntimeException("boom");
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = exceptionallyCompletedFuture(ex);

    final CompletableFuture<List<String>> result = Stream.of(a, b).collect(joinList());

    final ExecutionException e = assertThrows(ExecutionException.class, result::get);
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void joinList_containsNull() {
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = null;
    final Stream<CompletableFuture<String>> stream = Stream.of(a, b);

    assertThrows(NullPointerException.class, () -> stream.collect(joinList()));
  }

  @Test
  public void joinMap_empty() throws Exception {
    final Map<String, String> result =
        Stream.<String>of().collect(joinMap(identity(), CompletableFuture::completedFuture)).get();

    assertThat(result, not(nullValue()));
    assertThat(result.values(), hasSize(0));
  }

  @Test
  public void joinMap_one() throws Exception {
    final Map<String, String> result =
        Stream.of("a").collect(joinMap(identity(), k -> completedFuture(k + "v"))).get();

    assertThat(result.values(), hasSize(1));
    assertThat(result.values(), contains("av"));
    assertThat(result.keySet(), contains("a"));
  }

  @Test
  public void joinMap_two() throws Exception {
    final Map<String, String> result =
        Stream.of("hello", "world")
            .collect(joinMap(e -> "k " + e, e -> completedFuture("v " + e)))
            .get();
    assertThat(result.entrySet(), hasSize(2));
    assertThat(result, hasEntry("k hello", "v hello"));
    assertThat(result, hasEntry("k world", "v world"));
  }

  @Test
  public void joinMap_exceptional() throws Exception {
    final RuntimeException ex = new RuntimeException("boom");
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = exceptionallyCompletedFuture(ex);

    final CompletableFuture<Map<String, String>> result =
        Stream.of("a", "b").collect(joinMap(identity(), v -> v.equals("a") ? a : b));

    final ExecutionException e = assertThrows(ExecutionException.class, result::get);
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void joinMap_containsNull() {
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = null;
    final Stream<String> stream = Stream.of("a", "b");

    assertThrows(
        NullPointerException.class,
        () -> stream.collect(joinMap(identity(), v -> v.equals("a") ? a : b)));
  }

  @Test
  public void dereference_completed() {
    final CompletionStage<String> future = completedFuture("hello");
    final CompletionStage<String> dereferenced = dereference(completedFuture(future));

    assertThat(dereferenced, completesTo("hello"));
  }

  @Test
  public void dereference_exceptional() {
    final IllegalArgumentException ex = new IllegalArgumentException();
    final CompletionStage<Object> future = exceptionallyCompletedFuture(ex);
    final CompletionStage<Object> dereferenced = dereference(completedFuture(future));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(dereferenced));
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void dereference_null() {
    final CompletionStage<Object> dereferenced = dereference(completedFuture(null));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(dereferenced));
    assertThat(e.getCause(), is(instanceOf(NullPointerException.class)));
  }

  @Test
  public void exceptionallyCompose_complete() {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final CompletableFuture<String> fallback = completedFuture("hello");

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> fallback);

    assertThat(composed, completesTo("hello"));
  }

  @Test
  public void exceptionallyCompose_exceptional() {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final IllegalStateException fallbackException = new IllegalStateException();
    final CompletableFuture<String> fallback = exceptionallyCompletedFuture(fallbackException);

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> fallback);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(composed));
    assertThat(e.getCause(), is(equalTo(fallbackException)));
  }

  @Test
  public void exceptionallyCompose_unused() {
    final CompletionStage<String> future = completedFuture("hello");
    final IllegalStateException fallbackException = new IllegalStateException();
    final CompletableFuture<String> fallback = exceptionallyCompletedFuture(fallbackException);

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> fallback);
    assertThat(composed, completesTo("hello"));
  }

  @Test
  public void exceptionallyCompose_throws() {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final IllegalStateException ex = new IllegalStateException();

    final CompletionStage<String> composed =
        exceptionallyCompose(
            future,
            throwable -> {
              throw ex;
            });

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(composed));
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void exceptionallyCompose_returnsNull() {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));

    final CompletionStage<String> composed = exceptionallyCompose(future, throwable -> null);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(composed));
    assertThat(e.getCause(), is(instanceOf(NullPointerException.class)));
  }

  @Test
  public void handleCompose_completed() {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));

    final CompletionStage<String> composed =
        handleCompose(future, (s, t) -> completedFuture("hello"));

    assertThat(composed, completesTo("hello"));
  }

  @Test
  public void handleCompose_failure() {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final IllegalStateException ex = new IllegalStateException();

    final CompletionStage<String> composed =
        handleCompose(future, (s, t) -> exceptionallyCompletedFuture(ex));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(composed));
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void handleCompose_throws() {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final IllegalStateException ex = new IllegalStateException();

    final CompletionStage<String> composed =
        handleCompose(
            future,
            (s, throwable) -> {
              throw ex;
            });

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(composed));
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void handleCompose_returnsNull() {
    final CompletionStage<String> future = exceptionallyCompletedFuture(new Exception("boom"));
    final CompletionStage<String> composed = handleCompose(future, (s, throwable) -> null);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(composed));
    assertThat(e.getCause(), is(instanceOf(NullPointerException.class)));
  }

  @Test
  public void combine2_completed() {
    final CompletionStage<String> future =
        combine(completedFuture("a"), completedFuture("b"), (a, b) -> a + b);

    assertThat(future, completesTo("ab"));
  }

  @Test
  public void combine2_exceptional() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b) -> a + b);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combine3_completed() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            (a, b, c) -> a + b + c);

    assertThat(future, completesTo("abc"));
  }

  @Test
  public void combine3_exceptional() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c) -> a + b + c);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combine4_completed() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            (a, b, c, d) -> a + b + c + d);

    assertThat(future, completesTo("abcd"));
  }

  @Test
  public void combine4_exceptional() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d) -> a + b + c + d);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combine4_incomplete() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            incompleteFuture(),
            (a, b, c, d) -> a + b + c + d);
    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void combine5_completed() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            completedFuture("e"),
            (a, b, c, d, e) -> a + b + c + d + e);

    assertThat(future, completesTo("abcde"));
  }

  @Test
  public void combine5_exceptional() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d, e) -> a + b + c + d + e);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combine5_incomplete() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            incompleteFuture(),
            (a, b, c, d, e) -> a + b + c + d + e);
    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void combine6_completed() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            completedFuture("e"),
            completedFuture("f"),
            (a, b, c, d, e, f) -> a + b + c + d + e + f);

    assertThat(future, completesTo("abcdef"));
  }

  @Test
  public void combine6_exceptional() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            completedFuture("e"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d, e, f) -> a + b + c + d + e + f);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combine6_incomplete() {
    final CompletionStage<String> future =
        combine(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            completedFuture("e"),
            incompleteFuture(),
            (a, b, c, d, e, f) -> a + b + c + d + e + f);
    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void combineFutures2_completed() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"), completedFuture("b"), (a, b) -> completedFuture(a + b));

    assertThat(future, completesTo("ab"));
  }

  @Test
  public void combineFutures2_incomplete() {
    final CompletionStage<String> future =
        combineFutures(completedFuture("a"), incompleteFuture(), (a, b) -> completedFuture(a + b));

    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void combineFutures2_exceptional() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b) -> completedFuture(a + b));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combineFutures3_completed() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            (a, b, c) -> completedFuture(a + b + c));

    assertThat(future, completesTo("abc"));
  }

  @Test
  public void combineFutures3_incomplete() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            incompleteFuture(),
            (a, b, c) -> completedFuture(a + b + c));

    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void combineFutures3_exceptional() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c) -> completedFuture(a + b + c));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combineFutures4_completed() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            (a, b, c, d) -> completedFuture(a + b + c + d));

    assertThat(future, completesTo("abcd"));
  }

  @Test
  public void combineFutures4_incomplete() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            incompleteFuture(),
            (a, b, c, d) -> completedFuture(a + b + c + d));

    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void combineFutures4_exceptional() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d) -> completedFuture(a + b + c + d));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combineFutures5_completed() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            completedFuture("e"),
            (a, b, c, d, e) -> completedFuture(a + b + c + d + e));

    assertThat(future, completesTo("abcde"));
  }

  @Test
  public void combineFutures5_incomplete() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            incompleteFuture(),
            (a, b, c, d, e) -> completedFuture(a + b + c + d + e));

    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void combineFutures5_exceptional() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d, e) -> completedFuture(a + b + c + d + e));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combineFutures6_completed() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            completedFuture("e"),
            completedFuture("f"),
            (a, b, c, d, e, f) -> completedFuture(a + b + c + d + e + f));

    assertThat(future, completesTo("abcdef"));
  }

  @Test
  public void combineFutures6_incomplete() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            completedFuture("e"),
            incompleteFuture(),
            (a, b, c, d, e, f) -> completedFuture(a + b + c + d + e + f));

    assertThrows(IllegalStateException.class, () -> getCompleted(future));
  }

  @Test
  public void combineFutures6_exceptional() {
    final CompletionStage<String> future =
        combineFutures(
            completedFuture("a"),
            completedFuture("b"),
            completedFuture("c"),
            completedFuture("d"),
            completedFuture("e"),
            exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d, e, f) -> completedFuture(a + b + c + d + e + f));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combineVararg_success() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = completedFuture("b");
    final CompletionStage<String> future =
        combine(combined -> combined.get(first) + combined.get(second), first, second);

    assertEquals("ab", getCompleted(future));
  }

  @Test
  public void combineList_success() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = completedFuture("b");
    final CompletionStage<String> future =
        combine(combined -> combined.get(first) + combined.get(second), asList(first, second));

    assertEquals("ab", getCompleted(future));
  }

  @Test
  public void combineList_nullValues_success() {
    final CompletionStage<String> first = completedFuture(null);
    final CompletionStage<String> future = combine(combined -> combined.get(first), first);

    assertNull(getCompleted(future));
  }

  @Test
  public void combineVararg_exceptional() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second =
        exceptionallyCompletedFuture(new IllegalStateException());
    final CompletionStage<String> future =
        combine(combined -> combined.get(first) + combined.get(second), first, second);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combineList_exceptional() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second =
        exceptionallyCompletedFuture(new IllegalStateException());
    final CompletionStage<String> future =
        combine(combined -> combined.get(first) + combined.get(second), asList(first, second));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  @Test
  public void combineVararg_misuse() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = completedFuture("b");
    final CompletionStage<String> third = completedFuture("c");
    final CompletionStage<String> future =
        combine(
            combined -> combined.get(first) + combined.get(second) + combined.get(third),
            first,
            second);

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void combineList_misuse() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = completedFuture("b");
    final CompletionStage<String> third = completedFuture("c");
    final CompletionStage<String> future =
        combine(
            combined -> combined.get(first) + combined.get(second) + combined.get(third),
            asList(first, second));

    final CompletionException e =
        assertThrows(CompletionException.class, () -> getCompleted(future));
    assertThat(e.getCause(), is(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void ctor_preventInstantiation() throws Exception {
    final Constructor<CompletableFutures> ctor = CompletableFutures.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    final InvocationTargetException e =
        assertThrows(InvocationTargetException.class, ctor::newInstance);
    assertThat(e.getCause(), is(instanceOf(IllegalAccessError.class)));
  }

  @Test
  public void poll_done() {
    final Supplier<Optional<String>> supplier = () -> Optional.of("done");
    final CompletableFuture<String> future = poll(supplier, Duration.ofMillis(2), executor);

    executor.runNextPendingCommand();
    assertThat(future, completesTo("done"));
  }

  @Test
  public void poll_twice() {
    final List<Optional<String>> results = asList(Optional.empty(), Optional.of("done"));
    final Supplier<Optional<String>> supplier = results.iterator()::next;
    final CompletableFuture<String> future = poll(supplier, Duration.ofMillis(2), executor);

    executor.tick(1, MILLISECONDS);
    assertThat(future.isDone(), is(false));

    executor.tick(10, MILLISECONDS);
    assertThat(future, completesTo("done"));
  }

  @Test
  public void poll_taskReturnsNull() throws Exception {
    final Supplier<Optional<String>> supplier = () -> null;
    final CompletableFuture<String> future = poll(supplier, Duration.ofMillis(2), executor);

    executor.runNextPendingCommand();
    final ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertThat(e.getCause(), is(instanceOf(NullPointerException.class)));
  }

  @Test
  public void poll_taskThrows() throws Exception {
    final RuntimeException ex = new RuntimeException("boom");
    final Supplier<Optional<String>> supplier =
        () -> {
          throw ex;
        };
    final CompletableFuture<String> future = poll(supplier, Duration.ofMillis(2), executor);

    executor.runNextPendingCommand();
    final ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertThat(e.getCause(), is(equalTo(ex)));
  }

  @Test
  public void poll_scheduled() {
    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final Supplier<Optional<String>> supplier = () -> Optional.of("hello");
    poll(supplier, Duration.ofMillis(2), executor);

    verify(executor).scheduleAtFixedRate(any(), eq(0L), eq(2L), eq(MILLISECONDS));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void poll_resultFutureCanceled() {
    final ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    when(executor.scheduleAtFixedRate(any(), anyLong(), anyLong(), any()))
        .thenReturn(scheduledFuture);

    final CompletableFuture<String> future = poll(Optional::empty, Duration.ofMillis(2), executor);
    future.cancel(true);

    verify(scheduledFuture).cancel(true);
  }

  @Test
  public void poll_notRunningAfterCancel() {
    final CompletableFuture<String> future = poll(Optional::empty, Duration.ofMillis(2), executor);

    future.cancel(true);

    executor.tick(5, MILLISECONDS);
    assertThat(executor.isIdle(), is(true));
  }

  private static <T> CompletableFuture<T> incompleteFuture() {
    return new CompletableFuture<>();
  }

  private static <T> Matcher<CompletionStage<T>> completesTo(final T expected) {
    return completesTo(is(expected));
  }

  private static <T> Matcher<CompletionStage<T>> completesTo(final Matcher<T> expected) {
    return new CustomTypeSafeMatcher<CompletionStage<T>>(
        "completes to " + String.valueOf(expected)) {
      @Override
      protected boolean matchesSafely(CompletionStage<T> item) {
        try {
          final T value = item.toCompletableFuture().get(1, SECONDS);
          return expected.matches(value);
        } catch (Exception ex) {
          return false;
        }
      }
    };
  }

  private static <U, T> Map<U, T> asMap(final List<U> keys, final List<T> input) {
    Map<U, T> map = new HashMap<>();
    for (int i = 0; i < keys.size(); i++) {
      map.put(keys.get(i), input.get(i));
    }
    return map;
  }

  private static class NonThrowingFuture<T> extends CompletableFuture<T> {
    @Override
    public T join() {
      if (this.isCompletedExceptionally()) {
        return null;
      }
      return super.join();
    }
  }
}
