package com.dermatrace.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.llm.OllamaOverviewManager;
import com.dermatrace.app.model.AnalysisResult;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.model.Prediction;
import com.dermatrace.app.reminder.FollowUpScheduler;
import com.dermatrace.app.util.AppPreferences;
import com.dermatrace.app.util.IntentUtils;
import com.dermatrace.app.util.LesionLabelUtils;
import com.dermatrace.app.util.ReportUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class ResultActivity extends BaseDrawerActivity {
    public static final String EXTRA_IMAGE_PATH = "image_path";
    public static final String EXTRA_RESULT = "analysis_result";
    public static final String EXTRA_LESION_ID = "lesion_id";
    public static final String EXTRA_ASSESSMENT_ID = "assessment_id";
    public static final String EXTRA_DEBUG_RESULT = "debug_result"; // retained for backwards compatibility

    private DermaTraceDbHelper db;
    private AssessmentRecord record;
    private Patient patient;
    private Lesion lesion;
    private LinearLayout technicalPanel;
    private OllamaOverviewManager ollamaManager;
    private boolean overviewGenerationStarted = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_result, R.id.toolbarResult);
        db = new DermaTraceDbHelper(this);
        ollamaManager = new OllamaOverviewManager(this);

        long existingId = getIntent().getLongExtra(EXTRA_ASSESSMENT_ID, -1);

        if (existingId > 0) {
            record = db.getAssessment(existingId);
        } else {
            AnalysisResult result = (AnalysisResult)getIntent().getSerializableExtra(EXTRA_RESULT);
            String path = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
            long lesionId = getIntent().getLongExtra(EXTRA_LESION_ID, -1);

            if (result != null && result.getStatus() == AnalysisResult.Status.COMPLETE) {
                long newPatientId = getIntent().getLongExtra(CameraAnalysisActivity.EXTRA_NEW_PATIENT_ID, -1);
                if (lesionId <= 0 && newPatientId > 0) {
                    lesionId = db.addLesion(newPatientId,
                            getIntent().getStringExtra(CameraAnalysisActivity.EXTRA_NEW_LESION_LABEL),
                            getIntent().getStringExtra(CameraAnalysisActivity.EXTRA_NEW_LESION_BODY),
                            getIntent().getStringExtra(CameraAnalysisActivity.EXTRA_NEW_LESION_NOTES));
                }
                if (lesionId <= 0) lesionId = db.getOrCreateDefaultLesion();
                long id = db.saveAssessment(lesionId, path, result);
                getIntent().putExtra(EXTRA_ASSESSMENT_ID, id);
                record = db.getAssessment(id);
            } else if (result != null) {
                showNonComplete(path, result);
                return;
            }
        }

        if (record == null) {
            Toast.makeText(this, "Saved assessment could not be loaded.", Toast.LENGTH_LONG).show();
            finish(); return;
        }

        lesion = db.getLesion(record.lesionId);
        patient = lesion == null ? null : db.getPatient(lesion.patientId);
        bindRecord();
    }

    @Override protected void onResume() {
        super.onResume();
        if (record != null && record.id > 0) {
            AssessmentRecord fresh = db.getAssessment(record.id);
            if (fresh != null) { record = fresh; bindNote(); bindOverview(false); }
        }
    }

    private void bindRecord() {
        ImageView image = findViewById(R.id.imgResult);
        if (record.imagePath != null) image.setImageBitmap(com.dermatrace.app.util.BitmapUtils.loadSampledOriented(record.imagePath, 1280));

        ((TextView)findViewById(R.id.txtResultStatus)).setText("ASSESSMENT SAVED");
        ((TextView)findViewById(R.id.txtResultContext)).setText(
                (patient == null ? "" : patient.name + " • ") +
                        (lesion == null ? "Saved lesion" : lesion.label + " • " + lesion.bodyLocation));

        String concernTitle;
        String simpleExplanation;
        if (record.triageDisplayLabel != null && record.triageDisplayLabel.contains("HIGHER")) {
            concernTitle = "Higher model concern";
            simpleExplanation = "The AI found visual features that warrant professional assessment.";
        } else if (record.triageDisplayLabel != null && (record.triageDisplayLabel.contains("BORDERLINE") || record.triageDisplayLabel.contains("INCONCLUSIVE"))) {
            concernTitle = "AI result inconclusive";
            simpleExplanation = "The score is close to the model's decision boundary. Clinical review is reasonable if there is any concern.";
        } else {
            concernTitle = "Lower model concern";
            simpleExplanation = "The AI did not detect a strong higher-concern pattern. This does not rule out skin cancer.";
        }
        TextView triageView = findViewById(R.id.txtTriage);
        triageView.setText(concernTitle);
        if (concernTitle.startsWith("Higher")) {
            triageView.setBackgroundResource(R.drawable.bg_status_high);
            triageView.setTextColor(ContextCompat.getColor(this, R.color.dt_danger));
        } else if (concernTitle.contains("inconclusive")) {
            triageView.setBackgroundResource(R.drawable.bg_status_mid);
            triageView.setTextColor(ContextCompat.getColor(this, R.color.dt_warning));
        } else {
            triageView.setBackgroundResource(R.drawable.bg_status_low);
            triageView.setTextColor(ContextCompat.getColor(this, R.color.dt_success));
        }
        ((TextView)findViewById(R.id.txtSimpleExplanation)).setText(simpleExplanation);
        TextView readable = findViewById(R.id.txtOverviewPlainLanguage);
        if (readable != null) readable.setText(
                simpleExplanation + "\n\nThe three patterns above are visual similarities, not confirmed diagnoses. " +
                "The concern score is not a cancer probability. A clinician should assess " +
                "new, changing, bleeding, painful or otherwise concerning lesions.");
        TextView aiSummary = findViewById(R.id.txtAiAnalysisSummary);
        if (aiSummary != null) aiSummary.setText(String.format(Locale.US,
                "Concern category: %s\nModel concern score: %.1f%%\nDecision threshold: %.1f%%\n\n" +
                "The primary triage and detailed visual-pattern models run independently. " +
                "These are experimental decision-support outputs; professional examination is necessary when warranted.",
                concernTitle, record.higherConcernProbability * 100f, record.triageThreshold * 100f));

        TextView quality = findViewById(R.id.txtQuality);
        if (!"GOOD".equalsIgnoreCase(record.qualityLabel)) {
            quality.setText("Usable with caution • " + record.qualityLabel + String.format(Locale.US, " (%.0f%% model confidence)", record.qualityConfidence * 100f));
        } else quality.setText("Image quality suitable for analysis");

        ((TextView)findViewById(R.id.txtSegmentation)).setText(record.segmentationUsable
                ? "Lesion localization usable"
                : "Lesion localization uncertain — morphology should be interpreted cautiously");

        ((TextView)findViewById(R.id.txtTop3)).setText(String.format(Locale.US,
                "1. %s  %.1f%%\n2. %s  %.1f%%\n3. %s  %.1f%%",
                LesionLabelUtils.friendly(record.top1Label), record.top1Probability * 100f,
                LesionLabelUtils.friendly(record.top2Label), record.top2Probability * 100f,
                LesionLabelUtils.friendly(record.top3Label), record.top3Probability * 100f));
        bindPredictionRow(R.id.txtTop1Friendly, R.id.txtTop1Percent, R.id.progressTop1, record.top1Label, record.top1Probability);
        bindPredictionRow(R.id.txtTop2Friendly, R.id.txtTop2Percent, R.id.progressTop2, record.top2Label, record.top2Probability);
        bindPredictionRow(R.id.txtTop3Friendly, R.id.txtTop3Percent, R.id.progressTop3, record.top3Label, record.top3Probability);

        ((TextView)findViewById(R.id.txtClinicalMessage)).setText(record.message);

        ((TextView)findViewById(R.id.txtTechnicalDetails)).setText(String.format(Locale.US,
                "Higher-concern score: %.2f%%\nValidated threshold: %.2f%%\nQuality model: %s (%.2f%%)\nSegmentation status: %s\nMask fraction: %.3f%%\nTop-1 code: %s",
                record.higherConcernProbability * 100f,
                record.triageThreshold * 100f,
                record.qualityLabel, record.qualityConfidence * 100f,
                record.segmentationStatus,
                record.lesionFraction * 100f,
                record.top1Label));

        technicalPanel = findViewById(R.id.panelTechnical);
        technicalPanel.setVisibility(View.GONE);
        ((MaterialButton)findViewById(R.id.btnTechnical)).setOnClickListener(v -> {
            boolean show = technicalPanel.getVisibility() != View.VISIBLE;
            technicalPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            ((MaterialButton)v).setText(show ? "Hide technical details" : "View technical details");
        });

        ((MaterialButton)findViewById(R.id.btnResultActions)).setOnClickListener(this::showActions);
        ((MaterialButton)findViewById(R.id.btnNewAnalysis)).setOnClickListener(v -> startActivity(new Intent(this, NewAnalysisActivity.class)));
        ((MaterialButton)findViewById(R.id.btnHome)).setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });

        bindResultTabs();
        bindDirectActions();
        bindNote();
        bindOverview(true);
    }


    private void bindResultTabs() {
        View overview = findViewById(R.id.resultPanelOverview);
        View ai = findViewById(R.id.resultPanelAiAnalysis);
        View quality = findViewById(R.id.resultPanelQuality);
        View similar = findViewById(R.id.resultPanelSimilar);
        TextView tabOverview = findViewById(R.id.tabResultOverview);
        TextView tabAi = findViewById(R.id.tabResultAi);
        TextView tabQuality = findViewById(R.id.tabResultQuality);
        TextView tabSimilar = findViewById(R.id.tabResultSimilar);
        View indOverview = findViewById(R.id.indicatorOverview);
        View indAi = findViewById(R.id.indicatorAi);
        View indQuality = findViewById(R.id.indicatorQuality);
        View indSimilar = findViewById(R.id.indicatorSimilar);
        if (overview == null) return;

        View.OnClickListener listener = v -> {
            boolean o = v == tabOverview, a = v == tabAi, q = v == tabQuality, s = v == tabSimilar;
            overview.setVisibility(o ? View.VISIBLE : View.GONE);
            ai.setVisibility(a ? View.VISIBLE : View.GONE);
            quality.setVisibility(q ? View.VISIBLE : View.GONE);
            similar.setVisibility(s ? View.VISIBLE : View.GONE);
            int active = ContextCompat.getColor(this, R.color.dt_primary);
            int idle = ContextCompat.getColor(this, R.color.dt_text_muted);
            tabOverview.setTextColor(o ? active : idle); tabAi.setTextColor(a ? active : idle);
            tabQuality.setTextColor(q ? active : idle); tabSimilar.setTextColor(s ? active : idle);
            indOverview.setVisibility(o ? View.VISIBLE : View.INVISIBLE);
            indAi.setVisibility(a ? View.VISIBLE : View.INVISIBLE);
            indQuality.setVisibility(q ? View.VISIBLE : View.INVISIBLE);
            indSimilar.setVisibility(s ? View.VISIBLE : View.INVISIBLE);
        };
        tabOverview.setOnClickListener(listener); tabAi.setOnClickListener(listener);
        tabQuality.setOnClickListener(listener); tabSimilar.setOnClickListener(listener);
        tabOverview.performClick();
    }

    private void bindPredictionRow(int labelId, int percentId, int progressId, String code, float probability) {
        TextView label = findViewById(labelId);
        TextView percent = findViewById(percentId);
        ProgressBar bar = findViewById(progressId);
        if (label != null) label.setText(LesionLabelUtils.friendly(code));
        if (percent != null) percent.setText(String.format(Locale.US, "%.0f%%", probability * 100f));
        if (bar != null) { bar.setMax(1000); bar.setProgress(Math.round(probability * 1000f)); }
    }

    private void bindDirectActions() {
        View save = findViewById(R.id.btnSaveResult);
        View share = findViewById(R.id.btnShareResult);
        View pdf = findViewById(R.id.btnPdfResult);
        if (save != null) save.setOnClickListener(v -> editNote());
        if (share != null) share.setOnClickListener(v -> export(true, true));
        if (pdf != null) pdf.setOnClickListener(v -> export(true, false));
    }

    private void bindOverview(boolean allowAutoGenerate) {
        TextView overview = findViewById(R.id.txtAiOverview);
        TextView status = findViewById(R.id.txtAiOverviewStatus);
        MaterialButton button = findViewById(R.id.btnGenerateOverview);
        CircularProgressIndicator progress = findViewById(R.id.progressAiOverview);

        if (overview == null || status == null || button == null || progress == null || record == null) return;

        String saved = record.aiOverview == null ? "" : record.aiOverview.trim();
        View aiCard = findViewById(R.id.cardAiOverview);
        if (aiCard != null) aiCard.setVisibility(saved.isEmpty() && !AppPreferences.isOllamaEnabled(this) ? View.GONE : View.VISIBLE);
        if (!saved.isEmpty()) {
            progress.setVisibility(View.GONE);
            overview.setVisibility(View.VISIBLE);
            overview.setText(saved);
            String model = record.llmModel == null || record.llmModel.trim().isEmpty()
                    ? "local Ollama model" : record.llmModel.trim();
            status.setText("Generated locally with " + model + " • explanation only");
            button.setText("Regenerate overview");
            button.setEnabled(true);
        } else {
            progress.setVisibility(View.GONE);
            overview.setVisibility(View.VISIBLE);
            overview.setText(AppPreferences.isOllamaEnabled(this)
                    ? "No AI overview has been generated yet."
                    : "Automatic AI overview is disabled in Settings. The saved DermaTrace result is still complete.");
            status.setText("Optional local LLM explanation • the lesion image is not sent to Ollama");
            button.setText("Generate AI overview");
            button.setEnabled(true);

            if (allowAutoGenerate && AppPreferences.isOllamaEnabled(this) && !overviewGenerationStarted) {
                generateOverview(true);
            }
        }

        button.setOnClickListener(v -> generateOverview(false));
    }

    private void generateOverview(boolean automatic) {
        if (record == null || lesion == null || overviewGenerationStarted) return;

        overviewGenerationStarted = true;
        TextView overview = findViewById(R.id.txtAiOverview);
        TextView status = findViewById(R.id.txtAiOverviewStatus);
        MaterialButton button = findViewById(R.id.btnGenerateOverview);
        CircularProgressIndicator progress = findViewById(R.id.progressAiOverview);

        progress.setVisibility(View.VISIBLE);
        overview.setText("Generating a simple explanation from the saved DermaTrace result…");
        status.setText("Connecting to local Ollama on your laptop…");
        button.setEnabled(false);
        button.setText("Generating…");

        ollamaManager.generateOverview(record, lesion, new OllamaOverviewManager.Callback() {
            @Override public void onSuccess(String text, String model) {
                runOnUiThread(() -> {
                    long now = System.currentTimeMillis();
                    db.updateAiOverview(record.id, text, model, now);
                    AssessmentRecord fresh = db.getAssessment(record.id);
                    if (fresh != null) record = fresh;
                    overviewGenerationStarted = false;
                    bindOverview(false);
                    Toast.makeText(ResultActivity.this, "AI overview saved to this assessment.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    overviewGenerationStarted = false;
                    progress.setVisibility(View.GONE);
                    String saved = record.aiOverview == null ? "" : record.aiOverview.trim();
                    if (!saved.isEmpty()) {
                        overview.setText(saved);
                        status.setText("Could not regenerate overview: " + message + " • showing the previously saved overview");
                        button.setText("Retry regeneration");
                    } else {
                        overview.setText("AI overview unavailable. Your DermaTrace result is still saved.");
                        status.setText(message);
                        button.setText("Retry AI overview");
                    }
                    button.setEnabled(true);
                    if (!automatic) Toast.makeText(ResultActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void bindNote() {
        TextView note = findViewById(R.id.txtAssessmentNote);
        if (note != null) {
            String n = record == null ? "" : record.userNotes;
            note.setText(n == null || n.trim().isEmpty() ? "No personal note added." : "Note: " + n.trim());
        }
    }

    private void showNonComplete(String path, AnalysisResult result) {
        if (path != null) {
            ((ImageView)findViewById(R.id.imgResult)).setImageBitmap(com.dermatrace.app.util.BitmapUtils.loadSampledOriented(path, 1280));
            com.dermatrace.app.util.FileUtils.deleteAppImage(this, path);   // unsaved retake: do not keep the photo
        }
        ((TextView)findViewById(R.id.txtResultStatus)).setText(result.getStatus() == AnalysisResult.Status.RETAKE_IMAGE ? "RETAKE REQUIRED" : "ANALYSIS ERROR");
        ((TextView)findViewById(R.id.txtResultContext)).setText("Analysis was not saved");
        ((TextView)findViewById(R.id.txtTriage)).setText(result.getStatus() == AnalysisResult.Status.RETAKE_IMAGE ? "Please take another photo" : "Analysis could not be completed");
        ((TextView)findViewById(R.id.txtSimpleExplanation)).setText(result.getMessage());
        ((TextView)findViewById(R.id.txtQuality)).setText(result.getQualityLabel() + String.format(Locale.US, " • %.0f%% confidence", result.getQualityConfidence() * 100f));
        findViewById(R.id.cardSegmentation).setVisibility(View.GONE);
        findViewById(R.id.cardTop3).setVisibility(View.GONE);
        findViewById(R.id.txtClinicalMessage).setVisibility(View.GONE);
        View ai = findViewById(R.id.cardAiOverview); if (ai != null) ai.setVisibility(View.GONE);
        View note = findViewById(R.id.txtAssessmentNote); if (note != null) note.setVisibility(View.GONE);
        findViewById(R.id.btnTechnical).setVisibility(View.GONE);
        findViewById(R.id.btnResultActions).setVisibility(View.GONE);
        View rowActions = findViewById(R.id.rowResultActions); if (rowActions != null) rowActions.setVisibility(View.GONE);
        View save = findViewById(R.id.btnSaveResult); if (save != null) save.setVisibility(View.GONE);
        View share = findViewById(R.id.btnShareResult); if (share != null) share.setVisibility(View.GONE);
        View pdf = findViewById(R.id.btnPdfResult); if (pdf != null) pdf.setVisibility(View.GONE);
        ((MaterialButton)findViewById(R.id.btnNewAnalysis)).setText("Retake / try again");
        ((MaterialButton)findViewById(R.id.btnNewAnalysis)).setOnClickListener(v -> finish());
        ((MaterialButton)findViewById(R.id.btnHome)).setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
    }

    private void showActions(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.result_actions_menu);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.result_edit_note) { editNote(); return true; }
            if (id == R.id.result_reports) { openReportViewer(); return true; }
            if (id == R.id.result_export_pdf) { export(true, false); return true; }
            if (id == R.id.result_export_txt) { export(false, false); return true; }
            if (id == R.id.result_share) { export(true, true); return true; }
            if (id == R.id.result_compare) {
                Intent i = new Intent(this, ComparisonActivity.class);
                i.putExtra(ComparisonActivity.EXTRA_ASSESSMENT_ID, record.id);
                startActivity(i); return true;
            }
            if (id == R.id.result_nearby) { startActivity(new Intent(this, NearbyCareActivity.class)); return true; }
            if (id == R.id.result_reminder) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
                }
                if (lesion != null) {
                    int days = AppPreferences.getDefaultFollowUpDays(this);
                    FollowUpScheduler.scheduleDays(this, lesion.id, lesion.label, days);
                    Toast.makeText(this, FollowUpScheduler.scheduledMessage(this, days), Toast.LENGTH_LONG).show();
                }
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void editNote() {
        EditText input = new EditText(this);
        input.setText(record.userNotes == null ? "" : record.userNotes);
        input.setHint("Optional note about this assessment");
        input.setMaxLines(6);
        new AlertDialog.Builder(this)
                .setTitle("Edit assessment note")
                .setView(input)
                .setPositiveButton("Update", (d, w) -> {
                    db.updateAssessmentNotes(record.id, input.getText().toString());
                    record = db.getAssessment(record.id);
                    bindNote();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openReportViewer() {
        Intent i = new Intent(this, ReportViewerActivity.class);
        i.putExtra(ReportViewerActivity.EXTRA_ASSESSMENT_ID, record.id);
        startActivity(i);
    }

    private void export(boolean pdf, boolean share) {
        try {
            File file = pdf ? ReportUtils.createPdf(this, record, patient, lesion) : ReportUtils.createTxt(this, record, patient, lesion);
            if (share) IntentUtils.shareFile(this, file, pdf ? "application/pdf" : "text/plain");
            else Toast.makeText(this, "Saved report:\n" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Report failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
