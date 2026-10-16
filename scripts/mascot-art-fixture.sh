#!/bin/sh
# Rebuild src/test/resources/mascot-art.json (MascotArtTest) from the React
# Native SDK's core/mascotArt.ts: for 367 looks (every character in every arm
# pose, as a bust and in an owner's colour; every eye and accessory on five
# characters; junk), the first 16 hex digits of each layer's SHA-256, and
# every character's joints. Run it after mascotArt.ts changes in RN, then
# commit the copy. CONFERBOT_DEV is the folder holding sdk/ (default: two
# levels up).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
DEV="${CONFERBOT_DEV:-$(cd "$HERE/../../.." && pwd)}"
RN="$DEV/sdk/conferbot-react-native"
OUT="$HERE/../conferbot/src/test/resources/mascot-art.json"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
(cd "$RN" && npx tsc src/core/mascotArt.ts --outDir "$TMP/js" --module commonjs --target es2019 --skipLibCheck >/dev/null)
cat > "$TMP/gen.js" <<'JS'
const crypto = require('crypto');
const fs = require('fs');
const A = require(process.argv[2]);
const cases = [];
const add = (accent, arm, look, bust) => {
  const l = A.mascotLayers(accent, arm, look, { bust });
  const layers = {};
  for (const k of A.MASCOT_LAYER_ORDER) layers[k] = l[k] === null ? null : crypto.createHash('sha256').update(l[k]).digest('hex').slice(0, 16);
  cases.push({ accent, arm, look, bust, layers });
};
const arms = ['down', 'whisper', 'wave', 'reach', 'knock'];
for (const shape of A.MASCOT_SHAPES) {
  for (const arm of arms) add('#4f46e5', arm, { shape }, false);
  add('#4f46e5', 'down', { shape }, true);
  add('#0ea5e9', 'wave', { shape, color: '#F97316' }, false);
}
for (const shape of ['blob', 'robot', 'laptop', 'car', 'ghost']) for (const eyes of A.MASCOT_EYES) for (const accessory of A.MASCOT_ACCESSORIES) add('#123456', 'down', { shape, eyes, accessory }, shape === 'car');
add('rgb(10, 20, 30)', 'down', undefined, false);
add('not a colour', 'knock', { shape: 'dragon', eyes: 'laser', accessory: 'cape', color: 'red' }, false);
add('#abc', 'wave', { shape: 'tooth', color: '#fff' }, true);
const origins = {};
for (const s of A.MASCOT_SHAPES) origins[s] = A.mascotOrigins({ shape: s });
fs.writeFileSync(process.argv[3], JSON.stringify({ cases, origins }));
console.log(`${cases.length} looks to ${process.argv[3]}`);
JS
node "$TMP/gen.js" "$TMP/js/mascotArt.js" "$OUT"
