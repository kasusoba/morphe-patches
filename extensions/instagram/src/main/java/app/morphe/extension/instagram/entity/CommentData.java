/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


public class CommentData extends Entity implements GifMediaInterface {
    private final Object obj;

    public CommentData(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public boolean hasText() throws Exception {
        String text = this.getText();
        return text!=null && text.length()>0;
    }

    public String getText() throws Exception {
        return (String) super.getField("A0N");
    }

    public UserData getCommentUserData() throws Exception {
        Object userObj = super.getField("fieldName");
        if(userObj!=null){
            return new UserData(userObj);
        }
        return null;
    }

    public boolean hasMedia() throws Exception {
        return this.hasImageMedia() || this.hasGifMedia();
    }

    public boolean hasImageMedia() throws Exception {
        return this.getImageMedia()!=null;
    }

    public MediaData getImageMedia() throws Exception {
        Entity imageDataEntity = super.getFieldAsEntity("fieldName");
        Object mediaObj = imageDataEntity.getField("fieldName2");
        if(mediaObj!=null){
            return new MediaData(mediaObj);
        }
        return null;
    }

    private Object getGifMedia() throws Exception {
        return super.getField("fieldName");
    }

    public boolean hasGifMedia() throws Exception {
        return this.getGifMedia()!=null;
    }

    private String getGifObjectField(String fieldName) throws Exception {
        return (String) super.getField(this.getGifMedia(), fieldName);
    }

    @Override
    public String getGifUrl() throws Exception {
        return this.getGifObjectField("fieldName");
    }

    @Override
    public String getWebpUrl() throws Exception {
        return this.getGifObjectField("fieldName");
    }

    @Override
    public String getGifTag() throws Exception {
        return this.getGifObjectField("fieldName");
    }

    @Override
    public String getGifCreatorName() throws Exception {
        return this.getGifObjectField("fieldName");
    }

    @Override
    public String getGifDownloadName() throws Exception {
        String creatorName = this.getGifCreatorName();
        String gifTag = this.getGifTag();
        return creatorName+"_"+gifTag+".gif";
    }

    /**
     * The GIF's human-readable title (searchable keyword), e.g. "Happy Dance Sticker".
     * Reads it by calling the unobfuscated getTitle() accessor on the GIF object
     * (com.instagram.api.schemas.CommentGiphyMediaInfo) via reflection, so no
     * per-version field mapping is needed. Note: getGifTag() maps to the GIPHY *id*,
     * not the title — this is the field to use for "search this GIF by name".
     */
    public String getGifTitle() throws Exception {
        return this.invokeGifStringMethod("getTitle");
    }

    /** GIPHY id of the GIF (e.g. "Z5hH4UkgzyaTqhi82b"). */
    public String getGifId() throws Exception {
        return this.invokeGifStringMethod("getId");
    }

    /**
     * Best-effort: call a no-arg String accessor (e.g. getTitle/getId) on the GIF object
     * using Class#getMethod (searches the public method hierarchy incl. interfaces).
     * Returns null instead of throwing so a callsite never crashes on an unexpected model.
     */
    private String invokeGifStringMethod(String methodName) {
        try {
            Object gif = this.getGifMedia();
            if (gif == null) return null;
            java.lang.reflect.Method m = gif.getClass().getMethod(methodName);
            Object result = m.invoke(gif);
            return result == null ? null : result.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
