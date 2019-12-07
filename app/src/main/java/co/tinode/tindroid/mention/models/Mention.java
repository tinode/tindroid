package co.tinode.tindroid.mention.models;

import android.widget.EditText;


import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import co.tinode.tindroid.mention.mentions.Mentionable;

/**
 * A mention inserted into the {@link EditText}. All mentions inserted into the
 * {@link EditText} must implement the {@link Mentionable} interface.
 */
public class Mention implements Mentionable {

    private String mentionName;

    private String uid;

    private int offset;

    private int length;

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public int getMentionOffset() {
        return offset;
    }

    @Override
    public int getMentionLength() {
        return length;
    }

    @Override
    public String getMentionName() {
        return mentionName;
    }

    @Override
    public void setMentionOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public void setMentionLength(int length) {
        this.length = length;
    }

    @Override
    public void setMentionName(String mentionName) {
        this.mentionName = mentionName;
    }

    @Override
    public String getMentionUID() {
        return this.uid;
    }

    @Override
    public void setMentionUID(String uid) {
        this.uid = uid;
    }
}
