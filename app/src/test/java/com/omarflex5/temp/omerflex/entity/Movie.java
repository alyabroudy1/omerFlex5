package com.omarflex5.temp.omerflex.entity;


import android.os.Parcel;
import android.os.Parcelable;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import androidx.room.Ignore;
import androidx.room.Embedded;

import com.omerflex.db.Converters;
import com.omerflex.entity.specific.EpisodeData;
import com.omerflex.entity.specific.FilmData;
import com.omerflex.entity.specific.ResolutionData;
import com.omerflex.entity.specific.SeasonData;
import com.omerflex.entity.specific.SeriesData;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/*
 * Movie class represents video entity with title, description, image thumbs and video url.
 */
@Entity(tableName = "movies", indices = {@androidx.room.Index(value = {"videoUrl"}, unique = true)})
@TypeConverters(Converters.class)
public class Movie implements Parcelable {
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
    public final static int REQUEST_CODE_EXTEND_MOVIE_SUB_LIST = 13;
    public final static int REQUEST_CODE_FETCH_HTML = 14;
    public static final int HTML_STATE = 15;
    public static final int FETCH_MOVIE_AT_START = 16;
    public static final int NO_FETCH_MOVIE_AT_START = 17;
    public static final int ACTION_WATCH_LOCALLY = 18;
    public static final int IPTV_PLAY_LIST_STATE = 19;
    private static int count = 0;
    private String searchContext;

    @PrimaryKey(autoGenerate = true)
    private long id;
    private Long parentId;
    private MovieType type;
    private long movieLength;
    private int rowIndex;
    private String title;
    private String description;
    private String bgImageUrl;
    private String cardImageUrl;
    private String videoUrl;
    private String trailerUrl;
    private String studio;
    private String rate;
    private int state;
    private String mainMovieTitle;
    @Ignore
    private List<Movie> subList;
    private List<String> categories;
    private int isHistory;
    private long playedTime;
    private String createdAt;
    private Date updatedAt;
    private int fetch;
    private String backgroundImageUrl;
    private String group;

    @Ignore
    private MovieHistory movieHistory;

    @Embedded
    public SeriesData seriesData;

    @Embedded
    public SeasonData seasonData;

    @Embedded
    public EpisodeData episodeData;

    @Embedded
    public FilmData filmData;

    @Embedded
    public ResolutionData resolutionData;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public MovieType getType() {
        return type;
    }

    public void setType(MovieType type) {
        this.type = type;
    }

    public long getMovieLength() {
        return movieLength;
    }

    public void setMovieLength(long movieLength) {
        this.movieLength = movieLength;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }


    public Movie() {
        this.subList= new ArrayList<>();
        this.categories= new ArrayList<>();
        this.createdAt = new Date().toString();
        this.updatedAt = new Date();
        this.rate = "";
        this.fetch = 1;
    }

    //database things
    public Movie(
            long id,
            Long parentId,
            MovieType type,
            long movieLength,
            String searchContext,
            int rowIndex,
            String title,
            String description,
            String bgImageUrl,
            String cardImageUrl,
            String videoUrl,
            String trailerUrl,
            String studio,
            String rate,
            int state,
            String mainMovieTitle,
            List<String> categories,
            int isHistory,
            long playedTime,
            String createdAt,
            Date updatedAt,
            int fetch,
            String backgroundImageUrl,
            String group,
            SeriesData seriesData,
            SeasonData seasonData,
            EpisodeData episodeData,
            FilmData filmData,
            ResolutionData resolutionData
    ) {
        this.id = id;
        this.parentId = parentId;
        this.type = type;
        this.movieLength = movieLength;
        this.searchContext = searchContext;
        this.rowIndex = rowIndex;
        this.title = title;
        this.description = description;
        this.bgImageUrl = bgImageUrl;
        this.cardImageUrl = cardImageUrl;
        this.videoUrl = videoUrl;
        this.trailerUrl = trailerUrl;
        this.studio = studio;
        this.rate = rate;
        this.state = state;
        this.mainMovieTitle = mainMovieTitle;
        this.categories = categories;
        this.isHistory = isHistory;
        this.playedTime = playedTime;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.fetch = fetch;
        this.backgroundImageUrl = backgroundImageUrl;
        this.group = group;
        this.seriesData = seriesData;
        this.seasonData = seasonData;
        this.episodeData = episodeData;
        this.filmData = filmData;
        this.resolutionData = resolutionData;
        if (this.categories == null){
            this.categories= new ArrayList<>();
        }
    }

    protected Movie(Parcel in) {
        id = in.readLong();
        parentId = (Long) in.readValue(Long.class.getClassLoader());
        type = (MovieType) in.readSerializable();
        movieLength = in.readLong();
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
        seriesData = in.readParcelable(SeriesData.class.getClassLoader());
        seasonData = in.readParcelable(SeasonData.class.getClassLoader());
        episodeData = in.readParcelable(EpisodeData.class.getClassLoader());
        filmData = in.readParcelable(FilmData.class.getClassLoader());
        resolutionData = in.readParcelable(ResolutionData.class.getClassLoader());
        movieHistory = in.readParcelable(MovieHistory.class.getClassLoader());

        if (this.subList == null){
            this.subList= new ArrayList<>();
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeValue(parentId);
        dest.writeSerializable(type);
        dest.writeLong(movieLength);
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
        dest.writeParcelable(seriesData, flags);
        dest.writeParcelable(seasonData, flags);
        dest.writeParcelable(episodeData, flags);
        dest.writeParcelable(filmData, flags);
        dest.writeParcelable(resolutionData, flags);
        dest.writeParcelable(movieHistory, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Movie> CREATOR = new Creator<Movie>() {
        @Override
        public Movie createFromParcel(Parcel in) {
            return new Movie(in);
        }

        @Override
        public Movie[] newArray(int size) {
            return new Movie[size];
        }
    };

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
        return "Movie{"
                + "id=" + id +
                ", parentId=" + parentId +
                ", type=" + type +
                ", movieLength=" + movieLength +
                ", rowIndex=" + rowIndex +id +
                "rowIndex=" + rowIndex +
                ", title='" + title + "'"
                + ", studio='" + studio + "'"
                + ", state='" + state + "'"
                + ", trailer='" + trailerUrl + "'"
                + ", videoUrl='" + videoUrl + "'"
                + ", group='" + group + "'"
//                + ", backgroundImageUrl='" + bgImageUrl + "'"
                + ", cardImageUrl='" + cardImageUrl + "'"
//                + ", mainMovie='" + mainMovieTitle + "'"
                + ", searchContext='" + searchContext + "'"
                + ", createdAt='" + createdAt + "'"
                + ", isHistory='" + isHistory + "'"
                + ", playedTime='" + playedTime / 60000+ "'"
                + ", updatedAt='" + updatedAt.toString() + "'"
                + ", fetch='" + fetch + "'"
                + "}";
    }

    public static Movie buildMovieInfo(
            long id,
            Long parentId,
            MovieType type,
            long movieLength,
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
            String group,
            SeriesData seriesData,
            SeasonData seasonData,
            EpisodeData episodeData,
            FilmData filmData,
            ResolutionData resolutionData
            ) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setParentId(parentId);
        movie.setType(type);
        movie.setMovieLength(movieLength);
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
        movie.seriesData = seriesData;
        movie.seasonData = seasonData;
        movie.episodeData = episodeData;
        movie.filmData = filmData;
        movie.resolutionData = resolutionData;
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

    public static Movie clone(Movie movie){
        Movie newM = new Movie();
        newM.setParentId(movie.getParentId());
        newM.setType(movie.getType());
        newM.setMovieLength(movie.getMovieLength());
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
        newM.setSubList(movie.getSubList());
        newM.setSearchContext(movie.getSearchContext());
        newM.setIsHistory(movie.isHistory());
        newM.setPlayedTime(movie.getPlayedTime());
        newM.setUpdatedAt(movie.getUpdatedAt());
        newM.setFetch(movie.getFetch());
        newM.setBackgroundImageUrl(movie.getBackgroundImageUrl());
        newM.setGroup(movie.getGroup());
        newM.seriesData = movie.seriesData;
        newM.seasonData = movie.seasonData;
        newM.episodeData = movie.episodeData;
        newM.filmData = movie.filmData;
        newM.resolutionData = movie.resolutionData;
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
        Movie movie = (Movie) obj;
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

    public List<Movie> getSubList() {
        return subList;
    }

    public boolean addSubList(Movie movie) {
        return this.subList.add(movie);
    }
    public void setSubList(List<Movie> subList) {
        this.subList = subList;
    }

    @Ignore
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