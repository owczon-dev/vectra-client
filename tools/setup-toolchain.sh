#!/usr/bin/env bash
#
# Fetches the local build toolchain that CI publishes on the ci-toolchain branch:
#
#   jdk-min.tar.gz         a jlink'd JDK 25 providing javac, jar and javap
#   compile-libs.tar.gz    every jar on this project's compile classpath
#                          (Minecraft, Fabric API, Fabric Loader, brigadier, gson, ...)
#
# Gradle normally downloads those from Maven/Mojang, which makes a Gradle build impossible on a
# machine that can only reach github.com. CI has unrestricted network, so it does the downloading
# once and publishes the result to a branch that can be fetched with plain git.
#
# To (re)publish the toolchain, push a commit whose message contains "[toolchain]"; that triggers
# the provision-toolchain job in .github/workflows/build.yml.
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="${TOOLCHAIN_BRANCH:-ci-toolchain}"
JDK_DIR="${JDK_HOME:-$HOME/tools/jdk}"
DEPS_DIR="${LIBS_DIR:-$HOME/.cache/vectra-deps}"

cd "$REPO"

echo "Fetching toolchain from origin/$BRANCH ..."
git fetch --quiet origin "$BRANCH"

fetch() { # <file in toolchain/> <destination tarball>
	local name="$1" dest="$2"
	local blob
	blob="$(git rev-parse "FETCH_HEAD:toolchain/$name")" || {
		echo "error: toolchain/$name not found on origin/$BRANCH" >&2
		echo "       push a commit containing [toolchain] to regenerate it" >&2
		exit 1
	}
	git cat-file blob "$blob" > "$dest"
	echo "  $name ($(du -h "$dest" | cut -f1))"
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fetch jdk-min.tar.gz "$TMP/jdk-min.tar.gz"
fetch compile-libs.tar.gz "$TMP/compile-libs.tar.gz"

echo "Installing JDK to $JDK_DIR ..."
rm -rf "$JDK_DIR"
mkdir -p "$(dirname "$JDK_DIR")"
tar xzf "$TMP/jdk-min.tar.gz" -C "$(dirname "$JDK_DIR")"
[ -d "$(dirname "$JDK_DIR")/jdk-min" ] && mv "$(dirname "$JDK_DIR")/jdk-min" "$JDK_DIR"

echo "Installing compile classpath to $DEPS_DIR/compile-libs ..."
mkdir -p "$DEPS_DIR"
rm -rf "$DEPS_DIR/compile-libs"
tar xzf "$TMP/compile-libs.tar.gz" -C "$DEPS_DIR"

echo
echo "Done."
echo "  $($JDK_DIR/bin/javac -version 2>&1)"
echo "  $($JDK_DIR/bin/jar --version 2>&1 | head -1)"
echo "  $(ls "$DEPS_DIR/compile-libs"/*.jar | wc -l) jars in $DEPS_DIR/compile-libs"
echo
echo "Now run: ./tools/build.sh"
