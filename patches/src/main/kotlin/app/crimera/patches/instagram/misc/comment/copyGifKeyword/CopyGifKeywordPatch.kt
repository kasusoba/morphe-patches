/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.comment.copyGifKeyword

import app.crimera.patches.instagram.entity.commentDataEntity.commentDataEntity
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.comment.addButtonAttribute
import app.crimera.patches.instagram.misc.comment.addButtonInterface
import app.crimera.patches.instagram.misc.comment.addCommentPatch
import app.crimera.patches.instagram.misc.comment.commentButtonClickCheckPatch
import app.crimera.patches.instagram.misc.comment.copyComment.CopyTextChatButtonToStringFingerprint
import app.crimera.patches.instagram.misc.comment.debugComment.debugCommentPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i

/**
 * Adds a comment button that copies the search keyword/tag of a GIF in a comment,
 * so the same GIF can be found again by searching. The keyword is already resolved
 * at runtime by piko's [CommentData.getGifTag] (see commentDataEntity), this patch
 * just surfaces it as a button + settings toggle.
 *
 * Mirrors [app.crimera.patches.instagram.misc.comment.copyComment.copyCommentPatch].
 *
 * TODO(v2): the button currently reuses Instagram's own "Copy" label + copy icon
 *  (via CopyTextChatButtonToStringFingerprint). Give it a distinct label/icon once
 *  a custom string resource id is wired into addButtonAttribute.
 */
@Suppress("unused")
val copyGifKeywordPatch =
    bytecodePatch(
        name = "Copy GIF keyword",
        description = "Adds a button to copy the search keyword/name of a GIF in a comment.",
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

        val giphyApiKey by stringOption(
            key = "giphyApiKey",
            default = null,
            title = "GIPHY API key",
            description = "Your own GIPHY REST API key (free from developers.giphy.com -> Create App -> API). " +
                "Required for the button to resolve a GIF's name/keyword.",
            required = false,
        )

        execute {

            var stringLateral: Long
            CopyTextChatButtonToStringFingerprint.classDef.methods.first { it.name == "<init>" }.apply {
                stringLateral = (instructions.last { it.opcode == Opcode.CONST } as Instruction31i).wideLiteral
            }

            var drawableLateral: Long = getResourceId(ResourceType.DRAWABLE, "instagram_gif_outline_24")

            addButtonAttribute(
                stringLateral,
                drawableLateral,
                InitCopyGifKeywordButtonExtensionFingerprint,
                InitCopyGifKeywordButtonInitExtensionFingerprint,
            )

            addButtonInterface(InitCopyGifKeywordButtonExtensionFingerprint)

            enableSettings("copyGifKeywordButton")

            // Inject the installer-supplied GIPHY key into the extension (placeholder in source).
            giphyApiKey?.let { GiphyApiKeyExtensionFingerprint.changeFirstString(it) }
        }
    }
