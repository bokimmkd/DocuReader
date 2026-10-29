# W Reader 0.1.1 — Samsung acceptance APK

This is a **debug / UI acceptance build**, not a production or Play release.

## What should be visible
- Compact W Reader branding with the two-page icon.
- Compact PDF / Word / Excel format-filter cards.
- Home, Tools, Settings bottom bar fully above the Android navigation buttons.
- Shortened Settings section and item spacing.
- AdMob test banner only, outside the content canvas.

## Acceptance on a Samsung Android device
1. Install, launch, verify header is not under status bar.
2. Home and Settings in Light and Dark, with three-button and gesture navigation if available.
3. Tap each bottom tab; check the ad does not overlap navigation.
4. Tap PDF / Word / Excel filters; open a text PDF and re-open from Recent.
5. Inspect PDF Tools but **do not treat known defects as fixed**.

## Known limitations / not release ready
- DOCX / XLSX rendering not implemented; selecting Word or Excel currently shows an informational message.
- PDF vertical scrolling across pages is still a known bug.
- Extract visual selection is still a known bug.
- External attachments in Recent require additional tests.
- Local Library tab, categories, tags and stamping are only planned, not implemented.
- Play Billing and advertising production IDs are not configured. Build is unsigned for Play delivery and uses debug signing.

Only after device acceptance should a separate signed AAB/Play Console closed-test submission be prepared. Do not upload the debug APK to Play Console.
