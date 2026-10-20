package com.dermatrace.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.PopupMenu;

import com.dermatrace.app.adapter.LesionAdapter;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;

import java.util.List;

public class CompareHubActivity extends BaseDrawerActivity {
    private DermaTraceDbHelper db;
    private LesionAdapter adapter;
    private TextView empty;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_compare_hub, R.id.toolbarCompareHub);
        db = new DermaTraceDbHelper(this);
        empty = findViewById(R.id.txtCompareEmpty);
        ListView list = findViewById(R.id.listComparableLesions);
        adapter = new LesionAdapter(this, db, db.getComparableLesions());
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setOnItemClickListener((p, v, pos, id) -> compare(adapter.getItem(pos)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showPopup(view, adapter.getItem(position));
            return true;
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (adapter == null) return;
        List<Lesion> items = db.getComparableLesions();
        adapter.setItems(items);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPopup(View anchor, Lesion lesion) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.compare_actions_menu);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.compare_now) { compare(lesion); return true; }
            if (item.getItemId() == R.id.compare_open_lesion) {
                Intent i = new Intent(this, LesionDetailActivity.class);
                i.putExtra(LesionDetailActivity.EXTRA_LESION_ID, lesion.id);
                startActivity(i); return true;
            }
            return false;
        });
        popup.show();
    }

    private void compare(Lesion lesion) {
        AssessmentRecord[] pair = db.getLatestTwoAssessments(lesion.id);
        if (pair[0] == null || pair[1] == null) {
            Toast.makeText(this, "Two saved scans are required.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, ComparisonActivity.class);
        i.putExtra(ComparisonActivity.EXTRA_ASSESSMENT_ID, pair[0].id);
        startActivity(i);
    }
}
