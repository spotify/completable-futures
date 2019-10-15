/*
 * Copyright (c) 2016 Spotify AB
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * A collection of static utility methods that extend the
 * {@link java.util.concurrent.CompletableFuture Java completable future} API.
 *
 * @since 0.1.0
 */
public final class CompletableFutures {

  private CompletableFutures() {
    throw new IllegalAccessError("This class must not be instantiated.");
  }

  /**
   * Returns a new {@link CompletableFuture} which completes to a list of all values of its input
   * stages, if all succeed.  The list of results is in the same order as the input stages.
   *
   * <p> If any of the given stages complete exceptionally, then the returned future also does so,
   * with a {@link CompletionException} holding this exception as its cause.
   *
   * <p> If no stages are provided, returns a future holding an empty list.
   *
   * @param stages the stages to combine
   * @param <T>    the common super-type of all of the input stages, that determines the monomorphic
   *               type of the output future
   * @return a future that completes to a list of the results of the supplied stages
   * @throws NullPointerException if the stages list or any of its elements are {@code null}
   * @since 0.1.0
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
   * Returns a new {@link CompletableFuture} which completes to a list of values of those input
   * stages that succeeded. The list of results is in the same order as the input stages. For failed
   * stages, the defaultValueMapper will be called, and the value returned from that function will
   * be put in the resulting list.
   *
   * <p>If no stages are provided, returns a future holding an empty list.
   *
   * @param stages the stages to combine.
   * @param defaultValueMapper a function that will be called when a future completes exceptionally
   * to provide a default value to place in the resulting list
   * @param <T>    the common type of all of the input stages, that determines the type of the
   *               output future
   * @return a future that completes to a list of the results of the supplied stages
   * @throws NullPointerException if the stages list or any of its elements are {@code null}
   */
  public static <T> CompletableFuture<List<T>> successfulAsList(
      List<? extends CompletionStage<T>> stages,
      Function<Throwable, ? extends T> defaultValueMapper) {
    return stages.stream()
        .map(f -> f.exceptionally(defaultValueMapper))
        .collect(joinList());
  }

  /**
   * Returns a new {@code CompletableFuture} that is already exceptionally completed with
   * the given exception.
   *
   * @param throwable the exception
   * @param <T>       an arbitrary type for the returned future; can be anything since the future
   *                  will be exceptionally completed and thus there will never be a value of type
   *                  {@code T}
   * @return a future that exceptionally completed with the supplied exception
   * @throws NullPointerException if the supplied throwable is {@code null}
   * @since 0.1.0
   */
  public static <T> CompletableFuture<T> exceptionallyCompletedFuture(Throwable throwable) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(throwable);
    return future;
  }

  /**
   * Collect a stream of {@link CompletionStage}s into a single future holding a list of the
   * joined entities.
   *
   * <p> Usage:
   *
   * <pre>{@code
   * collection.stream()
   *     .map(this::someAsyncFunc)
   *     .collect(joinList())
   *     .thenApply(this::consumeList)
   * }</pre>
   *
   * <p> The generated {@link CompletableFuture} will complete to a list of all entities, in the
   * order they were encountered in the original stream.  Similar to
   * {@link CompletableFuture#allOf(CompletableFuture[])}, if any of the input futures complete
   * exceptionally, then the returned CompletableFuture also does so, with a
   * {@link CompletionException} holding this exception as its cause.
   *
   * @param <T> the common super-type of all of the input stages, that determines the monomorphic
   *            type of the output future
   * @param <S> the implementation of {@link CompletionStage} that the stream contains
   * @return a new {@link CompletableFuture} according to the rules outlined in the method
   * description
   * @throws NullPointerException if any future in the stream is {@code null}
   * @since 0.1.0
   */
  public static <T, S extends CompletionStage<? extends T>>
  Collector<S, ?, CompletableFuture<List<T>>> joinList() {
    return collectingAndThen(toList(), CompletableFutures::allAsList);
  }

  /**
   * Checks that a stage is completed.
   *
   * @param stage the {@link CompletionStage} to check
   * @throws IllegalStateException if the stage is not completed
   * @since 0.1.0
   */
  public static void checkCompleted(CompletionStage<?> stage) {
    if (!stage.toCompletableFuture().isDone()) {
      throw new IllegalStateException("future was not completed");
    }
  }

  /**
   * Gets the value of a completed stage.
   *
   * @param stage a completed {@link CompletionStage}
   * @param <T>   the type of the value that the stage completes into
   * @return the value of the stage if it has one
   * @throws IllegalStateException if the stage is not completed
   * @since 0.1.0
   */
  public static <T> T getCompleted(CompletionStage<T> stage) {
    CompletableFuture<T> future = stage.toCompletableFuture();
    checkCompleted(future);
    return future.join();
  }

  /**
   * Gets the exception from an exceptionally completed future
   * @param stage an exceptionally completed {@link CompletionStage}
   * @param <T>   the type of the value that the stage completes into
   * @return the exception the stage has completed with
   * @throws IllegalStateException if the stage is not completed exceptionally
   * @throws CancellationException if the stage was cancelled
   * @throws UnsupportedOperationException if the {@link CompletionStage} does not
   * support the {@link CompletionStage#toCompletableFuture()} operation
   */
  public static <T> Throwable getException(CompletionStage<T> stage) {
    CompletableFuture<T> future = stage.toCompletableFuture();
    if (!future.isCompletedExceptionally()) {
      throw new IllegalStateException("future was not completed exceptionally");
    }
    try {
      future.join();
      return null;
    } catch (CompletionException x) {
      return x.getCause();
    }
  }

  /**
   * Returns a new stage that, when this stage completes either normally or exceptionally, is
   * executed with this stage's result and exception as arguments to the supplied function.
   *
   * <p> When this stage is complete, the given function is invoked with the result (or {@code null}
   * if none) and the exception (or {@code null} if none) of this stage as arguments, and the
   * function's result is used to complete the returned stage.
   *
   * <p> This differs from
   * {@link java.util.concurrent.CompletionStage#handle(java.util.function.BiFunction)} in that the
   * function should return a {@link java.util.concurrent.CompletionStage} rather than the value
   * directly.
   *
   * @param stage the {@link CompletionStage} to compose
   * @param fn    the function to use to compute the value of the
   *              returned {@link CompletionStage}
   * @param <T>   the type of the input stage's value.
   * @param <U>   the function's return type
   * @return the new {@link CompletionStage}
   * @since 0.1.0
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
   * <p>This differs from
   * {@link java.util.concurrent.CompletionStage#exceptionally(java.util.function.Function)}
   * in that the function should return a {@link java.util.concurrent.CompletionStage} rather than
   * the value directly.
   *
   * @param stage the {@link CompletionStage} to compose
   * @param fn    the function to use to compute the value of the
   *              returned {@link CompletionStage} if this stage completed
   *              exceptionally
   * @param <T>   the type of the input stage's value.
   * @return the new {@link CompletionStage}
   * @since 0.1.0
   */
  public static <T> CompletionStage<T> exceptionallyCompose(
      CompletionStage<T> stage,
      Function<Throwable, ? extends CompletionStage<T>> fn) {
    return dereference(wrap(stage).exceptionally(fn));
  }

  /**
   * This takes a stage of a stage of a value and returns a plain stage of a value.
   *
   * @param stage a {@link CompletionStage} of a {@link CompletionStage} of a value
   * @param <T>   the type of the inner stage's value.
   * @return the {@link CompletionStage} of the value
   * @since 0.1.0
   */
  public static <T> CompletionStage<T> dereference(
      CompletionStage<? extends CompletionStage<T>> stage) {
    return stage.thenCompose(Function.identity());
  }

  private static <T> CompletionStage<CompletionStage<T>> wrap(CompletionStage<T> future) {
    //noinspection unchecked
    return future.thenApply(CompletableFuture::completedFuture);
  }

  /**
   * Combines multiple stages by applying a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param function the combining function.
   * @param <R>      the type of the combining function's return value.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @return a stage that completes into the return value of the supplied function.
   * @since 0.1.0
   */
  public static <R, A, B> CompletionStage<R> combine(
      CompletionStage<A> a, CompletionStage<B> b,
      BiFunction<A, B, R> function) {
    return a.thenCombine(b, function);
  }

  /**
   * Combines multiple stages by applying a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param c        the third stage.
   * @param function the combining function.
   * @param <R>      the type of the combining function's return value.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @param <C>      the type of the third stage's value.
   * @return a stage that completes into the return value of the supplied function.
   * @since 0.1.0
   */
  public static <R, A, B, C> CompletionStage<R> combine(
      CompletionStage<A> a, CompletionStage<B> b, CompletionStage<C> c,
      Function3<A, B, C, R> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf)
        .thenApply(ignored -> function.apply(af.join(), bf.join(), cf.join()));
  }

  /**
   * Combines multiple stages by applying a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param c        the third stage.
   * @param d        the fourth stage.
   * @param function the combining function.
   * @param <R>      the type of the combining function's return value.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @param <C>      the type of the third stage's value.
   * @param <D>      the type of the fourth stage's value.
   * @return a stage that completes into the return value of the supplied function.
   * @since 0.1.0
   */
  public static <R, A, B, C, D> CompletionStage<R> combine(
      CompletionStage<A> a, CompletionStage<B> b, CompletionStage<C> c, CompletionStage<D> d,
      Function4<A, B, C, D, R> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();
    final CompletableFuture<D> df = d.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf, df)
        .thenApply(ignored -> function.apply(af.join(), bf.join(), cf.join(), df.join()));
  }

  /**
   * Combines multiple stages by applying a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param c        the third stage.
   * @param d        the fourth stage.
   * @param e        the fifth stage.
   * @param function the combining function.
   * @param <R>      the type of the combining function's return value.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @param <C>      the type of the third stage's value.
   * @param <D>      the type of the fourth stage's value.
   * @param <E>      the type of the fifth stage's value.
   * @return a stage that completes into the return value of the supplied function.
   * @since 0.1.0
   */
  public static <R, A, B, C, D, E> CompletionStage<R> combine(
      CompletionStage<A> a, CompletionStage<B> b, CompletionStage<C> c,
      CompletionStage<D> d, CompletionStage<E> e,
      Function5<A, B, C, D, E, R> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();
    final CompletableFuture<D> df = d.toCompletableFuture();
    final CompletableFuture<E> ef = e.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf, df, ef)
        .thenApply(ignored ->
                       function.apply(af.join(), bf.join(), cf.join(), df.join(), ef.join()));
  }

  /**
   * Combines multiple stages by applying a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param c        the third stage.
   * @param d        the fourth stage.
   * @param e        the fifth stage.
   * @param f        the sixth stage.
   * @param function the combining function.
   * @param <R>      the type of the combining function's return value.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @param <C>      the type of the third stage's value.
   * @param <D>      the type of the fourth stage's value.
   * @param <E>      the type of the fifth stage's value.
   * @param <F>      the type of the sixth stage's value.
   * @return a stage that completes into the return value of the supplied function.
   * @since 0.3.2
   */
  public static <R, A, B, C, D, E, F> CompletionStage<R> combine(
      CompletionStage<A> a, CompletionStage<B> b, CompletionStage<C> c,
      CompletionStage<D> d, CompletionStage<E> e, CompletionStage<F> f,
      Function6<A, B, C, D, E, F, R> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();
    final CompletableFuture<D> df = d.toCompletableFuture();
    final CompletableFuture<E> ef = e.toCompletableFuture();
    final CompletableFuture<F> ff = f.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf, df, ef, ff)
        .thenApply(ignored ->
                       function.apply(af.join(),
                                      bf.join(),
                                      cf.join(),
                                      df.join(),
                                      ef.join(),
                                      ff.join()));
  }

  /**
   * Combines multiple stages by applying a function.
   *
   * @param function the combining function.
   * @param stages   the stages to combine
   * @param <T>      the type of the combining function's return value.
   * @return a stage that completes into the return value of the supplied function.
   * @since 0.4.0
   */
  public static <T> CompletionStage<T> combine(
          Function<Combined, T> function, CompletionStage<?>... stages) {
    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<?>[] all = new CompletableFuture[stages.length];
    for (int i = 0; i < stages.length; i++) {
      all[i] = stages[i].toCompletableFuture();
    }
    return CompletableFuture.allOf(all).thenApply(ignored -> function.apply(new Combined(stages)));
  }

  /**
   * Combines multiple stages by applying a function.
   *
   * @param function the combining function.
   * @param stages   the stages to combine
   * @param <T>      the type of the combining function's return value.
   * @return a stage that completes into the return value of the supplied function.
   * @since 0.4.0
   */
  public static <T> CompletionStage<T> combine(
          Function<Combined, T> function, List<? extends CompletionStage<?>> stages) {
    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<?>[] all = new CompletableFuture[stages.size()];
    for (int i = 0; i < stages.size(); i++) {
      all[i] = stages.get(i).toCompletableFuture();
    }
    return CompletableFuture.allOf(all).thenApply(ignored -> function.apply(new Combined(stages)));
  }

  /**
   * Composes multiple stages into another stage using a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param function the combining function.
   * @param <R>      the type of the composed {@link CompletionStage}.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @return a stage that is composed from the input stages using the function.
   * @throws UnsupportedOperationException if any of the {@link CompletionStage}s
   * do not interoperate with CompletableFuture
   */
  public static <R, A, B> CompletionStage<R> combineFutures(
      CompletionStage<A> a,
      CompletionStage<B> b,
      BiFunction<A, B, CompletionStage<R>> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();

    return CompletableFuture.allOf(af, bf)
        .thenCompose(ignored -> function.apply(af.join(), bf.join()));
  }

  /**
   * Composes multiple stages into another stage using a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param c        the third stage.
   * @param function the combining function.
   * @param <R>      the type of the composed {@link CompletionStage}.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @param <C>      the type of the third stage's value.
   * @return a stage that is composed from the input stages using the function.
   * @throws UnsupportedOperationException if any of the {@link CompletionStage}s
   * do not interoperate with CompletableFuture
   */
  public static <R, A, B, C> CompletionStage<R> combineFutures(
      CompletionStage<A> a,
      CompletionStage<B> b,
      CompletionStage<C> c,
      Function3<A, B, C, CompletionStage<R>> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf)
        .thenCompose(ignored -> function.apply(af.join(),
                                               bf.join(),
                                               cf.join()));
  }

  /**
   * Composes multiple stages into another stage using a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param c        the third stage.
   * @param d        the fourth stage.
   * @param function the combining function.
   * @param <R>      the type of the composed {@link CompletionStage}.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @param <C>      the type of the third stage's value.
   * @param <D>      the type of the fourth stage's value.
   * @return a stage that is composed from the input stages using the function.
   * @throws UnsupportedOperationException if any of the {@link CompletionStage}s
   * do not interoperate with CompletableFuture
   */
  public static <R, A, B, C, D> CompletionStage<R> combineFutures(
      CompletionStage<A> a,
      CompletionStage<B> b,
      CompletionStage<C> c,
      CompletionStage<D> d,
      Function4<A, B, C, D, CompletionStage<R>> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();
    final CompletableFuture<D> df = d.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf, df)
        .thenCompose(ignored -> function.apply(af.join(), bf.join(), cf.join(), df.join()));
  }

  /**
   * Composes multiple stages into another stage using a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param c        the third stage.
   * @param d        the fourth stage.
   * @param e        the fifth stage.
   * @param function the combining function.
   * @param <R>      the type of the composed {@link CompletionStage}.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @param <C>      the type of the third stage's value.
   * @param <D>      the type of the fourth stage's value.
   * @param <E>      the type of the fifth stage's value.
   * @return a stage that is composed from the input stages using the function.
   * @throws UnsupportedOperationException if any of the {@link CompletionStage}s
   * do not interoperate with CompletableFuture
   */
  public static <R, A, B, C, D, E> CompletionStage<R> combineFutures(
      CompletionStage<A> a,
      CompletionStage<B> b,
      CompletionStage<C> c,
      CompletionStage<D> d,
      CompletionStage<E> e,
      Function5<A, B, C, D, E, CompletionStage<R>> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();
    final CompletableFuture<D> df = d.toCompletableFuture();
    final CompletableFuture<E> ef = e.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf, df, ef)
        .thenCompose(ignored -> function.apply(af.join(),
                                               bf.join(),
                                               cf.join(),
                                               df.join(),
                                               ef.join()));
  }

  /**
   * Composes multiple stages into another stage using a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param c        the third stage.
   * @param d        the fourth stage.
   * @param e        the fifth stage.
   * @param f        the sixth stage.
   * @param function the combining function.
   * @param <R>      the type of the composed {@link CompletionStage}.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @param <C>      the type of the third stage's value.
   * @param <D>      the type of the fourth stage's value.
   * @param <E>      the type of the fifth stage's value.
   * @param <F>      the type of the sixth stage's value.
   * @return a stage that is composed from the input stages using the function.
   * @throws UnsupportedOperationException if any of the {@link CompletionStage}s
   * do not interoperate with CompletableFuture
   */
  public static <R, A, B, C, D, E, F> CompletionStage<R> combineFutures(
      CompletionStage<A> a,
      CompletionStage<B> b,
      CompletionStage<C> c,
      CompletionStage<D> d,
      CompletionStage<E> e,
      CompletionStage<F> f,
      Function6<A, B, C, D, E, F, CompletionStage<R>> function) {
    final CompletableFuture<A> af = a.toCompletableFuture();
    final CompletableFuture<B> bf = b.toCompletableFuture();
    final CompletableFuture<C> cf = c.toCompletableFuture();
    final CompletableFuture<D> df = d.toCompletableFuture();
    final CompletableFuture<E> ef = e.toCompletableFuture();
    final CompletableFuture<F> ff = f.toCompletableFuture();

    return CompletableFuture.allOf(af, bf, cf, df, ef, ff)
        .thenCompose(ignored -> function.apply(af.join(),
                                               bf.join(),
                                               cf.join(),
                                               df.join(),
                                               ef.join(),
                                               ff.join()));
  }

  /**
   * Composes multiple stages into another stage using a function.
   *
   * @param function the combining function.
   * @param stages   the stages to combine
   * @param <T>      the type of the combining function's return value.
   * @return a stage that is composed from the input stages using the function.
   * @since 0.4.0
   */
  public static <T> CompletionStage<T> combineFutures(
          Function<Combined, CompletionStage<T>> function, CompletionStage<?>... stages) {
    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<?>[] all = new CompletableFuture[stages.length];
    for (int i = 0; i < stages.length; i++) {
      all[i] = stages[i].toCompletableFuture();
    }
    return CompletableFuture.allOf(all)
            .thenCompose(ignored -> function.apply(new Combined(stages)));
  }

  /**
   * Composes multiple stages into another stage using a function.
   *
   * @param function the combining function.
   * @param stages   the stages to combine
   * @param <T>      the type of the combining function's return value.
   * @return a stage that is composed from the input stages using the function.
   * @since 0.4.0
   */
  public static <T> CompletionStage<T> combineFutures(
          Function<Combined, CompletionStage<T>> function,
          List<? extends CompletionStage<?>> stages) {
    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<?>[] all = new CompletableFuture[stages.size()];
    for (int i = 0; i < stages.size(); i++) {
      all[i] = stages.get(i).toCompletableFuture();
    }
    return CompletableFuture.allOf(all)
            .thenCompose(ignored -> function.apply(new Combined(stages)));
  }


  /**
   * Polls an external resource periodically until it returns a non-empty result.
   *
   * <p> The polling task should return {@code Optional.empty()} until it becomes available, and
   * then {@code Optional.of(result)}.  If the polling task throws an exception or returns null,
   * that will cause the result future to complete exceptionally.
   *
   * <p> Canceling the returned future will cancel the scheduled polling task as well.
   *
   * <p> Note that on a ScheduledThreadPoolExecutor the polling task might remain allocated for up
   * to {@code frequency} time after completing or being cancelled.  If you have lots of polling
   * operations or a long polling frequency, consider setting {@code removeOnCancelPolicy} to true.
   * See {@link java.util.concurrent.ScheduledThreadPoolExecutor#setRemoveOnCancelPolicy(boolean)}.
   *
   * @param pollingTask     the polling task
   * @param frequency       the frequency to run the polling task at
   * @param executorService the executor service to schedule the polling task on
   * @param <T>             the type of the result of the polling task, that will be returned when
   *                        the task succeeds.
   * @return a future completing to the result of the polling task once that becomes available
   */
  public static <T> CompletableFuture<T> poll(
      final Supplier<Optional<T>> pollingTask,
      final Duration frequency,
      final ScheduledExecutorService executorService) {
    final CompletableFuture<T> result = new CompletableFuture<>();
    final ScheduledFuture<?> scheduled = executorService.scheduleAtFixedRate(
        () -> pollTask(pollingTask, result), 0, frequency.toMillis(), TimeUnit.MILLISECONDS);
    result.whenComplete((r, ex) -> scheduled.cancel(true));
    return result;
  }

  private static <T> void pollTask(
      final Supplier<Optional<T>> pollingTask,
      final CompletableFuture<T> resultFuture) {
    try {
      pollingTask.get().ifPresent(resultFuture::complete);
    } catch (Exception ex) {
      resultFuture.completeExceptionally(ex);
    }
  }

}
