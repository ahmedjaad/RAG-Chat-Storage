#!/bin/sh
# Apache Maven Wrapper startup script, with automatic JAR download
set -euf

BASE_DIR="$(cd "$(dirname "$0")"; pwd -P)"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_PROPS="$WRAPPER_DIR/maven-wrapper.properties"

# Ensure wrapper jar is present (download if missing)
if [ ! -f "$WRAPPER_JAR" ]; then
  mkdir -p "$WRAPPER_DIR"
  if command -v curl >/dev/null 2>&1; then
    url=$(grep -E "^wrapperUrl=" "$WRAPPER_PROPS" | cut -d'=' -f2-)
    echo "Downloading Maven Wrapper jar from $url"
    curl -fsSL "$url" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    url=$(grep -E "^wrapperUrl=" "$WRAPPER_PROPS" | cut -d'=' -f2-)
    echo "Downloading Maven Wrapper jar from $url"
    wget -q "$url" -O "$WRAPPER_JAR"
  else
    echo "Error: curl or wget is required to download maven-wrapper.jar" >&2
    exit 1
  fi
fi

# Locate Java
if [ -n "${JAVA_HOME-}" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="java"
fi

# Run the wrapper
exec "$JAVA_CMD" -Dmaven.multiModuleProjectDirectory="$BASE_DIR" -cp "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
