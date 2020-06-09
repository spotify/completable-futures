/*-
 * -\-\-
 * completable-futures
 * --
 * Copyright (C) 2016 - 2020 Spotify AB
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

/**
 * Extends the {@link java.util.concurrent.CompletableFuture Java completable future} API.  The main
 * entry point of this package is the {@link com.spotify.futures.CompletableFutures} class, that
 * contains useful static utility methods.
 *
 * <p> This package uses the convention that all method parameters are non-null by default, i.e.
 * unless indicated by a {@link javax.annotation.Nullable @Nullable} annotation.
 *
 * @since 0.1.0
 */
@ParametersAreNonnullByDefault
package com.spotify.futures;

import javax.annotation.ParametersAreNonnullByDefault;
