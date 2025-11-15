package co.tinode.tindroid.media;

import android.os.Parcel;
import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import androidx.annotation.NonNull;

public class Wallpapers {
    @JsonProperty("patt")
    public List<WPaper> patterns;

    @JsonProperty("img")
    public List<WPaper> wallpapers;


    public static class WPaper implements Parcelable {
        public String name;
        public int size;
        @JsonProperty("pr")
        public String preview;

        public WPaper() {}

        // Constructor for recreating MyObject from a Parcel
        protected WPaper(Parcel in) {
            name = in.readString();
            size = in.readInt();
            preview = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeInt(size);
            dest.writeString(preview);
        }

        public static final Parcelable.Creator<WPaper> CREATOR
                = new Parcelable.Creator<>() {
            @Override
            public WPaper createFromParcel(Parcel in) {
                return new WPaper(in);
            }

            @Override
            public WPaper[] newArray(int size) {
                return new WPaper[size];
            }
        };
    }
}

