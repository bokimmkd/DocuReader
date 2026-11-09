# W Reader 0.1.18 — submitted to Closed Alpha review

Boki authorized this update on November 9, 2026: "Pukni go. Ako e ok relase verzija". Continued `fix/wreader-0113-viber-billing-zoom-excel`; no main merge, Production rollout or key change.

## Delivered behavior
- Sign PDF from Tools, Library PDF actions, and reader Share options / Tools. Draw, clear/retry, optional Save for reuse, My signature, Draw new and Delete saved signature. Select a page, drag/resize ink, pinch/pan preview and Fit, then save a new PDF copy with Open/Share/Print. Original stays unchanged; existing text Stamp PDF stays separate.
- Optional reusable PNG is private in `noBackupFilesDir`, outside FileProvider and backup roots; no cloud upload. Deleting reusable ink does not modify exported PDFs.
- Android system Back no longer steals strokes near the drawing edge: gesture exclusion applies only to the 180dp drawing canvas. Ordinary Back elsewhere remains available.
- Smaller Saved/Recent controls and compact Library action rows; Saved remains initial default. Rows grow for large fonts. Share with password is in the PDF tools submenu.
- Help and both Privacy copies explain signing/local reuse/deletion, dated November 9. Existing Viber, Buy/Restore, wide Excel fitting/export margins, per-page PDF/Word zoom and per-sheet Excel behavior preserved. No Office or monetization implementation changes.

## Verified release
Tested source `95db9690f94fcc1bde238a56a5e1eb5d3e86619e`, tree `b8aea4c5b92c5202be772e530ebfb3999a7af9b5`, version `0.1.18-closed` / code `20`.
CI https://github.com/bokimmkd/DocuReader/actions/runs/42691508594 succeeded: **57 unit + 41 Android tests, zero failures/errors/skips**, plus the optimized release-AAB APK smoke. Downloaded XML was independently counted. UI evidence includes Light/Dark, large fonts, edge drawing, zoomed placement, save/cancel/reopen/print/share, reuse/delete and Library entry points; eight rotated/cropped portrait/landscape export cases pass. Final signature placement and PDF neighbor screenshots inspected.

Evidence artifact `13708408756`, ZIP SHA256 `a6502baebe03a089bc058a2ef28517cc39d5c75210ba25951459fa27f6f15c4f`.
Unsigned artifact `13708263865`, ZIP SHA256 `98565d670ae2754c08f085ca509d18d597fd548f20384a4fc10399c3c4e467d6`; unsigned AAB SHA256 `d7500aa61da299bd7def267f63c15363498c2ede71a7107db87d7b06bf9ddba5`.
Signed `WReader-0.1.18-closed-v20-signed.aab`, 19,306,684 bytes, SHA256 `9ebaf0d2ff2bb3abcd7f7ab51c2cd43e54d3d53b12944a672302c35edca42633`, Library `libfile_23581e1e522c8191b6f7ab7d4a644a1f`.
JAR signature verified; every original ZIP payload entry byte-identical after signing. Manifest: `com.blw.wreader`, minSdk26, targetSdk36, four ABIs, ReTrace mapping, no debug or test Activity. Actual signed certificate matches accepted upload key SHA256 `16:76:77:B4:3B:67:4F:73:C2:87:73:C1:D1:94:D8:C3:F8:98:54:05:4C:98:AE:19:30:76:3F:06:67:15:77:F8`.

## Google Play submission — November 9, 2026, approximately 20:51 UTC
Play accepted code20 into Closed Alpha track `4699280419648890924`, release `15`, name `0.1.18 - PDF signatures and compact Library`, 100% of this Closed track. Previous code19 is excluded from this release. Device counts unchanged. No validation errors; only missing native debug symbols warning.
Submitted **six changes together**: Closed Alpha full rollout; English store title `W Reader: PDF & Office Reader`; short description; full description; phone screenshots; feature graphic. Approved banner and eight screenshots retained in order: Home, EPUB, Tools, Library light, PDF/StayGuide, Home dark, Bookmarks, Library dark. Settings is reserve only. Promotional artwork declarations completed; existing app icon retained.
Publishing overview visibly confirms **Changes in review**. Quick checks are still running; changes proceed to review after they pass. This is not Google approval or code20 tester availability. Managed publishing remains off; Production unchanged. Existing code19 was available to selected testers before this submission.
Verified result: https://play.google.com/console/u/0/developers/4766816481672407684/app/4976011020982627479/publishing
Proof: `/workspace/shared/WReader-0.1.18-closed-in-review.png` (not uploaded to Library).
Marketing handoff `libfile_9d09662069a88191adf787b4d93162e5` updated to version1 with the actual submission state. This section supersedes historical pending-signature/UI-polish plans below.

## Remaining acceptance
Await Google checks/review. After tester availability, physically verify signature drawing/reuse and placement, saved PDF copy, compact menus/tabs and an existing Play purchase. Automated checks do not establish a new live-purchase result.

## Superseded failed candidates — do not release
- CI `42675828491`: Library test selected another test's Word fixture.
- CI `42680284570`: save-picker selector matched the app filename field; corrected to system DocumentsUI.
- CI `42684051529`: exposed actual Android Back edge-gesture conflict; fixed canvas exclusion while retaining the failing stroke regression.
- CI `42687586026`: signature save/reopen/print/share passed, then teardown failed after changing Activity Intent; PDF pixel fixture also shared state. Final candidate launches MAIN from outset without Intent mutation and isolates PDF history/ad viewport; original strict pixel comparison retained. All 41 Android tests then passed.

---

# W Reader 0.1.17 — Library saving and page zoom

The user authorized implementing the agreed backlog and preparing the next Closed Alpha release on November 8, 2026. Production remains excluded. Continue on `fix/wreader-0113-viber-billing-zoom-excel`; no main merge or signing-key changes.

## Implemented behavior
- Library now separates Saved from Recent. Recent uses the last-opened time and a 30-day window; Home keeps three latest entries and See all. Explicit imports, Tools outputs, saved copies and Favorites remain Saved. All pre-existing entries migrate conservatively to Saved.
- External attachments retain the existing private-copy protection against expired Viber/browser grants, but new transient opens appear in Recent until deliberately saved. Save locally in the Library menu and Save to Library in document readers promote the existing entry, preserve metadata and source aliases, and avoid duplicate copies. A Favorite also becomes durable. History expiry removes metadata only; it does not delete originals or private copies. Automatic temporary-file cleanup is deliberately separate and is not implemented in this release.
- PDF zoom belongs to each page. Continuous scrolling remains the default. A compact floating Previous/current-total/Next bar sits inside the reading area above ads/system insets, with bottom content clearance and disabled end controls. Share options offers Single page, which fits a new page to the viewport; revisited pages retain their zoom while the session stays open. PDF Book mode keeps its existing navigation without a duplicate floating bar.
- Word pinch and zoom controls affect the touched/current page. Excel keeps zoom and scroll position per worksheet. Fit resets the current page/sheet to its available width. Native WebView global zoom is disabled; single-finger scrolling remains native. Office print/export snapshots remove all display zoom.
- Category dropdowns have a visible scrollbar and a downward continuation chevron while more items remain below.
- Billing, signing, accepted Excel-to-PDF margin/fitting logic, attachment retention, Settings and Book themes are preserved.

## Tester feedback — next UI polish (November 8, 2026)
Boki reported “Se drugo e perfektno” after testing and supplied a Library document-actions screenshot. This is a general positive tester report, not a separately enumerated pass of every feature.
- Remaining polish: the Library three-dot document actions menu still has excessive vertical spacing and occupies too much of the screen. Make this menu visibly more compact, with aligned icons/text and reduced unnecessary vertical padding. Keep usable touch targets, large-text readability and access to every action; verify on the supplied phone-sized layout in Light/Dark. This is pending work, not part of the already uploaded v19 AAB.
- Implementation note: `DocumentActionItem` currently uses the Material3 48dp minimum with horizontal padding only; inspect the full menu structure and actual phone density/text scale before changing spacing, rather than assuming extra vertical padding exists.
- Additional next-update request: compact the Library Saved / Recent tab row. The supplied screenshot shows a tall full-width tab strip; Boki says it takes roughly 15% of the page. This is a complaint about occupied screen space, not a specified 15% reduction. Reduce row height and surrounding blank space so more document rows are visible, retain clear selected state and comfortable taps, and check the combined header/search/filter layout at phone size and larger text. Preserve Saved as the initial default and existing Saved/Recent behavior. Pending next update only.
- Save visibility clarification: `LibraryScreen.kt` offers Save locally only when `!doc.savedLocally`. A file already labeled Saved locally intentionally has no duplicate save action. The Saved tab alone does not determine visibility: linked Saved entries without a private copy can still offer Save locally. Test using a newly opened browser/Viber document in Recent marked Temporary copy or Linked, then save and confirm one Saved entry. No new Save defect is established by this screenshot.

## Next update — handwritten PDF signature (November 9, 2026)
Boki reminded us that adding a signature is still missing. Record alongside the compact Library menu and Saved/Recent tabs for the next update; this is not implemented in v19.
- Current implementation has a text Stamp PDF tool (presets/custom text, color, fixed position and optional date); it has no handwritten signature canvas or reusable signature placement.
- Confirmed entry points: Tools → Sign PDF opens the PDF picker; the PDF document's ⋮ menu opens Sign PDF directly for that document. Expose it from Library document actions and the opened PDF reader's overflow menu, using the current page when available. All entry points share one signing flow. Keep the existing Stamp PDF action separate.
- Add a clearly named Sign PDF action. Proposed first version: draw a signature with a finger, clear/retry, preview it on a chosen page, drag to position and resize before applying.
- Confirmed signature reuse flow: first draw a signature with a finger, with Clear/Retry and an explicit optional Save for reuse choice. On later documents, offer My signature to insert the locally saved signature, then drag/resize it on the selected page. Always offer Draw new signature and Delete saved signature; do not require redrawing each time. Saving is opt-in, and deleting the reusable signature does not modify already exported PDFs. Keep signature data only on the device, with no cloud upload. User explicitly requested this behavior be recorded in the next-update plan.
- Save the signed result as a new PDF, preserve the original, then offer Open, Share and Print through the existing output flow. Word/Excel signing can use the existing convert-to-PDF flow.
- Scope is a visible handwritten signature, not certificate-based cryptographic signing. No certificate infrastructure or legal-validity claims are part of this request.
- Verify placement on portrait/landscape and rotated/cropped pages, placement while zoomed, reopening/printing/sharing the exported copy, cancellation without modifying the original, and optional signature reuse/removal.

## Verification / submission — submitted to Closed Alpha review
Final code candidate `0d2ce96b2a50d925eeeab6c08b58b4e971a7ef0e`, tree `b9fa90c92ca67214bf1573bf21afbaff3b34d5a8`, version `0.1.17-closed` / code `19`.
CI https://github.com/bokimmkd/DocuReader/actions/runs/42508429470 succeeded: **57 unit tests + 38 Android tests, zero failures/skips**, followed by the actual optimized release-AAB APK smoke. The smoke requires OCR-visible Word/Excel markers, not merely accessible DOM text. Inspected final Word/Excel screenshots: both visibly render rich content, Cyrillic and table columns. Inspected PDF Single page screenshot: page 2 fits, floating navigation is above the banner, Next is disabled. Word one-finger horizontal/vertical pan, independent Word-page and Excel-worksheet zoom, wide Excel/CSV export margins and existing reader/retention/settings workflows passed.

Evidence artifact `13629485404`, ZIP SHA-256 `d578d810e353f91a3522ced426cbff58466d0c15214317890ad7bb6c62b0f39b`.
Unsigned artifact `13628704631`, ZIP SHA-256 `0fe7316d9d7f6e4cee60223b9543d420d5c5e3457d6013bb04ec34a94942c420`; unsigned AAB SHA-256 `197bbc530be24349dd09238b1ed62766191ff96a70ad7b4c4428b052ec9db80e`.
Signed deliverable `/workspace/shared/WReader-0.1.17-closed-v19-signed.aab`, 19,267,531 bytes, SHA-256 `d353830e6903b3222a8b2188a6b125457e7a3b124e5d0efdc91409325d913746`, Library `libfile_68673e91bfc481919950da907df88f62`.
JAR signature verified; every original ZIP payload entry is byte-identical after signing. Bundle manifest confirms `com.blw.wreader`, code 19, minSdk 26, targetSdk 36, four ABIs, ReTrace mapping, no debug/test Activities. Same accepted upload certificate SHA-256 `16:76:77:B4:3B:67:4F:73:C2:87:73:C1:D1:94:D8:C3:F8:98:54:05:4C:98:AE:19:30:76:3F:06:67:15:77:F8`. No key changes.

Play accepted bundle 19 into existing Closed Alpha track `4699280419648890924`, release `14`, title `0.1.17 - Library saving and page zoom`. Saved the release at 100% of this Closed track. Supported-device counts unchanged; the only bundle validation warning is missing native debug symbols.
**Submitted for review on November 8, 2026.** Boki explicitly confirmed the Trader declaration ("Potvrdi ja"). Saved Yes / Trader applies to W Reader, matching the already-declared developer account; the optional Terms of Service URL remains blank. Submitted exactly two changes: `Closed testing - Alpha / 0.1.17 - Library saving and page zoom / Start full rollout` and `App content / Trader status / Complete app-level trader status declaration`.
Publishing overview visibly confirms **Changes in review**. Google's quick checks are still running; the page says the changes proceed to review when checks pass. Managed publishing remains off. Do not claim Google approval or tester availability for version 19 yet. Stable 0.1.16 remains the available release until the new Closed version is approved. Production was not changed.
Verified result URL: https://play.google.com/console/u/0/developers/4766816481672407684/app/4976011020982627479/publishing
Result screenshot `/workspace/shared/WReader-0.1.17-closed-in-review.png`, Library libfile_6855dc63f4888191b2df523f84343ec2. Cloud tab 27 is marked as the result page.
Earlier automatic approval review rejected skipping the incomplete declaration and saving it without explicit user confirmation. Those actions were not bypassed; the declaration was saved only after Boki's confirmation. The earlier pending form screenshot is retained as historical evidence, not the current release status.

## Superseded candidates — do not upload
CI 42501153050 passed 57 unit + 34/36 Android tests; the failures were an edge-zone test swipe invoking Android Back and a PDF disabled-state query on the icon instead of its button. Both fixed.
CI 42502684851 passed 57 unit + 37 Android tests and its former weak release smoke, but manual screenshot review caught blank Word/Excel content despite accessible DOM text. Its signed AAB is quarantined as `qa19-final/not-for-upload-signed.aab`; it was never uploaded.
CI 42504846023 correctly caught the actual-reader blank surface: body/viewport heights were zero despite window.innerHeight=590. Its other failure was shared test history, fixed by a unique import source. The 100vh attempt in CI 42506738237 still failed the Word screen check (37/38 Android tests passed).
The final passing candidate restores baseline natural document height and native vertical scrolling, keeps horizontal overflow in the document container, and adapts independent zoom anchors/worksheet vertical scroll restoration accordingly. It retains the bounded weighted native Office view, disabled overview/global zoom, visual-state readiness, pixel checks and OCR release checks.

The historical next-update plans below are implemented by this candidate; their earlier backlog-only status is superseded by this section.

---

# Next update — floating PDF page navigation (2026-11-08)

Status: user-requested next-update UI plan, not implemented or released.

- Add a compact floating page-navigation bar centered at the bottom of the PDF reader: Previous arrow, current / total page count, Next arrow.
- Position it above any banner ad and Android navigation/gesture insets. Provide enough reading-area clearance that bottom-page content remains reachable; do not overlap the ad or intercept document gestures outside the controls.
- Previous and Next change PDF pages, not app screens. Disable the appropriate control on the first/last page, with clear accessible labels and usable touch targets. Support Light/Dark and narrow screens.
- Context: user reports that document-wide zoom enlarging neighboring pages is distracting. The proposed solution discussed is an optional single-page mode, with zoom and one-finger pan scoped to that page, and a newly visited page initially fit to the viewport; continuous scrolling remains an alternative. Floating navigation placement is now explicitly requested. Exact mode/default and revisited-page zoom behavior remain design details to settle before implementation; do not silently replace the accepted continuous reader.
- Verify page navigation at zoom, first/last-page boundaries, page count, pan/zoom gesture handling, reading position, ad/no-ad layouts and system insets.

---

# Next update — Save locally from Library menu (2026-11-07)

Status: addition to the agreed next-update plan, not implemented yet.

- For a document opened from a web browser (and equivalent external sources), its presence in Library/Recent must not imply that a durable local copy has been saved.
- Add Save locally to the document's three-dot menu in Library, including Recent entries, whenever no durable local copy exists. This complements the reader's Save to Library action.
- Save the available document content as a durable local copy, mark the existing entry as Saved, and allow offline reopening without depending on the browser's temporary access. Preserve filename/extension, category, tags and Favorite state, and avoid creating duplicate Library entries.
- Distinguish temporary cached content from a deliberately saved copy. Once successfully saved, show a clear Saved locally state instead of offering the same save operation again.
- Only report success after the copy has completed. If source content is unavailable, explain that the document must be reopened from its source; retain the existing entry on failure.
- Verify browser-opened documents, temporary source access loss, save failure, repeated saving/deduplication, offline reopening and preservation of metadata.

---

# Next update — category list scroll indicator (2026-11-07)

Status: user-requested backlog only; record for the next update.

- In Library > Edit document > Category, the dropdown can show only part of the category list, but it is not obvious that more categories are available below. User supplied a screenshot demonstrating this.
- Make scrolling discoverable immediately: show a visible scrollbar when the list overflows, plus a small downward continuation indicator while more items remain below. Hide the downward indicator at the end of the list; avoid covering category labels or touch targets.
- Keep the dropdown within available screen space and make every category reachable by scrolling.
- Verify short and long lists, first/middle/last scroll positions, Light/Dark themes, smaller screens and larger text. Preserve category selection and document editing.

---

# Next update — agreed Library / Recent behavior (2026-11-06)

Status: approved backlog only. The user asked to record this for the next update; no implementation, build or release is requested now.

- Library has two tabs: Saved and Recent.
- Saved contains documents deliberately saved/imported by the user and keeps them until the user explicitly removes them.
- Recent contains documents opened in the last 30 days. Home continues to show only the latest three, with See all opening the full Recent list. Use last-opened time for the 30-day window.
- Opening a Viber, WhatsApp or email attachment opens it immediately and records it in Recent, without an interrupting save prompt or automatically adding it to Saved.
- Provide Save to Library in the reader to retain a durable local copy explicitly.
- Marking a document as a Favorite also saves it durably so the favorite does not disappear when Recent expires.
- Documents created through W Reader Tools are saved to Library.
- Keep existing Library documents during migration; apply the new intake behavior to future opens.
- Expiring a Recent entry must never delete the original file on the phone, a Saved document or a Favorite. Keep history expiry separate from temporary-copy cleanup; any cleanup must protect durable references and files currently open.
- If a temporary document is no longer accessible, explain clearly that it must be reopened from its source.
- Verify before release: 30-day expiry and reopening, Home latest-three/full history, attachment save/reopen, Favorites durability, Tools outputs, migration without data loss, and removal/Undo. Preserve accepted attachment retention and reader fixes.

---

# W Reader 0.1.16 — compact Settings, Closed Alpha

Released candidate `fd50e494435e1784baf1b4797f650582b4f23305` on `fix/wreader-0113-viber-billing-zoom-excel`, based on the accepted 0.1.15 build. VersionCode 18. The purchase section is flat and aligned with Data & help, with tight spacing and 48dp touch targets. Large text can stack. The version footer is centered. The tester reported the lifetime purchase still active the next day. The two earlier reversed test orders used the “approves then charges back” test card. Manual restore on a fresh install was not separately reported.

CI https://github.com/bokimmkd/DocuReader/actions/runs/41612707795 passed 55 unit and 33 Android tests. An APK from the optimized release AAB displayed all Settings rows, the version footer and a loaded test banner at 1080×2340 and density 450. Footer text was verified from the actual rendered pixels. An earlier check compressed the screen twice when an ad loaded. Its superseded artifact was not uploaded. Final evidence artifact 13221116156, unsigned bundle artifact 13220078525. Signed AAB has 19,217,879 bytes and SHA-256 `33a9f2be9c22e1d665d77669b5a674521cadc61bc98e383519c093c55c301a33`, Library `libfile_c90df0b6af508191a394d56215dccacc`. Verified unchanged payload and same accepted upload certificate as v17.

On November 3, 2026, Play accepted 18 (0.1.16-closed) for release 13 of the existing Alpha track. Publishing overview confirmed “Changes in review” for the sole item “Closed testing - Alpha / 0.1.16 - Compact Settings / Start full rollout”. Google quick checks were still running. No production rollout. The only Play warning was missing native debug symbols. Tester availability awaits Google approval.

---

# W Reader 0.1.15 — final visual polish, Closed Alpha

## Authorization and scope
The user authorized implementing the agreed design fixes and uploading the next Closed-testing update while the production-access test period completes. This supersedes the prior backlog-only notes below. Production publication is not authorized. Keep the same accepted upload signing key.
Version 0.1.14 (16) was accepted by the user for Excel, Favorites, PDF zoom and one-finger panning. Continue from that tested implementation, never from main or a blind branch merge.

## Implementation
- Version `0.1.15-closed`, versionCode `17`.
- Theme choices show System/Light/Dark icons with their labels and selected state.
- Ads & purchase is a grouped card with aligned icon/text columns, spacing, and Buy beside the lifetime offer. Narrow screens and font scale over 1.3 use a stacked layout. Active entitlement and Retry/price-loading behavior remain intact; billing code and live IDs are unchanged.
- Help and Privacy Policy use the W Reader logo, colors and typography, section cards, working public links, and a support email action. Privacy text and data practices are unchanged.
- Book mode themes cover toolbar, footer, dialogs and system bars, independently of app Light/Dark. Dark uses light icons. Sepia uses matching warm surfaces. Leaving Book mode restores the app theme and bar contrast policy.
- Existing Viber attachment retention, Buy/Restore, Excel PDF fitting, one-finger PDF pan and Favorites are retained.
- Three free original stories were discussed for a separate writing session; no stories are bundled in this release.

## Production eligibility observed
Play Console Dashboard was inspected on 2026-11-01 (UTC). Publishing a Closed release and at least 12 opted-in testers are marked complete. The remaining condition is at least 12 testers opted in continuously for 14 days. `Apply for production` is disabled. No remaining-day count or eligibility date was shown; do not invent a deadline or assume a new build restarts the test. Existing Alpha track/tester lists remain in place. Version 0.1.14 was shown available to selected testers.

## Verification and artifact provenance
- Branch: `fix/wreader-0113-viber-billing-zoom-excel`; start `f706aebafa2374ee4584046731507c9ef388046f`. Candidate `aeb977e5424c8c211b0f17217334301d8ace94d3`, tree `d1bca67678cd229bbbe9f8f820e095efa5914432`. No main merge or signing changes.
- Full CI https://github.com/bokimmkd/DocuReader/actions/runs/41358135714, job `125002518389`: SUCCESS. 55 unit + 33 Android tests, no failures/errors/skips. Actual optimized release-AAB APK smoke passed cold starts, resume, EPUB appearance, Word/Excel/image controls and Library removal/Undo.
- New Android regression checks Dark toolbar/footer/status/navigation backgrounds and icon appearance flags on both gesture and three-button navigation, Sepia footer, and app Light restoration on exit. Settings checks Light/Dark, 1.5x text, inactive/active entitlement layout, Help and Privacy branding.
- Visually inspected Light/Dark Settings, large text stacking, Active state, branded Help/Privacy, Dark Book mode in both navigation modes, and return to Light. QA Settings host has a test-only header/system bar configuration; production keeps the existing branded Settings header. Emulator three-button navigation icons appear dim despite correct appearance flags; verify their contrast on the tester Samsung after rollout. Dark backgrounds and gesture handle are confirmed.
- All 19 exported PDFs passed ink/margin checks; every text/table page has visible content and empty outer 20pt margins. Existing Office/PDF engines, monetization implementation and policy text have no diff from the accepted v16 baseline.
- Evidence artifact `13104138507`, ZIP SHA-256 `fc0f679a1716bb62942a5ced5625998550795c2a0aa25171fb1d70c50c6b7b16`; unsigned AAB artifact `13103934348`, ZIP SHA-256 `ef47ecfed96e7066d0c0c7b4322ddc37f6503bd3855d39107317a30c489b22f3`. Both downloaded ZIP digests match GitHub metadata and exact candidate SHA.
- Unsigned payload SHA-256 `6318a34d6c71d9fc7d109eb9fb0954e756bdbb5da0393edd8bdf13631a8057a8`.
- Signed `WReader-0.1.15-closed-v17-signed.aab`, 19,219,749 bytes; SHA-256 `55e8deb007706d56d156e4578d9d6fff08bf89fcdd648a75d76ee1716d066ed2`. Original AAB entries byte-identical after signing; only signature entries added. Jarsigner verification, certificate match, manifest version/package/min26/target36, non-debug release, four ABIs, ReTrace map, live AdMob ID and absence of test Activities passed.
- Same accepted SHA-256 upload certificate `16:76:77:B4:3B:67:4F:73:C2:87:73:C1:D1:94:D8:C3:F8:98:54:05:4C:98:AE:19:30:76:3F:06:67:15:77:F8`.
- Saved AAB: `libfile_21e0ab2eca4c8191b8b4a196e1c1dff2`.

## Play submission
Alpha release `12`, title `0.1.15 - Reader themes and Settings polish`, accepted AAB `17 (0.1.15-closed)`; previous version 16 excluded. Saved a full rollout to the existing Closed Alpha track. No device support loss. Only non-blocking warning: native debug symbols absent for bundled third-party native libraries; ReTrace mapping is included.
Submission verified on 2026-11-01 UTC: Publishing overview shows `Changes in review` for the sole change `Closed testing - Alpha` / `0.1.15 - Reader themes and Settings polish` / `Start full rollout`. Quick checks are running; Google states review dispatch proceeds when they pass. This is submitted, not an approval or evidence that version 17 is already available to testers. Managed publishing remains off. No Production changes, tester-list changes, key changes or billing changes.
Verified page: https://play.google.com/console/u/0/developers/4766816481672407684/app/4976011020982627479/publishing

## Physical acceptance after rollout
Check Settings in Light/Dark, Buy/Active/Restore layout and large fonts. Open Help and Privacy, confirm branding/back navigation and links. In Book mode switch Dark/Sepia/System, check upper/lower controls and phone bars, then exit and confirm the app returns to its own theme. Existing Excel/Viber/purchase/PDF fixes remain the accepted baseline.

---

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
