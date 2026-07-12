/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.gifPickerFavorite

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The GIF-tile long-press handler class (obfuscated `X.G1t`) is located via a stable
 * analytics string it references (`giphy_gif_send_attempt`). Its `(View)V` method is the
 * long-press handler `FFx` (its sibling `(View)Z` is the single-tap = send).
 */
internal object GifTileLongPressClassFingerprint : Fingerprint(
    strings = listOf("giphy_gif_send_attempt"),
)

/**
 * In the GIF picker, long-pressing a GIF builds a one-item "Favorite" context menu — but
 * only inside `if (C01C.A1C(wkj.A0G))`. That capability boolean is derived from the
 * picker's entrypoint and is false for `COMMENT_COMPOSER`, so the comment/reel picker
 * never shows "Favorite" (DMs do). The underlying save mutation (IGSaveGifMutation) has
 * no surface check, so we simply force that one gate true — this does NOT enable the
 * separate favorites tab (which has its own `entrypoint == DIRECT_SAVED_STICKER` gate).
 */
@Suppress("unused")
val favoriteGifInCommentPatch =
    bytecodePatch(
        name = "Favorite GIF in comment picker",
        description = "Enables the long-press \"Favorite\" action on GIFs in the comment/reel GIF picker (normally only in DMs).",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            // FFx = the long-press handler: the (View)V method (its sibling returns Z).
            val longPressMethod =
                GifTileLongPressClassFingerprint.classDef.methods.first {
                    it.returnType == "V" &&
                        it.parameters.size == 1 &&
                        it.parameters[0].type == "Landroid/view/View;"
                }

            longPressMethod.apply {
                // The gate is the only invoke-static returning Z in this method
                // (`C01C.A1C(wkj.A0G)`), followed by move-result + if-eqz.
                val gateIndex =
                    instructions.first {
                        it.opcode == Opcode.INVOKE_STATIC &&
                            it.getReference<MethodReference>()?.returnType == "Z"
                    }.location.index

                val ifEqz =
                    instructions.first {
                        it.location.index > gateIndex && it.opcode == Opcode.IF_EQZ
                    }
                val ifEqzIndex = ifEqz.location.index
                val register = (ifEqz as OneRegisterInstruction).registerA

                // Force the gate register to 1 right before the if-eqz so the "Favorite"
                // menu is always built, regardless of surface.
                addInstruction(ifEqzIndex, "const/4 v$register, 0x1")
            }
        }
    }
