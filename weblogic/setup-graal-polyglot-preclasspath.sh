#!/usr/bin/env bash
set -euo pipefail

# Optional helper for a WebLogic server-level Graal Polyglot layout.
# It uses the project pom.xml with the graal-polyglot-preclasspath profile to
# copy the Graal Polyglot / Truffle / JavaScript runtime jars from Maven
# Central into the directory that can be prepended to WebLogic PRE_CLASSPATH.
# Build the WAR with -Pprovided-polyglot when using this layout.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POM="${SCRIPT_DIR}/pom.xml"
ORACLE_HOME="${ORACLE_HOME:-${HOME}/Oracle/Middleware/Oracle_Home}"
export ORACLE_HOME
GRAALVM_POLYGLOT_VERSION="${GRAALVM_POLYGLOT_VERSION:-$(mvn -q -f "${POM}" help:evaluate -Dexpression=graal.version -DforceStdout)}"
GRAAL_JS_ARTIFACT="${GRAAL_JS_ARTIFACT:-js}"
GRAAL_POLYGLOT_LIB_DIR="${GRAAL_POLYGLOT_LIB_DIR:-${ORACLE_HOME}/graal-polyglot-${GRAALVM_POLYGLOT_VERSION}/lib}"

mkdir -p "${GRAAL_POLYGLOT_LIB_DIR}"

mvn -q -f "${POM}" -Pgraal-polyglot-preclasspath dependency:copy-dependencies \
  -Dgraal.version="${GRAALVM_POLYGLOT_VERSION}" \
  -Dgraal.js.artifact="${GRAAL_JS_ARTIFACT}" \
  -Dgraal.polyglot.lib.dir="${GRAAL_POLYGLOT_LIB_DIR}"

printf 'Copied Graal Polyglot %s runtime jars to %s\n' \
  "${GRAALVM_POLYGLOT_VERSION}" "${GRAAL_POLYGLOT_LIB_DIR}"
find "${GRAAL_POLYGLOT_LIB_DIR}" -maxdepth 1 -type f -name '*.jar' -exec basename {} \; | sort
