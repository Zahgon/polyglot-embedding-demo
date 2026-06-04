# Polyglot Embedding Demo with GraalVM: Micronaut

Demonstration project showing how to embed GraalJS in a Micronaut HTTP service with the GraalVM Polyglot API and Maven.
It exposes a `POST /js` endpoint that evaluates JavaScript source from a `text/plain` request body in a restricted polyglot context.

For more details on polyglot embedding, see the GraalVM documentation:
https://www.graalvm.org/latest/reference-manual/embed-languages/

## Setup

Use a GraalVM distribution that is compatible with the GraalVM Polyglot version configured in [pom.xml](./pom.xml). By default, this example uses the latest released Polyglot version `25.0.3`.

For the best guest-language performance, run the application on a runtime that provides the optimizing Truffle runtime. Oracle GraalVM and GraalVM Community Edition provide optimized guest-code execution. Other JDKs can also run the example, but guest code executes in interpreter-only mode unless runtime optimization is enabled for that JDK. In particular, Oracle JDK requires `-XX:+EnableJVMCI`; OpenJDK requires `-XX:+EnableJVMCI` and the Graal compiler on the `--upgrade-module-path`. If optimization is not available, the application still runs, but GraalVM prints a warning that the fallback runtime does not support runtime compilation.

Starting with Polyglot `25.1`, polyglot isolates can be used to get guest-language runtime compilation on a JDK that does not provide an optimizing Truffle runtime. For Polyglot versions before `25.1`, isolated language execution requires Oracle GraalVM. See [Runtime Optimization Support](https://www.graalvm.org/latest/reference-manual/embed-languages/#runtime-optimization-support) in the embedding guide for details.

[Download](https://www.graalvm.org/downloads/) a compatible GraalVM and point the `JAVA_HOME` environment variable to it.

```bash
export JAVA_HOME=/path/to/graalvm
export PATH="$JAVA_HOME/bin:$PATH"
```

## Maven Usage

Download Maven or import this directory as a Maven project into your IDE. The commands below should be run from this `micronaut` directory.

* `mvn test` to run the HTTP endpoint tests.
* `mvn -Pisolated test` to run the tests with the native isolate version of the JavaScript engine.
* `mvn mn:run` to run the Micronaut application.
* `mvn -Pisolated mn:run` to run the application with the isolated JavaScript engine.
* `mvn package` to build the Micronaut application.
* `mvn -Pnative package` to build a GraalVM native image at `target/micronaut-test`.

The `isolated` profile uses `org.graalvm.polyglot:js-isolate` instead of the default JavaScript artifact and passes `-Dengine.SpawnIsolate=true` to the Micronaut JVM. For Polyglot `25.1` Community Edition, use `org.graalvm.polyglot:js-isolate-community` as noted in [pom.xml](./pom.xml).

Please see the [pom.xml](./pom.xml) file for further details on the configuration.

## Evaluate JavaScript

After starting the service, post JavaScript source as a `text/plain` request body:

```bash
curl -s -X POST http://localhost:8080/js \
  -H 'Content-Type: text/plain' \
  --data-binary '21 + 21'
```

Response:

```text
42
```

Empty request bodies and JavaScript evaluation errors are returned as HTTP `400` responses.
