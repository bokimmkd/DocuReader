# W Reader — project handoff and technical operating procedures

**Updated:** 2026-10-30. **Scope:** documentation/knowledge transfer only; this handoff does **not** authorize code changes, builds, deployments, publishing, release promotion, or access checks in other systems. Carry out those actions only after an explicit concrete task. No passwords or tokens belong here.

## Project identity / latest known state (historical facts, re-check before action)
- Project / Android display name: **W Reader** (BLW Document Reader).
- GitHub repository: **bokimmkd/DocuReader**; working branch **feature/wreader-office-scroll-0.1.3**; default `main` was last observed at older **0.1.0**, without Library or Office reader. Do **not** use `main` as the release source without first checking/merging the intended release candidate.
- Android `applicationId` / package: **com.blw.documentreader**.
- Latest known working branch config: `versionCode = 4`, `versionName = "0.1.3-office-scroll-preview"`; verify before each AAB.
- Relevant GitHub Actions workflow: **.github/workflows/wreader-office-0.1.3.yml** — builds/tests **debug APK**, bundles pinned offline Office JS engines and runs Android 36 smoke tests. Previous successful run: **40957964426** (2026-10-29), with APK artifact **12932849675**, screenshots **12935270235**. These are *debug* artifacts, not Play-uploadable signed release AABs.
- Development features: local-first PDF / DOCX / XLSX reading, local Library, PDF page organizer; current Word and Excel preview uses locally bundled JS engines (HTML/CSS limitations), recent Office zoom 50% default / 25–250% range. PDF scrolling has been fixed in the working branch and smoke-tested, but real-device edge cases and print fidelity require further testing.
- Release signing: not previously configured in `app/build.gradle.kts` release block. **No verified signed AAB** exists in the cited workflow.
- Google Play Console: **new W Reader app/closed-testing release not confirmed created or uploaded**. Real AdMob IDs, purchase product and privacy/disclosures need verification; test IDs were previously observed. No confirmed W Reader Firebase/Sites project or deployment pipeline.
- Existing readiness checklist: GitHub issue **https://github.com/bokimmkd/DocuReader/issues/1**. Source of truth for release readiness must be verified afresh.

## Standard working procedure adopted from OTS (procedure only; NOT a claim that OTS access applies to W Reader)

### 1. GitHub
Use the connected **GitHub connector** for code reading, repository/branch/commit inspection, edits when authorized, GitHub Actions runs/logs and artifact downloads. **At the next concrete task**, first discover available connector actions and confirm access to this exact repository and branch. Do not infer W Reader permissions from other projects. Prefer small changes and verify resulting commit.

### 2. Android builds and signed artifacts
Use the project's existing appropriate **GitHub Actions workflow**, inspect build/test results and artifacts. When tasked with a Play release, configure/use an approved signed **release AAB** workflow; do **not** substitute debug APK. Verify package/applicationId, versionCode, versionName, signing configuration/upload-key continuity, and workflow outcome. Do not place keystores, passwords, signing secrets or API credentials in repository, logs or handoff. Keep original file checks and release tests separate from upload.

### 3. Google Play Console
Operate via browser only when user explicitly instructs: select the **correct developer account and W Reader app**, upload the verified AAB, prepare a **Closed testing** release, submit for review, and **read back status**. A successful GitHub build alone does not imply a Play Console upload. **Production requires separate explicit approval.** Browser/Cloud Computer access must be established when the task is assigned; connector or browser availability should not be presumed.

### 4. Firebase
For tasks that actually require it, check the specific Firebase project and status via browser. Before a Firebase CLI deployment, inspect the existing deploy script, exact Firebase project ID and **authenticated CLI credentials**. Browser login is **not proof of CLI authorization**. No W Reader Firebase project/deploy target is established by this handoff; do not invent one.

### 5. Sites-based web portal
The OTS portal uses a Sites connector and source workflow: existing project → checkout → change → test/build → saved revision → deployment. This is **OTS-only process knowledge**, not a direction to migrate W Reader from any hosting solution to Sites. Only use if a separately confirmed W Reader scope and explicit task requires it.

### 6. Missing access and user involvement
When authorized work hits an access issue, diagnose the exact missing permission, sign-in or repository URL first. Request **only the specific** user action needed. Asking the user to upload ZIP files or paste terminal commands is a **fallback after a confirmed blocker**, not the normal workflow. Do not assert that access works merely because it worked on OTS.

### 7. Future handoff updates and evidence
For each completed authorized task, document exact project IDs, repository/branch/commit, GitHub workflow/run, versionCode/name, signed AAB artifact ID/checks, relevant deployment procedure, Console/Firebase project IDs **only after verified**, observed access scope, test/review statuses and remaining blockers. Never document secret values, tokens or passwords.

## Operational guardrails for this handoff
- **Current instruction:** documentation transfer only. No app code change, Android build, Play Console app creation/upload, Firebase deployment, publication, or production promotion.
- A documentation-only commit to this handoff uses `[skip ci]` to avoid automatic builds.
- Check current access/capabilities and configuration **at the time of a subsequent concrete task**, not now merely because an OTS procedure mentions them.
