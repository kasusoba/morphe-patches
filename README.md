<div align="center">

# Kasusoba patches

Personal [Morphe](https://morphe.software) patches for **Instagram**, based on
[crimera/piko](https://github.com/crimera/piko).

</div>

## Patches

### Copy GIF name
Adds a **Copy GIF name** action that copies a GIF's **name** to your clipboard, so
you (or anyone) can find the same GIF in the GIF picker. Available on GIFs posted in
**comments** (a comment button) and on GIFs sent in **DMs** (a long-press menu item).

Instagram doesn't store the GIF's title (only its GIPHY id), so it resolves the real
title from the **GIPHY API** at tap time, cleans it up (strips the trailing
"GIF"/"Sticker" so it's directly searchable), and copies it. Fallbacks if a GIF has no
title: de-slugged slug → creator → `giphy.com/gifs/<id>`.

**Requires your own GIPHY API key.** It is never stored in this repo — you supply it
at patch time via the `giphyApiKey` patch option (Morphe Manager shows it as a field;
Morphe CLI: `-O giphyApiKey=<yourkey>`). Get a free key at
[developers.giphy.com](https://developers.giphy.com) → *Create an App* → **API**
(not SDK).

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
  <instagram>.apkm
```

Or add it as a source in Morphe Manager and supply the key there.

## Credits & license

Built on [piko](https://github.com/crimera/piko) by crimera and the Morphe project.
Licensed under **GPLv3** (see `LICENSE`). Not affiliated with or endorsed by
Instagram, GIPHY, or Morphe.
