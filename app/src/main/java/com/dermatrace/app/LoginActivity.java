package com.dermatrace.app;

import static com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.dermatrace.app.util.UiUtils;
import com.dermatrace.app.util.BrandingUtils;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private CredentialManager credentialManager;
    private ExecutorService authExecutor;

    private EditText email;
    private EditText password;
    private MaterialButton btnLogin;
    private MaterialButton btnGoogle;
    private CircularProgressIndicator progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ((TextView)findViewById(R.id.txtLoginBrand)).setText(BrandingUtils.wordmark(this));
        UiUtils.applySystemBars(this, findViewById(R.id.loginRoot));

        auth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);
        authExecutor = Executors.newSingleThreadExecutor();

        email = findViewById(R.id.edtLoginEmail);
        password = findViewById(R.id.edtLoginPassword);
        btnLogin = findViewById(R.id.btnEmailLogin);
        btnGoogle = findViewById(R.id.btnGoogleLogin);
        progress = findViewById(R.id.progressLogin);

        btnLogin.setOnClickListener(v -> signInWithEmail());
        btnGoogle.setOnClickListener(v -> signInWithGoogle());
        ((TextView)findViewById(R.id.txtCreateAccount)).setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
        ((TextView)findViewById(R.id.txtForgotPassword)).setOnClickListener(v ->
                showForgotPasswordDialog());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) openApp();
    }

    private void signInWithEmail() {
        String e = email.getText().toString().trim();
        String p = password.getText().toString();

        if (e.isEmpty()) { email.setError("Enter your email"); return; }
        if (p.isEmpty()) { password.setError("Enter your password"); return; }

        setBusy(true, "Signing in…");
        auth.signInWithEmailAndPassword(e, p).addOnCompleteListener(this, task -> {
            setBusy(false, null);
            if (task.isSuccessful()) {
                openApp();
            } else {
                String message = task.getException() == null ? "Sign-in failed." : task.getException().getLocalizedMessage();
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void signInWithGoogle() {
        setBusy(true, "Opening Google account chooser…");
        try {
            GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .build();

            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build();

            credentialManager.getCredentialAsync(
                    this,
                    request,
                    new CancellationSignal(),
                    authExecutor,
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(@NonNull GetCredentialResponse result) {
                            handleGoogleCredential(result.getCredential());
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException e) {
                            runOnUiThread(() -> {
                                setBusy(false, null);
                                String message = e.getLocalizedMessage();
                                if (message == null || message.trim().isEmpty()) {
                                    message = "Google sign-in was cancelled or no account was available.";
                                }
                                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
            );
        } catch (Exception e) {
            setBusy(false, null);
            Toast.makeText(this,
                    "Google sign-in is not configured yet. Check Firebase setup and google-services.json.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handleGoogleCredential(Credential credential) {
        try {
            if (credential instanceof CustomCredential
                    && TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                CustomCredential customCredential = (CustomCredential) credential;
                GoogleIdTokenCredential googleCredential =
                        GoogleIdTokenCredential.createFrom(customCredential.getData());
                firebaseAuthWithGoogle(googleCredential.getIdToken());
            } else {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    Toast.makeText(this, "The selected credential was not a Google account.", Toast.LENGTH_LONG).show();
                });
            }
        } catch (Exception e) {
            runOnUiThread(() -> {
                setBusy(false, null);
                Toast.makeText(this, "Could not read Google sign-in result: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            setBusy(false, null);
            if (task.isSuccessful()) {
                openApp();
            } else {
                String message = task.getException() == null ? "Firebase Google sign-in failed." : task.getException().getLocalizedMessage();
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showForgotPasswordDialog() {
        EditText input = new EditText(this);
        input.setHint("Email address");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        String prefill = email.getText().toString().trim();
        if (!prefill.isEmpty()) input.setText(prefill);

        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        holder.setPadding(pad, 0, pad, 0);
        holder.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Reset password")
                .setMessage("Firebase will send a password reset link to this email.")
                .setView(holder)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (dialog, which) -> {
                    String e = input.getText().toString().trim();
                    if (e.isEmpty()) {
                        Toast.makeText(this, "Enter an email address first.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    auth.sendPasswordResetEmail(e).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Password reset email sent.", Toast.LENGTH_LONG).show();
                        } else {
                            String msg = task.getException() == null ? "Could not send reset email." : task.getException().getLocalizedMessage();
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    private void setBusy(boolean busy, String message) {
        runOnUiThread(() -> {
            progress.setVisibility(busy ? android.view.View.VISIBLE : android.view.View.GONE);
            btnLogin.setEnabled(!busy);
            btnGoogle.setEnabled(!busy);
            email.setEnabled(!busy);
            password.setEnabled(!busy);
            ((TextView)findViewById(R.id.txtLoginStatus)).setText(message == null ? "" : message);
        });
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (authExecutor != null) authExecutor.shutdownNow();
    }
}
