#!/usr/bin/env bash
set -euo pipefail
# Developer/build-time only. The Android app includes the downloaded bundled JS
# and NEVER fetches scripts or documents from the internet at runtime.
OFFICE_TMP="$(mktemp -d)"
trap 'rm -rf "$OFFICE_TMP"' EXIT
npm --prefix "$OFFICE_TMP" install --no-save --ignore-scripts --no-audit --no-fund --package-lock=false \
    docx-preview@0.4.1 jszip@3.10.1 exceljs@4.4.0
ASSETS="app/src/main/assets/office"
mkdir -p "$ASSETS/licenses"
cp "$OFFICE_TMP/node_modules/docx-preview/dist/docx-preview.min.js" "$ASSETS/docx-preview.min.js"
cp "$OFFICE_TMP/node_modules/jszip/dist/jszip.min.js" "$ASSETS/jszip.min.js"
cp "$OFFICE_TMP/node_modules/exceljs/dist/exceljs.min.js" "$ASSETS/exceljs.min.js"
cp "$OFFICE_TMP/node_modules/docx-preview/LICENSE" "$ASSETS/licenses/docx-preview-APACHE-2.0.txt"
cp "$OFFICE_TMP/node_modules/jszip/LICENSE.markdown" "$ASSETS/licenses/jszip-MIT-GPL.txt"
cp "$OFFICE_TMP/node_modules/exceljs/LICENSE" "$ASSETS/licenses/exceljs-MIT.txt"
# Sanity checks: avoid false-green APKs that would display empty Office pages.
for library in docx-preview.min.js jszip.min.js exceljs.min.js; do
  test -s "$ASSETS/$library" || { echo "Missing Office engine $library"; exit 1; }
done
echo "Bundled offline Office renderer assets:"
ls -lh "$ASSETS"/*.js
