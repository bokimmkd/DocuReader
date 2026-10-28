# Engine selection & license evaluation — 2026-10-28

## PDF viewing — Android PdfRenderer
- Framework-provided, no third-party redistributable PDF renderer. API 21+ rendering; API 35 adds password `LoadParams` and text search APIs.
- Chosen for V1 raster rendering. PDFBox-Android provides selectable-text search independently for API 26+.
- Does not OCR image-only scanned documents. For security, Android recommends considering isolated rendering for untrusted PDFs.
- https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
- https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page

## PDF text and page manipulation — PDFBox-Android
- `com.tom-roush:pdfbox-android:2.0.27.0`, Apache 2.0; last Maven version (published Jan 2023). 
- Upstream Apache PDFBox is maintained, but this Android port is far behind upstream, so production deployment requires security and maintenance review. Third-party attribution/NOTICE required.
- Imported page contents MAY lose annotations/forms/tags/outlines/signatures. Warning is mandatory; don't promise full feature preservation.
- No paid SDK required for prototype; release compliance requires license notices.
- https://github.com/TomRoush/PdfBox-Android
- https://central.sonatype.com/artifact/com.tom-roush/pdfbox-android/versions

## Word DOCX and Excel XLSX — NOT SELECTED / NOT IMPLEMENTED
- Requirement is faithful on-device rendering, not text extraction. Apache POI alone can parse DOCX/XLSX but is not a native layout renderer and is inadequate for the acceptance criteria.
- Aspose.Words for Android via Java has a commercial license; advertised developer Small Business price $1,199; deployment/license terms must be checked for mass-distributed consumer app. Unlicensed evaluation adds watermark and document-size limits.
  https://purchase.aspose.com/pricing/words/android-java/
  https://docs.aspose.com/words/java/licensing/
- Aspose.Cells for Android via Java advertises native spreadsheet rendering, multiple sheets/formulas and high fidelity. It also uses commercial licensing; production price/appropriate OEM licensing requires a vendor quote. Runtime/offline behavior must be tested.
  https://docs.aspose.com/cells/java/aspose-cells-for-android-via-java-features/
  https://products.aspose.com/cells/android-java/
- ONLYOFFICE's mobile apps are separate products; white-label/editor embedding uses ONLYOFFICE Docs Developer and licensing. Open source availability does NOT grant unrestricted proprietary embedding. Its standalone open-source Document Editor is AGPLv3.
  https://helpcenter.onlyoffice.com/faq/faq-document-editor.aspx
- Do not ship a DOCX/XLSX claim until at least: 3 complex example docs each, offline testing, font substitution, worksheet merged cells, cached formula values, 50+ page/large-sheet performance, license/redistribution signoff.

## Business model
- AdMob adaptive anchored banner, documented SAMPLE app ID in source; UMP required before ads.
- Google Play Billing one-time INAPP product `remove_ads`, actual localized price from Play. Configure USD 1.99 base in Play Console. Purchase token server verification and live license tester QA required.
- https://developers.google.com/admob/android/banner/
- https://developers.google.com/admob/android/privacy
- https://developer.android.com/google/play/billing/integrate

## Google Play target API
- Starting August 31, 2026, new Android phone apps must target API 36+, so this project targets API 36.
- https://support.google.com/googleplay/android-developer/answer/11926878
