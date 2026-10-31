# W Reader in-app update fix

Source comparison: PTS premium-analytics-upgrade, commit 7b9391a412906e4004d12a0ab45e1bd1e54c92e1 (PlayUpdates.kt, UpdatePrompt.kt, UpdateOffer.kt and lifecycle/transfer tests). Adapted retained session, transfer reducer, expiring check tickets, lifecycle listeners, foreground retries and single-use Play consent. W Reader uses a compact non-modal banner rather than PTS dialogs.

## Confirmed source defects

- Old code set checkedAutomatically before Play replied. Failure/no-update could suppress subsequent checks for the Activity visit.
- The 24-hour preference hid the same available version across starts, contrary to Later until next opening.
- In 0.1.8, the idle LaunchedEffect could call while UMP was unsettled and return; consent completion was not an effect key, so the opportunity could be lost. Document/output state gated the check rather than only the UI.
- Availability was not retained for a branded offer. The listener exposed only downloaded/installed/failure, with no pending/download progress. refreshDownloaded onResume recovered only Ready.
- No retained transfer/consent session across recreation.

These demonstrate code defects; the exact reason for Boki's earlier phone not receiving an offer cannot be established without that installation's Play reply, version and account state.

## Behavior

Query the real AppUpdateManager onResume, then after 15 seconds and every 30 seconds while resumed; request timeouts expire after 15 seconds. Ignore responses from an obsolete lifecycle/request or preceding a newer install event. No network check blocks document opening or depends on UMP. UI is deferred during consent/printing and uses retained observable state when safe.

Available (only actual Play availability and higher version) -> Update/Later. Update starts Play flexible consent. Pending/downloading -> real bytes/total progress. Only DOWNLOADED -> Restart to update. Restart invokes completeUpdate only on explicit tap; disabled while saving or printing; save any tool edits before explicitly restarting. Later suppresses current foreground visit, survives rotation and returns on next foreground opening, matching PTS. Play consent is kept in the same visit. Check/listener failures remain usable; retry on resume/poll/manual check. No permanent success/failure preference, no fake release availability.

## Validation layers

1. Unit tests: version guard, Later/rotation/foreground/session behavior, consent callback ordering, download stage monotonicity, 100% not equal to DOWNLOADED, installation failure/retry, request coalescing/timeout/stale callback rejection.
2. Android instrumentation: official FakeAppUpdateManager plus actual production controller/banner, actual Activity lifecycle/rotation/listeners, document-action usability during download, progress/ready/install/failure, recovered ready state and failed query retries. FakeAppUpdateManager does NOT contact Play or install a real update.
3. Required real-device Play test: not completed in this environment. No Android tester device/session is connected; no actual installed package, signing certificate or owning Google Play account can be inspected remotely.

## Real Play acceptance, after a separately agreed release

- Keep the fixed build installed from Closed Alpha on the owning tester's Play account; a later higher-version fixed build must be available to the same account/track. The current installed 0.1.7/0.1.8 contains the OLD mechanism; uploading a fixed next AAB cannot replace its existing code before installation.
- Confirm package com.blw.wreader and lower installed versionCode. The last published build is 0.1.8/versionCode9, Closed Alpha track4699280419648890924,178countries,all4 tester lists. Account membership/ownership and available version must be verified on that phone.
- Use the established upload key; Google Play signs installed APKs with its app-signing key. Do not compare an upload-key fingerprint to an installed APK as though they were the same key. No reset/new key required.
- Open offline; documents still work. Connect and reopen; verify actual Play-driven availability banner.
- Later -> no reoffer during same visit or rotation; leave/reopen -> offer returns.
- Update -> genuine Play consent -> background progress while reading documents -> Ready -> explicit Restart to update -> check higher installed version and data retained.
- Background/rotate during consent/download; reopen after completed download; no duplicate dialogs/listeners or lost restart offer. Test cancellation and network/download failure.

## Release constraint

User: Ne praj nov aab bez dogovor. No new AAB, no version bump, no Play submission, no Production publishing. Source is isolated on fix/wreader-play-updates-20261031. CI runs unit tests and debug Android instrumentation only and uploads test evidence, not an installable release bundle. Do not merge to release/wreader-closed-0.1.4 without agreement because that branch automatically builds an AAB. Formats/Help/in-app Privacy remain queued and excluded from this focused fix.
