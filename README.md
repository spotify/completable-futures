# completable-futures [![Build Status](https://travis-ci.org/spotify/completable-futures.svg?branch=master)](https://travis-ci.org/spotify/completable-futures) [![Maven Central](https://img.shields.io/maven-central/v/com.spotify/completable-futures.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.spotify%22%20AND%20a%3A%22completable-futures%22) [![License](https://img.shields.io/github/license/spotify/completable-futures.svg)](LICENSE)

completable-futures is a set of small utility functions to simplify working with asynchronous code
in Java8.

This project adheres to the [Open Code of Conduct][code-of-conduct]. By participating, you are expected to honor this code.

[code-of-conduct]: https://github.com/spotify/code-of-conduct/blob/master/code-of-conduct.md

#### Build dependencies
* Java 8 or higher
* Maven

#### Runtime dependencies
* Java 8 or higher

### Import

completable-futures is meant to be used as a library in other software. To import it with maven,
use this:

```xml
<dependency>
    <groupId>com.spotify</groupId>
    <artifactId>completable-futures</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Combining more than two things

#### allAsList

If you want to join a list of futures, use `allAsList`:

```java
List<CompletableFuture<String>> futures = asList(completedFuture("a"), completedFuture("b"));
CompletableFutures.allAsList(futures).thenAccept(list -> System.out.println(list));
```

#### joinList

A stream collector that combines multiple futures into a list. This is handy if you apply an
asynchronous operation on a collection of entities:

```java
collection.stream()
    .map(this::someAsyncFunc)
    .collect(CompletableFutures.joinList())
    .thenApply(this::consumeList)
```

#### combine

The builtin API includes `future.thenCombine(otherFuture, function)` but if you want to combine
more than two things it gets trickier. To help out with that, you can use:

```java
CompletableFutures.combine(f1, f2, (a, b) -> a + b);
CompletableFutures.combine(f1, f2, f3, (a, b, c) -> a + b + c);
CompletableFutures.combine(f1, f2, f3, f4, (a, b, c, d) -> a + b + c + d);
CompletableFutures.combine(f1, f2, f3, f4, f5, (a, b, c, d, e) -> a + b + c + d + e);
```

### Missing parts of the CompletableFuture API

The CompletableFutures class includes utility functions for operating on futures that is missing
from the builtin API.

* `dereference` - unwrap a `CompletionStage<CompletionStage<T>>` to a plain `CompletionStage<T>`.
* `exceptionallyCompose` - like `CompletableFuture.exceptionally` but lets you return a new CompletionStage instead of a direct value
* `handleCompose` - like `CompletableFuture.handle` but lets you return a new CompletionStage instead of a direct value
* `exceptionallyCompletedFuture` - Creates a new {@code CompletableFuture} that is already exceptionally completed with the given
exception

## License

Copyright 2016 Spotify AB.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0

## Code of Conduct

This project adheres to the [Open Code of Conduct][code-of-conduct]. By participating, you are expected to honor this code.

[code-of-conduct]: https://github.com/spotify/code-of-conduct/blob/master/code-of-conduct.md
