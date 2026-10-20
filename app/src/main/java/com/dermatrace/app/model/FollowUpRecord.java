package com.dermatrace.app.model;

public class FollowUpRecord {
    public long id;
    public long lesionId;
    public long dueAt;
    public long createdAt;
    public boolean active;
    public String lesionLabel;
    public String patientName;
}
