#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.14.3"

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed. Install Gradle ${GRADLE_VERSION}, then rerun this script." >&2
  exit 1
fi

gradle wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin
echo "Gradle Wrapper generated. Review gradlew and gradlew.bat before committing them."
