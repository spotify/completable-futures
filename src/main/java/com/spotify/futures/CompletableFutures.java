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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.StampedLock;

public final class CompletableFutures {

  private CompletableFutures() {
    throw new AssertionError();
  }

  /**
   * Returns a new {@link CompletionStage} which completes to a list of all values of its input
   * stages, if all succeed.  The list of results is in the same order as the input stages.
   *
   * <p>If any of the given stages complete exceptionally, then the returned stage also does so,
   * with a CompletionException holding this exception as its cause.
   *
   * <p>If no stages are provided, returns a stage holding an empty list.
   *
   * @param stages The stages to combine.
   * @return A stage that completes to a list of the results of the supplied stages.
   * @throws NullPointerException if the stages list or any of its elements are {@code null}.
   */
  public static <A> CompletionStage<List<A>> allAsList(
      List<? extends CompletionStage<? extends A>> stages)
      throws NullPointerException {
    final int n = stages.size();

    // Need an array because of signature of CompletableFuture#allOf
    final CompletableFuture<?>[] futures = new CompletableFuture<?>[n];

    // We need writes into this data structure from multiple threads to be visible from an arbitrary
    // other thread with a strong store-load fence.  This is probably the best performing data
    // structure we can use without using 'sun.misc.Unsafe#storeFence', but it has a little bit of
    // unnecessary overhead due to us having to make 'n' .get() calls that each is atomic when
    // reifying the final list.
    final AtomicReferenceArray<A> resultReferences = new AtomicReferenceArray<>(n);

    for (int i = 0; i < n; i++) {
      // Make a final copy of i to use in closures
      final int index = i;
      final CompletionStage<? extends A> stage = stages.get(index);

      if (stage == null) {
        final String message =
            MessageFormat.format("The supplied stage at index {0} was null", index);
        throw new NullPointerException(message);
      }

      // We mustn't use stage.toCompletableFuture() directly, because then the ordering of the
      // thenAccept→set and the allOf(...).thenApply→reifyAsList calls are not deterministic, i.e.
      // we might start building the result list before all of the cells in the atomic reference
      // array have been populated.  Tests indicate that the likelihood of this happening is pretty
      // high; about 10-20% for 8 threads.
      final CompletableFuture<Void> future =
          stage.thenAccept((a) -> resultReferences.set(index, a)).toCompletableFuture();

      futures[index] = future;
    }

    return CompletableFuture.allOf(futures).thenApply((v) -> reifyAsList(resultReferences));
  }

  /**
   * Reifies an {@link AtomicReferenceArray} into a normal list.  The elements are not read as one
   * atomic action from the array, so the returned list does not necessarily represent one point in
   * time, but any atomic operations performed on the array prior to the call of this method will be
   * visible in the list.  It should be possible to use a {@link StampedLock} if stronger
   * synchronization is required.
   *
   * @param atomicArray The atomic array that should be reified into a list.
   * @param <A> The type of elements in the array/list.
   * @return A mutable list containing the same contents as the atomic array, sampled at different
   * points in time.
   */
  private static <A> List<A> reifyAsList(AtomicReferenceArray<A> atomicArray) {
    final int n = atomicArray.length();

    // We are wrapping this array in a List, so we don't need to worry about variance semantics
    @SuppressWarnings("unchecked")
    final A[] result = (A[]) new Object[n];

    for (int i = 0; i < n; i++) {
      result[i] = atomicArray.get(i);
    }

    return Arrays.asList(result);
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

}
