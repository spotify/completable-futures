package com.spotify.futures;

/**
 * Interface that defines fail fast behavior when combining several completion stages in a
 * completable future.
 *
 * @author Jose Alavez
 * @since 0.3.3
 */
public interface FailFast {

  /**
   * Evaluates if the {@code throwable} should lead to a fast failure when combining completion
   * stages together. A fast failure will not wait for all the combined stages to complete normally
   * or exceptionally.
   *
   * @param throwable         A {@link Throwable} returned by a exceptionally completed stage.
   * @param executionMetadata A {@link ExecutionMetadata} that provides data related to current
   *                          execution.
   * @return {@code true} if the combined completable future should fail fast, {@code false} if
   * otherwise.
   */
  boolean failFast(Throwable throwable, ExecutionMetadata executionMetadata);

  /**
   * Specifies a {@link Throwable} instance to return when a combined completable future fail fast.
   *
   * @param origin A {@link Throwable} that originated the fail fast.
   * @return A {@link Throwable} to used to complete exceptionally a combined completable future.
   */
  Throwable withThrowable(Throwable origin);

  /**
   * Defines if all the incomplete stages should be cancelled when failing fast.
   *
   * @return {@code true} if all the incomplete stages should be cancelled when failing fast, {@code
   * false} if otherwise.
   */
  boolean cancelAll();
}
