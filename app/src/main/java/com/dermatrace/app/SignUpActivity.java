package com.dermatrace.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dermatrace.app.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText name, email, password, confirm;
    private MaterialButton btnCreate;
    private CircularProgressIndicator progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        UiUtils.applySystemBars(this, findViewById(R.id.signupRoot));

        auth = FirebaseAuth.getInstance();
        name = findViewById(R.id.edtSignupName);
        email = findViewById(R.id.edtSignupEmail);
        password = findViewById(R.id.edtSignupPassword);
        confirm = findViewById(R.id.edtSignupConfirm);
        btnCreate = findViewById(R.id.btnCreateAccount);
        progress = findViewById(R.id.progressSignup);

        btnCreate.setOnClickListener(v -> createAccount());
        ((TextView)findViewById(R.id.txtBackToLogin)).setOnClickListener(v -> finish());
    }

    private void createAccount() {
        String n = name.getText().toString().trim();
        String e = email.getText().toString().trim();
        String p = password.getText().toString();
        String c = confirm.getText().toString();

        if (n.isEmpty()) { name.setError("Enter your name"); return; }
        if (e.isEmpty()) { email.setError("Enter your email"); return; }
        if (p.length() < 6) { password.setError("Password must be at least 6 characters"); return; }
        if (!p.equals(c)) { confirm.setError("Passwords do not match"); return; }

        setBusy(true);
        auth.createUserWithEmailAndPassword(e, p).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                setBusy(false);
                String message = task.getException() == null ? "Could not create account." : task.getException().getLocalizedMessage();
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                setBusy(false);
                Toast.makeText(this, "Account created, but user profile could not be loaded.", Toast.LENGTH_LONG).show();
                return;
            }

            UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                    .setDisplayName(n)
                    .build();

            user.updateProfile(profile).addOnCompleteListener(updateTask -> {
                setBusy(false);
                Toast.makeText(this, "Welcome to DermaTrace, " + n + ".", Toast.LENGTH_SHORT).show();
                openApp();
            });
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? android.view.View.VISIBLE : android.view.View.GONE);
        btnCreate.setEnabled(!busy);
        name.setEnabled(!busy);
        email.setEnabled(!busy);
        password.setEnabled(!busy);
        confirm.setEnabled(!busy);
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
