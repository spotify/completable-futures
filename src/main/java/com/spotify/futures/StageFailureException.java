/*-
 * -\-\-
 * completable-futures
 * --
 * Copyright (C) 2016 - 2021 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.futures;

import java.util.List;

/**
 * Indicates that one or more {@link java.util.concurrent.CompletionStage}s failed. The exceptions
 * of the failed stages are stored as suppressed exceptions.
 */
class StageFailureException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  StageFailureException(String message, List<Throwable> exceptions) {
    super(message);
    exceptions.forEach(this::addSuppressed);
  }
}
