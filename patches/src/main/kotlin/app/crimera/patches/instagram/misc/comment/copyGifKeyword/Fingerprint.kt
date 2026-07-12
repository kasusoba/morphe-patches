/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.comment.copyGifKeyword

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint

internal const val COPY_GIF_KEYWORD_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/copyGifKeywordButton"
internal const val COPY_GIF_KEYWORD_BUTTON_EXTENSION_CLASS = "${COPY_GIF_KEYWORD_EXTENSION_CLASS}/CopyGifKeywordButton;"
internal const val INIT_COPY_GIF_KEYWORD_BUTTON_EXTENSION_CLASS = "${COPY_GIF_KEYWORD_EXTENSION_CLASS}/InitCopyGifKeywordButton;"
internal const val GIF_KEYWORD_RESOLVER_CLASS = "${COPY_GIF_KEYWORD_EXTENSION_CLASS}/GifKeywordResolver;"

// Locates the placeholder GIPHY key const in GifKeywordResolver.apiKey() so the patch
// can inject the user-supplied key at patch time via changeFirstString.
internal object GiphyApiKeyExtensionFingerprint : Fingerprint(
    name = "apiKey",
    definingClass = GIF_KEYWORD_RESOLVER_CLASS,
    strings = listOf("PASTE_GIPHY_API_KEY_HERE"),
)

// Marker class that carries the singleton A00 button instance.
internal object InitCopyGifKeywordButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = COPY_GIF_KEYWORD_BUTTON_EXTENSION_CLASS,
)

// Init class whose super class / interface get injected dynamically at patch time.
internal object InitCopyGifKeywordButtonInitExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = INIT_COPY_GIF_KEYWORD_BUTTON_EXTENSION_CLASS,
)
