# W Reader — Closed testing launch handoff (30 October 2026)

This is the **W Reader Android app**, not OTS/StayGuide/etc. Never reuse other apps' AdMob identifiers, keystores or Google Play records.

## Verified GitHub and build identity
- Repository: `bokimmkd/DocuReader`
- **Correct Play applicationId:** `com.blw.wreader`, on `release/wreader-closed-0.1.4` (versionCode **5**, versionName `0.1.4-closed`)
- Correct release workflow: `.github/workflows/wreader-closed-0.1.4.yml`, run **41022866546**, build succeeded. **Workflow output is unsigned** and not the Google-accepted signed bundle.
- Prior Console work: an **original signed 0.1.4 AAB with the correct package was accepted** into the intended Play app; Closed testing activation/review has **not been independently confirmed** from Console in this session.
- A second **wrong-package draft** is thought to exist from initial console setup. It has not been independently identified by Play app ID and has **not been deleted**. Check developer account, package names, status and release history before deletion. Do not delete the accepted correct entry.
- The default repository `main` is an old 0.1.0 code line; not a Play release source.
- GitHub branch `feature/wreader-admob-0.1.5` is preparation for **future** versionCode **6**, versionName `0.1.5-admob-prep`; do **not** upload without signing and Play app identity verification.

## AdMob configuration: built but NOT connected to a live W Reader account
- Debug variant uses Google's sample app/banner IDs and UMP. Release variant has **ADS_ENABLED=false** unless **all** explicit Gradle properties are provided:
  - `WREADER_ENABLE_RELEASE_ADS=true`
  - `WREADER_ADMOB_APP_ID` must be the **W Reader** AdMob Android application ID (`ca-app-pub-…~…`).
  - `WREADER_ADMOB_BANNER_ID` must be a **W Reader** anchored adaptive banner unit ID (`ca-app-pub-…/…`).
- Production sample IDs are rejected when advertising is explicitly enabled. Do not put user identifiers or passwords in source or logs. AdMob IDs are publisher IDs, but keeping their installation config in approved CI variables is preferable.
- `PURCHASES_ENABLED=false` for release. Turning on advertising must **not** turn on unverified `remove_ads` payment.
- **Before ads-enabled Play update:** create/link W Reader in AdMob, create banner ad unit, check app approval/eligibility, configure UMP consent, update hosted Privacy Policy and Play Data safety + Contains ads. Do not assert W Reader AdMob approval or earnings from other apps' email notices.
- **Closed 0.1.4** existing accepted app bundle is ad-free and with purchases disabled. The new 0.1.5 code is not a Play release and does not modify 0.1.4.

## Google Play Console pending manual/browser actions
The current chat has GitHub/Gmail/Drive connectors but **no authenticated Play Console / AdMob app-creation / deletion / upload browser tool**. These are **not completed** here.
1. In **Work mode / Cloud Browser**, open https://play.google.com/console, select the correct developer account and enumerate the **two W Reader entries**, read back app ID/package and status.
2. Confirm **correct entry** has package `com.blw.wreader` and the previously accepted signed `0.1.4` AAB. **Do not recreate** this app or upload from `main`.
3. Only then delete **wrong unused draft** via Play Console **Test and release → Advanced settings → App availability → Delete app**, if eligible. Deletion is permanent and may have restrictions; if it is not eligible, leave it inactive and report actual reason.
4. Complete Store listing, app category, support/privacy links, content rating, ads declaration (correct for actual 0.1.4), app access, target audience, Data safety, countries, testers and other mandatory dashboard items. Existing published privacy URL must be verified.
5. Closed testing → create/edit release → ensure the previously accepted bundle is attached → release notes → submit for review → verify actual status/read back. **Production is explicitly out of scope**.
6. Testers/opt-in: for applicable new personal developer accounts, Google requires 12 continuously opted-in testers for 14 days before *production access*, not as evidence that this closed release has already started.
7. Confirm upload signing identity against the **accepted** Play signing lineage. Never generate/replace a previously registered upload certificate speculatively.
8. Verify app crashes/ANRs and open-with PDF, DOCX, XLSX, Print preview fidelity, page scrolling, offline app behavior and Library category persistence as follow-up QA items.

## Additional known app requirements
- Product: fast offline PDF/Word/Excel reader, local Library, PDF page organizer; no W Reader account; PDF originals unchanged when saving new versions.
- User requested persistent custom categories and last Library category selection in next upgrade (separate backlog; not part of signed 0.1.4 AAB).
- One bottom AdMob banner only; never interstitial/opening ads. One-time Remove Ads target $1.99, **not** active/verified in 0.1.4.
- Print fidelity of DOCX/XLSX remains unverified and may differ from original. Do not claim desktop-equivalent rendering or printing.
- Do not expose tokens, Google credentials, upload keystores, signing passwords or app-user data in handoff.

## Latest AdMob preparation verification (2026-10-30)
- GitHub Actions workflow `.github/workflows/wreader-admob-0.1.5.yml` **SUCCESS**, run **41050415989**, unsigned AAB artifact **12974143231**.
- Locally examined artifact `app-release.aab`: ZIP integrity **PASS**, bundled Office scripts included. Manifest string content includes `com.blw.wreader` and `0.1.5-admob-prep`. App Gradle `versionCode=6`.
- SHA256 of unsigned AAB: `9bb486757cad7d76d54c0dbc250e44c48f19f77e150f32b9f7d1119e6f45478a`.
- This new **unsigned** 0.1.5 AAB **cannot be used for Play upload** until signed with the authorized, existing correct upload key. Do not replace the already accepted signed 0.1.4 while working on AdMob.
- No W Reader-specific AdMob app/banner IDs were found in the searched email evidence. This is **not** proof an AdMob entry does not exist: check the AdMob console via the browser during the authorized follow-up.
- Suspected wrong draft Play app package: `com.blw.documentreader` (historical old test package), **not Console-verified**; confirm exact entries before deletion.

## Release rule
Explicit user approval is already to prepare **Closed testing**, not Production. GitHub build status alone cannot prove Play Console review submission. Only announce Play upload/review if verified from the Play Console itself.
