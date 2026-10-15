#!/bin/sh
# Copy the shared flow-node fixtures (conferbot-react-native
# scripts/parity/flows/fixtures, made by running the web widget's own code)
# into this SDK's test resources, where FlowFixturesTest reads them.
# Run it after the fixtures are regenerated there (node gen.js), then commit
# the copies. CONFERBOT_DEV is the folder holding sdk/ (default: two levels up).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
DEV="${CONFERBOT_DEV:-$(cd "$HERE/../../.." && pwd)}"
SRC="$DEV/sdk/conferbot-react-native/scripts/parity/flows/fixtures"
DST="$HERE/../conferbot/src/test/resources/flows"
[ -f "$SRC/index.json" ] || { echo "no fixtures at $SRC" >&2; exit 1; }
rm -rf "$DST"
mkdir -p "$DST"
cp "$SRC"/*.json "$DST"/
echo "copied $(ls "$DST" | wc -l | tr -d ' ') files to $DST"
