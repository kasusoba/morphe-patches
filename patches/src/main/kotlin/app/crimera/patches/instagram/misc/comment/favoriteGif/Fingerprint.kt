/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.crimera.patches.instagram.misc.comment.favoriteGif

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// ---- our extension button (the comment button UI) ----
internal const val FAVORITE_GIF_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/favoriteGifButton"
internal const val FAVORITE_GIF_BUTTON_EXTENSION_CLASS = "${FAVORITE_GIF_EXTENSION_CLASS}/FavoriteGifCommentButton;"
internal const val INIT_FAVORITE_GIF_BUTTON_EXTENSION_CLASS = "${FAVORITE_GIF_EXTENSION_CLASS}/InitFavoriteGifCommentButton;"

internal object InitFavoriteGifButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = FAVORITE_GIF_BUTTON_EXTENSION_CLASS,
)

internal object InitFavoriteGifButtonInitExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = INIT_FAVORITE_GIF_BUTTON_EXTENSION_CLASS,
)

// ---- app-side resolvers (stable strings) ----
// The save-gif GraphQL mutation A0H (its 4th param type is the LK1 wrapper class).
internal object SaveGifMutationFingerprint : Fingerprint(
    strings = listOf("xfb_save_gif_for_eimu"),
)

// The scheduler factory class C87023bp (its no-arg method returns a scheduler).
internal object SchedulerFactoryClassFingerprint : Fingerprint(
    strings = listOf("Schedulers.scheduleInline() called on main thread: task="),
)

// The global current-session accessor: the (only) static no-arg method returning
// UserSession. Matched by signature — a string anchor is ambiguous (that pref key is
// referenced by many classes).
internal object SessionAccessorClassFingerprint : Fingerprint(
    strings = listOf("IgSessionManager.SESSION_TOKEN_KEY"),
    // That pref key is referenced by many classes; keep only the one that also declares
    // the static no-arg UserSession accessor (the session-holder util).
    custom = { _, classDef ->
        classDef.methods.any {
            (it.accessFlags and AccessFlags.STATIC.value) != 0 &&
                it.parameterTypes.isEmpty() &&
                it.returnType == "Lcom/instagram/common/session/UserSession;"
        }
    },
)

// ---- extension placeholder targets (each returns a unique placeholder string) ----
internal object PhLk1ClassFingerprint : Fingerprint(strings = listOf("PLACEHOLDER_LK1_CLASS"))
internal object PhSaveClassFingerprint : Fingerprint(strings = listOf("PLACEHOLDER_SAVE_CLASS"))
internal object PhSaveMethodFingerprint : Fingerprint(strings = listOf("PLACEHOLDER_SAVE_METHOD"))
internal object PhSchedClassFingerprint : Fingerprint(strings = listOf("PLACEHOLDER_SCHED_CLASS"))
internal object PhSchedMethodFingerprint : Fingerprint(strings = listOf("PLACEHOLDER_SCHED_METHOD"))
internal object PhSessionClassFingerprint : Fingerprint(strings = listOf("PLACEHOLDER_SESSION_CLASS"))
internal object PhSessionMethodFingerprint : Fingerprint(strings = listOf("PLACEHOLDER_SESSION_METHOD"))
