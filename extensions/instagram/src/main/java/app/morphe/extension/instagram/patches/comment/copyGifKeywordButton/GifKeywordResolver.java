/*
 * Kasusoba patches — based on piko (https://github.com/crimera/piko), GPLv3.
 */

package app.morphe.extension.instagram.patches.comment.copyGifKeywordButton;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.PikoUtils;

/**
 * Resolves a GIF's human title/keyword from its GIPHY id via the GIPHY HTTP API,
 * off the main thread, then copies it to the clipboard and toasts it.
 *
 * The comment only stores the GIPHY id (+ creator + image URLs), not the title, so
 * we look the title up remotely. Fallback chain if the API can't resolve a title:
 * de-slugged slug -> creator name -> giphy.com link.
 */
public final class GifKeywordResolver {

    // GIPHY REST API key. Left as a placeholder in source (never commit a real key);
    // the "Copy GIF keyword" patch injects the real key at patch time from the
    // `giphyApiKey` option. NOT final, so javac won't inline it at use sites.
    private static String GIPHY_API_KEY = "PASTE_GIPHY_API_KEY_HERE";

    // Cache resolved id -> keyword so repeated taps on the same GIF don't spend an API
    // call (the beta key is limited to 100 calls/hour).
    private static final java.util.Map<String, String> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void resolveAndCopy(final String gifId, final String creator) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String keyword = (gifId != null) ? CACHE.get(gifId) : null;
                if (isBlank(keyword)) {
                    keyword = fetchTitle(gifId);
                    if (!isBlank(keyword) && gifId != null) CACHE.put(gifId, keyword);
                }
                if (isBlank(keyword)) keyword = creator;
                if (isBlank(keyword)) {
                    keyword = isBlank(gifId) ? null : ("https://giphy.com/gifs/" + gifId);
                }
                final String result = keyword;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!isBlank(result)) {
                                Utils.setClipboard(result);
                                PikoUtils.toast("GIF keyword copied: " + result);
                            } else {
                                PikoUtils.toast("No GIF keyword found");
                            }
                        } catch (Throwable t) {
                            PikoUtils.logger(t);
                        }
                    }
                });
            }
        }).start();
    }

    private static String fetchTitle(String gifId) {
        if (isBlank(gifId)) return null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.giphy.com/v1/gifs/" + gifId + "?api_key=" + GIPHY_API_KEY);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject data = new JSONObject(sb.toString()).optJSONObject("data");
            if (data == null) return null;

            String title = cleanTitle(data.optString("title", ""));
            if (!isBlank(title)) return title;

            // Fallback: derive keywords from the slug (e.g. "happy-dance-<id>").
            String slug = data.optString("slug", "").trim();
            if (!isBlank(slug)) {
                int dash = slug.lastIndexOf('-');
                if (dash > 0 && slug.substring(dash + 1).equals(gifId)) {
                    slug = slug.substring(0, dash);
                }
                return slug.replace('-', ' ').trim();
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * GIPHY titles look like "Show Ok GIF", "Happy Dance Sticker", or
     * "Happy Dance GIF by Creator". Strip the trailing "GIF"/"Sticker"/"Meme"
     * (and any " by <creator>") so the result is directly searchable in the picker.
     */
    private static String cleanTitle(String title) {
        if (isBlank(title)) return null;
        String cleaned = title.trim().replaceAll("(?i)\\s+(gif|sticker|meme)(\\s+by\\s+.*)?$", "").trim();
        return isBlank(cleaned) ? title.trim() : cleaned;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    private GifKeywordResolver() {}
}
