/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.comment.copyGiphyLink

import app.crimera.patches.instagram.entity.commentDataEntity.commentDataEntity
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.comment.addButtonAttribute
import app.crimera.patches.instagram.misc.comment.addButtonInterface
import app.crimera.patches.instagram.misc.comment.addCommentPatch
import app.crimera.patches.instagram.misc.comment.commentButtonClickCheckPatch
import app.crimera.patches.instagram.misc.comment.debugComment.debugCommentPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch

/**
 * Adds a comment button that copies a shareable GIPHY page link (`giphy.com/gifs/<id>`) of a
 * GIF in a comment. No GIPHY API key needed — the id is already resolved by piko's
 * [CommentData.getGifTag]; the extension just formats and copies the link.
 *
 * The button uses a link icon (instagram_link_pano_outline_24). For the label we prefer an
 * Instagram "copy link"-style string (so it reads distinctly from the "Copy GIF name" button),
 * but Instagram has no reliably-named one and custom string resources don't resolve at patch
 * time — so we fall back to Instagram's own "Copy" string (the link icon still distinguishes it).
 *
 * This is a 3rd custom comment button; it needs the robust bundle-ctor lookup in
 * [addButtonAttribute] (piko's original `getInstruction(gotoIndex - 1)` NPE'd at the 3rd
 * injection on IG 435).
 */
@Suppress("unused")
val copyGiphyLinkPatch =
    bytecodePatch(
        name = "Copy GIPHY link",
        description = "Adds a button to copy a shareable GIPHY link of a GIF in a comment.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            addCommentPatch,
            commentButtonClickCheckPatch,
            commentDataEntity,
            resourceMappingPatch,
            decoderEntity,
            debugCommentPatch,
        )

        execute {
            // Instagram's own "Copy text" button-label string id (X.C44528HeK) — same label as
            // the copy-name button; the link icon is what distinguishes them. IG's real strings
            // live in a runtime FB language pack (custom/added strings can't be used here and
            // there is no "Copy link" label). A proper custom label needs a custom comment-button
            // renderer (TODO, deferred). The id is stable through Morphe's recompile.
            val stringLateral = 2131961895L

            val drawableLateral: Long = getResourceId(ResourceType.DRAWABLE, "instagram_link_pano_outline_24")

            addButtonAttribute(
                stringLateral,
                drawableLateral,
                InitCopyGiphyLinkButtonExtensionFingerprint,
                InitCopyGiphyLinkButtonInitExtensionFingerprint,
            )

            addButtonInterface(InitCopyGiphyLinkButtonExtensionFingerprint)

            enableSettings("copyGiphyLinkButton")
        }
    }
