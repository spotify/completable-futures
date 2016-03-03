## java8-futures-extra

java8-futures-extra is a set of small utility functions to simplify working with
asynchronous code in Java8.

### Build dependencies
* Java 8 or higher
* Maven

### Runtime dependencies
* Java 8 or higher

### Usage

#### Import

java8-futures-extra is meant to be used as a library in other software.
To import it with maven, use this:

    <dependency>
      <groupId>com.spotify</groupId>
      <artifactId>java8-futures-extra</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>

### exceptionallyCompletedFuture

Creates a new {@code CompletableFuture} that is already exceptionally completed with the given
exception:

```java
return CompletableFutures.exceptionallyCompletedFuture(new RuntimeException("boom"));
```

### allAsList

If you want to join a list of futures, use `allAsList`:

```java
List<CompletableFuture<String>> futures = asList(completedFuture("a"), completedFuture("b"));
CompletableFutures.allAsList(futures).thenAccept(list -> System.out.println(list));
```
