package com.omarflex5.temp.omerflex.entity.specific;

import android.os.Parcel;
import android.os.Parcelable;

public class ResolutionData implements Parcelable {
    private String resolution;

    public ResolutionData() {
    }

    protected ResolutionData(Parcel in) {
        resolution = in.readString();
    }

    public static final Creator<ResolutionData> CREATOR = new Creator<ResolutionData>() {
        @Override
        public ResolutionData createFromParcel(Parcel in) {
            return new ResolutionData(in);
        }

        @Override
        public ResolutionData[] newArray(int size) {
            return new ResolutionData[size];
        }
    };

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(resolution);
    }
}
