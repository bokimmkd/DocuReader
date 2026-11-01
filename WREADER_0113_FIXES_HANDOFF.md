# W Reader 0.1.14 — Excel layout and PDF pan follow-up

## Latest user authorization and device feedback
The user explicitly requested another AAB for the pending fixes and to wait for release. Continue Closed testing ONLY, using the same accepted upload key. Production remains excluded.
User physically confirmed Viber opening, Buy (completed a purchase), and expanded PDF zoom on 0.1.13. Restore has not been separately confirmed. Reported failures: a new single-finger gesture cannot pan a zoomed PDF, and Excel invoice export changes column proportions/wraps text across six pages. Home Favorites should show three entries like Recents. The original customer workbook has not been provided; do not claim exact-file acceptance.

## Device acceptance update — 2026-11-01
User tested 0.1.14-closed and confirmed Favorites are OK and PDF zoom followed by one-finger panning is OK. Their screenshot shows 0.1.14-closed installed. At that point actual Excel invoice testing was pending; the subsequent acceptance below supersedes that pending status. This feedback does not authorize Production publication.

## Next update backlog
- User screenshot feedback: EPUB Book mode Dark only darkens the reading content; the top toolbar/status area and bottom progress/navigation/system navigation areas remain bright white. In the next visual polish, make the entire Book reader chrome follow the selected book reading theme, independently of the app's overall Light/System setting: dark surfaces and light readable icons/text for Dark, coordinated surfaces for Sepia/Light. Include title/back/bookmark/Aa/menu toolbar, progress/page count, Previous/Bookmarks/Next controls, status bar and Android navigation/gesture area. Preserve contrast, safe insets, reading position and existing controls; restore app/system bar appearance on leaving the reader. Verify theme switching and returning from Book mode on both gesture and three-button navigation. Record for the next update; no new AAB or Production rollout requested.

- Additional user screenshot feedback: Settings > Ads & purchase feels crowded and misaligned. Align the heading, description, lifetime offer text and Restore label on a consistent text column, with icons in their own consistent column. Group “Ad-free Lifetime” and its price/subtitle as one block, vertically align Buy alongside that block, add balanced spacing between header/description/offer/Restore, and preserve compact layout plus accessible touch targets. Check narrow screens and larger font sizes so the offer and Buy do not collide. This is visual polish for the next update; retain existing billing behavior and localized Play price.

- User reports that Help and Privacy Policy feel unbranded. Include both in the next design polish: W Reader logo/name, consistent app colors and typography, clear section headings and compact spacing, readable Light/Dark appearance where applicable, and a clear way back to the app. Inspect both existing destinations before implementing so native screens and linked pages remain consistent. Preserve the accurate policy text and existing support/privacy links; branding alone does not authorize changing legal claims or data practices. This feedback is recorded for the next update; it does not request a new AAB or Production rollout.

User requested visual icons for the Appearance choices: Light (sun), Dark (moon), System (device/system). Keep the three modes and clear selected-state/accessibility labels. This is recorded for the next update, not a request for another AAB now.

## Release candidate acceptance — 2026-11-01
User subsequently reported: “Mislam deka sme ok so ekselot” (“I think we are OK with Excel”). Record Excel as accepted based on the user's device feedback, with no remaining issue reported; this is not independent inspection of the original customer workbook. Together with confirmed Favorites and PDF zoom/pan, 0.1.14-closed (16), tested source fadd8f4d17337ca81da4ea0f1cfb2acba8bfa068, is the accepted release candidate. Keep Appearance icons in the next-update backlog. No new AAB or Production publication was requested by this feedback; existing Closed testing scope remains.

## 0.1.14 implementation
- Version `0.1.14-closed`, versionCode `16`, package `com.blw.wreader`.
- Working branch `fix/wreader-0113-viber-billing-zoom-excel`; tested candidate `fadd8f4d17337ca81da4ea0f1cfb2acba8bfa068`, tree `131a0ff2f9650630bb003c649544f0831e04416c`.
- Freeze actual unzoomed Excel header widths in a colgroup before print CSS hides column coordinates. Preserve original wrapping, merged cells, column proportions and image anchors when rows contain merged cells.
- Excel PDF dialog defaults to `Fit sheet to one page`. Turning it off fits every column to page width and continues long sheets onto additional pages. Portrait/Landscape remain explicit. Direct export validates preview completeness independently from the Android Print fit threshold.
- Normalize valid internal OOXML drawing namespace prefixes and package-absolute relationships in memory for ExcelJS; never fetch external relationships or modify the original workbook. The merged invoice with an embedded image exposed this existing reader limitation.
- Zoomed PDFs accept a fresh one-finger pan gesture. Native 8x sharp rendering and bounds remain; at pan limits, vertical list scrolling can continue.
- Home shows up to three Favorites, matching Recents (prior small commit `f4f0346`).
- Viber retention, billing/purchase recovery, live ad IDs, signing identity and existing tools are retained.

## 0.1.14 verification and release
CI `41345418447` on the candidate above: SUCCESS: 55 unit tests and 32 Android tests passed with zero failures/errors/skips. Actual optimized release-AAB APK black-box smoke passed cold start/resume pixels, EPUB appearance, Word/Excel/image controls, and Library removal/Undo. Downloaded evidence XML independently verified all 87 tests. All text/table PDF pages passed raster checks for visible content and clear outer 20pt margins. The merged invoice renders were visually inspected in portrait and landscape, with all 12 columns, 43 rows, totals and the embedded image on one page; CSV was also visually inspected.
Prior attempt `41344010850` / `b789da4` was rejected after the new invoice fixture timed out opening an unprefixed OOXML drawing. It must not be released. Local DOM/ExcelJS reproduction confirmed the corrected invoice opens all 43 rows and its image. The Android regression additionally checks both PDF orientations at reader zoom 25% and 200%, one page, rightmost column/totals, model text without reflow, embedded image, visible content and clear margins. A separate UI test pinches, releases all fingers, and asserts visible page movement after a new one-finger horizontal drag.
Play Alpha draft release `11`, name `0.1.14 - Excel fit to page and PDF pan`, prior version 15 excluded. Submission confirmed on 2026-11-01: Publishing overview shows `Changes in review` for the sole change `Closed testing - Alpha` / `0.1.14 - Excel fit to page and PDF pan` / `Start full rollout`. Google quick checks were still running; review dispatch proceeds when they complete successfully. This is not approval or tester availability yet. No device support loss. Only non-blocking warning is missing native debug symbols for bundled native libraries; ReTrace mapping is included. Managed publishing remains off. Production and signing keys were unchanged.
Artifact provenance:
- Unsigned artifact `13100025611`, ZIP SHA-256 `1d09d62bd90cf1b61e08987b57d9c9b0bec76061d7c4a0de10fc641cde3d1b1c`.
- Evidence artifact `13099746938`, ZIP SHA-256 `a321271d4d72413271d90eb7550287c59d871f89656fe0b99cf93ac5b6ef0c60`.
- Signed file `WReader-0.1.14-closed-v16-signed.aab`, 19,119,264 bytes, SHA-256 `a9381e49199fe2b60ffe4ce7fa702eba546bad23161ece8c97a6eab5c461cfb6`.
- Signature verified against the same accepted upload certificate `16:76:77:B4:3B:67:4F:73:C2:87:73:C1:D1:94:D8:C3:F8:98:54:05:4C:98:AE:19:30:76:3F:06:67:15:77:F8`. Every unsigned payload entry is byte-identical after signing. Release manifest, all four ABIs, ReTrace mapping and ZIP integrity passed.
- Saved artifact `libfile_d247c23eb27c8191b6d5f0d18507814e`.

## Next physical acceptance
Excel is now accepted per the user's follow-up; Favorites and PDF zoom/pan are also physically confirmed on 0.1.14. No further defect was reported. Purchase succeeded per earlier user feedback; ad removal/Restore has not been separately confirmed. Use the owned purchase for any later check, without charging again.

---

# W Reader 0.1.13 — reconciled four-fix Closed Alpha handoff

## Current authorization
On 1 November 2026 the user explicitly authorized completing the agreed fixes, testing, and uploading to Closed testing ONLY. This supersedes the older no-AAB guard. Production is excluded. Reuse the existing accepted upload key; do not replace or rotate keys.

## Source and branch reconciliation
- Working branch: `fix/wreader-0113-viber-billing-zoom-excel`.
- Starting commit: `1c145c8d6ad296e3fe1f003fcff1c120febeac6d`; common release ancestor `03928488c0f4c290423b347dc2af7fd5df634a32`.
- Compared branch: `fix/wreader-viber-billing-excel-zoom-0.1.13`, commit `497e8a7a10089b33cba8454cfda2e93c707464d7`, successful CI `41325235198` verified against that exact SHA.
- Final candidate: `8a79d47a7cb2c984e9034f5a29f5137ad3466b76`; tree `302963e1cbec38908c338ab1efab7eabecdf721d`.
- No merge/rebase from main and no blind merge between the divergent fix branches. Main and release branch were not moved.
- Keep the first branch's bounded temporary-attachment copy, source aliases/metadata, Buy/Retry and serialized purchase recovery, native PDF 8x zoom and 3M-pixel crop bound.
- Selectively port the successful branch's full-width PDF viewport fitting instead of CSS zoom. The prior failure `41326406203` was confirmed as CSV marks inside outer margins (27/28 Android tests passed).
- Preserve complete row-group pagination, selected orientation and all columns. Repeat THEAD headers; retain the 8192-pixel width safety limit. Export tests now retain real PDF output for inspection.
- Carry over the real 12-column XLSX fixture/test and adapt the transient-intake/reopen/dedup test to `openDocument(..., retainTemporary=true)`. Also keep the 14-column/40-row test, checking every cell and header in both orientations.

## Build and verification
Package `com.blw.wreader`, versionName `0.1.13-closed`, versionCode `15`, minSdk 26, targetSdk 36. Live AdMob IDs, purchase product `remove_ads`, existing update flow, Book mode, stamps, password sharing and Library behavior retained.
Full release CI: `41335444526`, job `124934179238`, exact candidate above. SUCCESS: 55 unit tests and 30 Android tests passed with zero failures/errors/skips. Actual optimized release-AAB APK smoke passed cold start/resume pixel checks, EPUB appearance, Word/Excel/image controls and Library remove/Undo. Extracted test PDFs passed raster checks for visible ink and empty outer 20pt margins on every text/table page; real XLSX portrait/landscape and CSV renders were visually inspected. Every cell/header in the synthetic 14-column, 40-row worksheet was checked in both orientations. Earlier run `41333143715` was superseded by the additional test-only commit; do not release its artifacts.
Visual QA of the superseded green run `41333649531` detected a real blank-wide-Excel defect: inherited system-print CSS zoom painted text at negative x coordinates, outside the page. Its artifacts are REJECTED and must never be uploaded. Final source explicitly overrides worksheet zoom to 1 and uses only the direct-PDF viewport fit. Android margin assertions now also require visible ink on every page, so text hidden outside the page cannot pass.

The full workflow builds the unsigned AAB, executes unit and all Android regression tests, and performs black-box smoke checks on an APK built from the actual optimized release AAB. The separate targeted workflow remains manually available; pushes use the full workflow to avoid duplicate suites.

## Signing and Play
Existing upload alias `wreader-upload`; accepted SHA-256 certificate `16:76:77:B4:3B:67:4F:73:C2:87:73:C1:D1:94:D8:C3:F8:98:54:05:4C:98:AE:19:30:76:3F:06:67:15:77:F8` was independently confirmed from the existing private backup. No private signing material goes into source, CI or reports.
Closed Alpha track `4699280419648890924`, developer `4766816481672407684`, app `4976011020982627479`. All four existing tester lists selected and 178 track countries/regions observed. Draft release 10 prepared with name `0.1.13 - Attachments, purchases and PDF fixes`; previous code14 excluded. Signed AAB uploaded and accepted as 15 (0.1.13-closed). No device support loss. Only non-blocking Play warning: bundled third-party native libraries have no native debug symbol archive (ReTrace mapping is included). Submission confirmed: Publishing overview shows `Changes in review` for the sole change `Closed testing - Alpha` / `0.1.13 - Attachments, purchases and PDF fixes` / `Start full rollout`. Play quick checks were still running; review dispatch proceeds automatically when they pass. This is not an approval or a claim that testers already have version 15. Managed publishing remains off. No Production release or key changes were made.
Read-only product check: `remove_ads` / lifetime Buy option is Active, backwards compatible, 174 purchase countries/regions. No product/pricing/account changes.

## Physical acceptance still required
Automated tests are not proof of a real Viber provider grant revocation, physical Samsung pinch/pan behavior, actual customer workbook fidelity, or Play purchase/account eligibility. After Google makes this Closed Alpha build available, verify those on tester devices. Do not charge again speculatively to test Restore; use an existing valid owned test purchase. The prior refund means an empty Restore result alone does not prove a restore bug.

## Release artifact provenance
- CI: https://github.com/bokimmkd/DocuReader/actions/runs/41335444526
- Tested source: `8a79d47a7cb2c984e9034f5a29f5137ad3466b76`. This handoff update is documentation-only.
- Unsigned artifact ID `13095389354`, ZIP SHA-256 `f87a7c04f8149bcdcf154d1d2189c269583b0b4ada1130e5dcbbe65d2b0fa4fe`.
- Test evidence artifact ID `13095667327`, ZIP SHA-256 `5e3dc4fc79280419f76e26922d918744624156fbc67bf6e2eb18bc17c97415e3`.
- Uploaded file `WReader-0.1.13-closed-v15-signed.aab`, 19,116,394 bytes, SHA-256 `1660a5726e7a8746fd4dd64319f9598660f1e2da57ced8bd2a5996459697cf2b`.
- Signed AAB signature verified; certificate matches the existing accepted upload key. Every original unsigned payload entry is byte-identical after signing; ZIP integrity, release manifest, four ABIs and ReTrace mapping verified.
- Saved artifact ID `libfile_7a9791fedc4481919c8fe83993e8fff6`.

Publishing overview: https://play.google.com/console/u/0/developers/4766816481672407684/app/4976011020982627479/publishing
