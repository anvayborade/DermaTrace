package com.dermatrace.app.adapter;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.dermatrace.app.R;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.util.DateUtils;
import com.dermatrace.app.util.LesionLabelUtils;

import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends BaseAdapter {
    private final Context context;
    private final DermaTraceDbHelper db;
    private List<AssessmentRecord> items;

    public HistoryAdapter(Context context, DermaTraceDbHelper db, List<AssessmentRecord> items) {
        this.context = context; this.db = db; this.items = items;
    }
    public void setItems(List<AssessmentRecord> items) { this.items = items; notifyDataSetChanged(); }
    @Override public int getCount() { return items == null ? 0 : items.size(); }
    @Override public AssessmentRecord getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return getItem(position).id; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.row_history, parent, false);
            h = new ViewHolder();
            h.image = convertView.findViewById(R.id.rowImage);
            h.title = convertView.findViewById(R.id.rowTitle);
            h.subtitle = convertView.findViewById(R.id.rowSubtitle);
            h.concern = convertView.findViewById(R.id.rowConcern);
            h.pattern = convertView.findViewById(R.id.rowPattern);
            convertView.setTag(h);
        } else h = (ViewHolder) convertView.getTag();

        AssessmentRecord a = getItem(position);
        Lesion l = db.getLesion(a.lesionId);
        Patient p = l == null ? null : db.getPatient(l.patientId);
        if (a.imagePath != null && !a.imagePath.isEmpty()) h.image.setImageBitmap(com.dermatrace.app.util.BitmapUtils.loadSampledOriented(a.imagePath, 256));
        else h.image.setImageResource(R.mipmap.ic_launcher);

        h.title.setText(l == null ? "Saved lesion" : l.label);
        String patient = p == null ? "" : p.name;
        h.subtitle.setText((patient.isEmpty() ? "" : patient + " • ") + DateUtils.format(a.createdAt));
        h.concern.setText(friendlyConcern(a.triageDisplayLabel) + String.format(Locale.US, " • %.0f%%", a.higherConcernProbability * 100f));
        h.pattern.setText("Top pattern: " + LesionLabelUtils.friendly(a.top1Label) +
                (a.userNotes == null || a.userNotes.trim().isEmpty() ? "" : "\nNote: " + a.userNotes.trim()));
        return convertView;
    }

    private String friendlyConcern(String v) {
        if (v == null) return "AI result";
        if (v.contains("HIGHER")) return "Higher concern";
        if (v.contains("BORDERLINE") || v.contains("INCONCLUSIVE")) return "Inconclusive";
        return "Lower concern";
    }
    private static class ViewHolder { ImageView image; TextView title, subtitle, concern, pattern; }
}
