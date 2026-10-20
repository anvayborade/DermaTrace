package com.dermatrace.app.model;

import java.io.Serializable;
import java.util.ArrayList;

public class AnalysisResult implements Serializable {
    public enum Status { COMPLETE, RETAKE_IMAGE, ERROR }

    private Status status = Status.COMPLETE;
    private String message = "";
    private String qualityLabel = "";
    private float qualityConfidence;
    private boolean segmentationUsable;
    private String segmentationStatus = "";
    private float lesionFraction;
    private String triageLabel = "";
    private String triageDisplayLabel = "";
    private float higherConcernProbability;
    private float triageThreshold;
    private ArrayList<Prediction> top3 = new ArrayList<>();

    public Status getStatus() { return status; }
    public void setStatus(Status v) { status = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { message = v; }
    public String getQualityLabel() { return qualityLabel; }
    public void setQualityLabel(String v) { qualityLabel = v; }
    public float getQualityConfidence() { return qualityConfidence; }
    public void setQualityConfidence(float v) { qualityConfidence = v; }
    public boolean isSegmentationUsable() { return segmentationUsable; }
    public void setSegmentationUsable(boolean v) { segmentationUsable = v; }
    public String getSegmentationStatus() { return segmentationStatus; }
    public void setSegmentationStatus(String v) { segmentationStatus = v; }
    public float getLesionFraction() { return lesionFraction; }
    public void setLesionFraction(float v) { lesionFraction = v; }
    public String getTriageLabel() { return triageLabel; }
    public void setTriageLabel(String v) { triageLabel = v; }
    public String getTriageDisplayLabel() { return triageDisplayLabel; }
    public void setTriageDisplayLabel(String v) { triageDisplayLabel = v; }
    public float getHigherConcernProbability() { return higherConcernProbability; }
    public void setHigherConcernProbability(float v) { higherConcernProbability = v; }
    public float getTriageThreshold() { return triageThreshold; }
    public void setTriageThreshold(float v) { triageThreshold = v; }
    public ArrayList<Prediction> getTop3() { return top3; }
    public void setTop3(ArrayList<Prediction> v) { top3 = v; }
}
