package com.dermatrace.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.dermatrace.app.util.UiUtils;
import com.dermatrace.app.util.BrandingUtils;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        ((TextView)findViewById(R.id.txtSplashBrand)).setText(BrandingUtils.wordmark(this));
        UiUtils.applySystemBars(this, findViewById(R.id.splashRoot));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Class<?> destination = FirebaseAuth.getInstance().getCurrentUser() == null
                    ? LoginActivity.class : MainActivity.class;
            Intent intent = new Intent(this, destination);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }, 650);
    }
}
