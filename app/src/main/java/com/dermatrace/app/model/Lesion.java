package com.dermatrace.app.model;

public class Lesion {
    public long id;
    public long patientId;
    public String label;
    public String bodyLocation;
    public String notes;
    public long createdAt;

    @Override
    public String toString() {
        return label + (bodyLocation == null || bodyLocation.isEmpty() ? "" : " • " + bodyLocation);
    }
}
