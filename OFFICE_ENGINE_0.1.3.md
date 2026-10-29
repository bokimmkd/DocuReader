# W Reader 0.1.3 — Offline Office engines and PDF gesture fix

## Scope
- DOCX read-only rendering through `docx-preview@0.4.1` (Apache-2.0), supported by `jszip@3.10.1` (MIT / GPL dual license; choose MIT).
- XLSX read-only rendering through `exceljs@4.4.0` (MIT), transformed into HTML worksheet grid with sheet selector, cell/merge/font/fill/border formatting, horizontal and vertical scrolling, cached formula results, images where anchored data is supported, zoom and search.
- Office data is loaded from Android document content URIs through a local `WebViewAssetLoader`, not remote APIs. At build time scripts are installed using pinned npm versions and packaged in the APK. No document bytes go to a cloud service.
- PDF: ordinary one-finger vertical swipe is **not consumed by the zoom handler**, allowing the LazyColumn to scroll pages continuously; two-finger gestures zoom/pan.

## Important limitations to test and disclose
- DOCX is rendered by semantic HTML/CSS; it cannot promise pixel-identical Word pagination, floating-object positioning or every font. It does render styled runs, images, tables and supplied page breaks when supported by the engine.
- XLSX is an in-app grid preview, not Excel itself. It does not calculate formulas, macros or external links; shows workbook cached values where available, otherwise a formula label. Date and custom number-format support is partial. Very large sheets are bounded to 800 rows × 90 columns in this preview; document remains unchanged.
- Source files are never edited. Support DOCX and XLSX, **not legacy DOC/XLS**. User can share original or print the preview.
- Offline preview cap is 35 MB, with a readable error for larger files.
- Embedded document hyperlinks to external domains are blocked in the WebView; no JS bridge exposed; remote requests are intercepted.
- This is a debugging build, **not production approval**. Android emulator screenshots are smoke tests only, and Samsung manual tests are still required.
- User-provided extra screenshot request was not implemented in this build until its intended interaction can be verified.

## Third-party licenses
The build script copies the upstream licenses into `app/src/main/assets/office/licenses/`. These notices must accompany the distributed application. Apache-2.0 and MIT allow commercial redistribution subject to their notices/terms. No paid license fee is required for these OSS libraries.

## Manual acceptance
1. Open PDF with 6+ pages; swipe one finger repeatedly to page 6; try two-finger zoom and return to scrolling.
2. Open DOCX from Files and through Viber/email share; verify text, images, tables, zoom, search, print, share original.
3. Open XLSX containing multiple sheets, merged cells, currency, percentages, formulas and pictures; verify sheet selector, two-axis scrolling, zoom/search.
4. Test offline/airplane mode, resume, rotation, lower-end Samsung memory conditions, expired URI grants.
5. Review library metadata and original files remain unchanged.

Build & QA workflow: `.github/workflows/wreader-office-0.1.3.yml`.
