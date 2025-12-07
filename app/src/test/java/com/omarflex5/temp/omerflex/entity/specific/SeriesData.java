package com.omarflex5.temp.omerflex.entity.specific;

import android.os.Parcel;
import android.os.Parcelable;

public class SeriesData implements Parcelable {
    private int seasonCount;

    public SeriesData() {
    }

    protected SeriesData(Parcel in) {
        seasonCount = in.readInt();
    }

    public static final Creator<SeriesData> CREATOR = new Creator<SeriesData>() {
        @Override
        public SeriesData createFromParcel(Parcel in) {
            return new SeriesData(in);
        }

        @Override
        public SeriesData[] newArray(int size) {
            return new SeriesData[size];
        }
    };

    public int getSeasonCount() {
        return seasonCount;
    }

    public void setSeasonCount(int seasonCount) {
        this.seasonCount = seasonCount;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(seasonCount);
    }
}
