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
