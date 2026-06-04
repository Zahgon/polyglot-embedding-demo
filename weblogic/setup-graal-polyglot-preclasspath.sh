#!/usr/bin/env bash
set -euo pipefail

# Optional helper for a WebLogic server-level Graal Polyglot layout.
# It downloads Graal Polyglot / Truffle / JavaScript runtime jars from Maven
# Central into the directory that can be prepended to WebLogic PRE_CLASSPATH.
# Build the WAR with -Pprovided-polyglot when using this layout.

ORACLE_HOME="${ORACLE_HOME:-${HOME}/Oracle/Middleware/Oracle_Home}"
export ORACLE_HOME
GRAALVM_POLYGLOT_VERSION="${GRAALVM_POLYGLOT_VERSION:-25.0.3}"
GRAAL_JS_ARTIFACT="${GRAAL_JS_ARTIFACT:-js}"
GRAAL_POLYGLOT_LIB_DIR="${GRAAL_POLYGLOT_LIB_DIR:-${ORACLE_HOME}/graal-polyglot-${GRAALVM_POLYGLOT_VERSION}/lib}"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/graal-polyglot-preclasspath.XXXXXX")"
trap 'rm -rf "${TMP_DIR}"' EXIT

mkdir -p "${GRAAL_POLYGLOT_LIB_DIR}"

cat > "${TMP_DIR}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.graalvm.polyglot.weblogic</groupId>
  <artifactId>graal-polyglot-preclasspath</artifactId>
  <version>1.0.0</version>
  <dependencies>
    <dependency>
      <groupId>org.graalvm.polyglot</groupId>
      <artifactId>polyglot</artifactId>
      <version>${GRAALVM_POLYGLOT_VERSION}</version>
    </dependency>
    <dependency>
      <groupId>org.graalvm.polyglot</groupId>
      <artifactId>${GRAAL_JS_ARTIFACT}</artifactId>
      <version>${GRAALVM_POLYGLOT_VERSION}</version>
      <type>pom</type>
    </dependency>
  </dependencies>
</project>
EOF

mvn -q -f "${TMP_DIR}/pom.xml" dependency:copy-dependencies \
  -DincludeScope=runtime \
  -DoutputDirectory="${GRAAL_POLYGLOT_LIB_DIR}"

# POM-type transitive artifacts are useful to Maven but not to PRE_CLASSPATH.
find "${GRAAL_POLYGLOT_LIB_DIR}" -maxdepth 1 -type f -name '*.pom' -delete

printf 'Copied Graal Polyglot %s runtime jars to %s\n' \
  "${GRAALVM_POLYGLOT_VERSION}" "${GRAAL_POLYGLOT_LIB_DIR}"
find "${GRAAL_POLYGLOT_LIB_DIR}" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort
