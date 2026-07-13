<div align="center">

# Kasusoba patches

Personal [Morphe](https://morphe.software) patches for **Instagram**, based on
[crimera/piko](https://github.com/crimera/piko).

</div>

All patches are Instagram-only and enabled by default.

## GIF name patches

Copy a GIF's **name** to your clipboard, so you (or anyone) can find the same GIF in the
GIF picker. Instagram doesn't store the GIF's title (only its GIPHY id), so the name is
resolved from the **GIPHY API** at tap time, cleaned up (strips the trailing
"GIF"/"Sticker" so it's directly searchable), and copied. Fallbacks if a GIF has no
title: de-slugged slug → creator → `giphy.com/gifs/<id>`. The menu label is localized.

> **Requires your own GIPHY API key** (shared by all three patches below). It is never
> stored in this repo — you supply it at patch time via the `giphyApiKey` patch option
> (Morphe Manager shows it as a field; Morphe CLI: `-O giphyApiKey=<yourkey>`). Get a free
> key at [developers.giphy.com](https://developers.giphy.com) → *Create an App* → **API**
> (not SDK).

- **`Copy GIF name`** — a button on a comment that contains a GIF.
- **`Copy GIF name in DM`** — an item in the long-press menu of a GIF sent in a DM
  (next to *Favorite*).
- **`Copy GIF name in picker`** — an item in the long-press menu of a GIF tile in the
  GIF picker (next to *Favorite*), in both the DM and comment/reel pickers.

## Copy GIPHY link patches

Copy a shareable GIPHY page link (`https://giphy.com/gifs/<id>`) for a GIF. No GIPHY key
needed. On DMs and the GIF picker the menu label is a localized "Copy GIPHY link"; the
comment button reuses Instagram's own "Copy" label (Instagram's real strings live in a
runtime language pack, so custom labels aren't available on the comment surface) and is
distinguished by a link icon.

- **`Copy GIPHY link`** — a button on a comment that contains a GIF.
- part of **`Copy GIF name in DM`** / **`Copy GIF name in picker`** — the DM and picker
  menus show *both* "Copy GIF name" and "Copy GIPHY link" items.

## Download GIF patches

Download a GIF to your device (reuses piko's downloader → your Gif folder). No GIPHY key
needed.

- part of **`Copy GIF name in DM`** / **`Copy GIF name in picker`** — the DM and picker
  menus also include a localized "Download GIF" item.
- for GIFs (and custom images) **posted in comments**, use piko's own
  **`Save media comment`** patch (`-e "Save media comment"`) — no extra code here.

## Favorite GIF patches

Favorite (save) GIFs on surfaces where Instagram doesn't normally let you. No GIPHY key
needed — these use Instagram's own save-GIF mutation, which has no surface restriction.

- **`Favorite GIF in comment picker`** — enables the long-press *Favorite* action on GIFs
  in the comment/reel GIF picker (Instagram only offers it in DMs).
- **`Favorite GIF comment`** — a button to favorite a GIF that's been posted in a comment.

## Building

```sh
# needs JDK 17 and a GitHub PAT with read:packages in ~/.gradle/gradle.properties
# (gpr.user / gpr.key) for the Morphe Maven registry.
./gradlew buildAndroid          # -> patches/build/libs/patches-*.mpp
```

## Usage (Morphe CLI)

```sh
java -jar morphe-cli.jar patch \
  -p patches/build/libs/patches-*.mpp \
  -O giphyApiKey=<your-giphy-api-key> \
  -e "Copy GIF name" \
  -e "Copy GIF name in DM" \
  -e "Copy GIF name in picker" \
  -e "Copy GIPHY link" \
  -e "Favorite GIF in comment picker" \
  -e "Favorite GIF comment" \
  -e "Save media comment" \
  <instagram>.apkm
```

Or add it as a source in Morphe Manager and supply the key there.

## Credits & license

Built on [piko](https://github.com/crimera/piko) by crimera and the Morphe project.
Licensed under **GPLv3** (see `LICENSE`). Not affiliated with or endorsed by
Instagram, GIPHY, or Morphe.
