#!/usr/bin/env bash
set -euo pipefail
# Developer/build-time only. The Android app includes the downloaded bundled JS
# and NEVER fetches scripts or documents from the internet at runtime.
OFFICE_TMP="$(mktemp -d)"
trap 'rm -rf "$OFFICE_TMP"' EXIT
npm --prefix "$OFFICE_TMP" install --no-save --ignore-scripts --no-audit --no-fund --package-lock=false \
    docx-preview@0.4.1 jszip@3.10.1 exceljs@4.4.0 jspdf@4.2.1 html2canvas@1.4.1
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

PDF_ASSETS="app/src/main/assets/pdf"
mkdir -p "$PDF_ASSETS/fonts" "$PDF_ASSETS/licenses"
cp "$OFFICE_TMP/node_modules/jspdf/dist/jspdf.umd.min.js" "$PDF_ASSETS/jspdf.umd.min.js"
cp "$OFFICE_TMP/node_modules/html2canvas/dist/html2canvas.min.js" "$PDF_ASSETS/html2canvas.min.js"
cp "$OFFICE_TMP/node_modules/jspdf/LICENSE" "$PDF_ASSETS/licenses/jsPDF-MIT.txt"
cp "$OFFICE_TMP/node_modules/html2canvas/LICENSE" "$PDF_ASSETS/licenses/html2canvas-MIT.txt"
FONT_REF=ffebf8c1ee449e544955a7e813c54f9b73848eac
for style in Regular Bold Italic BoldItalic; do
  curl --fail --location --retry 2 "https://raw.githubusercontent.com/notofonts/noto-fonts/$FONT_REF/hinted/ttf/NotoSans/NotoSans-$style.ttf" --output "$PDF_ASSETS/fonts/NotoSans-$style.ttf"
  test -s "$PDF_ASSETS/fonts/NotoSans-$style.ttf"
done
for family in NotoSansMath NotoSansSymbols NotoSansSymbols2; do
  curl --fail --location --retry 2 "https://raw.githubusercontent.com/notofonts/noto-fonts/$FONT_REF/hinted/ttf/$family/$family-Regular.ttf" --output "$PDF_ASSETS/fonts/$family-Regular.ttf"
  test -s "$PDF_ASSETS/fonts/$family-Regular.ttf"
done
curl --fail --location --retry 2 "https://raw.githubusercontent.com/notofonts/noto-fonts/$FONT_REF/LICENSE" --output "$PDF_ASSETS/licenses/NotoSans-OFL.txt"
test -s "$PDF_ASSETS/jspdf.umd.min.js"
test -s "$PDF_ASSETS/html2canvas.min.js"
