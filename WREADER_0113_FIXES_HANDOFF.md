# W Reader — Four-fix development handoff (1 Nov 2026)

## Authorization and release guard
User explicitly requested fixing the four remaining W Reader bugs. No authorization to upload AAB or submit a new Play release in this request; prior instruction to collect feedback without an AAB remains the release boundary.

Source branch: `fix/wreader-0113-viber-billing-zoom-excel`, derived from `release/wreader-closed-0.1.4` at `03928488c0f4c290423b347dc2af7fd5df634a32`, app package `com.blw.wreader`, existing signed Closed Alpha 0.1.12 / versionCode 14 unaffected. Do not rebase over main (old 0.1.0), create a new signing key, modify Play track, or start Production.

## Fixes committed
1. **External temporary attachment retention:** `MainActivity.handleIncomingIntent` now distinguishes persisted SAF permission from temporary external content grants; `MainViewModel.openDocument(...,retainTemporary=true)` keeps supported ephemeral documents in app-private Library and records original URI aliases, filename, category/tags/favorite/last page. `DocumentStore.keepOffline` supports a 128 MiB bounded copy for transient inputs and cleans partial files on error. Open-from-Viber real-device verification still pending; protected/encrypted and provider expiry behavior requires physical testing.
2. **Buy/Restore price recovery:** `RemoveAdsBilling` refreshes failed/empty Play ProductDetails lookup via explicit Retry, connection handling and restore-triggered price reload, and reports Play error codes when manually retried. Settings now shows Retry rather than trapping users behind an eternally disabled Buy. Retry never launches checkout. Existing `PurchaseRecovery` acknowledgement/owned-state code retained. Active-order Restore and actual offer eligibility must be checked with a valid Play test account; do not charge user again speculatively.
3. **Wide Excel direct PDF:** `app/src/main/assets/pdf/export.js` fits wide worksheets to selected Portrait/Landscape PDF width while preserving columns and row-group pagination. Original early rejection removed; tables too extremely wide for readable one-page width remain explicitly rejected. Guard against regression in CSV small-table printing: only apply CSS zoom if `fit<0.999` after first regression exposed margin shift at zoom=1. New Android test generates 14-column/40-row invoice, checks first/last cell, orientation, and printable margins.
4. **PDF zoom:** `Screens.kt` expanded pinch native viewport zoom from 4x to 8x; 3M-pixel crop render bound and pan clamps retained. No toolbar zoom slider reintroduced; physical Samsung pinch/pan QA pending.

## QA evidence (run status subject to further checks)
Dedicated GitHub workflow `.github/workflows/wreader-fixes-0113-qa.yml` (test-only; debug QA APK; NO AAB). It builds pinned Office reader engines, checks Node syntax, unit tests, existing Android regression tests plus new attachment/Excel tests.
- Initial run `41325079619`: compilation, unit tests, 27/28 Android tests passed. Only failure was existing CSV export printing-margin regression (content at x≈6.8pt). Saved evidence artifact `13091845022`; PDF inspection confirmed left edge margin issue. Wide-invoice content/orientation and attachment tests passed in this initial run.
- Follow-up source fixes: CSS zoom now applied ONLY to genuinely oversized tables, and added wide-invoice margin assertion. Second CI run `41326406203` started; **do not assert it passed** until workflow and all XML test evidence have been checked.
- All changes are in isolated fix branch only. No signed AAB/Play upload or Console change has been performed by this work.

## Follow-up release gate
Wait for complete Android QA PASS; fix any regressions in fix branch; ask user to test installed Play build only after an authorized, correctly signed Closed Alpha AAB release using the EXISTING upload key. Verify Viber ephemeral access after provider closes/restarts, Excel wide invoice actual file in both orientations, billing price and Restore with valid owned purchase, PDF 8x sharpness/panning. User previously accepted Book Mode, automatic update, Stamp, PDF password sharing and no Downloads duplicates; preserve these.
