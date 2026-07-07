#!/usr/bin/env bash
set -euo pipefail

# Builds this WebLogic WAR and copies it into the development-mode WebLogic
# autodeploy directory. WebLogic picks it up automatically when the AdminServer
# is running.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORACLE_HOME="${ORACLE_HOME:-${HOME}/Oracle/Middleware/Oracle_Home}"
export ORACLE_HOME
DOMAIN_HOME="${DOMAIN_HOME:-${ORACLE_HOME}/user_projects/domains/base_domain}"
AUTODEPLOY_DIR="${DOMAIN_HOME}/autodeploy"
MAVEN_ARGS="${MAVEN_ARGS:-clean package}"

mkdir -p "${AUTODEPLOY_DIR}"

echo "Building weblogic-test with: mvn ${MAVEN_ARGS}"
# shellcheck disable=SC2086 # MAVEN_ARGS intentionally supports multiple Maven arguments.
(cd "${ROOT_DIR}" && mvn -q ${MAVEN_ARGS})

cp -f "${ROOT_DIR}/target/weblogic-test.war" "${AUTODEPLOY_DIR}/"
echo "Copied weblogic-test.war to ${AUTODEPLOY_DIR}"
