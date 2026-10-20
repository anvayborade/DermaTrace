package com.dermatrace.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.llm.OllamaOverviewManager;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.reminder.FollowUpScheduler;
import com.dermatrace.app.util.AppPreferences;
import com.dermatrace.app.util.AuthUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends BaseDrawerActivity {

    private EditText ollamaUrl;
    private EditText ollamaModel;
    private SwitchMaterial ollamaEnabled;
    private TextView ollamaStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_settings, R.id.toolbarSettings);

        EditText name = findViewById(R.id.edtClinicianName);
        EditText phone = findViewById(R.id.edtClinicianPhone);
        EditText email = findViewById(R.id.edtClinicianEmail);
        EditText days = findViewById(R.id.edtFollowUpDays);
        SwitchMaterial debug = findViewById(R.id.switchDebugImport);

        ollamaEnabled = findViewById(R.id.switchOllamaEnabled);
        ollamaUrl = findViewById(R.id.edtOllamaUrl);
        ollamaModel = findViewById(R.id.edtOllamaModel);
        ollamaStatus = findViewById(R.id.txtOllamaStatus);

        name.setText(AppPreferences.getClinicianName(this));
        phone.setText(AppPreferences.getClinicianPhone(this));
        email.setText(AppPreferences.getClinicianEmail(this));
        days.setText(String.valueOf(AppPreferences.getDefaultFollowUpDays(this)));
        debug.setChecked(AppPreferences.isDebugImportEnabled(this));

        ollamaEnabled.setChecked(AppPreferences.isOllamaEnabled(this));
        ollamaUrl.setText(AppPreferences.getOllamaUrl(this));
        ollamaModel.setText(AppPreferences.getOllamaModel(this));

        ((MaterialButton)findViewById(R.id.btnSaveSettings)).setOnClickListener(v -> {
            int follow = -1;
            try { follow = Integer.parseInt(days.getText().toString().trim()); }
            catch (Exception ignored) {}
            if (follow < 1 || follow > 365) { days.setError("Enter 1 to 365 days"); return; }

            AppPreferences.saveClinician(
                    this,
                    name.getText().toString(),
                    phone.getText().toString(),
                    email.getText().toString()
            );

            AppPreferences.setDefaultFollowUpDays(this, follow);
            AppPreferences.setDebugImportEnabled(this, debug.isChecked());
            saveOllamaFields();

            Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show();
        });

        ((MaterialButton)findViewById(R.id.btnTestReminder)).setOnClickListener(v -> {
            requestNotifications();
            DermaTraceDbHelper db = new DermaTraceDbHelper(this);
            long lesionId = db.getOrCreateDefaultLesion();
            Lesion l = db.getLesion(lesionId);
            FollowUpScheduler.scheduleMinutes(
                    this,
                    lesionId,
                    l == null ? "test lesion" : l.label,
                    1
            );
            Toast.makeText(
                    this,
                    "Test reminder queued. WorkManager timing is approximate; wait about a minute or a little longer.",
                    Toast.LENGTH_LONG
            ).show();
        });

        ((MaterialButton)findViewById(R.id.btnTestOllama)).setOnClickListener(v -> testOllama());

        FirebaseUser account = FirebaseAuth.getInstance().getCurrentUser();
        TextView accountName = findViewById(R.id.txtSettingsAccountName);
        TextView accountEmail = findViewById(R.id.txtSettingsAccountEmail);
        TextView accountProvider = findViewById(R.id.txtSettingsAccountProvider);

        if (account != null) {
            accountName.setText(AuthUtils.displayName(account));
            accountEmail.setText(account.getEmail() == null ? "No email available" : account.getEmail());
            accountProvider.setText("Signed in with " + AuthUtils.providerLabel(account));
        }

        ((MaterialButton)findViewById(R.id.btnSettingsSignOut)).setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Sign out?")
                        .setMessage("Your local records stay on this device.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Sign out", (dialog, which) -> AuthUtils.signOut(this))
                        .show()
        );
    }

    private void saveOllamaFields() {
        AppPreferences.saveOllama(
                this,
                ollamaEnabled.isChecked(),
                ollamaUrl.getText().toString(),
                ollamaModel.getText().toString()
        );
    }

    private void testOllama() {
        saveOllamaFields();
        ollamaStatus.setText("Testing local Ollama connection…");
        MaterialButton button = findViewById(R.id.btnTestOllama);
        button.setEnabled(false);

        OllamaOverviewManager manager = new OllamaOverviewManager(this);
        manager.testConnection(new OllamaOverviewManager.ConnectionCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    ollamaStatus.setText(message);
                    Toast.makeText(SettingsActivity.this, "Ollama connection successful.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    ollamaStatus.setText("Connection failed: " + message);
                    Toast.makeText(SettingsActivity.this, "Could not connect to Ollama.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    90
            );
        }
    }
}
