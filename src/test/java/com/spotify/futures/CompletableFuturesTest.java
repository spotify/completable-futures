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
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.spotify.futures.CompletableFutures.allAsList;
import static com.spotify.futures.CompletableFutures.exceptionallyCompletedFuture;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

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
  public void testGetCompleted() throws Exception {
    final CompletionStage<String> future = CompletableFuture.completedFuture("hello");
    assertEquals("hello", CompletableFutures.getCompleted(future));
  }

  @Test(expected = IllegalStateException.class)
  public void testGetCompletedFails() throws Exception {
    final CompletionStage<String> future = new CompletableFuture<String>();
    CompletableFutures.getCompleted(future);
    fail();
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
  public void testDereferenceFailure() throws Exception {
    final CompletionStage<Object> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());
    final CompletionStage<CompletionStage<Object>> future2 = CompletableFuture.completedFuture(future);
    final CompletionStage<Object> dereferenced = CompletableFutures.dereference(future2);

    exception.expectCause(isA(IllegalArgumentException.class));
    CompletableFutures.getCompleted(dereferenced);
  }

  @Test
  public void testDereferenceNull() throws Exception {
    final CompletionStage<CompletableFuture<Object>> future2 = CompletableFuture.completedFuture(null);
    final CompletionStage<Object> dereferenced = CompletableFutures.dereference(future2);

    exception.expectCause(isA(NullPointerException.class));
    CompletableFutures.getCompleted(dereferenced);
  }

  @Test
  public void testDereferenceSuccess() throws Exception {
    final CompletionStage<String> future = CompletableFuture.completedFuture("hello");
    final CompletionStage<CompletionStage<String>> future2 = CompletableFuture.completedFuture(future);
    final CompletionStage<String> dereferenced = CompletableFutures.dereference(future2);
    assertEquals("hello", CompletableFutures.getCompleted(dereferenced));
  }

  @Test
  public void testExceptionallyCompose() throws Exception {
    final CompletionStage<String> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFutures.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        return CompletableFuture.completedFuture("hello");
      }
    });

    assertEquals("hello", CompletableFutures.getCompleted(composed));

  }

  @Test
  public void testExceptionallyComposeFailure() throws Exception {
    final CompletionStage<String> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFutures.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        return CompletableFutures.exceptionallyCompletedFuture(new IllegalStateException());
      }
    });

    exception.expectCause(isA(IllegalStateException.class));
    CompletableFutures.getCompleted(composed);
  }

  @Test
  public void testExceptionallyComposeUnused() throws Exception {
    final CompletionStage<String> future = CompletableFuture.completedFuture("hello");

    final CompletionStage<String> composed = CompletableFutures.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        return CompletableFutures.exceptionallyCompletedFuture(new IllegalStateException());
      }
    });
    assertEquals("hello", CompletableFutures.getCompleted(composed));
  }

  @Test
  public void testExceptionallyComposeThrows() throws Exception {
    final CompletionStage<String> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFutures.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        throw new IllegalStateException();
      }
    });

    exception.expectCause(isA(IllegalStateException.class));
    CompletableFutures.getCompleted(composed);
  }

  @Test
  public void testExceptionallyComposeReturnsNull() throws Exception {
    final CompletionStage<String> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFutures.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        return null;
      }
    });

    exception.expectCause(isA(NullPointerException.class));
    CompletableFutures.getCompleted(composed);
  }

  @Test
  public void testHandleCompose() throws Exception {
    final CompletionStage<String> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFutures.handleCompose(
        future, (s, throwable) -> CompletableFuture.completedFuture("hello"));

    assertEquals("hello", CompletableFutures.getCompleted(composed));

  }

  @Test
  public void testHandleComposeFailure() throws Exception {
    final CompletionStage<String> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFutures.handleCompose(
        future, (s, throwable) -> CompletableFutures.exceptionallyCompletedFuture(new IllegalStateException()));

    exception.expectCause(isA(IllegalStateException.class));
    CompletableFutures.getCompleted(composed);
  }

  @Test
  public void testHandleComposeThrows() throws Exception {
    final CompletionStage<String> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFutures.handleCompose(
        future, (s, throwable) -> { throw new IllegalStateException(); });

    exception.expectCause(isA(IllegalStateException.class));
    CompletableFutures.getCompleted(composed);
  }

  @Test
  public void testHandleComposeReturnsNull() throws Exception {
    final CompletionStage<String> future = CompletableFutures.exceptionallyCompletedFuture(new IllegalArgumentException());
    final CompletionStage<String> composed = CompletableFutures.handleCompose(future, (s, throwable) -> null);

    exception.expectCause(isA(NullPointerException.class));
    CompletableFutures.getCompleted(composed);
  }
}
