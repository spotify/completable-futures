/*
 * Copyright (c) 2017 Spotify AB
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

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;

/** Port of ConcurrencyLimiter from futures-extra for use with CompletionStages */
public class ConcurrencyLimiter<T> {

  private final BlockingQueue<Job<T>> queue;
  private final Semaphore limit;
  private final int maxQueueSize;

  private final int maxConcurrency;

  private ConcurrencyLimiter(int maxConcurrency, int maxQueueSize) {
    this.maxConcurrency = maxConcurrency;
    this.maxQueueSize = maxQueueSize;
    if (maxConcurrency <= 0) {
      throw new IllegalArgumentException("maxConcurrency must be at least 0");
    }

    if (maxQueueSize <= 0) {
      throw new IllegalArgumentException("maxQueueSize must be at least 0");
    }
    this.queue = new ArrayBlockingQueue<>(maxQueueSize);
    this.limit = new Semaphore(maxConcurrency);
  }

  /**
   * @param maxConcurrency maximum number of futures in progress,
   * @param maxQueueSize maximum number of jobs in queue. This is a soft bound and may be
   *     temporarily exceeded if add() is called concurrently.
   * @return a new concurrency limiter
   */
  public static <T> ConcurrencyLimiter<T> create(int maxConcurrency, int maxQueueSize) {
    return new ConcurrencyLimiter<>(maxConcurrency, maxQueueSize);
  }

  /**
   * the callable function will run as soon as the currently active set of futures is less than the
   * maxConcurrency limit.
   *
   * @param callable - a function that creates a future.
   * @return a proxy future that completes with the future created by the input function. This
   *     future will be immediately failed with {@link CapacityReachedException} if the soft queue
   *     size limit is exceeded.
   */
  public CompletableFuture<T> add(Callable<? extends CompletionStage<T>> callable) {
    requireNonNull(callable);
    final CompletableFuture<T> response = new CompletableFuture<>();
    final Job<T> job = new Job<>(callable, response);
    if (!queue.offer(job)) {
      final String message = "Queue size has reached capacity: " + maxQueueSize;
      return CompletableFutures.exceptionallyCompletedFuture(new CapacityReachedException(message));
    }
    pump();
    return response;
  }

  /** @return the number of callables that are queued up and haven't started yet. */
  public int numQueued() {
    return queue.size();
  }

  /** @return the number of currently active futures that have not yet completed. */
  public int numActive() {
    return maxConcurrency - limit.availablePermits();
  }

  /** @return the number of additional callables that can be queued before failing. */
  public int remainingQueueCapacity() {
    return queue.remainingCapacity();
  }

  /** @return the number of additional callables that can be run without queueing. */
  public int remainingActiveCapacity() {
    return limit.availablePermits();
  }

  /**
   * Does one of two things: 1) return a job and acquire a permit from the semaphore 2) return null
   * and does not acquire a permit from the semaphore
   */
  private Job<T> grabJob() {
    while (!queue.isEmpty()) {
      if (!limit.tryAcquire()) {
        return null;
      }

      final Job<T> job = queue.poll();
      if (job != null) {
        return job;
      }

      limit.release();
    }
    return null;
  }

  private void pump() {
    Job<T> job;
    while ((job = grabJob()) != null) {
      final CompletableFuture<T> response = job.response;

      if (response.isCancelled()) {
        limit.release();
      } else {
        invoke(response, job.callable);
      }
    }
  }

  private void invoke(
      final CompletableFuture<T> response, Callable<? extends CompletionStage<T>> callable) {
    final CompletionStage<T> future;
    try {
      future = callable.call();
      if (future == null) {
        limit.release();
        response.completeExceptionally(new NullPointerException());
        return;
      }
    } catch (Throwable e) {
      limit.release();
      response.completeExceptionally(e);
      return;
    }

    future.whenComplete(
        (result, t) -> {
          if (result != null) {
            limit.release();
            response.complete(result);
            pump();
          } else {
            limit.release();
            response.completeExceptionally(t);
            pump();
          }
        });
  }

  private static class Job<T> {
    private final Callable<? extends CompletionStage<T>> callable;
    private final CompletableFuture<T> response;

    public Job(Callable<? extends CompletionStage<T>> callable, CompletableFuture<T> response) {
      this.callable = callable;
      this.response = response;
    }
  }

  public static class CapacityReachedException extends RuntimeException {

    public CapacityReachedException(String errorMessage) {
      super(errorMessage);
    }
  }
}
