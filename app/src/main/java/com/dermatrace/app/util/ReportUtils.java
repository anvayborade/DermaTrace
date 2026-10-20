package com.dermatrace.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.dermatrace.app.R;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ReportUtils {
    private ReportUtils() {}

    // ------------------------------------------------------------------ TXT

    public static File createTxt(Context context, AssessmentRecord a, Patient p, Lesion l) throws Exception {
        File dir = reportDir(context);
        File file = new File(dir, reportBaseName(a, p) + ".txt");
        try (FileWriter w = new FileWriter(file, false)) {
            w.write("DERMATRACE ASSESSMENT REPORT\n\n");
            w.write("Research / educational decision-support prototype. Not a diagnosis.\n\n");
            w.write("Patient: " + safe(p == null ? "" : p.name) + "\n");
            w.write("Lesion: " + safe(l == null ? "" : l.label) + "\n");
            w.write("Body location: " + safe(l == null ? "" : l.bodyLocation) + "\n");
            w.write("Assessment date: " + DateUtils.format(a.createdAt) + "\n");
            if (a.userNotes != null && !a.userNotes.trim().isEmpty()) w.write("User note: " + a.userNotes.trim() + "\n");
            w.write("\n");
            w.write("AI concern result: " + friendlyConcern(a.triageDisplayLabel) + "\n");
            w.write(String.format(Locale.US, "Higher-concern score: %.1f%%\n", a.higherConcernProbability * 100f));
            w.write("Image quality: " + a.qualityLabel + String.format(Locale.US, " (%.1f%% confidence)\n", a.qualityConfidence * 100f));
            w.write("Lesion localization: " + a.segmentationStatus + String.format(Locale.US, " (mask %.2f%% of frame)\n\n", a.lesionFraction * 100f));
            w.write("AI VISUAL RESEMBLANCE (not diagnosis)\n");
            w.write(String.format(Locale.US, "1. %s — %.1f%%\n", LesionLabelUtils.friendly(a.top1Label), a.top1Probability * 100f));
            w.write(String.format(Locale.US, "2. %s — %.1f%%\n", LesionLabelUtils.friendly(a.top2Label), a.top2Probability * 100f));
            w.write(String.format(Locale.US, "3. %s — %.1f%%\n\n", LesionLabelUtils.friendly(a.top3Label), a.top3Probability * 100f));
            w.write("Guidance: " + safe(a.message) + "\n\n");
            w.write("LOCAL AI OVERVIEW (generated explanation, not diagnosis)\n");
            if (a.aiOverview != null && !a.aiOverview.trim().isEmpty()) {
                w.write(a.aiOverview.trim() + "\n");
                if (a.llmModel != null && !a.llmModel.trim().isEmpty()) {
                    w.write("Generated locally with: " + a.llmModel.trim() + "\n");
                }
            } else {
                w.write("No local AI overview has been generated for this assessment.\n");
            }
            w.write("\nIMPORTANT: A lower model concern result does not exclude skin cancer. New, changing, bleeding, painful, itchy, or otherwise concerning lesions require professional clinical review. Histopathology remains the reference standard when indicated.\n");
        }
        return file;
    }

    // ------------------------------------------------------------------ PDF

    /** File name pattern: DermaTrace_PatientName_yyyy-MM-dd_Report */
    public static String reportBaseName(AssessmentRecord a, Patient p) {
        String name = p == null || p.name == null ? "" : p.name.trim();
        name = name.replaceAll("\\s+", "_").replaceAll("[^A-Za-z0-9_\\-]", "").replaceAll("_-_", "_").replaceAll("_+", "_");
        name = name.replaceAll("^_+|_+$", "");
        if (name.length() > 40) name = name.substring(0, 40);
        if (name.isEmpty()) name = "Patient";
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(a.createdAt));
        return "DermaTrace_" + name + "_" + date + "_Report";
    }

    public static File createPdf(Context context, AssessmentRecord a, Patient p, Lesion l) throws Exception {
        File dir = reportDir(context);
        File file = new File(dir, reportBaseName(a, p) + ".pdf");
        File tmp = new File(dir, file.getName() + ".tmp");

        Bitmap photo = (a.imagePath == null || a.imagePath.isEmpty())
                ? null : BitmapUtils.loadSampledOriented(a.imagePath, 700);
        PdfDocument pdf = new PdfDocument();
        try {
            // pass 1: count pages (no drawing) so the footer can say "Page X of N"
            PageLayout dry = new PageLayout(context, a, p, l, photo, null, 0);
            dry.render();
            // pass 2: real drawing
            PageLayout real = new PageLayout(context, a, p, l, photo, pdf, dry.pageNo);
            real.render();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                pdf.writeTo(fos);
            }
        } finally {
            pdf.close();
            if (photo != null) photo.recycle();
        }
        if (file.exists() && !file.delete()) throw new IllegalStateException("Could not replace old report file.");
        if (!tmp.renameTo(file)) throw new IllegalStateException("Could not finish report file.");
        return file;
    }

    private static File reportDir(Context context) {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DermaTraceReports");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static String friendlyConcern(String value) {
        if (value == null) return "AI result unavailable";
        if (value.contains("HIGHER")) return "Higher model concern — professional review recommended";
        if (value.contains("BORDERLINE") || value.contains("INCONCLUSIVE")) return "AI result inconclusive — consider clinical review";
        return "Lower model concern — does not rule out skin cancer";
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // ------------------------------------------------------------------ page layout engine

    private static final class PageLayout {
        static final float W = 595f, H = 842f, M = 40f, CONTENT_W = W - 2 * M, BOTTOM = H - 58f;
        static final int TEAL = Color.parseColor("#118C8E"), TEAL_DARK = Color.parseColor("#0A7174"),
                TINT = Color.parseColor("#EAF7F6"), BORDER = Color.parseColor("#DCE4EA"),
                INK = Color.parseColor("#10243E"), MUTED = Color.parseColor("#6E7F91"),
                OK = Color.parseColor("#2C8B67"), OK_BG = Color.parseColor("#E3F4EC"),
                WARN = Color.parseColor("#B47A18"), WARN_BG = Color.parseColor("#FFF3DC"),
                BAD = Color.parseColor("#C74F67"), BAD_BG = Color.parseColor("#FCE8EC"),
                TRACK = Color.parseColor("#E7EDF1");

        final Context ctx; final AssessmentRecord a; final Patient p; final Lesion l; final Bitmap photo;
        final PdfDocument pdf; final int totalPages;
        PdfDocument.Page page; Canvas c; int pageNo = 0; float y;

        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint body = tp(10.5f, INK, false), small = tp(8.5f, MUTED, false), label = tp(8f, MUTED, true),
                bold = tp(10.5f, INK, true), h1 = tp(24f, TEAL_DARK, true), name = tp(20f, INK, true),
                section = tp(12.5f, INK, true);

        PageLayout(Context ctx, AssessmentRecord a, Patient p, Lesion l, Bitmap photo, PdfDocument pdf, int totalPages) {
            this.ctx = ctx; this.a = a; this.p = p; this.l = l; this.photo = photo; this.pdf = pdf; this.totalPages = totalPages;
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(1f); stroke.setColor(BORDER);
            label.setLetterSpacing(0.12f);
        }

        static TextPaint tp(float size, int color, boolean bold) {
            TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            t.setTextSize(size); t.setColor(color);
            t.setTypeface(bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
            return t;
        }

        // ---- page handling
        void startPage() {
            if (page != null) finishPage();
            pageNo++;
            if (pdf != null) {
                page = pdf.startPage(new PdfDocument.PageInfo.Builder((int) W, (int) H, pageNo).create());
                c = page.getCanvas();
                c.drawColor(Color.WHITE);
            }
            y = pageNo == 1 ? drawHeader() : drawRunningHeader();
        }

        void finishPage() {
            if (pdf != null && page != null) {
                drawFooter();
                pdf.finishPage(page);
                page = null;
            }
        }

        void ensure(float h) { if (y + h > BOTTOM) startPage(); }

        // ---- header / footer
        float drawHeader() {
            if (c != null) {
                Drawable logo = ContextCompat.getDrawable(ctx, R.drawable.ic_dermatrace_mark);
                if (logo != null) {
                    // render the vector logo at high resolution first so it stays sharp in the PDF
                    Bitmap lb = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
                    Canvas lc = new Canvas(lb);
                    logo.setBounds(0, 0, 300, 300);
                    logo.draw(lc);
                    Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    c.drawBitmap(lb, null, new RectF(M, 28, M + 46, 28 + 46), lp);
                    lb.recycle();
                }
                c.drawText("DermaTrace", M + 58, 54, h1);
                c.drawText("Skin Lesion Assessment Report", M + 58, 71, tp(11f, MUTED, false));
                TextPaint r = tp(9.5f, INK, true); r.setTextAlign(Paint.Align.RIGHT);
                c.drawText(String.format(Locale.US, "Report no. DT-%05d", a.id), W - M, 46, r);
                TextPaint g = tp(8.5f, MUTED, false); g.setTextAlign(Paint.Align.RIGHT);
                c.drawText("Generated " + DateUtils.format(System.currentTimeMillis()), W - M, 60, g);
                fill.setColor(TEAL); c.drawRect(M, 86, W - M, 89.5f, fill);
            }
            return 108f;
        }

        float drawRunningHeader() {
            if (c != null) {
                TextPaint t = tp(9f, MUTED, false);
                c.drawText("DermaTrace  |  " + trim(p == null ? "" : p.name, 40) + "  |  " + DateUtils.format(a.createdAt), M, 34, t);
                fill.setColor(TEAL); c.drawRect(M, 42, W - M, 44f, fill);
            }
            return 62f;
        }

        void drawFooter() {
            fill.setColor(BORDER); c.drawRect(M, H - 46, W - M, H - 45f, fill);
            c.drawText("DermaTrace - educational / research decision-support prototype. Not a diagnosis.", M, H - 30, small);
            TextPaint r = tp(8.5f, MUTED, true); r.setTextAlign(Paint.Align.RIGHT);
            c.drawText("Page " + pageNo + " of " + Math.max(totalPages, pageNo), W - M, H - 30, r);
        }

        // ---- text helpers
        float block(String text, TextPaint paint, float x, float top, float width, int maxLines) {
            StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), paint, (int) width)
                    .setIncludePad(false).setLineSpacing(0f, 1.15f)
                    .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build();
            if (c != null) { c.save(); c.translate(x, top); sl.draw(c); c.restore(); }
            return sl.getHeight();
        }

        /** paragraph that flows across pages line by line, wrapping on measured width */
        void paragraph(String text, TextPaint paint, float x, float width, float after) {
            if (text == null || text.trim().isEmpty()) return;
            StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), paint, (int) width)
                    .setIncludePad(false).setLineSpacing(0f, 1.2f).build();
            for (int i = 0; i < sl.getLineCount(); i++) {
                float lh = sl.getLineBottom(i) - sl.getLineTop(i);
                ensure(lh);
                int s = sl.getLineStart(i), e = sl.getLineEnd(i);
                while (e > s && Character.isWhitespace(text.charAt(e - 1))) e--;
                if (c != null) c.drawText(text, s, e, x, y + (sl.getLineBaseline(i) - sl.getLineTop(i)), paint);
                y += lh;
            }
            y += after;
        }

        void sectionTitle(String t) {
            ensure(44);
            if (c != null) {
                fill.setColor(TEAL); c.drawRoundRect(new RectF(M, y + 1, M + 4, y + 15), 2, 2, fill);
                c.drawText(t, M + 12, y + 13, section);
            }
            y += 26;
        }

        void card(float top, float h, int bg) {
            if (c == null) return;
            fill.setColor(bg); c.drawRoundRect(new RectF(M, top, W - M, top + h), 10, 10, fill);
            stroke.setColor(BORDER); c.drawRoundRect(new RectF(M, top, W - M, top + h), 10, 10, stroke);
        }

        void bar(float x, float top, float w, float h, float fraction, int color) {
            if (c == null) return;
            fill.setColor(TRACK); c.drawRoundRect(new RectF(x, top, x + w, top + h), h / 2, h / 2, fill);
            float fw = Math.max(h, w * Math.max(0f, Math.min(1f, fraction)));
            fill.setColor(color); c.drawRoundRect(new RectF(x, top, x + fw, top + h), h / 2, h / 2, fill);
        }

        static String trim(String s, int max) { s = s == null ? "" : s; return s.length() > max ? s.substring(0, max - 1) + "..." : s; }

        // ---- sections
        void render() {
            startPage();
            patientBlock();
            concernBlock();
            qualityBlock();
            resemblanceBlock();
            if (!TextUtils.isEmpty(a.message)) { sectionTitle("Guidance"); paragraph(a.message, body, M, CONTENT_W, 12); }
            if (a.userNotes != null && !a.userNotes.trim().isEmpty()) {
                sectionTitle("Notes"); paragraph(a.userNotes.trim(), body, M, CONTENT_W, 12);
            }
            if (a.aiOverview != null && !a.aiOverview.trim().isEmpty()) {
                sectionTitle("AI-generated explanation (optional)");
                paragraph(a.aiOverview.trim(), body, M, CONTENT_W, 4);
                String m = a.llmModel == null || a.llmModel.trim().isEmpty() ? "a local language model" : a.llmModel.trim();
                paragraph("Generated by " + m + " from the structured result above. It does not re-analyse the image and is not a diagnosis.", small, M, CONTENT_W, 12);
            }
            safetyBox();
            finishPage();
        }

        void patientBlock() {
            float boxH = 150f;
            ensure(boxH + 14);
            float top = y;
            card(top, boxH, TINT);
            float imgSize = 118f, imgX = W - M - 16 - imgSize;
            float textW = imgX - (M + 20) - 14;
            if (c != null) {
                fill.setColor(TEAL); c.drawRoundRect(new RectF(M, top, M + 6, top + boxH), 3, 3, fill);
                c.drawText("PATIENT", M + 20, top + 24, label);
            }
            String pn = p == null || p.name == null || p.name.trim().isEmpty() ? "Patient" : p.name.trim();
            float nh = block(pn, name, M + 20, top + 30, textW, 2);
            float ly = top + 30 + nh + 8;
            ly = fact("Lesion", l == null ? "" : l.label, M + 20, ly, textW);
            ly = fact("Location", l == null ? "" : l.bodyLocation, M + 20, ly, textW);
            String age = p == null || TextUtils.isEmpty(p.age) ? "" : "Age " + p.age;
            String sex = p == null || TextUtils.isEmpty(p.sex) || "Not specified".equals(p.sex) ? "" : p.sex;
            String agesex = age + (!age.isEmpty() && !sex.isEmpty() ? "  |  " : "") + sex;
            if (!agesex.isEmpty()) ly = fact("Details", agesex, M + 20, ly, textW);
            fact("Assessed", DateUtils.format(a.createdAt), M + 20, ly, textW);
            if (c != null) {
                RectF frame = new RectF(imgX, top + 16, imgX + imgSize, top + 16 + imgSize);
                fill.setColor(Color.WHITE); c.drawRoundRect(frame, 8, 8, fill);
                if (photo != null) {
                    c.save();
                    Path clip = new Path(); clip.addRoundRect(frame, 8, 8, Path.Direction.CW); c.clipPath(clip);
                    float s = Math.max(imgSize / photo.getWidth(), imgSize / photo.getHeight());
                    float dw = photo.getWidth() * s, dh = photo.getHeight() * s;
                    c.drawBitmap(photo, null, new RectF(imgX + (imgSize - dw) / 2, top + 16 + (imgSize - dh) / 2,
                            imgX + (imgSize + dw) / 2, top + 16 + (imgSize + dh) / 2), null);
                    c.restore();
                } else {
                    TextPaint t = tp(9f, MUTED, false); t.setTextAlign(Paint.Align.CENTER);
                    c.drawText("No image", imgX + imgSize / 2, top + 16 + imgSize / 2, t);
                }
                stroke.setColor(BORDER); c.drawRoundRect(frame, 8, 8, stroke);
                TextPaint cap = tp(7.5f, MUTED, false); cap.setTextAlign(Paint.Align.CENTER);
                c.drawText("Assessed image", imgX + imgSize / 2, top + 16 + imgSize + 12, cap);
            }
            y = top + boxH + 18;
        }

        float fact(String k, String v, float x, float top, float width) {
            if (c != null) c.drawText(k, x, top + 9, small);
            float h = block(v == null || v.isEmpty() ? "-" : v, bold, x + 56, top, width - 56, 1);
            return top + Math.max(h, 12f) + 4;
        }

        void concernBlock() {
            sectionTitle("AI concern assessment");
            float boxH = 96f;
            ensure(boxH + 14);
            float top = y;
            card(top, boxH, Color.WHITE);
            String disp = a.triageDisplayLabel == null ? "" : a.triageDisplayLabel;
            int fg, bg; String chip;
            if (disp.contains("HIGHER")) { fg = BAD; bg = BAD_BG; chip = "HIGHER MODEL CONCERN"; }
            else if (disp.contains("BORDERLINE") || disp.contains("INCONCLUSIVE")) { fg = WARN; bg = WARN_BG; chip = "INCONCLUSIVE"; }
            else { fg = OK; bg = OK_BG; chip = "LOWER MODEL CONCERN"; }
            float score = a.higherConcernProbability, thr = a.triageThreshold;
            if (c != null) {
                TextPaint cp = tp(10f, fg, true); cp.setLetterSpacing(0.06f);
                float cw = cp.measureText(chip) + 26;
                fill.setColor(bg); c.drawRoundRect(new RectF(M + 16, top + 14, M + 16 + cw, top + 38), 12, 12, fill);
                c.drawText(chip, M + 29, top + 30, cp);
                TextPaint sp = tp(26f, fg, true); sp.setTextAlign(Paint.Align.RIGHT);
                c.drawText(String.format(Locale.US, "%.1f%%", score * 100f), W - M - 16, top + 36, sp);
                TextPaint cap = tp(8.5f, MUTED, false); cap.setTextAlign(Paint.Align.RIGHT);
                c.drawText("model concern score", W - M - 16, top + 49, cap);
            }
            float bx = M + 16, bw = CONTENT_W - 32;
            bar(bx, top + 56, bw, 8, score, fg);
            if (c != null) {
                float tx = bx + bw * Math.max(0f, Math.min(1f, thr));
                fill.setColor(INK); c.drawRect(tx - 1f, top + 52, tx + 1f, top + 68, fill);
                c.drawText(String.format(Locale.US, "Decision threshold %.1f%% (black marker). The score is a model output, not a probability of cancer.", thr * 100f), bx, top + 84, small);
            }
            y = top + boxH + 16;
        }

        void qualityBlock() {
            float h = 48f;
            ensure(h + 14);
            float top = y, gap = 10f, w = (CONTENT_W - gap) / 2f;
            boolean good = "GOOD".equalsIgnoreCase(a.qualityLabel);
            tile(M, top, w, h, "IMAGE QUALITY",
                    safe(a.qualityLabel) + String.format(Locale.US, "  (%.0f%% confidence)", a.qualityConfidence * 100f), good ? OK : WARN);
            String seg = a.segmentationUsable ? "Usable" : "Uncertain - " + safe(a.segmentationStatus).replace('_', ' ').toLowerCase(Locale.US);
            tile(M + w + gap, top, w, h, "LESION LOCALISATION",
                    seg + String.format(Locale.US, "  (%.2f%% of frame)", a.lesionFraction * 100f), a.segmentationUsable ? OK : WARN);
            y = top + h + 18;
        }

        void tile(float x, float top, float w, float h, String k, String v, int accent) {
            if (c == null) return;
            fill.setColor(Color.WHITE); c.drawRoundRect(new RectF(x, top, x + w, top + h), 8, 8, fill);
            stroke.setColor(BORDER); c.drawRoundRect(new RectF(x, top, x + w, top + h), 8, 8, stroke);
            fill.setColor(accent); c.drawRoundRect(new RectF(x, top + 8, x + 3.5f, top + h - 8), 2, 2, fill);
            c.drawText(k, x + 14, top + 18, label);
            block(v, bold, x + 14, top + 24, w - 24, 1);
        }

        void resemblanceBlock() {
            sectionTitle("AI visual resemblance - top 3 (not a diagnosis)");
            String[] labels = {a.top1Label, a.top2Label, a.top3Label};
            float[] probs = {a.top1Probability, a.top2Probability, a.top3Probability};
            for (int i = 0; i < 3; i++) {
                ensure(34);
                if (c != null) {
                    fill.setColor(i == 0 ? TEAL : TRACK); c.drawCircle(M + 10, y + 9, 9, fill);
                    TextPaint n = tp(10f, i == 0 ? Color.WHITE : INK, true); n.setTextAlign(Paint.Align.CENTER);
                    c.drawText(String.valueOf(i + 1), M + 10, y + 12.5f, n);
                    c.drawText(LesionLabelUtils.friendly(labels[i]), M + 28, y + 12.5f, bold);
                    TextPaint r = tp(10.5f, INK, true); r.setTextAlign(Paint.Align.RIGHT);
                    c.drawText(String.format(Locale.US, "%.1f%%", probs[i] * 100f), W - M, y + 12.5f, r);
                }
                bar(M + 28, y + 19, CONTENT_W - 28, 5, probs[i], i == 0 ? TEAL : Color.parseColor("#8FB7C2"));
                y += 34;
            }
            y += 8;
        }

        void safetyBox() {
            String t = "A lower model concern result does not exclude skin cancer. Seek professional review for a new, changing, bleeding, painful, itchy or otherwise concerning lesion. "
                    + "This report is produced by an educational / research prototype that has not been clinically validated. It does not provide a diagnosis, staging or treatment advice.";
            TextPaint tpw = tp(9.5f, Color.parseColor("#6B4A0F"), false);
            StaticLayout sl = StaticLayout.Builder.obtain(t, 0, t.length(), tpw, (int) (CONTENT_W - 36))
                    .setIncludePad(false).setLineSpacing(0f, 1.2f).build();
            float h = sl.getHeight() + 44f;
            ensure(h + 6);
            float top = y;
            if (c != null) {
                fill.setColor(WARN_BG); c.drawRoundRect(new RectF(M, top, W - M, top + h), 10, 10, fill);
                stroke.setColor(Color.parseColor("#F0D9A5")); c.drawRoundRect(new RectF(M, top, W - M, top + h), 10, 10, stroke);
                fill.setColor(WARN); c.drawRoundRect(new RectF(M, top, M + 6, top + h), 3, 3, fill);
                c.drawText("IMPORTANT SAFETY NOTE", M + 18, top + 22, tp(9f, WARN, true));
            }
            block(t, tpw, M + 18, top + 32, CONTENT_W - 36, 20);
            y = top + h + 6;
        }
    }
}
