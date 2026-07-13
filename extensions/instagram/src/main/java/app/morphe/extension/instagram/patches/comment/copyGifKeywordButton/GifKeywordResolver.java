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

import android.content.Context;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.download.DownloadUtils;

/**
 * Resolves a GIF's human title/keyword from its GIPHY id via the GIPHY HTTP API,
 * off the main thread, then copies it to the clipboard and toasts it.
 *
 * The comment only stores the GIPHY id (+ creator + image URLs), not the title, so
 * we look the title up remotely. Fallback chain if the API can't resolve a title:
 * de-slugged slug -> creator name -> giphy.com link.
 */
public final class GifKeywordResolver {

    // GIPHY REST API key. The string below is a placeholder in source (never commit a
    // real key); the "Copy GIF keyword" patch replaces it at patch time via the
    // `giphyApiKey` option (fingerprint on this named method + changeFirstString).
    private static String apiKey() {
        return "PASTE_GIPHY_API_KEY_HERE";
    }

    // Cache resolved id -> keyword so repeated taps on the same GIF don't spend an API
    // call (the beta key is limited to 100 calls/hour).
    private static final java.util.Map<String, String> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Localized "Copy GIF name" menu label (IG has no matching string resource and custom
     * string resources don't resolve at patch time). Shared by the DM and picker surfaces.
     */
    public static String nameLabel() {
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

    /** Localized "Copy GIPHY link" menu label. Shared by the DM and picker surfaces. */
    public static String linkLabel() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "ja": return "GIPHYリンクをコピー";
            case "es": return "Copiar enlace de GIPHY";
            case "pt": return "Copiar link do GIPHY";
            case "fr": return "Copier le lien GIPHY";
            case "de": return "GIPHY-Link kopieren";
            case "it": return "Copia link GIPHY";
            case "id": return "Salin tautan GIPHY";
            case "ru": return "Копировать ссылку GIPHY";
            case "ko": return "GIPHY 링크 복사";
            case "zh": return "复制 GIPHY 链接";
            case "ar": return "نسخ رابط GIPHY";
            case "hi": return "GIPHY लिंक कॉपी करें";
            case "tr": return "GIPHY bağlantısını kopyala";
            default: return "Copy GIPHY link";
        }
    }

    /** Localized "Download GIF" menu label. Shared by the DM and picker surfaces. */
    public static String downloadLabel() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "ja": return "GIFをダウンロード";
            case "es": return "Descargar GIF";
            case "pt": return "Baixar GIF";
            case "fr": return "Télécharger le GIF";
            case "de": return "GIF herunterladen";
            case "it": return "Scarica GIF";
            case "id": return "Unduh GIF";
            case "ru": return "Скачать GIF";
            case "ko": return "GIF 다운로드";
            case "zh": return "下载 GIF";
            case "ar": return "تنزيل GIF";
            case "hi": return "GIF डाउनलोड करें";
            case "tr": return "GIF'i indir";
            default: return "Download GIF";
        }
    }

    /** Download a GIF from its (cdn) URL to the device's Gif folder, reusing piko's downloader. */
    public static void downloadGif(String gifUrl, String fileName) {
        try {
            if (isBlank(gifUrl)) { PikoUtils.toast("No GIF to download"); return; }
            Context context = (Context) Utils.getActivity();
            if (context == null) return;
            if (isBlank(fileName)) fileName = "gif";
            if (!fileName.toLowerCase().endsWith(".gif")) fileName = fileName + ".gif";
            DownloadUtils.downloadMediaUrl(context, gifUrl, Constants.DEFAULT_GIF_FOLDER, fileName);
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

    /** Copy a shareable GIPHY page link for the given GIF id (no API call needed). */
    public static void copyGiphyLink(String gifId) {
        try {
            if (isBlank(gifId)) { PikoUtils.toast("No GIPHY link"); return; }
            String link = "https://giphy.com/gifs/" + gifId;
            Utils.setClipboard(link);
            PikoUtils.toast("GIPHY link copied");
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

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
                                PikoUtils.toast("GIF name copied: " + result);
                            } else {
                                PikoUtils.toast("No GIF name found");
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
            URL url = new URL("https://api.giphy.com/v1/gifs/" + gifId + "?api_key=" + apiKey());
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
