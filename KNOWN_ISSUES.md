# W Reader — 0.1.0 QA findings / proposed 0.1.1 backlog

Current 0.1.0 debug APK compiled successfully, but the following issues were reported by a Samsung tester. **Do not treat these as fixed** until a later implementation and real-device verification.

1. **PDF reader continuous scrolling:** The reader requires navigation through the Pages menu. Implement seamless vertical page-to-page scrolling with zoom and page indicator; keep Pages for direct jumping.
2. **Extract Pages selection:** Thumbnail selection should produce an output without manually typing a page-range string; keep typed range as an optional shortcut. Review selection for Add Pages and Delete Pages too.
3. **Open-from-Viber Recent:** A PDF opened via Viber did not appear in Recent. Reproduce through Android ACTION_VIEW/SEND and refresh/update the Recent list immediately; handle intent re-entry correctly.
4. **Top system insets:** Status-bar clock overlaps the app header in the screenshot. Respect status bar and gesture navigation/window insets.
5. **Compact UI:** Reduce oversized header, paddings, section gaps, explanations, and bottom navigation height (target around 56–60dp with accessible touch targets); preserve a small AdMob banner outside the document canvas.
6. **File-type icons:** Use clearly differentiated PDF red / Word blue / Excel green icons in Recent, Favorites and file UI. Do not claim Word/Excel rendering until implemented.
7. **Settings polish:** Compact rows and short explanations; verify localized purchase label and restore flow (not configured for production).

Scope for 0.1.1: these bugs and visual refinement only. No repository changes to other apps, no signing or Play release without separate approval.
