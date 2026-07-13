/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.dmGifCopyKeyword

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXT = "Lapp/morphe/extension/instagram/patches/dmGif/KasusobaDmGif;"

// The DM message-actions builder class (X.Ni5). Its H4X builds the long-press menu.
internal object DirectMessageActionsFingerprint : Fingerprint(
    strings = listOf("DirectMessageActionsInteractor.handleMessageAction"),
)

// The context-menu (popup zone) item action callback X.OIt.ElS (X.C61646OIt, case 1): it
// reads its item (this.A00, a LongPressActionData) and dispatches the action via
// InterfaceC65219PjI.DgO. Our item renders in this popup zone (icon + title + A09 subtitle,
// via X.11a ~L686 `new C61646OIt(1, item, ..)`), NOT the bottom text-button list (which uses
// X.MQt). Disambiguated from the sibling X.OJF.ElS (also ()V + "context_menu") by requiring a
// reference to the real LongPressActionData type.
internal object ContextMenuActionFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("context_menu"),
    custom = { method, _ ->
        method.implementation?.instructions?.any {
            it.getReference<TypeReference>()?.type?.endsWith("/LongPressActionData;") == true
        } == true
    },
)

// The bottom action-list item click listener X.MQt.onClick(View) (case 13): reads the tapped
// LongPressActionData (this.A00) and dispatches via InterfaceC65219PjI.DgO. Items placed in
// the bottom list (with Reply/Favorite) route here rather than through OIt.ElS. Uniquely
// identified by returning V, one View param, and the "bottom_bar" string. We hook both this
// and OIt.ElS so the item's click is caught wherever it renders.
internal object BottomBarItemClickFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    strings = listOf("bottom_bar"),
)

/**
 * Adds a "Copy GIF name" item to the DM message long-press menu for GIF messages.
 *
 * Static RE (2026-07-13, decompiled 435) of the real click path:
 *  - Builder: X.Ni5.H4X (PointF, PjQ, C2JY, J, Z, Z)V builds the action list. The list is
 *    v5 (move-result of the first X.00F.A0E()->ArrayList), and the full message X.6fK is v9
 *    (move-result of X.790.A00 right after the "DirectThreadFragment.showMessageActionDialog"
 *    const-string) — v9 is resolved before the list and survives to the list-create point.
 *    We call maybeAdd(list, message) there.
 *  - Click: each rendered item is wired (X.11a ~L750) to `new X.MQt(13, item, state)`; on tap
 *    X.MQt.onClick reads this.A00 (the LongPressActionData) and routes its action enum through
 *    InterfaceC65219PjI.DgO — a NATIVE dispatch that never calls back into our code (reusing
 *    COPY_TEXT just makes IG copy the message's empty text). So we intercept at the TOP of
 *    MQt.onClick: handleItemClick(this.A00) returns true (and onClick returns) for our marked
 *    item. (An earlier attempt hooked X.Ni5.DkC — that method is NOT on the click path at all.)
 */
@Suppress("unused")
val dmGifCopyKeywordPatch =
    bytecodePatch(
        name = "Copy GIF name in DM",
        description = "Adds a \"Copy GIF name\" action to the long-press menu of a GIF sent in a DM.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            val ni5 = DirectMessageActionsFingerprint.classDef

            // H4X = the message long-press menu builder: (PointF, PjQ, C2JY, J, Z, Z)V. NOTE:
            // Ni5 also has H4L (PointF, View, Long, String, String, List, int)V which also
            // starts with PointF + returns V — disambiguate on the C2JY (LX/2JY;) 3rd param.
            val h4x = ni5.methods.firstOrNull { m ->
                m.returnType == "V" &&
                    m.parameters.size == 6 &&
                    m.parameters[0].type == "Landroid/graphics/PointF;" &&
                    m.parameters[2].type == "LX/2JY;"
            } ?: error("DMKW: H4X (PointF,PjQ,C2JY,J,Z,Z) not found")

            // --- 1. H4X: add our item right AFTER IG's Favorite (save-sticker) item, so it
            // lands in the same zone (bottom action list) instead of the separate top card that
            // the first item falls into. The menu list is v5 (move-result of X.00F.A0E), and
            // c2jy is v0 (moved from the param at entry, stable across the item-adding section —
            // the adds read `v0, LX/2JY;.*`). The Save/Unsave (Favorite/Unfavorite toggle) items
            // share one construction+add: we anchor on the save-sticker icon id (0x7f082728) and
            // inject after the next list `.add`, which runs for both favorite states.
            // (The message X.6fK via X.790.A00 is unreliable — it can return null on some paths.)
            h4x.apply {
                val arrayListInvoke = instructions.firstOrNull {
                    it.opcode == Opcode.INVOKE_STATIC &&
                        it.getReference<MethodReference>()?.returnType == "Ljava/util/ArrayList;"
                } ?: error("DMKW: ArrayList invoke not found")
                val listRegister = instructions.firstOrNull {
                    it.location.index > arrayListInvoke.location.index &&
                        it.opcode == Opcode.MOVE_RESULT_OBJECT
                }?.let { (it as OneRegisterInstruction).registerA }
                    ?: error("DMKW: ArrayList move-result not found")

                val favIconConst = instructions.firstOrNull {
                    (it as? WideLiteralInstruction)?.wideLiteral == 0x7f082728L
                } ?: error("DMKW: save-sticker icon const not found")
                val favAddIndex = instructions.firstOrNull {
                    it.location.index > favIconConst.location.index &&
                        it.opcode.name.startsWith("invoke") &&
                        it.getReference<MethodReference>()?.name == "add"
                }?.location?.index ?: error("DMKW: favorite add not found")

                check(listRegister < 16) { "DMKW: list v$listRegister register too high" }
                addInstruction(
                    favAddIndex + 1,
                    "invoke-static { v$listRegister, v0 }, $EXT->maybeAdd(Ljava/util/List;Ljava/lang/Object;)V",
                )
            }

            // --- 2. OIt.ElS: intercept the popup item tap before IG's native DgO dispatch ---
            ContextMenuActionFingerprint.method.apply {
                val oitType = ContextMenuActionFingerprint.classDef.type
                // ()V instance method: this = registerCount - 1 (ins = 1, only `this`).
                val thisRegister = implementation!!.registerCount - 1
                val free = findFreeRegister(0)
                addInstructionsWithLabels(
                    0,
                    """
                    iget-object v$free, v$thisRegister, $oitType->A00:Ljava/lang/Object;
                    invoke-static { v$free }, $EXT->handleItemClick(Ljava/lang/Object;)Z
                    move-result v$free
                    if-eqz v$free, :kdm_continue
                    return-void
                    """.trimIndent(),
                    ExternalLabel("kdm_continue", getInstruction(0)),
                )
            }

            // --- 3. MQt.onClick: same intercept for items rendered in the bottom action list ---
            BottomBarItemClickFingerprint.method.apply {
                val mqtType = BottomBarItemClickFingerprint.classDef.type
                // this = highest param register (registerCount - insCount; insCount includes this).
                val thisRegister = implementation!!.registerCount - parameterTypes.size - 1
                val free = findFreeRegister(0)
                addInstructionsWithLabels(
                    0,
                    """
                    move-object/from16 v$free, v$thisRegister
                    iget-object v$free, v$free, $mqtType->A00:Ljava/lang/Object;
                    invoke-static { v$free }, $EXT->handleItemClick(Ljava/lang/Object;)Z
                    move-result v$free
                    if-eqz v$free, :kdm_continue_mqt
                    return-void
                    """.trimIndent(),
                    ExternalLabel("kdm_continue_mqt", getInstruction(0)),
                )
            }
        }
    }
