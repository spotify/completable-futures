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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static java.util.Collections.emptySet;


/**
 * Collect a stream of {@link CompletableFuture}s into a single future holding a list of the
 * joined entities.
 */
final class CompletableFutureCollector<T> implements
    Collector<CompletableFuture<T>, List<CompletableFuture<T>>, CompletableFuture<List<T>>> {

  @Override
  public Supplier<List<CompletableFuture<T>>> supplier() {
    return ArrayList::new;
  }

  @Override
  public BiConsumer<List<CompletableFuture<T>>, CompletableFuture<T>> accumulator() {
    return List::add;
  }

  @Override
  public BinaryOperator<List<CompletableFuture<T>>> combiner() {
    return (left, right) -> {
      left.addAll(right);
      return left;
    };
  }

  @Override
  public Function<List<CompletableFuture<T>>, CompletableFuture<List<T>>> finisher() {
    return CompletableFutures::allAsList;
  }

  @Override
  public Set<Characteristics> characteristics() {
    return emptySet();
  }

}
