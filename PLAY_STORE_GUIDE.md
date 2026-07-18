# Google Play Store Publication Guide

Complete step-by-step guide to publish EbookReader on Google Play Store and automate releases via GitHub.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Google Play Setup](#google-play-setup)
3. [Release Automation](#release-automation)
4. [Testing & Rollout](#testing--rollout)
5. [Monitoring & Updates](#monitoring--updates)
6. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### 1. Google Play Developer Account

- Cost: $25 (one-time)
- Sign up: [Google Play Console](https://play.google.com/console)
- Accept **Developer Agreement & Policies**
- Add payment method

### 2. Signing Key (Release)

Generate a release signing key that will be used for all APK signatures:

```bash
# Generate 2048-bit RSA key valid for 10 years
keytool -genkey -v \
  -keystore ebooks-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias ebooks-release \
  -storepass <YOUR_STORE_PASSWORD> \
  -keypass <YOUR_KEY_PASSWORD>
```

**Important:** 
- Save the `.jks` file securely (NOT in the repository)
- **Never commit to git**
- Store passwords in a password manager
- Back up the keystore file

### 3. GitHub Secrets Configuration

Add these secrets to your GitHub repository (Settings → Secrets and variables → Actions):

#### Signing Secrets

```bash
# 1. Encode keystore to base64
base64 ebooks-release.jks | tr -d '\n' | pbcopy

# Paste into GitHub secret: SIGNING_KEYSTORE_BASE64
```

Then add these secrets in GitHub:

| Secret Name | Value |
|------------|-------|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded `.jks` file content |
| `SIGNING_STORE_PASSWORD` | Password used for keystore |
| `SIGNING_KEY_ALIAS` | `ebooks-release` |
| `SIGNING_KEY_PASSWORD` | Password used for key |

#### Play Store Secrets

```bash
# Create Service Account in Google Play Console:
# 1. Go to Settings → API access
# 2. Click "Create Service Account"
# 3. Grant "Admin" (all permissions)
# 4. Create JSON key
# 5. Encode to base64:

base64 service-account-key.json | tr -d '\n' | pbcopy

# Paste into GitHub secret: PLAY_STORE_SERVICE_ACCOUNT
```

| Secret Name | Value |
|------------|-------|
| `PLAY_STORE_SERVICE_ACCOUNT` | Base64-encoded service account JSON |

---

## Google Play Setup

### Step 1: Create App Listing

1. **Go to Google Play Console** → **Create app**
2. **App details:**
   - Name: `EbookReader`
   - Default language: English
   - App or game: **App**
   - Category: **Books & Reference**
   - Type: Free app (or Paid if preferred)
3. Accept policies and create

### Step 2: Complete App Listing

Navigate to **App listing** and fill in:

#### Main Store Listing

**Headline** (50 chars):
```
Fast offline ebook & RSS reader
```

**Short description** (80 chars):
```
Read EPUB, PDF, TXT, FB2, CBZ. Offline-first. No accounts.
```

**Full description** (4000 chars):
```
EbookReader is a clean, fast Android ebook reader with full offline support.

Features:
• Support for EPUB, PDF, TXT, FB2, CBZ formats
• Fully offline — no tracking, no telemetry
• Smart library with sort, filter, and 3D bookshelf view
• Reading progress sync (cloud, WebDAV)
• Text-to-speech (read aloud)
• Highlights, notes, bookmarks
• Dictionary lookup on selected text
• E-ink display profile for e-readers
• RSS feeds with offline articles
• OPDS catalog support
• Import custom fonts

No accounts needed. Your reading stays private on your device.
```

#### Screenshots

Upload **2-8 screenshots** for:
- Phone 7-inch
- Phone 10-inch

**Suggested screenshots:**
1. Library view with books
2. Reader with reading settings open
3. Highlights & bookmarks
4. RSS feeds tab
5. Reading stats

**Tools to create:** 
- [Screenshot maker](https://www.figma.com/)
- or use device screenshots + text overlay

#### Contact Details

Add in **About** section:

| Field | Value |
|-------|-------|
| Email | `corentin@bcit.fr` |
| Website | GitHub repo link |
| Privacy Policy | `https://github.com/BCIT-Formation/ebooks/blob/main/SECURITY.md` |

### Step 3: Content Rating

1. Go to **Content ratings**
2. Complete **IARC** questionnaire
3. Submit for rating
4. Apply rating to your app

**Expected:** Everyone (3+)

### Step 4: Target Audience

Go to **Target audience** and verify:
- Children: Not intended
- Teens: Yes
- Adults: Yes

### Step 5: Pricing & Distribution

Go to **Pricing & distribution**:
- **Price:** Free (or set a price if preferred)
- **Countries:** Select all (or specific regions)
- **Devices:** Phones and tablets
- **Min Android:** API 26

---

## Release Automation

### How It Works

The release process is **fully automated** via GitHub Actions:

```
1. Create PR with changes
2. Merge to main (after CI passes)
3. GitHub Actions:
   ✓ Bumps version (semver)
   ✓ Creates git tag
   ✓ Builds signed release APK
   ✓ Uploads to Play Store (alpha)
   ✓ Creates GitHub Release
```

### Conventional Commits (Version Bumping)

Commit messages determine version bumps:

| Prefix | Bump | Example |
|--------|------|---------|
| `feat:` | Minor | `feat: add offline dictionary` |
| `feat!:` | Major | `feat!: change file format` |
| `fix:` | Patch | `fix: correct scroll position` |
| `perf:` | Patch | `perf: optimize LazyColumn` |
| `refactor:` | Patch | `refactor: simplify API` |
| `chore:` | None | `chore: update deps` |
| `docs:` | None | `docs: add guide` |
| `ci:` | None | `ci: update workflow` |

### Automated Release Example

**Scenario:** Add new feature to main branch

```bash
# 1. Create feature branch
git checkout -b feat/new-feature

# 2. Make changes & commit
git commit -m "feat: add word definitions lookup"

# 3. Push & create PR
git push origin feat/new-feature
# → Open PR on GitHub

# 4. Review & merge to main (once CI is green)
# GitHub merges the PR

# 5. Auto-release triggers:
# ✓ Detects "feat:" → bumps minor version (v1.0.0 → v1.1.0)
# ✓ Creates tag: v1.1.0
# ✓ Builds APK with signing key
# ✓ Uploads to Play Store alpha track
# ✓ Creates GitHub Release with APK attached
```

### Manual Release

If you need to release without waiting for a merged PR:

**Via GitHub CLI:**
```bash
gh workflow run auto-release.yml
```

**Via GitHub Web:**
1. Go to **Actions**
2. Select **auto-release.yml**
3. Click **Run workflow**
4. Choose branch: **main**
5. Click **Run**

---

## Testing & Rollout

### Internal Testing Track

**Recommended before any public release:**

1. Go to **Google Play Console → Testing → Internal testing**
2. Click **Create release**
3. Upload APK
4. Add **test devices** (your device ID)
5. Send testers the [internal testing link](https://play.google.com/apps/internaltest/com.ebooks.reader)
6. Collect feedback for 1-2 weeks

**To find your test device ID:**
```bash
adb shell settings get secure android_id
```

### Beta Testing Track

**After internal testing passes:**

1. Go to **Testing → Closed testing** (Beta)
2. Create release with same APK
3. Add more testers (or "open beta" for wider testing)
4. Monitor for 1-2 weeks

### Production Rollout

**When beta is stable:**

1. Go to **Release → Production**
2. Create release with APK
3. Add **release notes:**
   ```markdown
   ## v1.1.0 - Dictionary Lookup & E-Ink Profile

   ### New Features
   - Dictionary lookup on selected text
   - E-ink display profile for e-readers
   - Enhanced file sharing (ebook + annotations)

   ### Improvements
   - Better performance on LazyColumn scrolling

   ### Fixes
   - Fixed crash when switching themes rapidly

   See full changelog: [GitHub Releases](...)
   ```
4. Choose rollout:
   - **100%** (immediate) or
   - **Staged** (10% → 50% → 100% over days)

5. Review & publish

---

## Monitoring & Updates

### Track Analytics

**Google Play Console → Statistics:**

1. **Overview**
   - Total installs
   - Active users
   - Crash rate
   - Average rating

2. **Vitals**
   - ANRs (Application Not Responding)
   - Crashes
   - Slow rendering
   - Frozen frames

3. **Reviews**
   - Read user feedback
   - Respond to reviews
   - Fix reported issues

### Regular Updates

**Release cadence:**
- Critical bugs: ASAP
- Features: Monthly (aligned with GitHub PRs)
- Minor polish: Quarterly

**Update communication:**
- Post in releases on GitHub
- Link release notes in Play Store
- Mention in PRs

---

## Troubleshooting

### Build Failures

**"Build failed: Could not find Gradle"**
- Ensure GitHub Actions has `setup-java` with `cache: gradle`

**"Signing failed: Invalid keystore"**
- Verify `SIGNING_KEYSTORE_BASE64` is properly base64-encoded
- Check passwords match
- Ensure no newlines in base64 string

**"APK build failed: Unsupported class-file format"**
- Ensure JDK 17+ is used
- Check `build.gradle.kts` has `sourceCompatibility = JavaVersion.VERSION_17`

### Upload Failures

**"Upload failed: version code already exists"**
- Each release must have a unique `versionCode`
- Increment happens automatically via semver
- Check no two releases have same code

**"Invalid APK: debuggable=true"**
- Ensure `debuggable = false` in `build.gradle.kts`
- Release builds should never be debuggable

**"Permission denied: Service Account lacks permissions"**
- Go to Google Play Console
- Verify Service Account has **Admin** role
- Re-generate JSON key if >90 days old

### Play Store Issues

**"App not appearing in Play Store"**
- Wait 2-4 hours after publishing
- Check app is approved (not pending review)
- Verify targeting settings (regions, devices)

**"Low rating after release"**
- Read reviews for common issues
- Prioritize crash/performance fixes
- Push quick hotfix if critical

**"High crash rate in Vitals"**
- Download crash logs from Play Console
- Reproduce and fix in next release
- Consider immediate hotfix if >2% crash rate

---

## References

- [Google Play Console Docs](https://support.google.com/googleplay/android-developer)
- [App signing guide](https://developer.android.com/studio/publish/app-signing)
- [Release management](https://developer.android.com/studio/publish/versioning)
- [GitHub Actions workflows](https://github.com/BCIT-Formation/ebooks/tree/main/.github/workflows)

---

**Last updated:** 2026-07-17
**Version:** 1.0
