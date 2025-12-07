package com.omarflex5.temp.omerflex.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.omerflex.R;

/*
 * Details activity class that loads LeanbackDetailsFragment class
 */
public class DetailsActivity extends FragmentActivity {
    public static final String SHARED_ELEMENT_NAME = "hero";
    public static final String MOVIE = "Movie";
    public static final String MAIN_MOVIE = "Main_Movie";
    public static final String MOVIE_SUBLIST = "Movie_sub";
    public static final String QUERY = "Query";
    public static final String TAG = "DetailsActivity";
    public static final String DETAILS_FRAGMENT_TAG = "details_fragment_tag";


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.details_fragment, new VideoDetailsFragment())
                    .commitNow();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Log.d(TAG, "onActivityResult: DetailsActivity");
        super.onActivityResult(requestCode, resultCode, data);
    }
}