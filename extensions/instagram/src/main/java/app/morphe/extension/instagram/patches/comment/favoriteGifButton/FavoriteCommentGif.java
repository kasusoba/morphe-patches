/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.morphe.extension.instagram.patches.comment.favoriteGifButton;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import app.morphe.extension.crimera.PikoUtils;

/**
 * Favorites a GIF that was posted as a comment, by reconstructing Instagram's own GIF
 * model and firing its IGSaveGifMutation — the same mutation the DM/picker "Favorite"
 * uses, which has no surface check.
 *
 * Real IG model classes (stable names) are built directly via reflection; the obfuscated
 * pieces (the LK1 wrapper class, the AbstractC43517H7l.A0H save method, and the
 * C87023bp.A01 scheduler factory) are filled in at patch time via the placeholders below
 * (see FavoriteGifCommentPatch — changeFirstString).
 */
@SuppressWarnings("unused")
public final class FavoriteCommentGif {

    // Obfuscated descriptors, injected at patch time. Non-final so javac doesn't inline.
    private static String lk1Class() { return "PLACEHOLDER_LK1_CLASS"; }          // e.g. X.LK1
    private static String saveClass() { return "PLACEHOLDER_SAVE_CLASS"; }        // e.g. X.AbstractC43517H7l
    private static String saveMethod() { return "PLACEHOLDER_SAVE_METHOD"; }      // e.g. A0H
    private static String schedClass() { return "PLACEHOLDER_SCHED_CLASS"; }      // e.g. X.C87023bp
    private static String schedMethod() { return "PLACEHOLDER_SCHED_METHOD"; }    // e.g. A01
    private static String sessionClass() { return "PLACEHOLDER_SESSION_CLASS"; }  // e.g. X.AnonymousClass240
    private static String sessionMethod() { return "PLACEHOLDER_SESSION_METHOD"; }// e.g. A0L (static ()UserSession)

    private static final String DAM = "com.instagram.model.direct.gifs.DirectAnimatedMedia";
    private static final String DAM_USER = "com.instagram.model.direct.gifs.DirectAnimatedMediaUser";
    private static final String GIF_URL = "com.instagram.model.mediasize.GifUrlImpl";

    public static void favorite(String giphyId, String url, String webp,
                                String creator, float width, float height) {
        try {
            ClassLoader cl = FavoriteCommentGif.class.getClassLoader();

            // Current logged-in session via the static global accessor.
            Object session = Class.forName(sessionClass(), false, cl)
                    .getMethod(sessionMethod()).invoke(null);

            // GifUrlImpl(url, webp, w, h)
            Class<?> gifUrlCls = Class.forName(GIF_URL, false, cl);
            Object gifUrl = gifUrlCls
                    .getConstructor(String.class, String.class, float.class, float.class)
                    .newInstance(url, webp, width, height);

            // DirectAnimatedMediaUser(username, isVerified)  (nullable if no creator)
            Class<?> userCls = Class.forName(DAM_USER, false, cl);
            Object user = (creator != null && creator.length() > 0)
                    ? userCls.getConstructor(String.class, boolean.class).newInstance(creator, true)
                    : null;

            // DirectAnimatedMedia(user, gifUrl, Boolean, Boolean, giphyId, null, false)
            Class<?> damCls = Class.forName(DAM, false, cl);
            Constructor<?> damCtor = damCls.getConstructor(
                    userCls, gifUrlCls, Boolean.class, Boolean.class, String.class, String.class, boolean.class);
            Object dam = damCtor.newInstance(user, gifUrl, Boolean.FALSE, Boolean.FALSE, giphyId, null, false);

            // LK1(dam, dam, false)
            Class<?> lk1Cls = Class.forName(lk1Class(), false, cl);
            Object lk1 = lk1Cls.getConstructor(damCls, damCls, boolean.class).newInstance(dam, dam, false);

            // scheduler = C87023bp.A01()
            Object scheduler = Class.forName(schedClass(), false, cl)
                    .getMethod(schedMethod()).invoke(null);

            // AbstractC43517H7l.A0H(callback=null, session, scheduler, lk1)
            Class<?> saveCls = Class.forName(saveClass(), false, cl);
            Method save = null;
            for (Method m : saveCls.getDeclaredMethods()) {
                if (m.getName().equals(saveMethod()) && m.getParameterTypes().length == 4) { save = m; break; }
            }
            if (save == null) throw new NoSuchMethodException("save method not found");
            save.setAccessible(true);
            save.invoke(null, null, session, scheduler, lk1);

            PikoUtils.toast("GIF favorited");
        } catch (Throwable t) {
            PikoUtils.logger(t);
            PikoUtils.toast("Couldn't favorite GIF");
        }
    }

    private FavoriteCommentGif() {}
}
