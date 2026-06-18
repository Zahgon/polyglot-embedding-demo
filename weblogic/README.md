# Deploying a GraalVM Polyglot Application to Oracle WebLogic

This Maven project builds a Jakarta EE WAR that embeds GraalJS with the GraalVM Polyglot API and deploys to Oracle WebLogic.
The application exposes a `POST /chart` endpoint under the WebLogic context root `/weblogic-test`. The endpoint validates JSON chart data and uses bundled, trusted JavaScript to generate an SVG bar chart.

For more details on polyglot embedding, see the GraalVM documentation:
https://www.graalvm.org/latest/reference-manual/embed-languages/

## WebLogic version

This project is a single WAR module for WebLogic Server 15.1.1 and Jakarta EE 9.1 (`jakarta.*`).
The helper scripts default `ORACLE_HOME` to:

```bash
${HOME}/Oracle/Middleware/Oracle_Home
```

The default development domain is derived from it:

```bash
${ORACLE_HOME}/user_projects/domains/base_domain
```

Set `ORACLE_HOME` if WebLogic is installed elsewhere, or set `DOMAIN_HOME` if your domain is outside `ORACLE_HOME`.

## Setup

Use JDK 21, which is supported by WebLogic Server 15.1.1, with the GraalVM Polyglot version configured in [pom.xml](./pom.xml). By default, this project uses Polyglot version `25.0.3`.

There are two supported deployment layouts.

### Application-packaged Polyglot runtime

The default Maven build packages the Graal Polyglot, Truffle, and JavaScript runtime jars inside the WAR. This layout is simple and is appropriate when only one deployed application uses GraalVM Polyglot languages, or when applications run with the fallback Truffle runtime.

For best guest-language performance, run WebLogic on a runtime that provides the optimizing Truffle runtime. JDKs without an optimizing Truffle runtime can run the application with the fallback Truffle runtime, but guest code executes in interpreter-only mode.

### Server-level Polyglot runtime with `PRE_CLASSPATH`

Use WebLogic `PRE_CLASSPATH` when multiple deployed applications use GraalVM Polyglot languages with the optimized Truffle runtime. The optimized runtime loads native shared libraries, so the Graal Polyglot, Truffle, and language runtime classes must be loaded from a shared server-level class loader rather than separately from each application class loader.

In this layout, the Graal Polyglot, Truffle, and language jars are placed on WebLogic `PRE_CLASSPATH`, and application WARs declare those dependencies as `provided`. This makes all deployed applications use the same server-level Polyglot class loader and avoids class-loader isolation problems such as loading Truffle native support from more than one web-application class loader.

See [Runtime Optimization Support](https://www.graalvm.org/latest/reference-manual/embed-languages/#runtime-optimization-support) in the embedding guide for details.

## Maven usage

* `mvn test` runs the resource tests.
* `mvn package` builds `target/weblogic-test.war` with Polyglot runtime jars packaged in the WAR.
* `mvn -Pprovided-polyglot package` builds a smaller WAR that expects Polyglot runtime jars on WebLogic `PRE_CLASSPATH`.

## Configure `PRE_CLASSPATH`

First create the server-level Graal jar directory:

```bash
export ORACLE_HOME=${ORACLE_HOME:-${HOME}/Oracle/Middleware/Oracle_Home}
./setup-graal-polyglot-preclasspath.sh
```

By default, this copies the Graal Polyglot runtime jars to:

```bash
${ORACLE_HOME}/graal-polyglot-25.0.3/lib
```

To use a different Polyglot version or output directory:

```bash
GRAALVM_POLYGLOT_VERSION=25.0.3 \
GRAAL_POLYGLOT_LIB_DIR=/path/to/graal-polyglot/lib \
./setup-graal-polyglot-preclasspath.sh
```

Before starting WebLogic, prepend those jars to `PRE_CLASSPATH`:

```bash
export ORACLE_HOME=${ORACLE_HOME:-${HOME}/Oracle/Middleware/Oracle_Home}
export DOMAIN_HOME=${DOMAIN_HOME:-${ORACLE_HOME}/user_projects/domains/base_domain}
export GRAAL_POLYGLOT_LIB_DIR=${GRAAL_POLYGLOT_LIB_DIR:-${ORACLE_HOME}/graal-polyglot-25.0.3/lib}

GRAAL_POLYGLOT_CLASSPATH="$(find "${GRAAL_POLYGLOT_LIB_DIR}" -maxdepth 1 -name '*.jar' -type f | sort | paste -sd: -)"
export PRE_CLASSPATH="${GRAAL_POLYGLOT_CLASSPATH}${PRE_CLASSPATH:+:${PRE_CLASSPATH}}"

"${DOMAIN_HOME}/startWebLogic.sh"
```

When WebLogic is started with this `PRE_CLASSPATH`, build the WAR with provided Polyglot dependencies:

```bash
mvn -Pprovided-polyglot package
```

## Deploy to WebLogic

Build and copy the WAR to the domain `autodeploy` directory:

```bash
./deploy-to-weblogic-autodeploy.sh
```

By default this copies to:

```bash
${ORACLE_HOME:-${HOME}/Oracle/Middleware/Oracle_Home}/user_projects/domains/base_domain/autodeploy
```

Override the WebLogic installation or domain if necessary:

```bash
ORACLE_HOME=/path/to/Oracle_Home ./deploy-to-weblogic-autodeploy.sh
DOMAIN_HOME=/path/to/domain ./deploy-to-weblogic-autodeploy.sh
```

To deploy a WAR built for the server-level `PRE_CLASSPATH` layout:

```bash
MAVEN_ARGS='-Pprovided-polyglot clean package' ./deploy-to-weblogic-autodeploy.sh
```

## Render an SVG Chart

After deploying the WAR, post chart data as an `application/json` request body:

```bash
curl -s -X POST http://localhost:7001/weblogic-test/chart \
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

Rendered chart:

![Rendered bar chart](chart.svg)

The resource validates the submitted chart data before invoking the bundled renderer. Invalid input is rejected with HTTP `400`.
