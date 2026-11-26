#!/usr/bin/env bash
set -eu

standalone=$1
store=$2
bundle=${3:-}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:?ANDROID_HOME or ANDROID_SDK_ROOT is required}}
aapt2=$(find "$sdk/build-tools" -name aapt2 -type f | sort -V | tail -1)
root=$(cd "$(dirname "$0")/.." && pwd)
updater='Lfr/sygix/tsuyu/(update/|server/UpdateInstaller;)|api\.github\.com/repos'

fail() { echo "Artifact check failed: $*" >&2; exit 1; }
[ -n "$aapt2" ] || fail "aapt2 not found under $sdk/build-tools"
identity() { "$aapt2" dump badging "$1" | sed -n "s/^package: \(name='[^']*' versionCode='[^']*' versionName='[^']*'\).*/\1/p"; }
manifest() { "$aapt2" dump xmltree --file AndroidManifest.xml "$1"; }
count() { grep -cE "$1" || true; }

[ -n "$(identity "$store")" ] || fail "$store has no readable package identity"
[ "$(identity "$standalone")" = "$(identity "$store")" ] || fail "package or version differs: $(identity "$standalone") / $(identity "$store")"
identity "$store" | grep -q "name='fr.sygix.tsuyu'" || fail "unexpected package in $store"
if [ -f "$root/version.properties" ]; then
  name=$(sed -n 's/^versionName=//p' "$root/version.properties")
  code=$(sed -n 's/^versionCode=//p' "$root/version.properties")
  identity "$store" | grep -q "versionCode='$code' versionName='$name'" || fail "$store does not carry version $name ($code) from version.properties"
fi
[ "$(manifest "$standalone" | count 'fr\.sygix\.tsuyu\.update\.UpdateActivity')" -gt 0 ] || fail "$standalone does not declare the updater"
[ "$(manifest "$store" | count 'fr\.sygix\.tsuyu\.update\.')" -eq 0 ] || fail "$store declares updater components"
[ "$(unzip -p "$standalone" 'classes*.dex' | strings -n 8 | count "$updater")" -gt 0 ] || fail "$standalone does not contain the updater"
[ "$(unzip -p "$store" 'classes*.dex' | strings -n 8 | count "$updater")" -eq 0 ] || fail "$store contains updater code"
if [ -n "$bundle" ]; then
  [ "$(unzip -p "$bundle" base/manifest/AndroidManifest.xml | strings -n 8 | count 'fr\.sygix\.tsuyu\.update\.')" -eq 0 ] || fail "$bundle declares updater components"
  [ "$(unzip -p "$bundle" 'base/dex/classes*.dex' | strings -n 8 | count "$updater")" -eq 0 ] || fail "$bundle contains updater code"
  [ "$(unzip -p "$bundle" 'base/dex/classes*.dex' | strings -n 8 | count 'Lfr/sygix/tsuyu/server/ServerMain;')" -gt 0 ] || fail "$bundle lacks the server entry point in its base module"
fi
echo "Artifact check passed"
