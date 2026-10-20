package com.dermatrace.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.dermatrace.app.R;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.Patient;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class PatientAdapter extends BaseAdapter {
    private final Context context; private final DermaTraceDbHelper db; private List<Patient> items;
    private Map<Long, String> statuses = new HashMap<>();
    public void setStatuses(Map<Long, String> values) {
        statuses = new HashMap<>(values);
        notifyDataSetChanged();
    }
    public PatientAdapter(Context context, DermaTraceDbHelper db, List<Patient> items) { this.context=context; this.db=db; this.items=items; }
    public void setItems(List<Patient> items){this.items=items;notifyDataSetChanged();}
    @Override public int getCount(){return items==null?0:items.size();}
    @Override public Patient getItem(int position){return items.get(position);}
    @Override public long getItemId(int position){return getItem(position).id;}
    @Override public View getView(int position, View convertView, ViewGroup parent){
        if(convertView==null) convertView= LayoutInflater.from(context).inflate(R.layout.row_patient,parent,false);
        Patient p=getItem(position);
        ((TextView)convertView.findViewById(R.id.rowPatientName)).setText(p.name);
        int n=db.getLesionsForPatient(p.id).size();
        String meta=n+(n==1?" lesion":" lesions");
        if(p.age!=null&&!p.age.isEmpty()) meta+=" • Age "+p.age;
        if(p.sex!=null&&!p.sex.isEmpty()) meta+=" • "+p.sex;
        ((TextView)convertView.findViewById(R.id.rowPatientMeta)).setText(meta);
        TextView status = convertView.findViewById(R.id.rowPatientStatus);
        String kind = statuses.get(p.id);
        if ("FOLLOWUP".equals(kind)) {
            status.setText("Follow-up scheduled");
            status.setBackgroundResource(R.drawable.bg_status_followup);
            status.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.dt_accent_c));
        } else if ("ACTIVE".equals(kind)) {
            status.setText("Active");
            status.setBackgroundResource(R.drawable.bg_status_low);
            status.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.dt_success));
        } else {
            status.setText("No recent scans");
            status.setBackgroundResource(R.drawable.bg_status_neutral);
            status.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.dt_text_muted));
        }
        return convertView;
    }
}
