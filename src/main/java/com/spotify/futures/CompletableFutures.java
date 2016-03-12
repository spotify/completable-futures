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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public final class CompletableFutures {

  private CompletableFutures() {
    throw new AssertionError();
  }

  /**
   * Returns a new {@link CompletableFuture} which completes to a list of all values of its input
   * stages, if all succeed.  The list of results is in the same order as the input stages.
   *
   * <p>If any of the given stages complete exceptionally, then the returned future also does so,
   * with a CompletionException holding this exception as its cause.
   *
   * <p>If no stages are provided, returns a future holding an empty list.
   *
   * @param stages The stages to combine.
   * @return A future that completes to a list of the results of the supplied stages.
   * @throws NullPointerException if the stages list or any of its elements are {@code null}.
   */
  public static <T> CompletableFuture<List<T>> allAsList(
      List<? extends CompletionStage<? extends T>> stages) {
    // We use traditional for-loops instead of streams here for performance reasons,
    // see AllAsListBenchmark

    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<? extends T>[] all = new CompletableFuture[stages.size()];
    for (int i = 0; i < stages.size(); i++) {
      all[i] = stages.get(i).toCompletableFuture();
    }
    return CompletableFuture.allOf(all)
        .thenApply(ignored -> {
          final List<T> result = new ArrayList<>(all.length);
          for (int i = 0; i < all.length; i++) {
            result.add(all[i].join());
          }
          return result;
        });
  }

  /**
   * Returns a new {@code CompletableFuture} that is already exceptionally completed with
   * the given exception.
   *
   * @param throwable the exception
   * @return a future that exceptionally completed with the supplied exception
   * @throws NullPointerException if the supplied throwable is {@code null}
   */
  public static <T> CompletableFuture<T> exceptionallyCompletedFuture(Throwable throwable) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(throwable);
    return future;
  }

  /**
   * Collect a stream of {@link CompletionStage}s into a single future holding a list of the
   * joined entities.
   * Usage:
   * <pre>
   *   collection.stream()
   *      .map(this::someAsyncFunc)
   *      .collect(joinList())
   *      .thenApply(this::consumeList)
   * </pre>
   * The generated CompletableFuture will complete to a list of all entities, in the order they were
   * encountered in the original stream. Similar to
   * {@link CompletableFuture#allOf(CompletableFuture[])}, if any of the input futures complete
   * exceptionally, then the returned CompletableFuture also does so, with a CompletionException
   * holding this exception as its cause.
   *
   * @throws NullPointerException if any future in the stream is {@code null}.
   */
  public static <T, S extends CompletionStage<? extends T>>
  Collector<S, ?, CompletableFuture<List<T>>> joinList() {
    return collectingAndThen(toList(), CompletableFutures::allAsList);
  }

  /**
   * check that a stage is completed.
   * @param stage a {@link CompletionStage}.
   * @throws IllegalStateException if the stage is not completed.
   */
  public static <T> void checkCompleted(CompletionStage<T> stage) {
    if (!stage.toCompletableFuture().isDone()) {
      throw new IllegalStateException("future was not completed");
    }
  }

  /**
   * Get the value of a completed stage.
   *
   * @param stage a completed {@link CompletionStage}.
   * @return the value of the stage if it has one.
   * @throws IllegalStateException if the stage is not completed.
   */
  public static <T> T getCompleted(CompletionStage<T> stage) {
    CompletableFuture<T> future = stage.toCompletableFuture();
    checkCompleted(future);
    return future.join();
  }

  /**
   * Returns a new stage that, when this stage completes
   * either normally or exceptionally, is executed with this stage's
   * result and exception as arguments to the supplied function.
   *
   * <p>When this stage is complete, the given function is invoked
   * with the result (or {@code null} if none) and the exception (or
   * {@code null} if none) of this stage as arguments, and the
   * function's result is used to complete the returned stage.
   *
   * This differs from
   * {@link java.util.concurrent.CompletionStage#handle(java.util.function.BiFunction)}
   * in that the function should return a {@link java.util.concurrent.CompletionStage} rather than
   * the value directly.
   *
   * @param stage the {@link CompletionStage} to compose
   * @param fn the function to use to compute the value of the
   * returned {@link CompletionStage}
   * @param <U> the function's return type
   * @return the new {@link CompletionStage}
   */
  public static <T, U> CompletionStage<U> handleCompose(
      CompletionStage<T> stage,
      BiFunction<? super T, Throwable, ? extends CompletionStage<U>> fn) {
    return dereference(stage.handle(fn));
  }

  /**
   * Returns a new stage that, when this stage completes
   * exceptionally, is executed with this stage's exception as the
   * argument to the supplied function.  Otherwise, if this stage
   * completes normally, then the returned stage also completes
   * normally with the same value.
   *
   * This differs from
   * {@link java.util.concurrent.CompletionStage#exceptionally(java.util.function.Function)}
   * in that the function should return a {@link java.util.concurrent.CompletionStage} rather than
   * the value directly.
   *
   * @param stage the {@link CompletionStage} to compose
   * @param fn the function to use to compute the value of the
   * returned {@link CompletionStage} if this stage completed
   * exceptionally
   * @return the new {@link CompletionStage}
   */
  public static <T> CompletionStage<T> exceptionallyCompose(
      CompletionStage<T> stage,
      Function<Throwable, ? extends CompletionStage<T>> fn) {
    return dereference(wrap(stage).exceptionally(fn));
  }

  /**
   * This takes a stage of a stage of a value and
   * returns a plain stage of a value.
   *
   * @param stage a {@link CompletionStage} of a {@link CompletionStage} of a value
   * @return the {@link CompletionStage} of the value
   */
  public static <T> CompletionStage<T> dereference(
      CompletionStage<? extends CompletionStage<T>> stage) {
    return stage.thenCompose(Function.identity());
  }

  private static <T> CompletionStage<CompletionStage<T>> wrap(CompletionStage<T> future) {
    //noinspection unchecked
    return future.thenApply(CompletableFuture::completedFuture);
  }

  public static <R, A, B> CompletionStage<R> combine2(
      CompletionStage<A> a, CompletionStage<B> b,
      BiFunction<A, B, R> function) {
    return a.thenCombine(b, function);
  }

  public static <R, A, B, C> CompletionStage<R> combine3(
      CompletionStage<A> a, CompletionStage<B> b, CompletionStage<C> c,
      Function3<A, B, C, R> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf)
        .thenApply(ignored -> function.apply(af.join(), bf.join(), cf.join()));
  }

  public static <R, A, B, C, D> CompletionStage<R> combine4(
      CompletionStage<A> a, CompletionStage<B> b, CompletionStage<C> c, CompletionStage<D> d,
      Function4<A, B, C, D, R> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();
    final CompletableFuture<D> df = d.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf)
        .thenApply(ignored -> function.apply(af.join(), bf.join(), cf.join(), df.join()));
  }

  public static <R, A, B, C, D, E> CompletionStage<R> combine5(
      CompletionStage<A> a, CompletionStage<B> b, CompletionStage<C> c,
      CompletionStage<D> d, CompletionStage<E> e,
      Function5<A, B, C, D, E, R> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();
    final CompletableFuture<D> df = d.toCompletableFuture();
    final CompletableFuture<E> ef = e.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf)
        .thenApply(ignored ->
                       function.apply(af.join(), bf.join(), cf.join(), df.join(), ef.join()));
  }
}
