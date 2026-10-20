package com.dermatrace.app.util;

public final class LesionLabelUtils {
    private LesionLabelUtils() {}

    public static String friendly(String code) {
        if (code == null) return "Unknown pattern";
        switch (code) {
            case "AKIEC": return "Actinic keratosis / atypical keratinocytic-like";
            case "BCC": return "Basal cell carcinoma-like";
            case "BEN_OTH": return "Other benign-like lesion";
            case "BKL": return "Benign keratosis-like";
            case "DF": return "Dermatofibroma-like";
            case "INF": return "Inflammatory / infectious-like";
            case "MAL_OTH": return "Other malignant-like lesion";
            case "MEL": return "Melanoma-like";
            case "NV": return "Common mole / melanocytic nevus-like";
            case "SCCKA": return "SCC / keratoacanthoma-like";
            case "VASC": return "Vascular lesion-like";
            default: return code.replace('_', ' ');
        }
    }

    public static String shortDescription(String code) {
        if (code == null) return "";
        switch (code) {
            case "AKIEC": return "Sun-damaged atypical keratinocytic pattern.";
            case "BCC": return "Pattern resembling basal cell carcinoma, a common usually slow-growing skin cancer.";
            case "BEN_OTH": return "A benign-appearing pattern not covered by the other specific benign groups.";
            case "BKL": return "Pattern resembling a benign keratosis such as seborrhoeic keratosis.";
            case "DF": return "Pattern resembling dermatofibroma, a usually harmless fibrous skin nodule.";
            case "INF": return "Pattern resembling inflammatory or infectious skin change.";
            case "MAL_OTH": return "A malignant-appearing pattern outside the main named cancer groups.";
            case "MEL": return "Pattern resembling melanoma, a potentially serious melanocyte cancer.";
            case "NV": return "Pattern resembling a common usually benign mole.";
            case "SCCKA": return "Pattern resembling squamous-cell carcinoma / keratoacanthoma.";
            case "VASC": return "Pattern resembling a blood-vessel lesion such as an angioma.";
            default: return "AI visual resemblance category.";
        }
    }
}
