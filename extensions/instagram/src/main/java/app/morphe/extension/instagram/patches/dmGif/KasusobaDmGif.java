/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.morphe.extension.instagram.patches.dmGif;

import java.lang.reflect.Constructor;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.patches.comment.copyGifKeywordButton.GifKeywordResolver;

/**
 * Adds a "Copy GIF name" item to the DM message long-press menu for GIF messages, and
 * handles its click.
 *
 * Build side: X.Ni5.H4X (the menu builder) calls {@link #maybeAdd} with the action list and
 * the message's C2JY (X.2JY). For a GIF message C2JY.A00 (GifUrlImpl) is non-null and
 * C2JY.A0G holds the giphy id (the gif URLs are IG-proxied cdn.fbsbx.com links that do NOT
 * contain it). We build a real LongPressActionData and stash {@link #MARK} + giphy id in the
 * item's A0A String slot (A0A is not displayed by the popup renderer — which shows A07/A09 —
 * and IG only reads it when dispatching, which we pre-empt).
 *
 * Click side: our item renders in the context-menu popup zone, whose taps route through
 * X.OIt.ElS (X.C61646OIt, case 1) → InterfaceC65219PjI.DgO. We intercept at the top of ElS:
 * {@link #handleItemClick} returns true (so ElS returns early) for our marked item and
 * resolves + copies the giphy keyword, mirroring the comment "Copy GIF keyword" button.
 */
@SuppressWarnings("unused")
public final class KasusobaDmGif {

    private static final String LPAD = "com.instagram.direct.messagethread.interaction.longpressaction.LongPressActionData";
    // instagram_gif_outline_24 (id is stable through Morphe's recompile).
    private static final int ICON = 0x7f08239f;
    // Sentinel prefix tagging our item; carries the giphy id, stored in the item's A0A slot.
    private static final String MARK = "KDMKW:";

    private static Object rf(Object o, String name) {
        try {
            return o.getClass().getField(name).get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Localized "Copy GIF name" label. IG has no matching string resource and custom string
     * resources don't resolve at patch time, so translate by the device language (falls back
     * to English). Add more languages as needed.
     */
    private static String label() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "ja": return "GIF名をコピー";
            case "es": return "Copiar nombre del GIF";
            case "pt": return "Copiar nome do GIF";
            case "fr": return "Copier le nom du GIF";
            case "de": return "GIF-Namen kopieren";
            case "it": return "Copia nome GIF";
            case "id": return "Salin nama GIF";
            case "ru": return "Копировать название GIF";
            case "ko": return "GIF 이름 복사";
            case "zh": return "复制 GIF 名称";
            case "ar": return "نسخ اسم GIF";
            case "hi": return "GIF नाम कॉपी करें";
            case "tr": return "GIF adını kopyala";
            default: return "Copy GIF name";
        }
    }

    /** Called from X.Ni5.H4X: maybeAdd(list, c2jy). */
    public static void maybeAdd(java.util.List list, Object c2jy) {
        try {
            if (c2jy == null) return;
            // C2JY.A00 (GifUrlImpl) is non-null only for gif/animated messages.
            if (rf(c2jy, "A00") == null) return;
            // C2JY.A0G is the giphy id (e.g. "H5C8CevNMbpBqNqFjl").
            Object idObj = rf(c2jy, "A0G");
            if (!(idObj instanceof String) || ((String) idObj).length() == 0) return;
            String giphyId = (String) idObj;

            Class<?> lpad = Class.forName(LPAD);
            Constructor<?> ctor = null;
            for (Constructor<?> c : lpad.getConstructors()) {
                if (c.getParameterCount() == 11) { ctor = c; break; }
            }
            if (ctor == null) return;
            Class<?>[] pt = ctor.getParameterTypes();
            Object placement = pt[1].getField("A09").get(null);         // EnumC46636ITk.A09 (normal)
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object action = Enum.valueOf((Class) pt[2], "COPY_TEXT");    // any non-null action; we intercept before dispatch
            // ctor -> fields: span, placement, action(A04), action2(A02), action3(A03),
            //                 icon(A05), num2(A06), label(A07), str2(A0A), str3(A09), str4(A08).
            // Stash the id in A0A (arg 8, not shown); leave the subtitle A09 (arg 9) empty.
            Object item = ctor.newInstance(
                    null, placement, action, null, null,
                    Integer.valueOf(ICON), null, label(), MARK + giphyId, null, null);
            list.add(item);
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

    /**
     * Called from X.OIt.ElS (top) with this.A00. Returns true (so ElS returns early) only for
     * our marked item; everything else falls through to IG's normal handling.
     */
    public static boolean handleItemClick(Object item) {
        try {
            if (item == null || !item.getClass().getName().endsWith(".LongPressActionData")) return false;
            Object v = rf(item, "A0A");
            if (!(v instanceof String)) return false;
            String s = (String) v;
            if (!s.startsWith(MARK)) return false;
            GifKeywordResolver.resolveAndCopy(s.substring(MARK.length()), null);
            return true;
        } catch (Throwable t) {
            PikoUtils.logger(t);
            return false;
        }
    }

    private KasusobaDmGif() {}
}
