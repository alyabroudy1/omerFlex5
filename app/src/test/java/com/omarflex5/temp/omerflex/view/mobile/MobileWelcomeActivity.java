package com.omarflex5.temp.omerflex.view.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.omerflex.R;
import com.omerflex.view.MainActivity;

public class MobileWelcomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mobile_welcome);
        Intent mainActivity = new Intent(this, MainActivity.class);
        startActivity(mainActivity);
        finish();
    }
}
