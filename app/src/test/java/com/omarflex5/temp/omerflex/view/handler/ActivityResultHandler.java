package com.omarflex5.temp.omerflex.view.handler;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.Row;

import com.omerflex.entity.Movie;
import com.omerflex.server.Util;
import com.omerflex.view.VideoDetailsFragment;

import java.util.ArrayList;

public class ActivityResultHandler {

    private static final String TAG = "ActivityResultHandler";
    private Activity activity;

    public ActivityResultHandler(Activity activity) {
        this.activity = activity;
    }

    public void handleResult(int requestCode, int resultCode, Intent data, ArrayObjectAdapter mAdapter, Fragment fragment) {
        Log.d(TAG, "handleActivityResult: " + requestCode + ", " + resultCode + ", " + data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.d(TAG, "onActivityResult:RESULT_NOT_OK ");
            return;
        }
        int clickedRowId = data.getIntExtra(Movie.KEY_CLICKED_ROW_ID, -1);

        if (clickedRowId == -1){
            Log.d(TAG, "handleActivityResult: not ListRow");
            Toast.makeText(activity, "حدث خطأ...", Toast.LENGTH_SHORT).show();
            return;
        }
        Movie resultMovie = Util.recieveSelectedMovie(data);

        int clickedMovieIndex = data.getIntExtra(Movie.KEY_CLICKED_MOVIE_INDEX, -1);
        Log.d(TAG, "handleActivityResult: clickedMovieIndex: "+ clickedMovieIndex);
        Log.d(TAG, "handleActivityResult: clickedRowId: "+ clickedRowId);
        if (!(mAdapter.get(clickedRowId) instanceof ListRow)){
            Log.d(TAG, "handleResult: error: fail to find the clicked row");
        }

        ListRow listRow = (ListRow) mAdapter.get(clickedRowId);
        ArrayObjectAdapter clickedAdapter = (ArrayObjectAdapter) listRow.getAdapter();
        if (clickedMovieIndex != -1) {
            // we have to updated the clicked movie
            updateClickedMovieItem(clickedAdapter, clickedMovieIndex, resultMovie);
        }

        switch (requestCode) {
            case Movie.REQUEST_CODE_EXOPLAYER:
                Log.d(TAG, "onActivityResult: REQUEST_CODE_EXOPLAYER");
                Util.openExoPlayer(resultMovie, activity, true);
                break;
            case Movie.REQUEST_CODE_EXTERNAL_PLAYER:
                Log.d(TAG, "onActivityResult: REQUEST_CODE_EXTERNAL_PLAYER: " + resultMovie);
                Util.openExoPlayer(resultMovie, activity, true);
                break;
            default:
                Log.d(TAG, "handleResult: default");
                if (fragment instanceof VideoDetailsFragment){
                    VideoDetailsFragment videoDetailsFragment = (VideoDetailsFragment) fragment;
                    videoDetailsFragment.updateOverviewUI(resultMovie);
                }
                extendClickedRow(clickedAdapter, (ArrayList<Movie>) (resultMovie.getSubList()));
                break;
        }
    }

    private void extendClickedRow(ArrayObjectAdapter clickedAdapter, ArrayList<Movie> movieList) {
        if (movieList == null || movieList.isEmpty()) {
            Log.d(TAG, "extendClickedRow: empty list");
            return;
        }
        Log.d(TAG, "extendClickedRow: movieList:" +movieList);
        clickedAdapter.addAll(clickedAdapter.size(), movieList);
    }

    private void updateClickedMovieItem(ArrayObjectAdapter clickedAdapter, int clickedMovieIndex, Movie resultMovie) {
        Log.d(TAG, "updateClickedMovieItem: clickedMovieIndex "+ clickedMovieIndex + ", size: "+ clickedAdapter.size());
        if (resultMovie != null && clickedAdapter.size() > clickedMovieIndex) {
            clickedAdapter.replace(clickedMovieIndex, resultMovie);
        }
    }
}