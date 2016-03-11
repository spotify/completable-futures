package com.spotify.futures;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class JoinedResults {

  private static final int LIMIT = 32;

  private final CompletableFuture<?>[] futures;
  private final Set<CompletableFuture<?>> validFutures;

  private JoinedResults(CompletableFuture<?>[] futures) {
    this.futures = futures;
    if (futures.length >= LIMIT) {
      Map<CompletableFuture<?>, Void> map = new IdentityHashMap<>();
      for (CompletableFuture<?> future : futures) {
        map.put(future, null);
      }
      validFutures = map.keySet();
    } else {
      validFutures = null;
    }
  }

  public <T> T get(CompletableFuture<T> future) {
    if (validFutures != null) {
      if (validFutures.contains(future)) {
        return future.join();
      }
    } else {
      for (int i = 0; i < futures.length; i++) {
        if (futures[i] == future) {
          return future.join();
        }
      }
    }
    throw new IllegalArgumentException("Future was not one of the inputs");
  }

  static CompletionStage<JoinedResults> from(CompletableFuture<?>[] futures) {
    return CompletableFuture.allOf(futures).thenApply(ignored -> new JoinedResults(futures));
  }
}
