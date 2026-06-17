# Polyglot Embedding Demo with GraalVM: Micronaut

Demonstration project showing how to embed GraalJS in a Micronaut HTTP service with the GraalVM Polyglot API and Maven.
It exposes a `POST /chart` endpoint that validates JSON chart data and uses bundled, trusted JavaScript to generate an SVG bar chart.

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
* `mvn -Pisolated test` to run the tests with the isolated JavaScript engine.
* `mvn mn:run` to run the Micronaut application.
* `mvn -Pisolated mn:run` to run the application with the isolated JavaScript engine.
* `mvn package` to build the Micronaut application.
* `mvn -Pnative package` to build a GraalVM native image at `target/micronaut-test`.

The `isolated` profile uses `org.graalvm.polyglot:js-isolate` instead of the default JavaScript artifact and passes `-Dpolyglot.engine.SpawnIsolate=true` to the Micronaut application and test JVMs. For Polyglot `25.1` Community Edition, use `org.graalvm.polyglot:js-isolate-community` as noted in [pom.xml](./pom.xml).

Please see the [pom.xml](./pom.xml) file for further details on the configuration.

## Render an SVG Chart

After starting the service, post chart data as a JSON request body:

```bash
curl -s -X POST http://localhost:8080/chart \
  -H 'Content-Type: application/json' \
  -H 'Accept: image/svg+xml' \
  --data-binary '{"title":"Fruit sold","xLabel":"Fruit","yLabel":"Count","x":["Apples","Bananas","Cherries"],"y":[4,7,5],"width":480,"height":320}'
```

Response:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 320">
  <title>Fruit sold</title>
  ...
  <rect x="..." y="..." width="..." height="..." fill="#2563eb"/>
  ...
</svg>
```

The controller validates the submitted chart data before invoking the bundled renderer. Invalid input is rejected with HTTP `400`.
