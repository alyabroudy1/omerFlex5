package com.omarflex5.temp.omerflex.entity;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "movie_history",
        foreignKeys = @ForeignKey(entity = Movie.class,
                parentColumns = "id",
                childColumns = "movieId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"movieId"}, unique = true)})
public class MovieHistory implements Parcelable {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long movieId;
    private long watchedPosition;
    private Date lastWatchedDate;

    public MovieHistory(long movieId, long watchedPosition, Date lastWatchedDate) {
        this.movieId = movieId;
        this.watchedPosition = watchedPosition;
        this.lastWatchedDate = lastWatchedDate;
    }

    protected MovieHistory(Parcel in) {
        id = in.readLong();
        movieId = in.readLong();
        watchedPosition = in.readLong();
        long tmpLastWatchedDate = in.readLong();
        lastWatchedDate = tmpLastWatchedDate == -1 ? null : new Date(tmpLastWatchedDate);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeLong(movieId);
        dest.writeLong(watchedPosition);
        dest.writeLong(lastWatchedDate != null ? lastWatchedDate.getTime() : -1);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MovieHistory> CREATOR = new Creator<MovieHistory>() {
        @Override
        public MovieHistory createFromParcel(Parcel in) {
            return new MovieHistory(in);
        }

        @Override
        public MovieHistory[] newArray(int size) {
            return new MovieHistory[size];
        }
    };

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getMovieId() {
        return movieId;
    }

    public void setMovieId(long movieId) {
        this.movieId = movieId;
    }

    public long getWatchedPosition() {
        return watchedPosition;
    }

    public void setWatchedPosition(long watchedPosition) {
        this.watchedPosition = watchedPosition;
    }

    public Date getLastWatchedDate() {
        return lastWatchedDate;
    }

    public void setLastWatchedDate(Date lastWatchedDate) {
        this.lastWatchedDate = lastWatchedDate;
    }
}
