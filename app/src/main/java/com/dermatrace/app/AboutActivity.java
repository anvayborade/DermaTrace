package com.dermatrace.app;

import android.os.Bundle;

public class AboutActivity extends BaseDrawerActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_about, R.id.toolbarAbout);
    }
}
