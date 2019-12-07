package co.tinode.tindroid.mention.models;

import android.graphics.drawable.Drawable;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * The local JSON file users.json contains sample user data that is loaded and used
 * to demonstrate '@' mentions.
 */
public class User {

    private String name;
    private String uid;
    private Drawable image;

    public User(String name, String uid, Drawable image) {
        this.name = name;
        this.uid = uid;
        this.image = image;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Drawable getImage() {
        return image;
    }

    public void setImage(Drawable image) {
        this.image = image;
    }
}
