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

import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a function that accepts five arguments and produces a result.  This is the five-arity
 * specialization of {@link Function}.
 *
 * <p> This is a functional interface whose functional method is
 * {@link #apply(Object, Object, Object, Object, Object, Object)}.
 *
 * @param <A> the type of the first argument to the function
 * @param <B> the type of the second argument to the function
 * @param <C> the type of the third argument to the function
 * @param <D> the type of the fourth argument to the function
 * @param <E> the type of the fifth argument to the function
 * @param <F> the type of the sixth argument to the function
 * @param <R> the type of the result of the function
 * @see Function
 * @since 0.1.0
 */
@FunctionalInterface
public interface Function6<A, B, C, D, E, F, R> {

  /**
   * Applies this function to the given arguments.
   *
   * @param a the first function argument
   * @param b the second function argument
   * @param c the third function argument
   * @param d the fourth function argument
   * @param e the firth function argument
   * @param f the sixth function argument
   * @return the function result
   * @since 0.1.0
   */
  R apply(A a, B b, C c, D d, E e, F f);

  /**
   * Returns a composed function that first applies this function to
   * its input, and then applies the {@code after} function to the result.
   * If evaluation of either function throws an exception, it is relayed to
   * the caller of the composed function.
   *
   * @param <V>   the type of output of the {@code after} function, and of the
   *              composed function
   * @param after the function to apply after this function is applied
   * @return a composed function that first applies this function and then
   * applies the {@code after} function
   * @throws NullPointerException if after is null
   * @since 0.1.0
   */
  default <V> Function6<A, B, C, D, E, F, V> andThen(Function<? super R, ? extends V> after) {
    Objects.requireNonNull(after);
    return (A a, B b, C c, D d, E e, F f) -> after.apply(apply(a, b, c, d, e, f));
  }
}
