/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.morphe.extension.instagram.patches.dmGif;

import java.lang.reflect.Constructor;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.patches.comment.copyGifKeywordButton.GifKeywordResolver;

/**
 * Adds "Copy GIF name" and "Copy GIPHY link" items to the DM message long-press menu for GIF
 * messages, and handles their clicks.
 *
 * Build side: X.Ni5.H4X (the menu builder) calls {@link #maybeAdd} with the action list and
 * the message's C2JY (X.2JY). For a GIF message C2JY.A00 (GifUrlImpl) is non-null and
 * C2JY.A0G holds the giphy id (the gif URLs are IG-proxied cdn.fbsbx.com links that do NOT
 * contain it). We build real LongPressActionData items and stash a sentinel + giphy id in each
 * item's A0A String slot (A0A is not displayed by the popup renderer — which shows A07/A09 —
 * and IG only reads it when dispatching, which we pre-empt).
 *
 * Click side: our items render in the context-menu popup zone, whose taps route through
 * X.OIt.ElS (X.C61646OIt, case 1) → InterfaceC65219PjI.DgO. We intercept at the top of ElS:
 * {@link #handleItemClick} returns true (so ElS returns early) for our marked items and either
 * resolves+copies the giphy name or copies the GIPHY link.
 */
@SuppressWarnings("unused")
public final class KasusobaDmGif {

    private static final String LPAD = "com.instagram.direct.messagethread.interaction.longpressaction.LongPressActionData";
    // Stable IG drawable ids (survive Morphe's recompile): gif outline + copy icons.
    private static final int ICON_NAME = 0x7f08239f; // instagram_gif_outline_24
    private static final int ICON_LINK = 0x7f0824cc; // instagram_link_pano_outline_24
    // Sentinel prefixes tagging our items (each carries the giphy id in the item's A0A slot).
    private static final String NAME_MARK = "KDMN:";
    private static final String LINK_MARK = "KDML:";

    private static Object rf(Object o, String name) {
        try {
            return o.getClass().getField(name).get(o);
        } catch (Throwable t) {
            return null;
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

            Object nameItem = buildItem(ctor, ICON_NAME, GifKeywordResolver.nameLabel(), NAME_MARK + giphyId);
            Object linkItem = buildItem(ctor, ICON_LINK, GifKeywordResolver.linkLabel(), LINK_MARK + giphyId);
            if (nameItem != null) list.add(nameItem);
            if (linkItem != null) list.add(linkItem);
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

    /**
     * Build one LongPressActionData. ctor -> fields: span, placement(A01), action(A04),
     * action2(A02), action3(A03), icon(A05), num2(A06), label(A07), str2(A0A), str3(A09),
     * str4(A08). We stash the sentinel+id in A0A (arg 8, not shown) and leave the subtitle
     * A09 (arg 9) empty; the action enum is reused (COPY_TEXT) but never dispatched.
     */
    private static Object buildItem(Constructor<?> ctor, int icon, String label, String payload) {
        try {
            Class<?>[] pt = ctor.getParameterTypes();
            Object placement = pt[1].getField("A09").get(null);         // EnumC46636ITk.A09 (normal)
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object action = Enum.valueOf((Class) pt[2], "COPY_TEXT");
            return ctor.newInstance(
                    null, placement, action, null, null,
                    Integer.valueOf(icon), null, label, payload, null, null);
        } catch (Throwable t) {
            PikoUtils.logger(t);
            return null;
        }
    }

    /**
     * Called from X.OIt.ElS (top) with this.A00. Returns true (so ElS returns early) only for
     * our marked items; everything else falls through to IG's normal handling.
     */
    public static boolean handleItemClick(Object item) {
        try {
            if (item == null || !item.getClass().getName().endsWith(".LongPressActionData")) return false;
            Object v = rf(item, "A0A");
            if (!(v instanceof String)) return false;
            String s = (String) v;
            if (s.startsWith(NAME_MARK)) {
                GifKeywordResolver.resolveAndCopy(s.substring(NAME_MARK.length()), null);
                return true;
            }
            if (s.startsWith(LINK_MARK)) {
                GifKeywordResolver.copyGiphyLink(s.substring(LINK_MARK.length()));
                return true;
            }
            return false;
        } catch (Throwable t) {
            PikoUtils.logger(t);
            return false;
        }
    }

    private KasusobaDmGif() {}
}
