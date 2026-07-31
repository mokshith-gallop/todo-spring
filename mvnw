#!/bin/sh
# Maven Wrapper script — downloads Maven if needed, then delegates.
# Simplified version; full script from maven-wrapper project.

set -e

MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven-3.9.8"
MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.8/apache-maven-3.9.8-bin.tar.gz"

if [ ! -f "${MAVEN_HOME}/bin/mvn" ]; then
    echo "Downloading Maven 3.9.8..."
    mkdir -p "${MAVEN_HOME}"
    curl -fsSL "${MAVEN_URL}" | tar xz -C "${MAVEN_HOME}" --strip-components=1
fi

export JAVA_HOME="${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}"
exec "${MAVEN_HOME}/bin/mvn" "$@"
