/*-
 * -\-\-
 * completable-futures
 * --
 * Copyright (C) 2016 - 2023 Spotify AB
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class FunctionsTest {

  @Test
  public void function3_andThen() {
    final Function3<String, String, String, Integer> f = (a, b, c) -> 1;
    assertThat(f.andThen(i -> i + 1).apply("", "", ""), is(2));
  }

  @Test
  public void function3_andThenNull() {
    final Function3<String, String, String, Integer> f = (a, b, c) -> 1;
    assertThrows(NullPointerException.class, () -> f.andThen(null));
  }

  @Test
  public void function4_andThen() {
    final Function4<String, String, String, String, Integer> f = (a, b, c, d) -> 1;
    assertThat(f.andThen(i -> i + 1).apply("", "", "", ""), is(2));
  }

  @Test
  public void function4_andThenNull() {
    final Function4<String, String, String, String, Integer> f = (a, b, c, d) -> 1;
    assertThrows(NullPointerException.class, () -> f.andThen(null));
  }

  @Test
  public void function5_andThen() {
    final Function5<String, String, String, String, String, Integer> f = (a, b, c, d, e) -> 1;
    assertThat(f.andThen(i -> i + 1).apply("", "", "", "", ""), is(2));
  }

  @Test
  public void function5_andThenNull() {
    final Function5<String, String, String, String, String, Integer> f = (a, b, c, d, e) -> 1;
    assertThrows(NullPointerException.class, () -> f.andThen(null));
  }

  @Test
  public void function6_andThen() {
    final Function6<String, String, String, String, String, String, Integer> ff =
        (a, b, c, d, e, f) -> 1;
    assertThat(ff.andThen(i -> i + 1).apply("", "", "", "", "", ""), is(2));
  }

  @Test
  public void function6_andThenNull() {
    final Function6<String, String, String, String, String, String, Integer> ff =
        (a, b, c, d, e, f) -> 1;
    assertThrows(NullPointerException.class, () -> ff.andThen(null));
  }
}
