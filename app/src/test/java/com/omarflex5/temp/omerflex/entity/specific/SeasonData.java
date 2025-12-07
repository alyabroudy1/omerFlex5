package com.omarflex5.temp.omerflex.entity.specific;

import android.os.Parcel;
import android.os.Parcelable;

public class SeasonData implements Parcelable {
    private int seasonNumber;
    private int episodeCount;

    public SeasonData() {
    }

    protected SeasonData(Parcel in) {
        seasonNumber = in.readInt();
        episodeCount = in.readInt();
    }

    public static final Creator<SeasonData> CREATOR = new Creator<SeasonData>() {
        @Override
        public SeasonData createFromParcel(Parcel in) {
            return new SeasonData(in);
        }

        @Override
        public SeasonData[] newArray(int size) {
            return new SeasonData[size];
        }
    };

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public int getEpisodeCount() {
        return episodeCount;
    }

    public void setEpisodeCount(int episodeCount) {
        this.episodeCount = episodeCount;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(seasonNumber);
        dest.writeInt(episodeCount);
    }
}
