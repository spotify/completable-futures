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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

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
    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<T>[] all = stages.stream()
        .map(CompletionStage::toCompletableFuture)
        .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(all)
        .thenApply(i -> Stream.of(all)
            .map(CompletableFuture::join)
            .collect(toList()));
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
