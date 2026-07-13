/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.comment.copyGiphyLink

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint

internal const val COPY_GIPHY_LINK_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/copyGiphyLinkButton"
internal const val COPY_GIPHY_LINK_BUTTON_EXTENSION_CLASS = "${COPY_GIPHY_LINK_EXTENSION_CLASS}/CopyGiphyLinkButton;"
internal const val INIT_COPY_GIPHY_LINK_BUTTON_EXTENSION_CLASS = "${COPY_GIPHY_LINK_EXTENSION_CLASS}/InitCopyGiphyLinkButton;"

// Marker class that carries the singleton A00 button instance.
internal object InitCopyGiphyLinkButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = COPY_GIPHY_LINK_BUTTON_EXTENSION_CLASS,
)

// Init class whose super class / interface get injected dynamically at patch time.
internal object InitCopyGiphyLinkButtonInitExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = INIT_COPY_GIPHY_LINK_BUTTON_EXTENSION_CLASS,
)
