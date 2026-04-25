#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."
scripts/compile.sh
if [ ! -f data/attendance-keystore.p12 ]; then
  echo "Tip: run scripts/setup-https.sh once to enable live mobile camera scanning."
fi
java -cp out smartattendance.Main
