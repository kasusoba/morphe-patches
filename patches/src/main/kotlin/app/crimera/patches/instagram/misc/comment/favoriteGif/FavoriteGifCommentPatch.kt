/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.comment.favoriteGif

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
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i

/**
 * Adds a "Favorite" button to a GIF posted in a comment. Instagram only offers favoriting
 * GIFs in the DM/picker (via a DirectAnimatedMedia model); comment GIFs are a different
 * model. This rebuilds the DirectAnimatedMedia from the comment GIF's id/urls/creator and
 * fires the same IGSaveGifMutation (which has no surface check). The obfuscated pieces
 * (LK1 wrapper, the save method, the scheduler factory, the session accessor) are resolved
 * here and injected into the extension's placeholders.
 *
 * TODO(v2): custom label + real GIF width/height (currently reuses "Copy" label + 0 dims).
 */
@Suppress("unused")
val favoriteGifCommentPatch =
    bytecodePatch(
        name = "Favorite GIF comment",
        description = "Adds a button to favorite a GIF posted in a comment (Instagram only allows this in DMs/picker).",
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
            // --- 1. Add the comment button, reusing the GIF picker's own "Favorite"
            // icon + label resources (IG resource ids are stable through Morphe's
            // recompile). These are the exact ids the picker's favorite menu item uses:
            //   string  2131963665 = "Favorite"
            //   drawable 2131240744 = the favorite star icon
            val stringLateral = 2131963665L
            val drawableLateral = 2131240744L

            addButtonAttribute(
                stringLateral,
                drawableLateral,
                InitFavoriteGifButtonExtensionFingerprint,
                InitFavoriteGifButtonInitExtensionFingerprint,
            )
            addButtonInterface(InitFavoriteGifButtonExtensionFingerprint)
            enableSettings("favoriteGifButton")

            // --- 2. Resolve the obfuscated descriptors the extension needs ---
            val saveMethod = SaveGifMutationFingerprint.method
            val saveClassName = classNameToExtension(saveMethod.definingClass)
            val saveMethodName = saveMethod.name
            // A0H signature: (callback, UserSession, scheduler, LK1) -> param index 3 is LK1.
            val lk1ClassName = classNameToExtension(saveMethod.parameterTypes[3].toString())

            val schedClassDef = SchedulerFactoryClassFingerprint.classDef
            val schedClassName = classNameToExtension(schedClassDef.type)
            val schedMethodName = schedClassDef.methods.first {
                (it.accessFlags and AccessFlags.STATIC.value) != 0 &&
                    it.parameters.isEmpty() &&
                    it.returnType.startsWith("L")
            }.name

            val sessionClassDef = SessionAccessorClassFingerprint.classDef
            val sessionClassName = classNameToExtension(sessionClassDef.type)
            val sessionMethodName = sessionClassDef.methods.first {
                (it.accessFlags and AccessFlags.STATIC.value) != 0 &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == "Lcom/instagram/common/session/UserSession;"
            }.name

            // --- 3. Inject them into the extension placeholders ---
            PhLk1ClassFingerprint.changeFirstString(lk1ClassName)
            PhSaveClassFingerprint.changeFirstString(saveClassName)
            PhSaveMethodFingerprint.changeFirstString(saveMethodName)
            PhSchedClassFingerprint.changeFirstString(schedClassName)
            PhSchedMethodFingerprint.changeFirstString(schedMethodName)
            PhSessionClassFingerprint.changeFirstString(sessionClassName)
            PhSessionMethodFingerprint.changeFirstString(sessionMethodName)
        }
    }
