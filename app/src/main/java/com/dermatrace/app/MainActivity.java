package com.dermatrace.app;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.util.DateUtils;

import java.util.Calendar;
import java.util.List;

import com.dermatrace.app.util.AuthUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends BaseDrawerActivity {

    private DermaTraceDbHelper db;
    private LinearLayout latestContainer;
    private TextView latestEmpty;
    private HorizontalScrollView latestScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_main, R.id.toolbar);

        db = new DermaTraceDbHelper(this);
        latestContainer = findViewById(R.id.latestContainer);
        latestEmpty = findViewById(R.id.txtLatestEmpty);
        latestScroll = findViewById(R.id.latestScroll);

        View seeAll = findViewById(R.id.btnSeeAllScans);
        if (seeAll != null) seeAll.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        setGreeting();
        setDashboardSummary();
        bindCard(R.id.cardNewAnalysis, NewAnalysisActivity.class);
        bindCard(R.id.cardPatients, PatientsActivity.class);
        bindCard(R.id.cardHistory, HistoryActivity.class);
        bindCard(R.id.cardReports, ReportsActivity.class);
        bindCard(R.id.cardCompare, CompareHubActivity.class);
        bindCard(R.id.cardFollowUps, FollowUpsActivity.class);
        refreshLatestScans();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) { refreshLatestScans(); setDashboardSummary(); }
    }

    private void setGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) greeting = "Good morning";
        else if (hour < 17) greeting = "Good afternoon";
        else greeting = "Good evening";
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        ((TextView)findViewById(R.id.txtGreeting)).setText(greeting + ", " + AuthUtils.firstName(user));
    }


    private void setDashboardSummary() {
        if (db == null) return;
        List<AssessmentRecord> all = db.getAssessments();
        List<Patient> patients = db.getPatients();
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH);
        int year = now.get(Calendar.YEAR);
        int thisMonth = 0;
        for (AssessmentRecord a : all) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(a.createdAt);
            if (c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year) thisMonth++;
        }
        TextView scans = findViewById(R.id.txtDashboardTotalScans);
        TextView monthText = findViewById(R.id.txtDashboardMonthScans);
        TextView patientCount = findViewById(R.id.txtDashboardPatientCount);
        if (scans != null) scans.setText(String.valueOf(all.size()));
        if (monthText != null) monthText.setText("+" + thisMonth + " this month");
        if (patientCount != null) patientCount.setText(String.valueOf(patients.size()));
    }

    private void bindCard(int id, Class<?> destination) {
        View card = findViewById(id);
        card.setOnClickListener(v -> startActivity(new Intent(this, destination)));
    }

    private void refreshLatestScans() {
        latestContainer.removeAllViews();
        List<AssessmentRecord> all = db.getAssessments();
        int count = Math.min(3, all.size());
        latestEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        latestScroll.setVisibility(count == 0 ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < count; i++) {
            AssessmentRecord a = all.get(i);
            Lesion l = db.getLesion(a.lesionId);
            Patient p = l == null ? null : db.getPatient(l.patientId);

            View card = inflater.inflate(R.layout.item_latest_scan, latestContainer, false);
            ImageView image = card.findViewById(R.id.imgLatestScan);
            TextView title = card.findViewById(R.id.txtLatestTitle);
            TextView meta = card.findViewById(R.id.txtLatestMeta);
            TextView concern = card.findViewById(R.id.txtLatestConcern);

            if (a.imagePath != null && !a.imagePath.isEmpty()) {
                image.setImageBitmap(com.dermatrace.app.util.BitmapUtils.loadSampledOriented(a.imagePath, 512));
            } else {
                image.setImageResource(R.mipmap.ic_launcher);
            }

            title.setText(l == null ? "Saved lesion" : l.label);
            String patient = p == null ? "Local record" : p.name;
            meta.setText(patient + " • " + DateUtils.format(a.createdAt));
            concern.setText(friendlyConcern(a.triageDisplayLabel));
            String label = a.triageDisplayLabel == null ? "" : a.triageDisplayLabel;
            if (label.contains("HIGHER")) {
                concern.setBackgroundResource(R.drawable.bg_status_high);
                concern.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dt_danger));
            } else if (label.contains("BORDERLINE") || label.contains("INCONCLUSIVE")) {
                concern.setBackgroundResource(R.drawable.bg_status_mid);
                concern.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dt_warning));
            } else {
                concern.setBackgroundResource(R.drawable.bg_status_low);
                concern.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dt_success));
            }

            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, ResultActivity.class);
                intent.putExtra(ResultActivity.EXTRA_ASSESSMENT_ID, a.id);
                startActivity(intent);
            });
            latestContainer.addView(card);
        }
        latestScroll.post(() -> latestScroll.scrollTo(0, 0));
    }

    private String friendlyConcern(String value) {
        if (value == null) return "AI assessment";
        if (value.contains("HIGHER")) return "Higher model concern";
        if (value.contains("BORDERLINE") || value.contains("INCONCLUSIVE")) return "AI result inconclusive";
        return "Lower model concern";
    }
}
