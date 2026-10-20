package com.dermatrace.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.dermatrace.app.util.AppPreferences;
import com.dermatrace.app.util.IntentUtils;
import com.google.android.material.button.MaterialButton;

public class NearbyCareActivity extends BaseDrawerActivity {

    private TextView clinician;

    private final ActivityResultLauncher<Intent> contactPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                try (Cursor c = getContentResolver().query(
                        uri,
                        new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        String name = c.getString(0);
                        String phone = c.getString(1);
                        AppPreferences.saveClinician(this, name, phone, AppPreferences.getClinicianEmail(this));
                        refreshClinician();
                        Toast.makeText(this, "Clinician contact saved.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_nearby_care, R.id.toolbarNearbyCare);
        clinician = findViewById(R.id.txtSavedClinician);

        ((MaterialButton)findViewById(R.id.btnFindDermatologist)).setOnClickListener(v -> IntentUtils.openMapsSearch(this, "dermatologist"));
        ((MaterialButton)findViewById(R.id.btnFindSkinClinic)).setOnClickListener(v -> IntentUtils.openMapsSearch(this, "skin cancer clinic"));
        ((MaterialButton)findViewById(R.id.btnCallClinician)).setOnClickListener(v -> IntentUtils.dial(this, AppPreferences.getClinicianPhone(this)));
        ((MaterialButton)findViewById(R.id.btnEmailClinician)).setOnClickListener(v -> IntentUtils.email(this, AppPreferences.getClinicianEmail(this), "DermaTrace assessment follow-up"));
        ((MaterialButton)findViewById(R.id.btnChooseContact)).setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            contactPicker.launch(pick);
        });
        ((MaterialButton)findViewById(R.id.btnCareSettings)).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        refreshClinician();
    }

    @Override protected void onResume() { super.onResume(); refreshClinician(); }

    private void refreshClinician() {
        if (clinician == null) return;
        String name = AppPreferences.getClinicianName(this);
        String phone = AppPreferences.getClinicianPhone(this);
        String email = AppPreferences.getClinicianEmail(this);
        if (name.isEmpty() && phone.isEmpty() && email.isEmpty()) {
            clinician.setText("No clinician contact saved yet.");
        } else {
            clinician.setText((name.isEmpty() ? "Saved clinician" : name) +
                    (phone.isEmpty() ? "" : "\n" + phone) +
                    (email.isEmpty() ? "" : "\n" + email));
        }
    }
}
