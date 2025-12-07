package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.omerflex.R;

/*
 * Main Activity class that loads {@link SearchFragment}.
 */
public class GetSearchQueryActivity extends Activity {

    private static final String TAG = "GetSearchQueryActivity";
    EditText editTextName;
    Button buttonSearch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_search_query);

        editTextName = findViewById(R.id.editTextName);
        buttonSearch = findViewById(R.id.buttonSearch);


        editTextName.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    // Perform action on key press
                    String query = editTextName.getText().toString();
                    Log.i(TAG, "onButtonClick." + "query: " + query);

                    //  Intent searchIntent = new Intent(SearchActivity.this, SearchActivity.class);
                    Intent searchResultIntent = new Intent(GetSearchQueryActivity.this, SearchResultActivity.class);
                    searchResultIntent.putExtra("query", query);
                    // setResult(Activity.RESULT_OK,returnIntent);
                    //  finish();
                    startActivity(searchResultIntent);

                    //searchIntent.putExtra("QUERY", query);
                    //start the activity
                    //startActivity(searchIntent);
                    return true;
                }
                return false;
            }
        });


        //click listener for the button
        buttonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String query = editTextName.getText().toString();
                Log.i(TAG, "onButtonClick." + "query: " + query);

                Intent searchResultIntent = new Intent(GetSearchQueryActivity.this, SearchResultActivity.class);
                searchResultIntent.putExtra("query", query);
                // setResult(Activity.RESULT_OK,returnIntent);
                //  finish();
                startActivity(searchResultIntent);


                // Intent searchIntent = new Intent(SearchActivity.this, SearchActivity.class);
                // searchIntent.putExtra("QUERY", query);
                //start the activity
                // startActivity(searchIntent);
            }
        });
    }
}