#!/bin/bash
# Write the git-ignored keystore.properties used by app/build.gradle for release signing.
#
# The keystore itself lives outside the repository, e.g. ~/.credentials/bahairesearch.jks.
# The password and alias are validated against the keystore before anything is written, so a
# mistyped value cannot silently produce a badly signed APK.
#
# Usage: tools/configure-signing.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPERTIES="$ROOT/keystore.properties"
DEFAULT_KEYSTORE="$HOME/.credentials/bahairesearch.jks"

if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: keytool not found on PATH. Install a JDK 11+."
  exit 1
fi

read -r -p "Keystore path [$DEFAULT_KEYSTORE]: " KEYSTORE
KEYSTORE="${KEYSTORE:-$DEFAULT_KEYSTORE}"
KEYSTORE="${KEYSTORE/#\~/$HOME}"

if [ ! -f "$KEYSTORE" ]; then
  echo "ERROR: keystore not found: $KEYSTORE"
  exit 1
fi

read -r -s -p "Keystore password: " STORE_PASSWORD
echo
if [ -z "$STORE_PASSWORD" ]; then
  echo "ERROR: keystore password is required."
  exit 1
fi

if ! LISTING="$(keytool -list -keystore "$KEYSTORE" -storepass "$STORE_PASSWORD" 2>&1)"; then
  echo "ERROR: cannot open the keystore with that password."
  printf '%s\n' "$LISTING" | tail -2
  exit 1
fi

# keytool prints one "<alias>, <date>, PrivateKeyEntry," line per key
AVAILABLE="$(printf '%s\n' "$LISTING" | grep 'PrivateKeyEntry' | sed 's/,.*//' | sed 's/[[:space:]]*$//')"
if [ -z "$AVAILABLE" ]; then
  echo "ERROR: the keystore contains no private key entries."
  exit 1
fi
echo "Aliases in keystore:"
printf '%s\n' "$AVAILABLE" | sed 's/^/  /'

DEFAULT_ALIAS="$(printf '%s\n' "$AVAILABLE" | head -n 1)"
read -r -p "Key alias [$DEFAULT_ALIAS]: " KEY_ALIAS
KEY_ALIAS="${KEY_ALIAS:-$DEFAULT_ALIAS}"
if ! printf '%s\n' "$AVAILABLE" | grep -qxF "$KEY_ALIAS"; then
  echo "ERROR: alias '$KEY_ALIAS' is not in the keystore."
  exit 1
fi

read -r -s -p "Key password (blank = same as keystore): " KEY_PASSWORD
echo
KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

umask 077
cat > "$PROPERTIES" <<EOF
storeFile=$KEYSTORE
storePassword=$STORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
EOF
chmod 600 "$PROPERTIES"

if git -C "$ROOT" check-ignore -q keystore.properties; then
  echo "Wrote $PROPERTIES (mode 600, git-ignored)."
else
  echo "WARNING: $PROPERTIES is NOT git-ignored. Add 'keystore.properties' to .gitignore."
fi

cat <<EOF

Next:
  ./gradlew :app:assembleRelease
  # -> app/build/outputs/apk/release/app-release.apk (signed)

Verify:
  \${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/<version>/apksigner verify --print-certs \\
      app/build/outputs/apk/release/app-release.apk
EOF
