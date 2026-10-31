# W Reader 0.1.9 image display investigation — 31 October 2026

Scope: user explicitly requested investigation only, no new AAB. Production source,
versionCode, signing and Play tracks are unchanged. QA lives only on
`qa/wreader019-image-display`, based on release commit
`f0999e4cee926f1d05bacad2f964af92e3a825e5`.

## Confirmed defect

User screenshot: JPG reader header and successful image preview notice are visible,
but the document area is white. Reproduced with the unchanged production
`TextReaderScreen` in a debug-only host on Android API 35.

GitHub Actions run `41235793972`, job `124639186519`, QA test commit
`c630e1bf77997ea1a5da81706b9b5217586b99ee`. One instrumentation test covers
three generated blue/orange fixtures. All three correctly fail the visible-image
assertion. Evidence artifact: `13056190591`.

| Input | Decoded natural size | Displayed image bounds | Computed max-height |
| --- | --- | --- | --- |
| JPG 600 × 400 | 600 × 400 | 0 × 0 | 0px |
| PNG 1080 × 1920 | 1080 × 1920 | 0 × 0 | 0px |
| Camera JPG 4032 × 3024 | 2016 × 1512 after sampling | 0 × 0 | 0px |

For all cases the image's `complete` property is true, its source is the generated
local JPEG data URI, and the WebView has a nonzero native size (1080 × 1510) and
DOM viewport (412 × 576). Neither expected blue nor orange pixels appear. The
camera fixture screenshot shows the same header/notice/white content as the
user's screenshot.

`LocalFormats.wrap` supplies `.image img{max-height:85vh;object-fit:contain}`.
In this Compose/WebView display path that viewport-based cap resolves to zero,
collapsing the successfully decoded image. This establishes a display-size defect;
it is not evidence of a missing source file or failed decoder.

The exact WebView viewport initialization/recalculation sequence still needs a
controlled follow-up. A prospective fix should remove the zero-prone viewport cap
or derive display bounds after layout, preserve aspect ratio/width fitting and
scroll/zoom, and retain printing's physical-size bounds. No such production fix
has been applied. `.slide img` also uses a vh cap and needs a separate preview
regression check; it was not exercised in this investigation.

## Prior coverage gap

The release's extended-format test rendered PNG into a separate raw WebView and
confirmed a real saved PDF contained an image. JPG/WebP were checked for decoding
and generated HTML. Those checks did not exercise the actual Compose reader's
on-screen dimensions. Their successful result was insufficient proof that these
formats displayed inside W Reader. The new test measures the actual screen and
records DOM size only after capturing the unchanged screen. JavaScript is enabled
temporarily in the diagnostic host for read-only DOM measurements, then disabled;
that instrumentation never ships and does not alter the release.

## Library report remains open

User reports Library empty after updating while Recent remains. User confirmed one
Recent file still opens. v0.1.8 and v0.1.9 use the same `metadata_only/recent` store,
entry fields, private `files/library` directory and FileProvider path. Both views
use `vm.recent`. No update-time removal of private Library files was found in the
source comparison; startup cleanup touches only named temporary cache files.

Library's initial filter can survive navigation from Home; bottom Library navigation
does not reset that filter. A hidden-filter explanation is plausible but has NOT
been confirmed on the user's phone. The question about clearing All/category/tag/
search filters was not answered. Do not claim all documents are intact or restored,
or that the Library symptom is fixed. Do not uninstall or clear app data while
investigating preservation.

## Release state

The release source remains `f0999e4cee926f1d05bacad2f964af92e3a825e5`,
0.1.9-closed/code11, previously verified available to selected Closed Alpha testers.
No new release bundle, version bump, upload, review submission or Production action
was performed for this investigation. The diagnostic workflow builds only the
debug test installation and uploads logs/screenshots; it has no bundle task.
