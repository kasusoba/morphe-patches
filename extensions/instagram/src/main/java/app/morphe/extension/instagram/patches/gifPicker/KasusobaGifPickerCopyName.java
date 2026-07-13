/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.morphe.extension.instagram.patches.gifPicker;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.patches.comment.copyGifKeywordButton.GifKeywordResolver;

/**
 * Adds a "Copy GIF name" item to the GIF-tile long-press menu in the GIF picker, next to the
 * "Favorite" item that X.G1t.FFx builds (a one-item {@code Collections.singletonList}). The
 * patch redirects that list through {@link #menu}, which returns [favorite, copy-name].
 *
 * Everything is resolved reflectively from the favorite item at runtime, so no obfuscated
 * names are hardcoded:
 *  - giphy id: favoriteItem.A01(ME2).A00(LEC).A01(LK1).A01(DirectAnimatedMedia).getId()
 *  - the menu-item interface (X.InterfaceC90767alz, single method AKB(Context)->X.C58446MxL)
 *    is the interface the favorite item implements; we build a second item as a {@link Proxy}.
 *  - the row model X.C58446MxL and its 9-arg ctor come from invoking the favorite item's own
 *    AKB, and the onClick interface (X.InterfaceC113025oro: void ElS()/boolean BhJ()) is that
 *    ctor's 3rd parameter — also proxied (ElS -> resolve+copy the name, BhJ -> true).
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class KasusobaGifPickerCopyName {

    private static final int ICON = 0x7f08239f; // instagram_gif_outline_24

    private static Object rf(Object o, String name) throws Exception {
        return o.getClass().getField(name).get(o);
    }

    /** Called from X.G1t.FFx in place of Collections.singletonList(favoriteItem). */
    public static List menu(final Object favoriteItem) {
        try {
            // favoriteItem.A01.A00.A01.A01 -> DirectAnimatedMedia, .getId() -> giphy id
            Object dam = rf(rf(rf(rf(favoriteItem, "A01"), "A00"), "A01"), "A01");
            final String giphyId = (String) dam.getClass().getMethod("getId").invoke(dam);

            final Class<?> itemIface = itemInterface(favoriteItem);
            Object copyItem = Proxy.newProxyInstance(
                    itemIface.getClassLoader(),
                    new Class[]{itemIface},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method.getDeclaringClass() == Object.class) {
                                return objectMethod(proxy, method, args);
                            }
                            // AKB(Context) -> build our row
                            if (args != null && args.length == 1 && args[0] instanceof Context) {
                                return buildRow((Context) args[0], favoriteItem, giphyId);
                            }
                            return defaultValue(method.getReturnType());
                        }
                    });

            List list = new ArrayList();
            list.add(favoriteItem);
            list.add(copyItem);
            return list;
        } catch (Throwable t) {
            PikoUtils.logger(t);
            return java.util.Collections.singletonList(favoriteItem); // fall back to IG's behavior
        }
    }

    /** The interface the favorite item implements whose method takes a Context (AKB). */
    private static Class<?> itemInterface(Object favoriteItem) {
        for (Class<?> c : favoriteItem.getClass().getInterfaces()) {
            for (Method m : c.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == Context.class) return c;
            }
        }
        return favoriteItem.getClass().getInterfaces()[0];
    }

    /** Build our X.C58446MxL row, mirroring the favorite item's own AKB output. */
    private static Object buildRow(Context context, Object favoriteItem, final String giphyId) throws Exception {
        // Invoke the favorite item's AKB to learn the row class + ctor.
        Method akb = null;
        for (Method m : itemInterface(favoriteItem).getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == Context.class) { akb = m; break; }
        }
        Object template = akb.invoke(favoriteItem, context);
        Class<?> rowClass = template.getClass();

        Constructor<?> ctor = null;
        for (Constructor<?> c : rowClass.getConstructors()) {
            if (c.getParameterCount() == 9) { ctor = c; break; }
        }
        if (ctor == null) return template; // give up gracefully (shows a second Favorite)
        Class<?>[] pt = ctor.getParameterTypes();

        final Class<?> onClickIface = pt[2]; // X.InterfaceC113025oro
        Object onClick = Proxy.newProxyInstance(
                onClickIface.getClassLoader(),
                new Class[]{onClickIface},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
                        // ElS() is void (the tap action); BhJ() returns boolean (enabled?).
                        if (method.getReturnType() == void.class) {
                            GifKeywordResolver.resolveAndCopy(giphyId, null);
                            return null;
                        }
                        if (method.getReturnType() == boolean.class) return Boolean.TRUE;
                        return defaultValue(method.getReturnType());
                    }
                });

        Drawable icon = context.getDrawable(ICON);
        // ctor args mirror IG's: (Drawable, Drawable icon, onClick, Integer, String label, String, boolean x3)
        return ctor.newInstance(null, icon, onClick, null, GifKeywordResolver.nameLabel(), null, false, false, false);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        String n = method.getName();
        if (n.equals("hashCode")) return System.identityHashCode(proxy);
        if (n.equals("equals")) return proxy == (args != null ? args[0] : null);
        if (n.equals("toString")) return "KasusobaCopyGifNameItem";
        return null;
    }

    private static Object defaultValue(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return Boolean.FALSE;
        if (t == void.class) return null;
        if (t == int.class || t == short.class || t == byte.class || t == char.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        return null;
    }

    private KasusobaGifPickerCopyName() {}
}
