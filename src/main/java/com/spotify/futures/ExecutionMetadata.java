package com.spotify.futures;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Provides execution metadata in relation to a group of completable futures.
 *
 * @author Jose Alavez
 * @since 0.3.3
 */
public class ExecutionMetadata {

  private final CompletableFuture<?>[] all;

  ExecutionMetadata(CompletableFuture<?>... all) {
    this.all = all;
  }

  private int accumulate(Function<CompletableFuture<?>, Boolean> fn) {
    int accumulator = 0;
    for (int i = 0; i < all.length; i++) {
      if (fn.apply(all[i])) {
        accumulator++;
      }
    }
    return accumulator;
  }

  /**
   * Returns the total of completable futures being combined.
   */
  public int getTotal() {
    return all.length;
  }

  /**
   * Returns the total of completable futures that have been completed normally, exceptionally or
   * via cancellation.
   *
   * @see CompletableFuture#isDone()
   */
  public int getDone() {
    return accumulate(CompletableFuture::isDone);
  }

  /**
   * Returns the total of completable futures that have been completed normally.
   *
   * @see CompletableFuture#isDone()
   * @see CompletableFuture#isCompletedExceptionally()
   */
  public int getCompletedNormally() {
    return accumulate(
        completableFuture ->
            completableFuture.isDone() && !completableFuture.isCompletedExceptionally());
  }

  /**
   * Returns the total of completable futures that have been completed exceptionally, in any way.
   *
   * @see CompletableFuture#isCompletedExceptionally()
   */
  public int getCompletedExceptionally() {
    return accumulate(CompletableFuture::isCompletedExceptionally);
  }
}
