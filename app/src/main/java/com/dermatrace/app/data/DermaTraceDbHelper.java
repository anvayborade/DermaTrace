package com.dermatrace.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.dermatrace.app.model.AnalysisResult;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.FollowUpRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.model.Prediction;
import com.dermatrace.app.util.FileUtils;

import java.util.ArrayList;
import java.util.List;

public class DermaTraceDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "dermatrace.db";
    private static final int DB_VERSION = 4;

    private final Context appContext;

    public DermaTraceDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE patients (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "age TEXT," +
                "sex TEXT," +
                "notes TEXT," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE lesions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "patient_id INTEGER NOT NULL," +
                "label TEXT NOT NULL," +
                "body_location TEXT," +
                "notes TEXT," +
                "created_at INTEGER NOT NULL," +
                "FOREIGN KEY(patient_id) REFERENCES patients(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE assessments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "lesion_id INTEGER NOT NULL," +
                "image_path TEXT," +
                "created_at INTEGER NOT NULL," +
                "quality_label TEXT," +
                "quality_conf REAL," +
                "seg_usable INTEGER," +
                "seg_status TEXT," +
                "lesion_fraction REAL," +
                "triage_label TEXT," +
                "triage_display TEXT," +
                "higher_score REAL," +
                "triage_threshold REAL," +
                "top1_label TEXT," +
                "top1_prob REAL," +
                "top2_label TEXT," +
                "top2_prob REAL," +
                "top3_label TEXT," +
                "top3_prob REAL," +
                "message TEXT," +
                "user_notes TEXT DEFAULT ''," +
                "ai_overview TEXT DEFAULT ''," +
                "llm_model TEXT DEFAULT ''," +
                "llm_generated_at INTEGER DEFAULT 0," +
                "FOREIGN KEY(lesion_id) REFERENCES lesions(id) ON DELETE CASCADE)");

        createFollowUpsTable(db);
    }

    private void createFollowUpsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS followups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "lesion_id INTEGER NOT NULL UNIQUE," +
                "due_at INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "active INTEGER NOT NULL DEFAULT 1," +
                "FOREIGN KEY(lesion_id) REFERENCES lesions(id) ON DELETE CASCADE)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE assessments ADD COLUMN user_notes TEXT DEFAULT ''"); }
            catch (Exception ignored) {}
        }
        if (oldVersion < 3) {
            createFollowUpsTable(db);
        }
        if (oldVersion < 4) {
            try { db.execSQL("ALTER TABLE assessments ADD COLUMN ai_overview TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE assessments ADD COLUMN llm_model TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE assessments ADD COLUMN llm_generated_at INTEGER DEFAULT 0"); } catch (Exception ignored) {}
        }
    }

    // -------------------- CREATE --------------------

    public long addPatient(String name, String age, String sex, String notes) {
        ContentValues v = new ContentValues();
        v.put("name", clean(name, "My profile"));
        v.put("age", clean(age, ""));
        v.put("sex", clean(sex, ""));
        v.put("notes", clean(notes, ""));
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("patients", null, v);
    }

    public long findOrCreatePatientByName(String name) {
        String finalName = clean(name, "My profile");
        try (Cursor c = getReadableDatabase().query(
                "patients", new String[]{"id"}, "name = ?", new String[]{finalName},
                null, null, "id ASC", "1")) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return addPatient(finalName, "", "", "");
    }

    public long addLesion(long patientId, String label, String bodyLocation, String notes) {
        ContentValues v = new ContentValues();
        v.put("patient_id", patientId);
        v.put("label", clean(label, "Skin lesion"));
        v.put("body_location", clean(bodyLocation, "Not specified"));
        v.put("notes", clean(notes, ""));
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("lesions", null, v);
    }

    public long getOrCreateDefaultLesion() {
        long patientId = findOrCreatePatientByName("My profile");
        try (Cursor c = getReadableDatabase().query(
                "lesions", new String[]{"id"}, "patient_id = ? AND label = ?",
                new String[]{String.valueOf(patientId), "Unassigned lesion"},
                null, null, "id ASC", "1")) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return addLesion(patientId, "Unassigned lesion", "Not specified", "Created automatically for quick analyses.");
    }

    public long saveAssessment(long lesionId, String imagePath, AnalysisResult result) {
        ContentValues v = new ContentValues();
        v.put("lesion_id", lesionId);
        v.put("image_path", imagePath);
        v.put("created_at", System.currentTimeMillis());
        v.put("quality_label", result.getQualityLabel());
        v.put("quality_conf", result.getQualityConfidence());
        v.put("seg_usable", result.isSegmentationUsable() ? 1 : 0);
        v.put("seg_status", result.getSegmentationStatus());
        v.put("lesion_fraction", result.getLesionFraction());
        v.put("triage_label", result.getTriageLabel());
        v.put("triage_display", result.getTriageDisplayLabel());
        v.put("higher_score", result.getHigherConcernProbability());
        v.put("triage_threshold", result.getTriageThreshold());
        v.put("message", result.getMessage());
        v.put("user_notes", "");

        List<Prediction> p = result.getTop3();
        if (p.size() > 0) { v.put("top1_label", p.get(0).getLabel()); v.put("top1_prob", p.get(0).getProbability()); }
        if (p.size() > 1) { v.put("top2_label", p.get(1).getLabel()); v.put("top2_prob", p.get(1).getProbability()); }
        if (p.size() > 2) { v.put("top3_label", p.get(2).getLabel()); v.put("top3_prob", p.get(2).getProbability()); }

        return getWritableDatabase().insert("assessments", null, v);
    }

    // -------------------- READ --------------------

    public List<Patient> getPatients() {
        ArrayList<Patient> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("patients", null, null, null, null, null, "created_at DESC")) {
            while (c.moveToNext()) out.add(patientFromCursor(c));
        }
        return out;
    }

    public Patient getPatient(long id) {
        try (Cursor c = getReadableDatabase().query("patients", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? patientFromCursor(c) : null;
        }
    }

    public List<Lesion> getLesions() {
        ArrayList<Lesion> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("lesions", null, null, null, null, null, "created_at DESC")) {
            while (c.moveToNext()) out.add(lesionFromCursor(c));
        }
        return out;
    }

    public List<Lesion> getLesionsForPatient(long patientId) {
        ArrayList<Lesion> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("lesions", null, "patient_id = ?", new String[]{String.valueOf(patientId)}, null, null, "created_at DESC")) {
            while (c.moveToNext()) out.add(lesionFromCursor(c));
        }
        return out;
    }

    public List<Lesion> getComparableLesions() {
        ArrayList<Lesion> out = new ArrayList<>();
        String sql = "SELECT l.* FROM lesions l WHERE (SELECT COUNT(*) FROM assessments a WHERE a.lesion_id=l.id) >= 2 ORDER BY l.created_at DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) out.add(lesionFromCursor(c));
        }
        return out;
    }

    public Lesion getLesion(long id) {
        try (Cursor c = getReadableDatabase().query("lesions", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? lesionFromCursor(c) : null;
        }
    }

    public List<AssessmentRecord> getAssessments() {
        ArrayList<AssessmentRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("assessments", null, null, null, null, null, "created_at DESC")) {
            while (c.moveToNext()) out.add(assessmentFromCursor(c));
        }
        return out;
    }

    public List<AssessmentRecord> getAssessmentsForLesion(long lesionId) {
        ArrayList<AssessmentRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("assessments", null, "lesion_id = ?", new String[]{String.valueOf(lesionId)}, null, null, "created_at DESC")) {
            while (c.moveToNext()) out.add(assessmentFromCursor(c));
        }
        return out;
    }

    public AssessmentRecord getAssessment(long id) {
        try (Cursor c = getReadableDatabase().query("assessments", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? assessmentFromCursor(c) : null;
        }
    }

    public AssessmentRecord getLatestAssessmentForLesion(long lesionId) {
        try (Cursor c = getReadableDatabase().query("assessments", null, "lesion_id = ?", new String[]{String.valueOf(lesionId)}, null, null, "created_at DESC", "1")) {
            return c.moveToFirst() ? assessmentFromCursor(c) : null;
        }
    }

    public AssessmentRecord getPreviousAssessment(long lesionId, long currentAssessmentId) {
        AssessmentRecord current = getAssessment(currentAssessmentId);
        if (current == null) return null;
        try (Cursor c = getReadableDatabase().query(
                "assessments", null, "lesion_id = ? AND created_at < ?",
                new String[]{String.valueOf(lesionId), String.valueOf(current.createdAt)},
                null, null, "created_at DESC", "1")) {
            return c.moveToFirst() ? assessmentFromCursor(c) : null;
        }
    }

    public AssessmentRecord[] getLatestTwoAssessments(long lesionId) {
        AssessmentRecord[] result = new AssessmentRecord[2];
        try (Cursor c = getReadableDatabase().query("assessments", null, "lesion_id = ?", new String[]{String.valueOf(lesionId)}, null, null, "created_at DESC", "2")) {
            if (c.moveToFirst()) result[0] = assessmentFromCursor(c);
            if (c.moveToNext()) result[1] = assessmentFromCursor(c);
        }
        return result;
    }

    public int countAssessmentsForLesion(long lesionId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM assessments WHERE lesion_id = ?", new String[]{String.valueOf(lesionId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public List<FollowUpRecord> getActiveFollowUps() {
        ArrayList<FollowUpRecord> out = new ArrayList<>();
        String sql = "SELECT f.id,f.lesion_id,f.due_at,f.created_at,f.active,l.label,p.name " +
                "FROM followups f JOIN lesions l ON l.id=f.lesion_id JOIN patients p ON p.id=l.patient_id " +
                "WHERE f.active=1 ORDER BY f.due_at ASC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                FollowUpRecord f = new FollowUpRecord();
                f.id = c.getLong(0);
                f.lesionId = c.getLong(1);
                f.dueAt = c.getLong(2);
                f.createdAt = c.getLong(3);
                f.active = c.getInt(4) == 1;
                f.lesionLabel = c.getString(5);
                f.patientName = c.getString(6);
                out.add(f);
            }
        }
        return out;
    }

    // -------------------- UPDATE --------------------

    public int updatePatient(long id, String name, String age, String sex, String notes) {
        ContentValues v = new ContentValues();
        v.put("name", clean(name, "My profile"));
        v.put("age", clean(age, ""));
        v.put("sex", clean(sex, ""));
        v.put("notes", clean(notes, ""));
        return getWritableDatabase().update("patients", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public int updateLesion(long id, String label, String bodyLocation, String notes) {
        ContentValues v = new ContentValues();
        v.put("label", clean(label, "Skin lesion"));
        v.put("body_location", clean(bodyLocation, "Not specified"));
        v.put("notes", clean(notes, ""));
        return getWritableDatabase().update("lesions", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public int updateAssessmentNotes(long id, String notes) {
        ContentValues v = new ContentValues();
        v.put("user_notes", clean(notes, ""));
        return getWritableDatabase().update("assessments", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public int updateAiOverview(long id, String overview, String model, long generatedAt) {
        ContentValues v = new ContentValues();
        v.put("ai_overview", clean(overview, ""));
        v.put("llm_model", clean(model, ""));
        v.put("llm_generated_at", generatedAt);
        return getWritableDatabase().update("assessments", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public void upsertFollowUp(long lesionId, long dueAt) {
        ContentValues v = new ContentValues();
        v.put("lesion_id", lesionId);
        v.put("due_at", dueAt);
        v.put("created_at", System.currentTimeMillis());
        v.put("active", 1);
        getWritableDatabase().insertWithOnConflict("followups", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void markFollowUpComplete(long lesionId) {
        ContentValues v = new ContentValues();
        v.put("active", 0);
        getWritableDatabase().update("followups", v, "lesion_id = ?", new String[]{String.valueOf(lesionId)});
    }

    // -------------------- DELETE --------------------

    public void deleteAssessment(long id) {
        AssessmentRecord a = getAssessment(id);
        getWritableDatabase().delete("assessments", "id = ?", new String[]{String.valueOf(id)});
        if (a != null) deleteImageIfUnused(a.imagePath);
    }

    public void deleteLesion(long id) {
        List<String> paths = imagePaths("SELECT image_path FROM assessments WHERE lesion_id = ?", id);
        getWritableDatabase().delete("lesions", "id = ?", new String[]{String.valueOf(id)});
        for (String p : paths) deleteImageIfUnused(p);
    }

    public void deletePatient(long id) {
        List<String> paths = imagePaths("SELECT image_path FROM assessments WHERE lesion_id IN (SELECT id FROM lesions WHERE patient_id = ?)", id);
        getWritableDatabase().delete("patients", "id = ?", new String[]{String.valueOf(id)});
        for (String p : paths) deleteImageIfUnused(p);
    }

    private List<String> imagePaths(String sql, long id) {
        ArrayList<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(id)})) {
            while (c.moveToNext()) if (!c.isNull(0)) out.add(c.getString(0));
        }
        return out;
    }

    /** Removes the stored photo once no assessment refers to it any more. */
    private void deleteImageIfUnused(String path) {
        if (path == null || path.isEmpty()) return;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM assessments WHERE image_path = ?", new String[]{path})) {
            if (c.moveToFirst() && c.getInt(0) > 0) return;
        }
        FileUtils.deleteAppImage(appContext, path);
    }

    public void deleteFollowUp(long lesionId) {
        getWritableDatabase().delete("followups", "lesion_id = ?", new String[]{String.valueOf(lesionId)});
    }

    private Patient patientFromCursor(Cursor c) {
        Patient p = new Patient();
        p.id = getLong(c, "id");
        p.name = getString(c, "name");
        p.age = getString(c, "age");
        p.sex = getString(c, "sex");
        p.notes = getString(c, "notes");
        p.createdAt = getLong(c, "created_at");
        return p;
    }

    private Lesion lesionFromCursor(Cursor c) {
        Lesion l = new Lesion();
        l.id = getLong(c, "id");
        l.patientId = getLong(c, "patient_id");
        l.label = getString(c, "label");
        l.bodyLocation = getString(c, "body_location");
        l.notes = getString(c, "notes");
        l.createdAt = getLong(c, "created_at");
        return l;
    }

    private AssessmentRecord assessmentFromCursor(Cursor c) {
        AssessmentRecord a = new AssessmentRecord();
        a.id = getLong(c, "id");
        a.lesionId = getLong(c, "lesion_id");
        a.imagePath = getString(c, "image_path");
        a.createdAt = getLong(c, "created_at");
        a.qualityLabel = getString(c, "quality_label");
        a.qualityConfidence = getFloat(c, "quality_conf");
        a.segmentationUsable = getInt(c, "seg_usable") == 1;
        a.segmentationStatus = getString(c, "seg_status");
        a.lesionFraction = getFloat(c, "lesion_fraction");
        a.triageLabel = getString(c, "triage_label");
        a.triageDisplayLabel = getString(c, "triage_display");
        a.higherConcernProbability = getFloat(c, "higher_score");
        a.triageThreshold = getFloat(c, "triage_threshold");
        a.top1Label = getString(c, "top1_label");
        a.top1Probability = getFloat(c, "top1_prob");
        a.top2Label = getString(c, "top2_label");
        a.top2Probability = getFloat(c, "top2_prob");
        a.top3Label = getString(c, "top3_label");
        a.top3Probability = getFloat(c, "top3_prob");
        a.message = getString(c, "message");
        a.userNotes = hasColumn(c, "user_notes") ? getString(c, "user_notes") : "";
        a.aiOverview = hasColumn(c, "ai_overview") ? getString(c, "ai_overview") : "";
        a.llmModel = hasColumn(c, "llm_model") ? getString(c, "llm_model") : "";
        a.llmGeneratedAt = hasColumn(c, "llm_generated_at") ? getLong(c, "llm_generated_at") : 0L;
        return a;
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static boolean hasColumn(Cursor c, String name) { return c.getColumnIndex(name) >= 0; }
    private static int idx(Cursor c, String name) { return c.getColumnIndexOrThrow(name); }
    private static String getString(Cursor c, String name) { String v = c.getString(idx(c, name)); return v == null ? "" : v; }
    private static long getLong(Cursor c, String name) { return c.getLong(idx(c, name)); }
    private static int getInt(Cursor c, String name) { return c.getInt(idx(c, name)); }
    private static float getFloat(Cursor c, String name) { return c.getFloat(idx(c, name)); }
}
