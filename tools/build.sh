#!/usr/bin/env bash
#
# Builds the mod jar locally, without Gradle.
#
# Minecraft 26.1+ ships unobfuscated, so there is no remapping step: a mod jar is just the
# compiled classes plus the processed resources. That means javac + jar is enough, and Gradle
# (which needs to download Minecraft, the loader and Fabric API) is only strictly required to
# *obtain* those dependency jars.
#
# Prerequisites (see tools/setup-toolchain.sh):
#   $HOME/tools/jdk                  a JDK 25 providing javac and jar
#   $HOME/.cache/vectra-deps/compile-libs/*.jar   the jars this mod compiles against
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JDK="${JDK_HOME:-$HOME/tools/jdk}"
LIBS="${LIBS_DIR:-$HOME/.cache/vectra-deps/compile-libs}"

JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"

for tool in "$JAVAC" "$JAR"; do
	if [ ! -x "$tool" ]; then
		echo "error: $tool not found. Run tools/setup-toolchain.sh first." >&2
		exit 1
	fi
done
if [ ! -d "$LIBS" ]; then
	echo "error: $LIBS not found. Run tools/setup-toolchain.sh first." >&2
	exit 1
fi

VERSION="$(sed -n 's/^mod_version=//p' "$REPO/gradle.properties")"
[ -n "$VERSION" ] || { echo "error: mod_version missing from gradle.properties" >&2; exit 1; }

OUT="$REPO/build/local"
CLASSES="$OUT/classes"
JAR_FILE="$REPO/release/vectra-client-$VERSION.jar"

echo "Vectra Client $VERSION"
echo "  javac : $($JAVAC -version 2>&1)"
echo "  libs  : $LIBS ($(ls "$LIBS"/*.jar 2>/dev/null | wc -l) jars)"

rm -rf "$OUT"
mkdir -p "$CLASSES" "$REPO/release"

# 1. Compile. --release 25 pins the bytecode level to what the 26.2 client expects.
find "$REPO/src/main/java" -name '*.java' -print0 | xargs -0 \
	"$JAVAC" --release 25 -nowarn -cp "$LIBS/*" -d "$CLASSES"
echo "  compiled $(find "$CLASSES" -name '*.class' | wc -l) classes"

# 2. Resources, expanding the version placeholder in fabric.mod.json the same way
#    Gradle's processResources would.
cp -r "$REPO/src/main/resources/." "$CLASSES/"
sed -i "s/\${version}/$VERSION/g" "$CLASSES/fabric.mod.json"

# 3. Package.
rm -f "$JAR_FILE"
"$JAR" --create --file "$JAR_FILE" -C "$CLASSES" .
echo "  -> $JAR_FILE ($(du -h "$JAR_FILE" | cut -f1))"
