package com.spotify.futures;

import static java.util.Arrays.asList;

import java.util.Collection;

/**
 * Fail fast implementation that completes exceptionally when one of the stages completes
 * exceptionally matching a specific throwable class.
 *
 * <p>This implementation maintains the originating {@link Throwable} causing the fast failure and
 * propagates it through the combined completable future.
 *
 * <p>This implementation cancels all the remaining incomplete completable futures when failing
 * fast.
 *
 * @author Jose Alavez
 * @see FailFast
 * @since 0.3.3
 */
public class FailFastWithThrowable implements FailFast {

  private final Collection<Class<? extends Throwable>> classes;

  /**
   * @see FailFastWithThrowable#FailFastWithThrowable(java.util.Collection)
   */
  @SafeVarargs
  public FailFastWithThrowable(Class<? extends Throwable>... throwableClasses) {
    this(asList(throwableClasses));
  }

  /**
   * @param throwableClasses {@link Collection} of throwable classes to fail fast.
   */
  public FailFastWithThrowable(Collection<Class<? extends Throwable>> throwableClasses) {
    this.classes = throwableClasses;
  }

  @Override
  public boolean cancelAll() {
    return true;
  }

  @Override
  public Throwable withThrowable(Throwable origin) {
    return origin;
  }

  @Override
  public boolean failFast(Throwable throwable, ExecutionMetadata executionMetadata) {

    if (throwable.getCause() != null) {
      return classes.contains(throwable.getCause().getClass());
    }

    return classes.contains(throwable.getClass());
  }
}
