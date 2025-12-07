package com.omarflex5.temp.omerflex.entity.specific;

import android.os.Parcel;
import android.os.Parcelable;

public class FilmData implements Parcelable {
    public FilmData() {
    }

    protected FilmData(Parcel in) {
    }

    public static final Creator<FilmData> CREATOR = new Creator<FilmData>() {
        @Override
        public FilmData createFromParcel(Parcel in) {
            return new FilmData(in);
        }

        @Override
        public FilmData[] newArray(int size) {
            return new FilmData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }
}
