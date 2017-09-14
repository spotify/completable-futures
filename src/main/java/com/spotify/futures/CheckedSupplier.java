/*
 * Copyright (c) 2017 Spotify AB
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

/**
 * A checked version of {@link java.util.function.Supplier} to allow for getting
 * elements but that might thrown an exception.
 * @param <T> The type of result the supplier produces
 * @see java.util.function.Supplier
 * @since 0.4.0
 */
@FunctionalInterface
public interface CheckedSupplier<T> {

  /**
   * Gets a result
   * @return The result
   * @throws Exception The supplier is allowed to throw an exception
   */
  T get() throws Exception;
}
