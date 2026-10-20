package com.dermatrace.app;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;


import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.util.DateUtils;
import com.dermatrace.app.util.LesionLabelUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

public class ComparisonActivity extends BaseDrawerActivity {
    public static final String EXTRA_ASSESSMENT_ID = "assessment_id";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_comparison, R.id.toolbarComparison);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarComparison);

        long currentId = getIntent().getLongExtra(EXTRA_ASSESSMENT_ID, -1);
        DermaTraceDbHelper db = new DermaTraceDbHelper(this);
        AssessmentRecord current = db.getAssessment(currentId);
        if (current == null) { finish(); return; }
        AssessmentRecord previous = db.getPreviousAssessment(current.lesionId, current.id);
        Lesion lesion = db.getLesion(current.lesionId);
        toolbar.setTitle(lesion == null ? "Compare scans" : "Compare • " + lesion.label);

        TextView note = findViewById(R.id.txtComparisonNote);
        if (previous == null) {
            note.setText("There is no earlier scan for this lesion yet. Take another follow-up photo, then compare again.");
            return;
        }

        ImageView oldImage = findViewById(R.id.imgComparisonOld);
        ImageView newImage = findViewById(R.id.imgComparisonNew);
        if (previous.imagePath != null) oldImage.setImageBitmap(com.dermatrace.app.util.BitmapUtils.loadSampledOriented(previous.imagePath, 1024));
        if (current.imagePath != null) newImage.setImageBitmap(com.dermatrace.app.util.BitmapUtils.loadSampledOriented(current.imagePath, 1024));

        ((TextView)findViewById(R.id.txtComparisonOldDate)).setText("Previous\n" + DateUtils.format(previous.createdAt));
        ((TextView)findViewById(R.id.txtComparisonNewDate)).setText("Current\n" + DateUtils.format(current.createdAt));

        float scoreDelta = (current.higherConcernProbability - previous.higherConcernProbability) * 100f;
        float areaDelta = (current.lesionFraction - previous.lesionFraction) * 100f;

        ((TextView)findViewById(R.id.txtComparisonConcern)).setText(String.format(
                Locale.US,
                "Concern score: %.1f%% → %.1f%%  (%+.1f points)",
                previous.higherConcernProbability * 100f,
                current.higherConcernProbability * 100f,
                scoreDelta));

        ((TextView)findViewById(R.id.txtComparisonArea)).setText(String.format(
                Locale.US,
                "Segmentation area: %.2f%% → %.2f%% of frame  (%+.2f points)",
                previous.lesionFraction * 100f,
                current.lesionFraction * 100f,
                areaDelta));

        ((TextView)findViewById(R.id.txtComparisonPattern)).setText(
                "Top visual pattern: " + LesionLabelUtils.friendly(previous.top1Label) +
                        " → " + LesionLabelUtils.friendly(current.top1Label));

        String summary;
        if (Math.abs(scoreDelta) < 5f) summary = "The AI concern score is broadly similar between the two captures.";
        else if (scoreDelta > 0) summary = "The AI concern score is higher on the newer capture.";
        else summary = "The AI concern score is lower on the newer capture.";
        note.setText(summary + " Differences can also come from lighting, angle, distance, focus, or framing. This comparison is not a clinical measurement of tumour growth.");
    }
}
