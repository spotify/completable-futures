/*-
 * -\-\-
 * completable-futures
 * --
 * Copyright (C) 2016 - 2020 Spotify AB
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

import java.util.HashMap;
import java.util.Map;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Matcher;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

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
import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CompletableFuturesTest {

  private Consumer<List<Throwable>> partialFailureAction;
  private DeterministicScheduler executor;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    partialFailureAction = (Consumer<List<Throwable>>) mock(Consumer.class);
    executor = new DeterministicScheduler();
  }

  @Test
  public void allAsList_empty() throws Exception {
    final List<CompletionStage<String>> input = emptyList();
    assertThat(allAsList(input), completesTo(emptyList()));
  }

  @Test
  public void allAsList_one() throws Exception {
    final String value = "a";
    final List<CompletionStage<String>> input = singletonList(completedFuture(value));
    assertThat(allAsList(input), completesTo(singletonList(value)));
  }

  @Test
  public void allAsList_multiple() throws Exception {
    final List<String> values = asList("a", "b", "c");
    final List<CompletableFuture<String>> input = values.stream()
        .map(CompletableFuture::completedFuture)
        .collect(toList());
    assertThat(allAsList(input), completesTo(values));
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
  public void allAsList_exceptional_failFast() {
    final CompletableFuture<String> incomplete = incompleteFuture();
    final CompletableFuture<String> failed =
        exceptionallyCompletedFuture(new TimeoutException());
    final List<CompletionStage<String>> input =
        asList(incomplete, failed);

    try {
      allAsList(input).join();
      fail("Expected exception being thrown.");
    } catch (Exception e) {
      assertThat(e, instanceOf(CompletionException.class));
      assertThat(e.getCause(), instanceOf(TimeoutException.class));
    }
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
  public void allAsMap_empty() throws Exception {
    final Map<String, CompletionStage<String>> input = emptyMap();
    assertThat(allAsMap(input), completesTo(emptyMap()));
  }

  @Test
  public void allAsMap_one() throws Exception {
    final String key = "1";
    final String value = "a";
    final Map<String, CompletionStage<String>> input = singletonMap(key, completedFuture(value));
    assertThat(allAsMap(input), completesTo(singletonMap(key, value)));
  }

  @Test
  public void allAsMap_multiple() throws Exception {
    final List<String> keys = asList("1", "2", "3");
    final List<String> values = asList("a", "b", "c");
    final List<CompletableFuture<String>> stagedValues = values.stream()
        .map(CompletableFuture::completedFuture)
        .collect(toList());
    final Map<String, CompletableFuture<String>> input = asMap(keys, stagedValues);
    assertThat(allAsMap(input), completesTo(asMap(keys, values)));
  }

  @Test
  public void allAsMap_exceptional() throws Exception {
    final RuntimeException ex = new RuntimeException("boom");
    final List<String> keys = asList("1", "2", "3");
    final List<CompletionStage<String>> values = asList(
        completedFuture("a"),
        exceptionallyCompletedFuture(ex),
        completedFuture("b")
    );
    Map input = asMap(keys, values);
    exception.expectCause(is(ex));
    allAsMap(input).get();
  }

  @Test
  public void allAsMap_null() throws Exception {
    exception.expect(NullPointerException.class);
    allAsMap(null);
  }

  @Test
  public void allAsMap_valueContainsNull() throws Exception {
    final List<String> keys = asList("1", "2", "3");
    final List<CompletionStage<String>> values = asList(
        completedFuture("a"),
        null,
        completedFuture("b")
    );

    final Map<String, CompletionStage<String>> input = asMap(keys, values);
    exception.expect(NullPointerException.class);
    allAsMap(input);
  }

  @Test
  public void allAsMap_keyContainsNull() throws Exception {
    final List<String> keys = asList("1", null, "3");
    final List<String> values = asList("a", "b", "c");
    final List<CompletableFuture<String>> stagedValues = values.stream()
        .map(CompletableFuture::completedFuture)
        .collect(toList());;

    final Map<String, CompletableFuture<String>> input = asMap(keys, stagedValues);
    assertThat(allAsMap(input), completesTo(asMap(keys, values)));
  }

  @Test
  public void successfulAsListWithDefaultValues_exceptionalAndNull() throws Exception {
    final List<CompletableFuture<String>> input = asList(
        completedFuture("a"),
        exceptionallyCompletedFuture(new RuntimeException("boom")),
        completedFuture(null),
        completedFuture("d")
    );
    final List<String> expected = asList("a", "default", null, "d");
    assertThat(successfulAsList(input, t -> "default"), completesTo(expected));
  }

  @Test
  public void successfulAsListWithPartialFailureAction_null() {
    exception.expect(NullPointerException.class);
    successfulAsList(null, partialFailureAction);
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_null() {
    exception.expect(NullPointerException.class);
    successfulAsList(null, partialFailureAction, result -> true);
  }

  @Test
  public void successfulAsListWithPartialFailureAction_containsNull() {
    final List<CompletionStage<String>> input = asList(
        completedFuture("a"),
        null
    );

    exception.expect(NullPointerException.class);
    successfulAsList(input, partialFailureAction);
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_containsNull() {
    final List<CompletionStage<String>> input = asList(
        completedFuture("a"),
        null
    );

    exception.expect(NullPointerException.class);
    successfulAsList(input, partialFailureAction, result -> true);
  }

  @Test
  public void successfulAsListWithPartialFailureAction_empty() {
    final List<CompletionStage<String>> input = emptyList();
    assertThat(successfulAsList(input, partialFailureAction), completesTo(emptyList()));
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_empty() {
    final List<CompletionStage<String>> input = emptyList();
    assertThat(successfulAsList(input, partialFailureAction, result -> true),
        completesTo(emptyList()));
  }

  @Test
  public void successfulAsListWithPartialFailureAction_successfulResults() {
    final String result1 = "a";
    final String result2 = "b";
    final List<CompletionStage<String>> input =
        asList(completedFuture(result1), completedFuture(result2));

    assertThat(successfulAsList(input, partialFailureAction),
        completesTo(asList(result1, result2)));
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_matchingResults() {
    final String result1 = "a";
    final String result2 = "b";
    final List<CompletionStage<String>> input =
        asList(completedFuture(result1), completedFuture(result2));

    assertThat(successfulAsList(input, partialFailureAction, isEqual(result1)),
        completesTo(singletonList(result1)));
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_noMatchingResults() {
    final List<CompletionStage<String>> input = singletonList(completedFuture("a"));
    assertThat(successfulAsList(input, partialFailureAction, result -> false),
        completesTo(emptyList()));
  }

  @Test
  public void successfulAsListWithPartialFailureAction_notFailFast() {
    final CompletableFuture<String> future1 = incompleteFuture();
    final CompletableFuture<String> future2 = incompleteFuture();
    final List<CompletionStage<String>> input = asList(future1, future2);

    final CompletableFuture<List<String>> result = successfulAsList(input, partialFailureAction);

    // We complete the futures manually to control the completion order and to verify that we wait
    // for the second future to complete even if the first one fails. We wouldn't be able to do that
    // if we used completed futures above.
    future1.completeExceptionally(new RuntimeException());
    final String successfulResult = "a";
    future2.complete(successfulResult);
    assertThat(result, completesTo(singletonList(successfulResult)));
  }

  @Test
  public void successfulAsListWithPartialFailureAction_someStagesFail_successfulResultsReturned() {
    final String successfulResult = "a";
    final List<CompletionStage<String>> input =
        asList(completedFuture(successfulResult),
            exceptionallyCompletedFuture(new RuntimeException()));

    assertThat(successfulAsList(input, partialFailureAction),
        completesTo(singletonList(successfulResult)));
  }

  @Test
  public void successfulAsListWithPartialFailureAction_someStageFail_partialFailureActionCalled() {
    final Throwable exception = new RuntimeException();
    final List<CompletionStage<String>> input =
        asList(completedFuture("a"), exceptionallyCompletedFuture(exception));

    successfulAsList(input, partialFailureAction).join();

    verify(partialFailureAction).accept(singletonList(exception));
  }

  @Test
  public void successfulAsListWithPartialFailureAction_allStagesFail_exceptionReturned() {
    final Throwable exception1 = new RuntimeException("a");
    final Throwable exception2 = new RuntimeException("b");
    final List<CompletionStage<String>> input =
        asList(exceptionallyCompletedFuture(exception1), exceptionallyCompletedFuture(exception2));

    final Throwable result = getException(successfulAsList(input, partialFailureAction));

    assertThat(result, is(instanceOf(StageFailureException.class)));
    assertThat(result.getSuppressed(), is(arrayContaining(exception1, exception2)));
  }

  @Test
  public void successfulAsListWithPartialFailureAction_allStagesFail_partialFailureActionNotCalled() {
    final List<CompletionStage<String>> input =
        asList(exceptionallyCompletedFuture(new RuntimeException("a")),
            exceptionallyCompletedFuture(new RuntimeException("b")));

    try {
      successfulAsList(input, partialFailureAction).join();
    } catch (Exception e) {
      // Ignore
    }

    verify(partialFailureAction, never()).accept(any());
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_someStagesFail_matchingResultsReturned() {
    final String successfulResult = "a";
    final List<CompletionStage<String>> input =
        asList(completedFuture(successfulResult),
            exceptionallyCompletedFuture(new RuntimeException()));

    assertThat(successfulAsList(input, partialFailureAction, isEqual(successfulResult)),
        completesTo(singletonList(successfulResult)));
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_someStagesFailAndSomeMatchingResults_partialFailureActionCalled() {
    final String successfulResult = "a";
    final Throwable exception = new RuntimeException();
    final List<CompletionStage<String>> input =
        asList(completedFuture(successfulResult), exceptionallyCompletedFuture(exception));

    successfulAsList(input, partialFailureAction, isEqual(successfulResult)).join();

    verify(partialFailureAction).accept(singletonList(exception));
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_someStagesFailAndNoMatchingResults_exceptionReturned() {
    final Throwable exception = new RuntimeException();
    final List<CompletionStage<String>> input =
        asList(completedFuture("a"), exceptionallyCompletedFuture(exception));

    final Throwable result =
        getException(successfulAsList(input, partialFailureAction, successfulResult -> false));

    assertThat(result, is(instanceOf(StageFailureException.class)));
    assertThat(result.getSuppressed(), is(arrayContaining(exception)));
  }

  @Test
  public void successfulAsListWithPartialFailureActionAndPredicate_someStagesFailAndNoMatchingResults_partialFailureActionNotCalled() {
    final List<CompletionStage<String>> input =
        asList(completedFuture("a"), exceptionallyCompletedFuture(new RuntimeException()));

    try {
      successfulAsList(input, partialFailureAction, successfulResult -> false).join();
    } catch (Exception e) {
      // Ignore
    }

    verify(partialFailureAction, never()).accept(any());
  }

  @Test
  public void getCompleted_done() throws Exception {
    final CompletionStage<String> future = completedFuture("hello");
    assertThat(getCompleted(future), is("hello"));
  }

  @Test
  public void getCompleted_exceptional() throws Exception {
    final Exception ex = new Exception("boom");
    final CompletionStage<String> future = exceptionallyCompletedFuture(ex);
    exception.expectCause(is(ex));
    getCompleted(future);
  }

  @Test
  public void getCompleted_nilResult() throws Exception {
    final CompletableFuture<Void> future = completedFuture(null);
    assertNull(getCompleted(future));
  }

  @Test
  public void getCompleted_pending() throws Exception {
    final CompletionStage<String> future = new CompletableFuture<>();

    exception.expect(IllegalStateException.class);
    getCompleted(future);
  }

  @Test
  public void getException_completedExceptionally() throws Exception {
    final Exception ex = new Exception("boom");
    final CompletionStage<String> future = exceptionallyCompletedFuture(ex);
    assertThat(getException(future), is(ex));
  }

  @Test
  public void getException_completedNormally() throws Exception {
    final CompletionStage<String> future = completedFuture("hello");
    exception.expect(IllegalStateException.class);
    getException(future);
  }

  @Test
  public void getException_pending() throws Exception {
    final CompletionStage<String> future = new CompletableFuture<>();
    exception.expect(IllegalStateException.class);
    getException(future);
  }

  @Test
  public void getException_cancelled() throws Exception {
    final CompletionStage<String> future = new CompletableFuture<>();
    future.toCompletableFuture().cancel(true);
    exception.expect(CancellationException.class);
    getException(future);
  }

  @Test
  public void getException_returnsNullIfImplementationDoesNotThrow() throws Exception {
    final CompletableFuture<Void> future = new NonThrowingFuture<>();
    future.completeExceptionally(new NullPointerException());
    assertNull(getException(future));
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

    final Stream<? extends CompletionStage<? extends Number>> s = Stream.of(a, b);

    final List<? extends Number> result = s
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
    final Stream<CompletableFuture<String>> stream = Stream.of(a, b);

    exception.expect(NullPointerException.class);
    stream.collect(joinList());
  }

  @Test
  public void joinMap_empty() throws Exception {
    final Map<String, String> result = Stream.<String>of()
            .collect(joinMap(identity(), CompletableFuture::completedFuture))
            .get();

    assertThat(result, not(nullValue()));
    assertThat(result.values(), hasSize(0));
  }

  @Test
  public void joinMap_one() throws Exception {
    final Map<String, String> result = Stream.of("a")
            .collect(joinMap(identity(), k -> completedFuture(k + "v")))
            .get();

    assertThat(result.values(), hasSize(1));
    assertThat(result.values(), contains("av"));
    assertThat(result.keySet(), contains("a"));
  }

  @Test
  public void joinMap_two() throws Exception {
    final Map<String, String> result = Stream.of("hello", "world")
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

    final CompletableFuture<Map<String, String>> result = Stream.of("a", "b")
            .collect(joinMap(identity(), v -> v.equals("a") ? a : b));

    exception.expectCause(is(ex));
    result.get();
  }

  @Test
  public void joinMap_containsNull() throws Exception {
    final CompletableFuture<String> a = completedFuture("hello");
    final CompletableFuture<String> b = null;
    final Stream<String> stream = Stream.of("a", "b");

    exception.expect(NullPointerException.class);
    stream.collect(joinMap(identity(), v -> v.equals("a") ? a : b));
  }

  @Test
  public void dereference_completed() throws Exception {
    final CompletionStage<String> future = completedFuture("hello");
    final CompletionStage<String> dereferenced = dereference(completedFuture(future));

    assertThat(dereferenced, completesTo("hello"));
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

    assertThat(composed, completesTo("hello"));
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
    assertThat(composed, completesTo("hello"));
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

    assertThat(composed, completesTo("hello"));
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
  public void combine2_completed() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"),
        (a, b) -> a + b);

    assertThat(future, completesTo("ab"));
  }

  @Test
  public void combine2_exceptional() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b) -> a + b);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combine3_completed() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        (a, b, c) -> a + b + c);

    assertThat(future, completesTo("abc"));
  }

  @Test
  public void combine3_exceptional() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b, c) -> a + b + c);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combine4_completed() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"),
        (a, b, c, d) -> a + b + c + d);

    assertThat(future, completesTo("abcd"));
  }

  @Test
  public void combine4_exceptional() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d)-> a + b + c + d);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combine4_incomplete() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        incompleteFuture(),
            (a, b, c, d) -> a + b + c + d);
    exception.expect(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combine5_completed() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"), completedFuture("e"),
        (a, b, c, d, e) -> a + b + c + d + e);

    assertThat(future, completesTo("abcde"));
  }

  @Test
  public void combine5_exceptional() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"),
        exceptionallyCompletedFuture(new IllegalStateException()),
            (a, b, c, d, e)-> a + b + c + d + e);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combine5_incomplete() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"),
        incompleteFuture(),
        (a, b, c, d, e) -> a + b + c + d + e);
    exception.expect(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combine6_completed() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"), completedFuture("e"), completedFuture("f"),
        (a, b, c, d, e, f) -> a + b + c + d + e + f);

    assertThat(future, completesTo("abcdef"));
  }

  @Test
  public void combine6_exceptional() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"), completedFuture("e"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b, c, d, e, f)-> a + b + c + d + e + f);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combine6_incomplete() throws Exception {
    final CompletionStage<String> future = combine(
        completedFuture("a"), completedFuture("b"), completedFuture("c"),
        completedFuture("d"), completedFuture("e"),
        incompleteFuture(),
        (a, b, c, d, e, f) -> a + b + c + d + e + f);
    exception.expect(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures2_completed() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        (a, b) -> completedFuture(a + b));

    assertThat(future, completesTo("ab"));
  }

  @Test
  public void combineFutures2_incomplete() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        incompleteFuture(),
        (a, b) -> completedFuture(a + b));

    exception.expect(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures2_exceptional() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b) -> completedFuture(a + b));

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures3_completed() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        (a, b, c) -> completedFuture(a + b + c));

    assertThat(future, completesTo("abc"));
  }

  @Test
  public void combineFutures3_incomplete() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        incompleteFuture(),
        (a, b, c) -> completedFuture(a + b + c));

    exception.expect(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures3_exceptional() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b, c) -> completedFuture(a + b + c));

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures4_completed() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        completedFuture("d"),
        (a, b, c, d) -> completedFuture(a + b + c + d));

    assertThat(future, completesTo("abcd"));
  }

  @Test
  public void combineFutures4_incomplete() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        incompleteFuture(),
        (a, b, c, d) -> completedFuture(a + b + c + d));

    exception.expect(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures4_exceptional() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b, c, d) -> completedFuture(a + b + c + d));

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures5_completed() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        completedFuture("d"),
        completedFuture("e"),
        (a, b, c, d, e) -> completedFuture(a + b + c + d + e));

    assertThat(future, completesTo("abcde"));
  }

  @Test
  public void combineFutures5_incomplete() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        completedFuture("d"),
        incompleteFuture(),
        (a, b, c, d, e) -> completedFuture(a + b + c + d + e));

    exception.expect(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures5_exceptional() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        completedFuture("d"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b, c, d, e) -> completedFuture(a + b + c + d + e));

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures6_completed() throws Exception {
    final CompletionStage<String> future = combineFutures(
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
  public void combineFutures6_incomplete() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        completedFuture("d"),
        completedFuture("e"),
        incompleteFuture(),
        (a, b, c, d, e, f) -> completedFuture(a + b + c + d + e + f));

    exception.expect(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineFutures6_exceptional() throws Exception {
    final CompletionStage<String> future = combineFutures(
        completedFuture("a"),
        completedFuture("b"),
        completedFuture("c"),
        completedFuture("d"),
        completedFuture("e"),
        exceptionallyCompletedFuture(new IllegalStateException()),
        (a, b, c, d, e, f) -> completedFuture(a + b + c + d + e + f));

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineVararg_success() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = completedFuture("b");
    final CompletionStage<String> future = combine(combined -> combined.get(first) + combined.get(second), first, second);

    assertEquals("ab", getCompleted(future));
  }

  @Test
  public void combineList_success() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = completedFuture("b");
    final CompletionStage<String> future = combine(combined -> combined.get(first) + combined.get(second), asList(first, second));

    assertEquals("ab", getCompleted(future));
  }

  @Test
  public void combineList_nullValues_success() {
    final CompletionStage<String> first = completedFuture(null);
    final CompletionStage<String> future = combine(combined -> combined.get(first), first);

    assertEquals(null, getCompleted(future));
  }

  @Test
  public void combineVararg_exceptional() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = exceptionallyCompletedFuture(new IllegalStateException());
    final CompletionStage<String> future = combine(combined -> combined.get(first) + combined.get(second), first, second);

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineList_exceptional() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = exceptionallyCompletedFuture(new IllegalStateException());
    final CompletionStage<String> future = combine(combined -> combined.get(first) + combined.get(second), asList(first, second));

    exception.expectCause(isA(IllegalStateException.class));
    getCompleted(future);
  }

  @Test
  public void combineVararg_misuse() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = completedFuture("b");
    final CompletionStage<String> third = completedFuture("c");
    final CompletionStage<String> future = combine(combined -> combined.get(first) + combined.get(second) + combined.get(third), first, second);

    exception.expectCause(isA(IllegalArgumentException.class));
    getCompleted(future);
  }

  @Test
  public void combineList_misuse() {
    final CompletionStage<String> first = completedFuture("a");
    final CompletionStage<String> second = completedFuture("b");
    final CompletionStage<String> third = completedFuture("c");
    final CompletionStage<String> future = combine(combined -> combined.get(first) + combined.get(second) + combined.get(third), asList(first, second));

    exception.expectCause(isA(IllegalArgumentException.class));
    getCompleted(future);
  }

  @Test
  public void ctor_preventInstantiation() throws Exception {
    exception.expect(both(isA(InvocationTargetException.class))
                         .and(hasProperty("cause", isA(IllegalAccessError.class))));

    final Constructor<CompletableFutures> ctor = CompletableFutures.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    ctor.newInstance();
  }

  @Test
  public void poll_done() throws Exception {
    final Supplier<Optional<String>> supplier = () -> Optional.of("done");
    final CompletableFuture<String> future = poll(supplier, Duration.ofMillis(2), executor);

    executor.runNextPendingCommand();
    assertThat(future, completesTo("done"));
  }

  @Test
  public void poll_twice() throws Exception {
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
    exception.expectCause(isA(NullPointerException.class));
    future.get();
  }

  @Test
  public void poll_taskThrows() throws Exception {
    final RuntimeException ex = new RuntimeException("boom");
    final Supplier<Optional<String>> supplier = () ->  {throw ex;};
    final CompletableFuture<String> future = poll(supplier, Duration.ofMillis(2), executor);

    executor.runNextPendingCommand();
    exception.expectCause(is(ex));
    future.get();
  }

  @Test
  public void poll_scheduled() throws Exception {
    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final Supplier<Optional<String>> supplier = () -> Optional.of("hello");
    poll(supplier, Duration.ofMillis(2), executor);

    verify(executor).scheduleAtFixedRate(any(), eq(0L), eq(2L), eq(MILLISECONDS));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void poll_resultFutureCanceled() throws Exception {
    final ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    when(executor.scheduleAtFixedRate(any(), anyLong(), anyLong(), any()))
        .thenReturn(scheduledFuture);

    final CompletableFuture<String> future = poll(Optional::empty, Duration.ofMillis(2), executor);
    future.cancel(true);

    verify(scheduledFuture).cancel(true);
  }

  @Test
  public void poll_notRunningAfterCancel() throws Exception {
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
    return new CustomTypeSafeMatcher<CompletionStage<T>>("completes to " + String.valueOf(expected)) {
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
    Map<U, T> map = new HashMap();
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
