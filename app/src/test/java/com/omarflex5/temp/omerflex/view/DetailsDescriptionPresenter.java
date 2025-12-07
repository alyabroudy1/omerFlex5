package com.omarflex5.temp.omerflex.view;

import android.os.Build;
import android.util.Log;

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;

import com.omerflex.R;
import com.omerflex.entity.Movie;

public class DetailsDescriptionPresenter extends AbstractDetailsDescriptionPresenter {

    String TAG = "DetailsDescriptionPresenter";

    @Override
    protected void onBindDescription(ViewHolder viewHolder, Object item) {
        Movie movie = (Movie) item;

        if (movie != null) {
            viewHolder.getTitle().setText(movie.getTitle());
            viewHolder.getSubtitle().setText(movie.getStudio());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                viewHolder.getBody().setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Large);
            }

            String history = "";
            if (movie.getMovieHistory() != null &&
                    (
                            movie.getState() == Movie.GROUP_OF_GROUP_STATE ||
                                    movie.getState() == Movie.GROUP_STATE
                    )
            ) {
//                history = ((movie.getMovieHistory().getEpisode() != null) ?
//                        (" | " + movie.getMovieHistory().getEpisode()) : "") +
//                        ((movie.getMovieHistory().getSeason() != null) ? (" | " + movie.getMovieHistory().getSeason()) : "");
            }
//            Log.d(TAG, "onBindDescription: history: "+history);
            viewHolder.getBody().setText(movie.getDescription() + history);
        }
    }
}