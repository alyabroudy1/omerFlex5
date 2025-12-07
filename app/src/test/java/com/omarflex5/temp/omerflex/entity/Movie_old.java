package com.omarflex5.temp.omerflex.entity;


import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/*
 * Movie class represents video entity with title, description, image thumbs and video url.
 */
@Entity(tableName = "movies")
public class Movie_old implements Parcelable {
    public static final String SERVER_APP = "app";
    public static final String SERVER_OMAR = "omar";
//    static final long serialVersionUID = 727566175075960653L;
    public final static String SERVER_AKWAM = "akwam";
    public final static String SERVER_OLD_AKWAM = "old_Akwam";
    public final static String SERVER_AFLAM_PRO = "AflamPro";
    public final static String SERVER_SHAHID4U = "Shahid4u";
    public final static String SERVER_CIMA4U = "Cima4u";
    public final static String SERVER_FASELHD = "faselhd";
    public final static String SERVER_MyCima = "mycima";
    public final static String SERVER_CimaNow = "cimaNow";
    public final static String SERVER_GOAFLAM = "GoAflam";
    public final static String SERVER_SERIES_TIME = "Series_time";
    public final static String SERVER_CIMA_CLUB = "cimaclub";
    public final static String SERVER_GOOGLE = "google";
    public final static String SERVER_IPTV = "Iptv";
    public final static String SERVER_KOORA_LIVE = "koora";
    public final static String SERVER_ARAB_SEED = "arabseed";
    public static final String SERVER_LAROZA = "laroza";
    public final static String SERVER_WATAN_FLIX = "watanflix";
    public static final String SERVER_IMDB = "imdb";
    public static final String KEY_CLICKED_ROW_ID = "clickedRowId";
    public static final String KEY_CLICKED_MOVIE_INDEX = "clickedMovieIndex";
    public static final String KEY_IS_COOKIE_FETCH = "isCookieFetch";
    public final static int GROUP_OF_GROUP_STATE = 0;
    public final static int GROUP_STATE = 1;
    public final static int ITEM_STATE = 2;
    public final static int RESOLUTION_STATE = 3;
    public final static int VIDEO_STATE = 4;
    public final static int BROWSER_STATE = 5;
    public final static int RESULT_STATE = 6;
    public final static int COOKIE_STATE = 7;

    public final static int PLAYLIST_STATE = 8;
    public final static int NEXT_PAGE_STATE = 9;
    public final static int REQUEST_CODE_EXOPLAYER = 10;
    public final static int REQUEST_CODE_EXTERNAL_PLAYER = 11;
    public final static int REQUEST_CODE_MOVIE_UPDATE = 12;
    public final static int REQUEST_CODE_MOVIE_LIST = 13;
    public final static int REQUEST_CODE_FETCH_HTML = 14;
    public static final int HTML_STATE = 15;
    public static final int FETCH_MOVIE_AT_START = 16;
    public static final int NO_FETCH_MOVIE_AT_START = 17;
    public static final int ACTION_WATCH_LOCALLY = 18;
    public static final int IPTV_PLAY_LIST_STATE = 19;
    private static int count = 0;
    private String searchContext;

    private int id;
    private int rowIndex;
    private String title;
    private String description;
    private String bgImageUrl;
    private String cardImageUrl;
    @PrimaryKey
    private String videoUrl;
    private String trailerUrl;
    private String studio;
    private String rate;
    private int state;
    private String mainMovieTitle;
    private List<Movie_old> subList;
    private List<String> categories;
    private int isHistory;
    private long playedTime;
    private Movie_old mainMovie;
    private String createdAt;
    private Date updatedAt;
    private int fetch;
    private String backgroundImageUrl;
    private String group;
    private MovieHistory movieHistory;


    public Movie_old() {
       // this.createdAt = Calendar.getInstance().getTime().toString();
        this.subList= new ArrayList<>();
        this.categories= new ArrayList<>();
        this.createdAt = new Date().toString();
        this.updatedAt = new Date();
        this.rate = "";
        this.fetch = 1;
    }

    //database things
    public Movie_old(
            String title,
            String searchContext,
            String mainMovieTitle,
            String studio,
            int state,
            String description,
            String bgImageUrl,
            String cardImageUrl,
            String videoUrl,
            String rate,
            String trailerUrl,
            int isHistory,
            long playedTime,
            String createdAt,
            Date updatedAt,
            int fetch,
            String backgroundImageUrl,
            String group
            ) {
        this.title = title;
        this.searchContext = searchContext;
        this.mainMovieTitle = mainMovieTitle;
        this.studio = studio;
        this.state = state;
        this.description = description;
        this.bgImageUrl = bgImageUrl;
        this.cardImageUrl = cardImageUrl;
        this.videoUrl = videoUrl;
        this.rate = rate;
        this.trailerUrl = trailerUrl;
        this.isHistory = isHistory;
        this.playedTime = playedTime;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.fetch = fetch;
        this.backgroundImageUrl = backgroundImageUrl;
        this.group = group;
        if (this.subList == null){
            this.subList= new ArrayList<>();
        }
    }

    protected Movie_old(Parcel in) {
        id = in.readInt();
        rowIndex = in.readInt();
        title = in.readString();
        searchContext = in.readString();
        mainMovieTitle = in.readString();
        studio = in.readString();
        state = in.readInt();
        description = in.readString();
        bgImageUrl = in.readString();
        cardImageUrl = in.readString();
        videoUrl = in.readString();
        rate = in.readString();
        trailerUrl = in.readString();
        isHistory = in.readInt();
        playedTime = in.readLong();
        createdAt = in.readString();
        updatedAt = new Date(in.readLong());
        fetch = in.readInt();
        backgroundImageUrl = in.readString();
        group = in.readString();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            movieHistory = in.readTypedObject(MovieHistory.CREATOR);
        }
        if (this.subList == null){
            this.subList= new ArrayList<>();
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeInt(rowIndex);
        dest.writeString(title);
        dest.writeString(searchContext);
        dest.writeString(mainMovieTitle);
        dest.writeString(studio);
        dest.writeInt(state);
        dest.writeString(description);
        dest.writeString(bgImageUrl);
        dest.writeString(cardImageUrl);
        dest.writeString(videoUrl);
        dest.writeString(rate);
        dest.writeString(trailerUrl);
        dest.writeInt(isHistory);
        dest.writeLong(playedTime);
        dest.writeString(createdAt);
        dest.writeLong(updatedAt.getTime());
        dest.writeInt(fetch);
        dest.writeString(backgroundImageUrl);
        dest.writeString(group);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dest.writeTypedObject(movieHistory, MovieHistory.PARCELABLE_WRITE_RETURN_VALUE);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Movie_old> CREATOR = new Creator<Movie_old>() {
        @Override
        public Movie_old createFromParcel(Parcel in) {
            return new Movie_old(in);
        }

        @Override
        public Movie_old[] newArray(int size) {
            return new Movie_old[size];
        }
    };
////

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
            return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStudio() {
        return studio;
    }

    public void setStudio(String studio) {
        this.studio = studio;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getBackgroundImageUrl() {
        return bgImageUrl;
    }

    public void setBackgroundImageUrl(String bgImageUrl) {
        this.bgImageUrl = bgImageUrl;
    }

    public String getCardImageUrl() {
        return cardImageUrl;
    }

    public void setCardImageUrl(String cardImageUrl) {
        this.cardImageUrl = cardImageUrl;
    }

    public String getBgImageUrl() {
        return bgImageUrl;
    }

    public void setBgImageUrl(String bgImageUrl) {
        this.bgImageUrl = bgImageUrl;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getRate() {
        return rate;
    }

    public void setRate(String rate) {
        this.rate = rate;
    }


    @Override
    public String toString() {
        return "Movie{" +
                "id=" + id +
                "rowIndex=" + rowIndex +
                ", title='" + title + '\'' +
                ", studio='" + studio + '\'' +
                ", state='" + state + '\'' +
                ", trailer='" + trailerUrl + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", group='" + group + '\'' +
                ", backgroundImageUrl='" + bgImageUrl + '\'' +
                ", cardImageUrl='" + cardImageUrl + '\'' +
                ", mainMovie='" + mainMovieTitle + '\'' +
                ", searchContext='" + searchContext + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", isHistory='" + isHistory + '\'' +
                ", playedTime='" + playedTime / 60000+ '\'' +
                ", updatedAt='" + updatedAt.toString() + '\'' +
                ", fetch='" + fetch + '\'' +
                '}';
    }

    public static Movie_old buildMovieInfo(
            String title,
            String searchContext,
            String mainMovieTitle,
            String studio,
            int state,
            String description,
            String backgroundImageUrl,
            String cardImageUrl,
            String videoUrl,
            String rate,
            String trailerUrl,
            int isHistory,
            long playedTime,
            String createdAt,
            Date updatedAt,
            int fetch,
            String group
            ) {
        Movie_old movie = new Movie_old();
        movie.setId(count++);
        movie.setTitle(title);
        movie.setDescription(description);
        movie.setStudio(studio);
        movie.setCardImageUrl(cardImageUrl);
        movie.setBackgroundImageUrl(backgroundImageUrl);
        movie.setState(state);
        movie.setVideoUrl(videoUrl);
        movie.setRate(rate);
        movie.setTrailerUrl(trailerUrl);
        movie.setCreatedAt(createdAt);
        movie.setSearchContext(searchContext);
        movie.setMainMovieTitle(mainMovieTitle);
        movie.setIsHistory(isHistory);
        movie.setPlayedTime(playedTime);
        movie.setUpdatedAt(updatedAt);
        movie.setFetch(fetch);
        movie.setGroup(group);
        return movie;
    }

    public String getMainMovieTitle() {
        return mainMovieTitle;
    }

    public void setMainMovieTitle(String mainMovieTitle) {
        this.mainMovieTitle = mainMovieTitle;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getIsHistory() {
        return isHistory;
    }

    public static Movie_old clone(Movie_old movie){
        Movie_old newM = new Movie_old();
        newM.setId(movie.getId());
        newM.setRowIndex(movie.getRowIndex());
        newM.setTitle(movie.getTitle());
        newM.setVideoUrl(movie.getVideoUrl());
        newM.setStudio(movie.getStudio());
        newM.setState(movie.getState());
        newM.setDescription(movie.getDescription());
        newM.setCardImageUrl(movie.getCardImageUrl());
        newM.setBackgroundImageUrl(movie.getBackgroundImageUrl());
        newM.setBgImageUrl(movie.getBgImageUrl());
        newM.setRate(movie.getRate());
        newM.setTrailerUrl(movie.getTrailerUrl());
        newM.setCreatedAt(movie.getCreatedAt());
        newM.setMainMovieTitle(movie.getMainMovieTitle());
        newM.setMainMovie(movie.getMainMovie());
        newM.setSubList(movie.getSubList());
        newM.setSearchContext(movie.getSearchContext());
        newM.setIsHistory(movie.isHistory());
        newM.setPlayedTime(movie.getPlayedTime());
        newM.setUpdatedAt(movie.getUpdatedAt());
        newM.setFetch(movie.getFetch());
        newM.setBackgroundImageUrl(movie.getBackgroundImageUrl());
        newM.setGroup(movie.getGroup());
        return newM;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Movie_old movie = (Movie_old) obj;
        return videoUrl != null && videoUrl.equals(movie.videoUrl);
    }

    @Override
    public int hashCode() {
        return videoUrl != null ? videoUrl.hashCode() : 0;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public void setTrailerUrl(String trailerUrl) {
        this.trailerUrl = trailerUrl;
    }

    public String getSearchContext() {
        return searchContext;
    }


//    public boolean save(MovieDbHelper dbHelper){
//        return dbHelper.saveMovie(this, true);
//    }
    public void setSearchContext(String searchContext) {
        this.searchContext = searchContext;
    }

    public List<Movie_old> getSubList() {
        return subList;
    }

    public boolean addSubList(Movie_old movie) {
        return this.subList.add(movie);
    }
    public void setSubList(List<Movie_old> subList) {
        this.subList = subList;
    }

    public int isHistory() {
        return isHistory;
    }

    public void setIsHistory(int history) {
        isHistory = history;
    }

    public long getPlayedTime() {
        return playedTime;
    }

    public void setPlayedTime(long playedTime) {
        this.playedTime = playedTime;
    }

    public Movie_old getMainMovie() {
        return mainMovie;
    }

    public void setMainMovie(Movie_old mainMovie) {
        this.mainMovie = mainMovie;
    }

    public int getFetch() {
        return fetch;
    }

    public void setFetch(int fetch) {
        this.fetch = fetch;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public MovieHistory getMovieHistory() {
        return movieHistory;
    }

    public void setMovieHistory(MovieHistory movieHistory) {
        this.movieHistory = movieHistory;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public void addCategory(String category) {
        this.categories.add(category);
    }
}
