# Maintaining Kasusoba patches

A solo-maintainer runbook for keeping this bundle working over time. Written for future-me.

## What this repo is
A **fork of [piko](https://github.com/crimera/piko)**. The built bundle (`patches-*.mpp`)
therefore contains **all of piko's Instagram patches PLUS the kasusoba gif features**. In
Morphe Manager you add **one** source (this repo) and pick from everything in it — including
piko's own `Clone` and `Save media comment`. Do **not** also add piko's official bundle; it
would double-apply and conflict.

`upstream` git remote = crimera/piko. `origin` = kasusoba/morphe-patches.

## The build → patch → install loop (on a computer)
```sh
export JAVA_HOME="$PWD/tools/jdk/jdk-17.0.19+10/Contents/Home"     # JDK 17
export ANDROID_HOME="$HOME/Library/Android/sdk"                    # or build fails "SDK location not found"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

# 1) build the bundle (needs a classic GitHub PAT with read:packages in
#    ~/.gradle/gradle.properties as gpr.user / gpr.key, for the Morphe Maven registry)
./gradlew buildAndroid                          # -> patches/build/libs/patches-3.7.0.mpp

# 2) patch an Instagram APKM (grab the matching arch/dpi bundle from apkmirror.com)
java -jar tools/morphe-cli-1.9.1-all.jar patch \
  -p patches/build/libs/patches-3.7.0.mpp \
  --exclusive -O giphyApiKey=<YOUR_GIPHY_KEY> \
  -e "Copy GIF name" -e "Copy GIF name in DM" -e "Copy GIF name in picker" \
  -e "Copy GIPHY link" -e "Favorite GIF in comment picker" -e "Favorite GIF comment" \
  -e "Save media comment" -e "Clone" \
  -o out.apkm -t .patch-tmp --purge <instagram>.apkm

# 3) install (the output is a single merged universal apk; ignore the .apkm extension)
cp out.apkm out.apk && adb install -r out.apk
adb shell am force-stop com.instagram.android.piko
```
`--exclusive` applies only the `-e` patches (+ their dependencies). The `Clone` patch makes it
install as `com.instagram.android.piko` ("Piko Instagram") alongside real Instagram. The GIPHY
key is a **patch option, never committed** — supply it at patch time.

## Updating the clone (new Instagram version)
The clone does **not** auto-update. Real Instagram (`com.instagram.android`) updates normally
and is unaffected. To move the clone to a newer IG:
1. Download the new IG APKM (apkmirror, matching arch/dpi).
2. Re-run the patch loop above with the new APKM.
3. Install over the clone — same package id ⇒ updates in place, keeps login/data.

Only do this when you actually want a newer IG. Staying on a known-good version is fine.

## ⚠️ What breaks on IG updates (the real maintenance work)
Instagram's code is **re-obfuscated every build**, so patches split into:

**Robust — survives most updates** (keyed on stable strings / resource ids):
- Fingerprint strings: `giphy_gif_send_attempt`, `context_menu`, `bottom_bar`,
  `more_action_sheet`, `DirectMessageActionsInteractor.handleMessageAction`,
  `xfb_save_gif_for_eimu`, `IgSessionManager.SESSION_TOKEN_KEY`, etc.
- Hardcoded IG resource ids (icons/labels) — usually stable across recompiles.

**Fragile — breaks when IG re-obfuscates** (hardcoded obfuscated names):
- DM (`KasusobaDmGif`): `c2jy.A0G` (giphy id), `.A00`/`.A0A` fields.
- Picker (`KasusobaGifPickerCopyName`): the `.A01.A00.A01.A01` field chain to the gif.
- Patch fingerprints referencing obfuscated types: `LX/Icr;`, `LX/MIt;`, `LX/2JY;`,
  `LX/MQC;`, class selectors like "H4X vs H4L".

Expect the gif menus / comment buttons to be the first to break on a major IG jump.

### When a patch breaks — the RE loop
1. Decompile the new IG `base.apk` with jadx (`tools/jadx/`), and dexdump for smali
   (`build-tools/*/dexdump`). Keep decompiled sources somewhere gitignored (was `.gifhunt/`).
2. Find what changed. Anchor on **strings**, never jadx class names (jadx renames classes;
   e.g. jadx `C40822G1t` = real `LX/G1t;`). Re-find the obfuscated field/class names.
3. Update the fingerprints / reflection field names in the affected patch or extension.
4. Rebuild, re-patch, install, test the specific surface.

Gotchas learned (see the memory notes / commit history for detail):
- Our extension logs (`PikoUtils.logger`) do **not** reach logcat — diagnose on-device with
  `PikoUtils.toast(...)` or `Utils.setClipboard(...)`, not logcat.
- `getResourceId(STRING, <added-string>)` fails at patch time; comment-button labels must be
  an existing IG resource id (IG's real strings live in a Facebook runtime language pack).
- `dexlib2 Opcode.name` is the lowercase mnemonic ("invoke-virtual"), not the enum name.
- Build fails "SDK location not found" if `ANDROID_HOME` isn't exported.

## Publishing / installing on-device (no computer, via Morphe Manager)
Morphe Manager patches on the phone. To use this bundle there **without** listing it publicly:
1. Publish a **GitHub Release** with `patches-<version>.mpp` attached (and `patches-bundle.json`
   / `patches-list.json` committed, pointing at that release). Do **NOT** add the repo topic
   `morphe-patches` — the topic is what puts it in the public gallery; a plain Release is
   installable by you via a direct source-add without being listed.
2. On the phone: Morphe Manager → **Add source** →
   `https://morphe.software/add-source?github=kasusoba/morphe-patches`.
3. Pick patches, paste your GIPHY key, choose an IG APKM, patch + install — all on-device.

Manual release from a computer (most controllable for a solo maintainer):
```sh
./gradlew buildAndroid generatePatchesList      # -> patches-list.json
gh release create v<version> patches/build/libs/patches-3.7.0.mpp \
  --title "v<version>" --notes "…"              # attach the .mpp
```
The inherited semantic-release CI (`.github/workflows/release.yml` + `.releaserc`) can automate
this, but it requires **conventional-commit messages** (`feat:` / `fix:` …) and GitHub Actions
secrets (`GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `GPG_FINGERPRINT`, a read:packages token for the
gradle build). For a personal bundle, manual releases are simpler and give you full control.

## Security hygiene
- **GIPHY key**: patch option only, never in the repo (verify with a grep before each commit).
- **GitHub PAT** in `~/.gradle/gradle.properties` (`gpr.key`): rotate it if it was ever pasted
  anywhere. Treat any token shared in chat/logs as compromised.
- `.gitignore` excludes `tools/`, decompiled sources, `.patch-tmp/`, `*.apk`, `*.apkm`.

## Version pinning
Note which IG version each release was built and tested against (currently: **IG 435**,
`com.instagram.android_435.0.0.37.76`). When you bump IG support, record the new version and
which patches you had to re-RE.
