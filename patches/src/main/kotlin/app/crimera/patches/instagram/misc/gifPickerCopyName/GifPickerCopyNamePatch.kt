/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.gifPickerCopyName

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXT =
    "Lapp/morphe/extension/instagram/patches/gifPicker/KasusobaGifPickerCopyName;"

// The GIF-tile long-press handler class X.G1t, located via a stable analytics string.
internal object GifTileCopyNameFingerprint : Fingerprint(
    strings = listOf("giphy_gif_send_attempt"),
)

/**
 * Adds a "Copy GIF name" item to the GIF-tile long-press menu in the GIF picker, next to the
 * "Favorite" item. In X.G1t.FFx the menu is built as
 * `AbstractC74534Te5.A00(view, .., Collections.singletonList(favoriteItem))`. We insert a call
 * to our extension between the `singletonList` invoke and its move-result, so the move-result
 * captures `KasusobaGifPickerCopyName.menu(favoriteItem)` (= [favorite, copy-name]) instead.
 *
 * The picker is shared by the DM and comment/reel surfaces, so this shows on both (harmless —
 * copying a GIF's name is useful anywhere). Pairs with "Favorite GIF in comment picker", which
 * makes the long-press menu appear in the comment/reel picker too.
 */
@Suppress("unused")
val gifPickerCopyNamePatch =
    bytecodePatch(
        name = "Copy GIF name in picker",
        description = "Adds a \"Copy GIF name\" action to the long-press menu of a GIF tile in the GIF picker.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            // FFx = the long-press handler: the (View)V method.
            val ffx = GifTileCopyNameFingerprint.classDef.methods.first {
                it.returnType == "V" &&
                    it.parameters.size == 1 &&
                    it.parameters[0].type == "Landroid/view/View;"
            }

            ffx.apply {
                val singletonList = instructions.firstOrNull {
                    it.opcode == Opcode.INVOKE_STATIC &&
                        it.getReference<MethodReference>()?.let { m ->
                            m.definingClass == "Ljava/util/Collections;" && m.name == "singletonList"
                        } == true
                } ?: error("GPCN: singletonList not found in FFx")

                // The single arg of singletonList is the favorite menu item.
                val favRegister = (singletonList as Instruction35c).registerC

                // Insert BEFORE the existing move-result so it captures our list instead of
                // singletonList's (whose result we intentionally discard — menu() re-adds the item).
                addInstruction(
                    singletonList.location.index + 1,
                    "invoke-static { v$favRegister }, $EXT->menu(Ljava/lang/Object;)Ljava/util/List;",
                )
            }
        }
    }
