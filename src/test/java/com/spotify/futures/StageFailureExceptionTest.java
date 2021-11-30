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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import org.junit.Test;

public class StageFailureExceptionTest {

  @Test
  public void exceptionsAreStoredAsSuppressedExceptions() {
    Throwable exception1 = new RuntimeException("1");
    Throwable exception2 = new RuntimeException("2");

    StageFailureException stageFailureException =
        new StageFailureException("message", Arrays.asList(exception1, exception2));

    assertThat(stageFailureException.getSuppressed(), is(arrayContaining(exception1, exception2)));
  }
}
