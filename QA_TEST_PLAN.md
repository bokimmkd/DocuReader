# Android QA / acceptance matrix

**Status: test plan only. No Android emulator/device tests executed.**

| Document | Test input | Expected | Status |
|---|---|---|---|
| Text PDF | 20 pages, selectable text | Smooth reader, search page match, go-to-page, thumbnails, print original | NOT RUN |
| Scanned PDF | 8 image-only scanned pages | Page views; no incorrect text search claim | NOT RUN |
| Protected PDF | AES/password with known password | Password dialog, correct/wrong password handling, private temp cleanup | NOT RUN |
| Large PDF | 300+ pages incl. huge images | Lazy scroll, rotation/background, no OOM, cancel behavior | NOT RUN |
| Rich DOCX | Tables, image, page breaks, headers, multi-page | Faithful offline rendering (engine not selected) | BLOCKED |
| Rich XLSX | Multiple sheets, merges, dates, numbers, formulas | Sheet selector, formatted cells, cached formula values (engine not selected) | BLOCKED |
| Invalid/expired URI | Grant revoked or document deleted | Useful message + Locate action | NOT RUN |
| Attachments | Gmail, Viber, WhatsApp, file manager | ACTION_VIEW/ACTION_SEND open without manual forwarding | NOT RUN |
| Extract PDF | Select `2, 5, 10–12` | Exact 5 pages in that order, original byte-identical | NOT RUN |
| Merge PDFs | Source 1 all, source 2 pages `1-3` | Combined PDF, original sources unchanged, save destination prompt | NOT RUN |
| Organize PDF | Move, rotate 90/180/270, delete | Exact page order/orientation, no empty exports | NOT RUN |
| Signed/form PDF | Interactive forms and cryptographic signatures | Explicit warning, inspect feature loss, do not promise preservation | NOT RUN |
| Ads | Free / Consent needed / Consent not needed / Offline | One small bottom banner, no overlay or interference; offline reading continues | NOT RUN |
| Billing | Pending / success / cancel / restore / refund / offline | Actual store price, no ad-free for pending, no ads for verified owned purchase | NOT RUN |
| Samsung | A16 or A52, Android 16, free memory stress | No crash, no white screen, app background/resume works | NOT RUN |
| Rotation | Reader and page editor after changes | Page editor changes persist, reader position retained when enabled | NOT RUN |

## Test execution checklist

1. `gradle :app:testDebugUnitTest :app:assembleDebug` (API 36, JDK 17).
2. Install debug APK. Keep AdMob **test IDs** during testing.
3. Open documents through picker and through at least 3 real apps.
4. Compare byte SHA-256 of source PDFs before/after transformations.
5. For exported files, inspect with independent desktop PDF viewer, verify page order and rotation.
6. Exercise stale URI after revoking persisted permission in Android settings and after deleting source document.
7. Inspect PDFBox third-party dependencies via OWASP dependency check and applicable CVEs.
8. Configure Play internal tester and one-time `remove_ads` product; test purchases, pending, cancel, restore, refund, offline cached rights.
9. Check banners with gesture navigation and 3-button navigation, status bar, keyboard, rotation.
10. Run Android vitals, Pre-launch report, Play policy and accessibility audit; fix before production.

Pure Kotlin page range parsing test executed successfully; all other tests await an Android build runner and devices.
