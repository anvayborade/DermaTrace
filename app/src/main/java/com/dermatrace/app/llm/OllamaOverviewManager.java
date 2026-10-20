package com.dermatrace.app.llm;

import android.content.Context;

import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.util.AppPreferences;
import com.dermatrace.app.util.LesionLabelUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small explanation-only client for a local Ollama server.
 * It never sends the lesion image and never changes the validated ML result.
 */
public class OllamaOverviewManager {

    public interface Callback {
        void onSuccess(String overview, String model);
        void onError(String message);
    }

    public interface ConnectionCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public OllamaOverviewManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void generateOverview(AssessmentRecord record, Lesion lesion, Callback callback) {
        executor.execute(() -> {
            try {
                String model = AppPreferences.getOllamaModel(context);
                String response = postChat(
                        AppPreferences.getOllamaUrl(context),
                        model,
                        SYSTEM_PROMPT,
                        buildResultPrompt(record, lesion),
                        0.2
                );

                if (response == null || response.trim().isEmpty()) {
                    callback.onError("The local AI returned an empty response.");
                    return;
                }

                callback.onSuccess(response.trim(), model);
            } catch (Exception e) {
                callback.onError(friendlyError(e));
            }
        });
    }

    public void testConnection(ConnectionCallback callback) {
        executor.execute(() -> {
            try {
                String model = AppPreferences.getOllamaModel(context);
                String content = postChat(
                        AppPreferences.getOllamaUrl(context),
                        model,
                        "You are a connection test. Reply with only: OK",
                        "Reply OK.",
                        0.0
                );
                callback.onSuccess("Connected to Ollama • " + model + " • response: " + content.trim());
            } catch (Exception e) {
                callback.onError(friendlyError(e));
            }
        });
    }

    private String postChat(String baseUrl, String model, String system, String user, double temperature) throws Exception {
        String cleanBase = baseUrl == null ? "" : baseUrl.trim();
        while (cleanBase.endsWith("/")) cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
        URL url = new URL(cleanBase + "/api/chat");

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(90000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", user));

        JSONObject options = new JSONObject();
        options.put("temperature", temperature);
        options.put("num_predict", 240);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("options", options);

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(bytes);
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        String text = readAll(stream);
        connection.disconnect();

        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Ollama HTTP " + code + (text.isEmpty() ? "" : ": " + text));
        }

        JSONObject root = new JSONObject(text);
        JSONObject message = root.optJSONObject("message");
        if (message == null) throw new IllegalStateException("Ollama response did not contain a message.");
        return message.optString("content", "");
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private String buildResultPrompt(AssessmentRecord a, Lesion lesion) {
        String location = lesion == null || lesion.bodyLocation == null || lesion.bodyLocation.trim().isEmpty()
                ? "Not specified" : lesion.bodyLocation.trim();

        return String.format(Locale.US,
                "DermaTrace structured result:\n" +
                "Body location: %s\n" +
                "Image quality: %s (model confidence %.1f%%)\n" +
                "Lesion localization: %s; mask fraction %.2f%% of frame\n" +
                "Concern assessment: %s\n" +
                "Higher-concern score: %.1f%%\n" +
                "Validated decision threshold: %.1f%%\n" +
                "Visual resemblance 1: %s (%.1f%%)\n" +
                "Visual resemblance 2: %s (%.1f%%)\n" +
                "Visual resemblance 3: %s (%.1f%%)\n" +
                "Existing safety guidance: %s\n\n" +
                "Explain only this supplied information. Do not infer extra visual findings.",
                location,
                safe(a.qualityLabel), a.qualityConfidence * 100f,
                safe(a.segmentationStatus), a.lesionFraction * 100f,
                safe(a.triageDisplayLabel),
                a.higherConcernProbability * 100f,
                a.triageThreshold * 100f,
                LesionLabelUtils.friendly(a.top1Label), a.top1Probability * 100f,
                LesionLabelUtils.friendly(a.top2Label), a.top2Probability * 100f,
                LesionLabelUtils.friendly(a.top3Label), a.top3Probability * 100f,
                safe(a.message)
        );
    }

    private static String friendlyError(Exception e) {
        String raw = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (raw.contains("Connection refused") || raw.contains("Failed to connect") || raw.contains("ECONNREFUSED")) {
            return "The AI explanation service is not reachable right now. Check that Ollama is running and that the address in Settings is correct.";
        }
        if (raw.toLowerCase(Locale.US).contains("timed out")) {
            return "Ollama timed out. The model may still be loading; try again in a moment.";
        }
        return raw;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static final String SYSTEM_PROMPT =
            "You are the explanation component of DermaTrace, an educational and research skin-lesion decision-support prototype. " +
            "You receive only structured outputs from trained image-analysis models. Follow these rules strictly: " +
            "(1) Do not diagnose skin cancer or any disease. " +
            "(2) Do not call the higher-concern score a cancer probability or disease probability. " +
            "(3) Do not provide cancer stage, prognosis, medication, treatment, or management instructions. " +
            "(4) Do not invent visual findings, symptoms, history, risk factors, or patient details. " +
            "(5) Treat the Top-3 labels only as visual resemblance patterns, not diagnoses. " +
            "(6) Treat the higher-concern score only as model concern. " +
            "(7) A lower-concern result does not rule out skin cancer. " +
            "(8) For higher concern or inconclusive results, recommend professional clinical review. " +
            "(9) Use calm, simple language for a non-medical user. " +
            "Return plain text under 130 words with exactly these headings on separate lines: What the AI found; What this means; What to do next. " +
            "Do not use markdown bullets, tables, or code blocks.";
}
