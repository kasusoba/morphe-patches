# Instagram GIF patch for Morphe — Plan

Goal: two features on the **GIF picker / GIFs in Instagram comments**, packaged as your
own Morphe patch bundle, developed and tested locally.

- **Feature A — Favorite a GIF in the comment/reel GIF picker.**
  The favorite action *already exists* in Instagram's **DM** GIF picker (search a GIF →
  long-press → "Favorite"). It is simply **not wired into the comment/reel picker**.
  So this is an "unlock an existing feature in another surface" patch, not a new API.
- **Feature B — Expose a GIF's keyword/name** so others can search for the same GIF.
  Piko *already extracts* this at the data layer (`CommentData.getGifTag()`), it just has
  no button. This is a ~few-hour clone of an existing patch. **Do this first.**

Everything here is **local + private**. Nothing is published, and the patch is **never**
listed on morphe-patches.software unless you deliberately publish a GitHub release AND add
the `morphe-patches` repo topic. Both are opt-in, done last (Stage 6), optional.

---

## Mental model (how Morphe/ReVanced patching works)

Morphe is a ReVanced fork. A "patch" has two halves:

1. **Kotlin patch** (`patches/…/XxxPatch.kt`) — runs at *patch time* on your computer.
   It finds code in the target APK by **fingerprint** (match a method by its strings /
   return type / params, since names are obfuscated and change each IG version), then
   edits the app's bytecode (smali).
2. **Extension** (`extensions/…/Xxx.java`) — Java you write that gets **compiled to DEX
   and injected** into the app, so it runs at *runtime* inside Instagram. The Kotlin patch
   adds an `invoke-static` call into your injected method.

**The obfuscation trick:** IG field names (`A0N`, etc.) change every version. Piko's
extension Java uses placeholder strings like `super.getField("fieldName")`, and a Kotlin
"entity" patch fingerprints the real field name at patch time and rewrites the placeholder
(`GetGifTagMediaExtension.changeFirstString(gifTagFieldName)`). This is why patches keep
working across versions — the names are resolved dynamically, not hardcoded.

Key reference files in `piko/` (already cloned to `morphe-research/piko`):
- `extensions/instagram/.../entity/CommentData.java` — already has `getGifTag()`,
  `getGifUrl()`, `getGifCreatorName()` (Feature B is basically pre-built here).
- `patches/.../misc/comment/copyComment/` — cleanest button template to copy.
- `patches/.../misc/comment/saveMediaComment/` — the GIF-download button (proves the
  comment-button infra works end to end).
- `patches/.../misc/comment/HandleCommentButton` (Kotlin) +
  `extensions/.../patches/comment/HandleCommentButton.java` — where comment buttons are
  registered and their clicks handled.
- `patches/.../entity/commentDataEntity/CommentDataEntity.kt` — the fingerprint→field-name
  resolution for comment GIF data.

---

## Stage 0 — Decide your base repo

**Fork/base off `crimera/piko`, not the blank template.** Instagram lives in piko, and you
get the entire comment-button + GIF-entity infrastructure for free. Two options:

- **A (recommended for now): work inside the local piko clone**, add your patch, build a
  local `.mpp`, test it. No GitHub repo, fully private.
- **B (only if/when you publish): fork piko on GitHub** or start from
  `MorpheApp/morphe-patches-template`. Not needed until Stage 6.

---

## Stage 1 — Dev environment (one-time)

Required: JDK 17, Android SDK (you have it), a GitHub token for Morphe's private Maven,
and `jadx` for reverse-engineering.

1. **JDK 17 + jadx** — installing via `brew install openjdk@17 jadx` (in progress).
   Then set in your shell profile (`~/.zshrc`):
   ```sh
   export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
   export PATH="$JAVA_HOME/bin:$PATH"
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   export PATH="$ANDROID_HOME/platform-tools:$PATH"
   ```

2. **GitHub PAT for Morphe's private registry (the big gotcha).**
   Building piko/template pulls `app.morphe:*` from
   `https://maven.pkg.github.com/MorpheApp/registry`, which requires auth. Create a
   **classic PAT** with scope **`read:packages`** at
   github.com/settings/tokens, then add to `~/.gradle/gradle.properties`:
   ```properties
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.key=ghp_YOUR_CLASSIC_PAT_WITH_read:packages
   ```
   (The `gho_…` token from `gh auth` usually lacks `read:packages`, so make a dedicated PAT.)

3. **Sanity build** (from `morphe-research/piko`):
   ```sh
   ./gradlew buildAndroid          # produces patches/build/libs/patches-*.mpp
   ```
   If dependency resolution fails → the PAT/registry step (2) is wrong.

---

## Stage 2 — Get the target APK + set up the RE workspace

1. Download the **exact Instagram APKM** version piko targets (see
   `piko/.../instagram/utils/Constants.kt` → currently `435.0.0.37.76`, ARM64) from
   ApkMirror. Do NOT unsplit/modify it (Morphe patches the APKM directly).
2. An `.apkm` is a zip of split APKs. For RE, unzip and open the `base.apk` in **jadx-gui**
   (`jadx-gui base.apk`) or decompile to smali for fingerprinting.
3. Piko ships mapping dumps at `piko/docs/mappings/435.0.0.37.68.json` etc. — useful cross
   reference for class/field names.

---

## Stage 3 — Feature B: "Copy GIF keyword" button (DO FIRST, the easy win)

This proves your whole toolchain and gives a working dev loop. It's a near-copy of
`copyComment`.

1. **New patch package** `patches/.../misc/comment/copyGifKeyword/`:
   - `CopyGifKeywordPatch.kt` — clone `CopyCommentPatch.kt`. It:
     `dependsOn(settingsPatch, addCommentPatch, commentButtonClickCheckPatch,
     commentDataEntity, resourceMappingPatch, decoderEntity, debugCommentPatch)`,
     resolves a string+drawable literal, calls `addButtonAttribute(...)` +
     `addButtonInterface(...)`, and `enableSettings("copyGifKeywordButton")`.
   - `Fingerprint.kt` — clone copyComment's; define the extension class descriptors
     (`InitCopyGifKeywordButton`, `CopyGifKeywordButton`) and a `toString`/`<init>`
     fingerprint for the button's string literal.
2. **New extension button** `extensions/.../patches/comment/copyGifKeywordButton/`:
   - `CopyGifKeywordButton.java` + `InitCopyGifKeywordButton.java` — copy the
     `copyTextButton` pair (singleton `A00` marker classes).
3. **Wire into `HandleCommentButton.java`:**
   - In `addButtons(...)`: `if (commentData.hasGifMedia() && Pref.commentCopyGifKeywordButton()) list.add(CopyGifKeywordButton.A00);`
   - In `checkOnCommentButtonClick(...)`: on `button.equals(CopyGifKeywordButton.A00)` →
     `String kw = commentData.getGifTag(); Utils.setClipboard(kw); PikoUtils.toast(...)`.
     (`getGifTag()` already returns the keyword; optionally also `getGifCreatorName()`.)
4. **Settings plumbing** (mirror `saveMediaCommentButton`):
   - `extensions/.../settings/SettingsStatus.java`: add `copyGifKeywordButton` bool+setter,
     add to `miscSection()` and the `FLAGS.put(...)` map.
   - `extensions/.../utils/Pref.java`: add `commentCopyGifKeywordButton()` reading the pref.
   - `extensions/.../settings/preference/ScreenBuilder.java`: add the toggle UI.
   - Add the string resources (`piko_copy_gif_keyword` etc.).
5. Build, patch a test APK, install, verify the button appears on comment GIFs and copies
   the keyword.

**Definition of done:** long-press / open the button on a GIF comment → its search keyword
lands on the clipboard.

---

## Stage 4 — Feature A: Favorite GIF in comment/reel picker (the RE project)

The action exists in the **DM GIF picker**. Strategy: find how DM wires long-press→favorite,
find why it's gated to DM, and enable/replicate it in the comment/reel picker.

Reverse-engineering steps (in jadx-gui on the decompiled APK):
1. **Locate the GIF picker.** Search strings for `giphy`, `gif`, `sticker`, `favorite`,
   `favourite`, `GIF_PICKER`, entrypoint/surface enums. Find the search-results grid used
   by both DM and comment composers.
2. **Find the long-press → Favorite path (DM).** Look for the context/long-press menu that
   adds "Favorite" and the call that persists a favorite (likely a GIPHY favorites store /
   an IG action with a `surface`/`entrypoint`/`source` parameter).
3. **Find the gate.** Determine *why* comment/reel picker lacks it — usually a `surface`
   enum, a boolean capability flag, or a config gate (`mobileconfig`/QE) that differs
   between DM and comment entrypoints.
4. **Pick the intervention:**
   - Easiest: **flip the gate** (override the boolean flag / force the surface capability),
     analogous to piko's `overrideMobileConfigBooleanFlag(...)` / `HookFlags` pattern.
   - Or: **inject the long-press handler** into the comment/reel picker's item view,
     calling the same favorite action the DM path uses.
5. **Write it as a bytecodePatch** using fingerprints (`strings=[…]`, `returnType`,
   `parameters` with `"L"` for obfuscated types, `custom { method, classDef -> … }`),
   then `addInstruction(s)` / `insertLiteralOverride` / `returnEarly` as needed. Put any
   runtime logic in an extension method and `invoke-static` into it.

This stage genuinely needs the APK in hand; expect iteration. We'll do the jadx spelunking
together once the environment builds.

---

## Stage 5 — Local test loop

```sh
# 1. build your bundle
cd morphe-research/piko && ./gradlew buildAndroid
# 2. patch the APKM with your bundle via Morphe CLI (grab cli.jar from MorpheApp/morphe-cli releases)
java -jar cli.jar patch --patches patches/build/libs/patches-*.mpp instagram-435.apkm
# 3. install the patched output
adb install-multiple patched-*.apk    # or single apk if produced
```
Iterate: edit → `buildAndroid` → `patch` → `adb install` → test on device.

---

## Stage 6 — (Optional, later) Publish + get listed

Only if you decide to share it:
1. Push your fork to GitHub (from the `morphe-patches-template` layout or your piko fork).
2. Set bundle identity in `patches/build.gradle.kts` (`group`, `about { name/author/... }`).
3. Commit with `feat:`/`fix:` messages; the preconfigured `release.yml` + semantic-release
   builds `patches-<ver>.mpp`, attaches it to a **GitHub Release**, and writes
   `patches-bundle.json` / `patches-list.json`.
4. Users add it via `https://morphe.software/add-source?github=<you>/<repo>`.
5. To appear in the morphe-patches.software gallery: add the **`morphe-patches`** GitHub
   **topic** to the repo — the site's backend crawler aggregates topic-tagged repos'
   `patches-list.json` (with a `firstSeen` date). Until then it stays completely private.

---

## Immediate next actions
1. Finish env install (JDK17/jadx) + set env vars + create the `read:packages` PAT.
2. `./gradlew buildAndroid` in piko to confirm the toolchain works.
3. Start Stage 3 (Copy GIF keyword) — I can scaffold those files.
4. Grab the IG 435 APKM for Stage 2/4 RE.
