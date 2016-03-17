## completable-futures

completable-futures is a set of small utility functions to simplify working with asynchronous code
in Java8.

### Build dependencies
* Java 8 or higher
* Maven

### Runtime dependencies
* Java 8 or higher

### Usage

#### Import

completable-futures is meant to be used as a library in other software. To import it with maven,
use this:

```xml
<dependency>
    <groupId>com.spotify</groupId>
    <artifactId>completable-futures</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### exceptionallyCompletedFuture

Creates a new {@code CompletableFuture} that is already exceptionally completed with the given
exception:

```java
return CompletableFutures.exceptionallyCompletedFuture(new RuntimeException("boom"));
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

#### combineN

The builtin API includes `future.thenCombine(otherFuture, function)` but if you want to combine
more than two things it gets trickier. To help out with that, you can use:

```java
CompletableFutures.combine3(f1, f2, f3, (a, b, c) -> a + b + c);
CompletableFutures.combine4(f1, f2, f3, f4, (a, b, c, d) -> a + b + c + d);
CompletableFutures.combine5(f1, f2, f3, f4, f5, (a, b, c, d, e) -> a + b + c + d + e);
```

### Missing parts of the CompletableFuture API

The CompletableFutures class includes utility functions for operating on futures that is missing
from the builtin API.

* `dereference` - unwrap a `CompletionStage<CompletionStage<T>>` to a plain `CompletionStage<T>`.
* `exceptionallyCompose` - like `CompletableFuture.exceptionally` but lets you return a new CompletionStage instead of a direct value
* `handleCompose` - like `CompletableFuture.handle` but lets you return a new CompletionStage instead of a direct value
