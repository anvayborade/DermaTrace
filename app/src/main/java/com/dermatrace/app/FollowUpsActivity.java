package com.dermatrace.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.dermatrace.app.adapter.FollowUpAdapter;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.FollowUpRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.reminder.FollowUpScheduler;

import java.util.List;

public class FollowUpsActivity extends BaseDrawerActivity {
    private DermaTraceDbHelper db;
    private FollowUpAdapter adapter;
    private TextView empty;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_followups, R.id.toolbarFollowUps);
        db = new DermaTraceDbHelper(this);
        empty = findViewById(R.id.txtFollowUpsEmpty);
        ListView list = findViewById(R.id.listFollowUps);
        adapter = new FollowUpAdapter(this, db.getActiveFollowUps());
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setOnItemClickListener((p, v, pos, id) -> openLesion(adapter.getItem(pos).lesionId));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showPopup(view, adapter.getItem(position));
            return true;
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (adapter == null) return;
        List<FollowUpRecord> items = db.getActiveFollowUps();
        adapter.setItems(items);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPopup(View anchor, FollowUpRecord f) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.inflate(R.menu.followup_actions_menu);
        p.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.followup_open) { openLesion(f.lesionId); return true; }
            if (id == R.id.followup_reschedule) { reschedule(f); return true; }
            if (id == R.id.followup_cancel) {
                FollowUpScheduler.cancel(this, f.lesionId);
                refresh(); return true;
            }
            return false;
        });
        p.show();
    }

    private void reschedule(FollowUpRecord f) {
        EditText days = new EditText(this);
        days.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        days.setText("30");
        days.setHint("Days from today");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reschedule follow-up")
                .setView(days)
                .setPositiveButton("Update", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            int value = -1;
            try { value = Integer.parseInt(days.getText().toString().trim()); } catch (Exception ignored) {}
            if (value < 1 || value > 365) { days.setError("Enter 1 to 365 days"); return; }
            Lesion l = db.getLesion(f.lesionId);
            if (l != null) {
                FollowUpScheduler.scheduleDays(this, l.id, l.label, value);
                Toast.makeText(this, FollowUpScheduler.scheduledMessage(this, value), Toast.LENGTH_LONG).show();
                refresh();
            }
            dialog.dismiss();
        });
    }

    private void openLesion(long lesionId) {
        Intent i = new Intent(this, LesionDetailActivity.class);
        i.putExtra(LesionDetailActivity.EXTRA_LESION_ID, lesionId);
        startActivity(i);
    }
}
