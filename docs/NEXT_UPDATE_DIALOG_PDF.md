# W Reader 0.1.10 closed: document editing, image display and direct PDF saving

## Authorization and scope

The user authorized the agreed changes through AAB with “Praj promena do aab bis” on 2026-10-31. This supersedes the earlier no-AAB restriction for this release only. Closed Alpha testing; no Production release. Version code 12, applicationId com.blw.wreader, same upload key and user data store.

## Changes

The document editor now uses W Reader branding, theme-matched surfaces and a blue Save action. Category is a dropdown containing existing categories, defaults, Uncategorized and New category. Name extension retention, tags, Cancel and original file bytes are preserved. There is no global Delete Category manager in this release; choosing Uncategorized removes a document's category. Keep offline copy remains an optional app-private copy, not cloud backup.

JPG/PNG decoded correctly in 0.1.9 but the production Compose/WebView path computed an image max-height of zero from 85vh, producing a blank reader. Removed the viewport-height cap from image/slide content while retaining width and aspect-ratio constraints. Production-reader instrumentation now checks actual visible colored pixels, natural/rendered image dimensions and tall/camera fixtures.

Convert to PDF creates a real local PDF before opening Android CreateDocument. After a complete stream write, PDF saved offers Open, Share, Print and Done. The saved destination URI and provider's actual filename are registered in Library and retained when clearing Recent. Only an explicit printer action starts PrintManager. A PDFBox text-preservation check rejects output that cannot retain source text in order; it does not replace visual/layout acceptance. Source bytes and reader zoom are unchanged; Excel uses the selected worksheet and existing completeness/readability checks. Canceling the destination picker does not claim success. Private staging files are removed on completion/error/cancellation; a failed provider write can leave an incomplete destination file, so an error does not claim it was saved.

## Engine and data boundaries

Uses packaged jsPDF 4.2.1 and html2canvas 1.4.1 (MIT), plus Noto Sans/Math/Symbols/Symbols2 from pinned notofonts/noto-fonts commit ffebf8c1ee449e544955a7e813c54f9b73848eac (OFL). Licenses ship with assets. Office capture embeds already loaded local images; CSP permits local blob reads but blocks remote hosts. A temporary noninteractive WebView renders sanitized document HTML using only packaged scripts/assets and an intercepted same-origin payload. No JavaScript bridge, document upload, hidden print callback constructor, reflection or package-name shim. Image-only PDF uses Android PdfDocument and the bounded reader preview.

Portable fonts support Latin, Greek, Cyrillic and common mathematical symbols. Layout can differ from the original Office document. Unsupported characters fail explicitly with a Print → Save as PDF fallback, rather than silently losing glyphs. Advanced complex-script shaping is not claimed. Export is bounded to 32 MB snapshot payload / 64 MB PDF, with preparation timeout and cancellation. Font embedding increases AAB size.

## Verification

The integration workflow tests actual Office/PDF save UI and system file picker, multi-page Word/selected Excel/TXT/CSV text, Cyrillic and math markers, all fixture rows/paragraphs, whole-row table pagination, orientation, zoom independence, image-only PDFs, saved Library registration, Open/Share callbacks, explicit Print and picker cancellation. Branded category editing is tested in light and dark themes. The complete release workflow additionally runs existing unit, print, format, Settings, Open with and update lifecycle tests. Integration run 41241936090 (head cf662659dbc1b601b7c7416ebcf680a25b2c53c2) passed all 4 Android cases. Its actual UI used DocumentsUI, saved valid Word PDF bytes, offered Open/Share/Print, opened PrintSpooler only after Print, canceled a later picker without success, and retained the registered PDF after Clear Recent. Word/Excel/TXT/CSV exported at 50% and 200% reader zoom with all test markers/rows and Cyrillic, common math in Word/TXT, orientation and no blank pages. Final release CI also includes a table-width guard, a stricter per-row assertion and pixel checks that every exported page has a clear outer print margin. Record its actual run ID/result in the final handoff; uploading an AAB is not proof of a Play upgrade.

## Retained limitations

Library files reappeared and the user suggested an unnoticed filter. No permanent deletion or one-hour storage/migration delay was established. Help explains checking All/categories/tags/search; storage identifiers remain unchanged.

The Google Play update mechanism from 0.1.9 remains included. Mock/lifecycle tests do not prove an owning Play tester can upgrade. The pending real scenario is code 11 installed from Play, then an eligible code 12 available on the same Alpha track. Old installed builds retain old update logic until upgraded. No real purchase, ad click, physical print or Production publication is performed.
