package com.dermatrace.app.ml;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ModelConfig {
    public String qualityModel, segmenterModel, triageModel, classifierModel;
    public int qualityInputSize, segmenterInputSize, triageInputSize, classifierInputSize;
    public int higherConcernIndex;
    public float qualityConfidenceThreshold, segmentationThreshold, triageTemperature, triageThreshold, classifierTemperature;
    public List<String> qualityClasses = new ArrayList<>();
    public List<String> classifierClasses = new ArrayList<>();

    public static ModelConfig fromAssets(Context context, String filename) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(filename)));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        JSONObject root = new JSONObject(sb.toString());
        ModelConfig c = new ModelConfig();

        JSONObject q = root.getJSONObject("quality");
        c.qualityModel = q.getString("model");
        c.qualityInputSize = q.getInt("input_size");
        c.qualityConfidenceThreshold = (float) q.getDouble("confidence_threshold");
        JSONArray qa = q.getJSONArray("classes");
        for (int i=0; i<qa.length(); i++) c.qualityClasses.add(qa.getString(i));

        JSONObject s = root.getJSONObject("segmentation");
        c.segmenterModel = s.getString("model");
        c.segmenterInputSize = s.getInt("input_size");
        c.segmentationThreshold = (float) s.getDouble("mask_threshold");

        JSONObject t = root.getJSONObject("triage");
        c.triageModel = t.getString("model");
        c.triageInputSize = t.getInt("input_size");
        c.higherConcernIndex = t.getInt("higher_concern_index");
        c.triageTemperature = (float) t.getDouble("temperature");
        c.triageThreshold = (float) t.getDouble("threshold");

        JSONObject cl = root.getJSONObject("classifier");
        c.classifierModel = cl.getString("model");
        c.classifierInputSize = cl.getInt("input_size");
        c.classifierTemperature = (float) cl.getDouble("temperature");
        JSONArray ca = cl.getJSONArray("classes");
        for (int i=0; i<ca.length(); i++) c.classifierClasses.add(ca.getString(i));
        return c;
    }
}
