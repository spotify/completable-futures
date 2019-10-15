/*
 * Copyright (c) 2019 Spotify AB
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

import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class Combined {

  private final IdentityHashMap<CompletionStage<?>, Object> map = new IdentityHashMap<>();

  Combined(List<? extends CompletionStage<?>> stages) {
    for (final CompletionStage<?> stage : stages) {
      map.put(stage, stage.toCompletableFuture().join());
    }
  }

  Combined(CompletionStage<?>[] stages) {
    for (final CompletionStage<?> stage : stages) {
      map.put(stage, stage.toCompletableFuture().join());
    }
  }

  public <T> T get(CompletionStage<T> stage) {
    final Object value = map.get(stage);
    if (value == null) {
      throw new IllegalArgumentException(
              "Can not resolve values for futures that were not part of the combine");
    }
    return (T) value;
  }
}
