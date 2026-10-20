package com.dermatrace.app.model;

import java.io.Serializable;

public class Prediction implements Serializable {
    private final String label;
    private final float probability;

    public Prediction(String label, float probability) {
        this.label = label;
        this.probability = probability;
    }

    public String getLabel() { return label; }
    public float getProbability() { return probability; }
}
