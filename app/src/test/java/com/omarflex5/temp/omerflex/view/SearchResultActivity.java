package com.omarflex5.temp.omerflex.view;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.omerflex.R;

/*
 * Main Activity class that loads {@link SearchResultActivity}.
 */
public class SearchResultActivity extends FragmentActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.omerflex.R.layout.activity_search_result);
    }
}