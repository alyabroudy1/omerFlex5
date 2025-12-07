package com.omarflex5.temp.omerflex.entity.specific;

import android.os.Parcel;
import android.os.Parcelable;

public class EpisodeData implements Parcelable {
    private int episodeNumber;

    public EpisodeData() {
    }

    protected EpisodeData(Parcel in) {
        episodeNumber = in.readInt();
    }

    public static final Creator<EpisodeData> CREATOR = new Creator<EpisodeData>() {
        @Override
        public EpisodeData createFromParcel(Parcel in) {
            return new EpisodeData(in);
        }

        @Override
        public EpisodeData[] newArray(int size) {
            return new EpisodeData[size];
        }
    };

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(episodeNumber);
    }
}
