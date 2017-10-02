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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FunctionsTest {

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void function3_andThen() {
    final Function3<String, String, String, Integer> f = (a, b, c) -> 1;
    assertThat(f.andThen(i -> i+1).apply("", "", ""), is(2));
  }

  @Test
  public void function3_andThenNull() {
    final Function3<String, String, String, Integer> f = (a, b, c) -> 1;
    exception.expect(NullPointerException.class);
    f.andThen(null);
  }

  @Test
  public void function4_andThen() {
    final Function4<String, String, String, String, Integer> f = (a, b, c, d) -> 1;
    assertThat(f.andThen(i -> i+1).apply("", "", "", ""), is(2));
  }

  @Test
  public void function4_andThenNull() {
    final Function4<String, String, String, String, Integer> f = (a, b, c, d) -> 1;
    exception.expect(NullPointerException.class);
    f.andThen(null);
  }

  @Test
  public void function5_andThen() {
    final Function5<String, String, String, String, String, Integer> f = (a, b, c, d, e) -> 1;
    assertThat(f.andThen(i -> i+1).apply("", "", "", "", ""), is(2));
  }

  @Test
  public void function5_andThenNull() {
    final Function5<String, String, String, String, String, Integer> f = (a, b, c, d, e) -> 1;
    exception.expect(NullPointerException.class);
    f.andThen(null);
  }

  @Test
  public void function6_andThen() {
    final Function6<String, String, String, String, String, String, Integer> ff = (a, b, c, d, e, f) -> 1;
    assertThat(ff.andThen(i -> i+1).apply("", "", "", "", "", ""), is(2));
  }

  @Test
  public void function6_andThenNull() {
    final Function6<String, String, String, String, String, String, Integer> ff = (a, b, c, d, e, f) -> 1;
    exception.expect(NullPointerException.class);
    ff.andThen(null);
  }
}
