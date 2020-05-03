package com.spotify.futures;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;

public class ExecutionMetadataTest {

  ExecutionMetadata executionMetadata;

  @Before
  public void setup() {
    CompletableFuture<?> normallyCompletedFuture = completedFuture(1);

    CompletableFuture<?> exceptionallyCompletedFuture = new CompletableFuture<>();
    exceptionallyCompletedFuture.completeExceptionally(new NullPointerException());

    CompletableFuture<?> cancelledFuture = new CompletableFuture<>();
    cancelledFuture.cancel(true);

    CompletableFuture<?> incompleteFuture = new CompletableFuture<>();

    executionMetadata =
        new ExecutionMetadata(
            normallyCompletedFuture,
            exceptionallyCompletedFuture,
            cancelledFuture,
            incompleteFuture);
  }

  @Test
  public void testGetCompletedNormally() {
    assertThat(executionMetadata.getCompletedNormally(), is(1));
  }

  @Test
  public void testGetCompletedExceptionally() {
    assertThat(executionMetadata.getCompletedExceptionally(), is(2));
  }

  @Test
  public void testGetDone() {
    assertThat(executionMetadata.getDone(), is(3));
  }

  @Test
  public void testGetTotal() {
    assertThat(executionMetadata.getTotal(), is(4));
  }
}
