package com.dermatrace.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.dermatrace.app.R;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.util.DateUtils;
import java.util.List;

public class LesionAdapter extends BaseAdapter {
    private final Context context; private final DermaTraceDbHelper db; private List<Lesion> items;
    public LesionAdapter(Context context, DermaTraceDbHelper db, List<Lesion> items){this.context=context;this.db=db;this.items=items;}
    public void setItems(List<Lesion> items){this.items=items;notifyDataSetChanged();}
    @Override public int getCount(){return items==null?0:items.size();}
    @Override public Lesion getItem(int position){return items.get(position);}
    @Override public long getItemId(int position){return getItem(position).id;}
    @Override public View getView(int position, View convertView, ViewGroup parent){
        if(convertView==null) convertView= LayoutInflater.from(context).inflate(R.layout.row_lesion,parent,false);
        Lesion l=getItem(position); AssessmentRecord latest=db.getLatestAssessmentForLesion(l.id);
        ((TextView)convertView.findViewById(R.id.rowLesionTitle)).setText(l.label);
        ((TextView)convertView.findViewById(R.id.rowLesionLocation)).setText(l.bodyLocation==null?"Not specified":l.bodyLocation);
        ((TextView)convertView.findViewById(R.id.rowLesionLatest)).setText(latest==null?"No scans yet":"Latest scan • "+ DateUtils.format(latest.createdAt));
        return convertView;
    }
}
