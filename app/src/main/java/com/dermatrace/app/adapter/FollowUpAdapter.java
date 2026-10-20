package com.dermatrace.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.dermatrace.app.R;
import com.dermatrace.app.model.FollowUpRecord;
import com.dermatrace.app.util.DateUtils;
import java.util.List;

public class FollowUpAdapter extends BaseAdapter {
    private final Context context; private List<FollowUpRecord> items;
    public FollowUpAdapter(Context context, List<FollowUpRecord> items){this.context=context;this.items=items;}
    public void setItems(List<FollowUpRecord> items){this.items=items;notifyDataSetChanged();}
    @Override public int getCount(){return items==null?0:items.size();}
    @Override public FollowUpRecord getItem(int position){return items.get(position);}
    @Override public long getItemId(int position){return getItem(position).id;}
    @Override public View getView(int position, View convertView, ViewGroup parent){
        if(convertView==null) convertView= LayoutInflater.from(context).inflate(R.layout.row_followup,parent,false);
        FollowUpRecord f=getItem(position);
        ((TextView)convertView.findViewById(R.id.rowFollowupTitle)).setText(f.lesionLabel==null?"Lesion follow-up":f.lesionLabel);
        ((TextView)convertView.findViewById(R.id.rowFollowupMeta)).setText((f.patientName==null?"":f.patientName+" • ")+"Due "+ DateUtils.format(f.dueAt));
        return convertView;
    }
}
