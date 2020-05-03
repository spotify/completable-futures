package com.spotify.futures;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;

public class FailFastWithThrowableTest {

  FailFastWithThrowable failFastWithThrowable;

  @Before
  public void setup() {
    failFastWithThrowable = new FailFastWithThrowable(singletonList(TimeoutException.class));
  }

  @Test
  public void testCancelAll() {
    assertThat(failFastWithThrowable.cancelAll(), is(true));
  }

  @Test
  public void testWithThrowable() {
    TimeoutException expectedThrowable = new TimeoutException();
    assertThat(failFastWithThrowable.withThrowable(expectedThrowable), is(expectedThrowable));
  }

  @Test
  public void testFailFastWithDifferentException() {
    assertThat(
        failFastWithThrowable.failFast(new NullPointerException(), new ExecutionMetadata()),
        is(false));
  }

  @Test
  public void testFailFastWithMatchingException() {
    assertThat(
        failFastWithThrowable.failFast(new TimeoutException(), new ExecutionMetadata()), is(true));
  }

  @Test
  public void testFailFastWithDifferentCause() {
    CompletionException completionException = new CompletionException(new NullPointerException());
    assertThat(
        failFastWithThrowable.failFast(completionException, new ExecutionMetadata()), is(false));
  }

  @Test
  public void testFailFastWithMatchingCause() {
    CompletionException completionException = new CompletionException(new TimeoutException());
    assertThat(
        failFastWithThrowable.failFast(completionException, new ExecutionMetadata()), is(true));
  }
}
