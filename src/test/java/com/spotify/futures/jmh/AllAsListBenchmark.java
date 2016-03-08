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
package com.spotify.futures.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;


@BenchmarkMode(Mode.SampleTime)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@SuppressWarnings("unused")
public class AllAsListBenchmark {

  @State(Scope.Benchmark)
  public static class Input {

    @Param({
        "4",
        "16",
        "64",
        "256",
        "1024"
    })
    String inputSize;

    public List<CompletionStage<String>> stages() {
      return Collections.nCopies(parseInt(inputSize), completedFuture("hello"));
    }
  }

  @Benchmark
  @Fork(1)
  public void stream(final Input input) throws Exception {
    final List<CompletionStage<String>> stages = input.stages();

    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<String>[] all = stages.stream()
        .map(CompletionStage::toCompletableFuture)
        .toArray(CompletableFuture[]::new);
    final CompletableFuture<List<String>> future = CompletableFuture.allOf(all)
        .thenApply(i -> Stream.of(all)
            .map(CompletableFuture::join)
            .collect(toList()));

    future.get();
  }

  @Benchmark
  @Fork(1)
  public void instantiateAndFor(final Input input) throws Exception {
    final List<CompletionStage<String>> stages = input.stages();

    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<String>[] all = new CompletableFuture[stages.size()];
    for (int i = 0; i < stages.size(); i++) {
      all[i] = stages.get(i).toCompletableFuture();
    }
    final CompletableFuture<List<String>> future = CompletableFuture.allOf(all)
        .thenApply(ignored -> {
          final List<String> result = new ArrayList<>(all.length);
          for (int i = 0; i < all.length; i++) {
            result.add(all[i].join());
          }
          return result;
        });

    future.get();
  }


  @Benchmark
  @Fork(1)
  public void instantiateAndForeach(final Input input) throws Exception {
    final List<CompletionStage<String>> stages = input.stages();

    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<String>[] all = new CompletableFuture[stages.size()];
    for (int i = 0; i < stages.size(); i++) {
      all[i] = stages.get(i).toCompletableFuture();
    }

    final CompletableFuture<List<String>> future = CompletableFuture.allOf(all)
        .thenApply(ignored -> {
          final List<String> result = new ArrayList<>(all.length);
          for (CompletableFuture<String> entry : all) {
            result.add(entry.join());
          }
          return result;
        });

    future.get();
  }


  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(".*" + AllAsListBenchmark.class.getSimpleName() + ".*")
        .build();
    new Runner(opt).run();
  }

}
