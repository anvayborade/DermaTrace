package com.dermatrace.app.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.dermatrace.app.model.AnalysisResult;
import com.dermatrace.app.model.Prediction;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tflite.java.TfLite;

import org.tensorflow.lite.InterpreterApi;
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime;
import org.tensorflow.lite.support.common.FileUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class DermaTraceModelManager implements AutoCloseable {

    public interface InitCallback {
        void onReady();
        void onError(Exception e);
    }

    private final Context context;
    private InterpreterApi quality, segmenter, triage, classifier;
    private ModelConfig config;
    private boolean ready = false;

    // Presentation safeguard only; validated hard threshold remains unchanged.
    private static final float BORDERLINE_MARGIN = 0.025f;

    public DermaTraceModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isReady() { return ready; }

    public void initialize(InitCallback callback) {
        try {
            config = ModelConfig.fromAssets(context, "model_config.json");
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        Task<Void> task = TfLite.initialize(context);
        task.addOnSuccessListener(unused -> {
            try {
                InterpreterApi.Options options = new InterpreterApi.Options()
                        .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
                        .setNumThreads(4);

                quality = InterpreterApi.create(FileUtil.loadMappedFile(context, config.qualityModel), options);
                segmenter = InterpreterApi.create(FileUtil.loadMappedFile(context, config.segmenterModel), options);
                triage = InterpreterApi.create(FileUtil.loadMappedFile(context, config.triageModel), options);
                classifier = InterpreterApi.create(FileUtil.loadMappedFile(context, config.classifierModel), options);

                ready = true;
                callback.onReady();
            } catch (Exception e) {
                callback.onError(e);
            }
        }).addOnFailureListener(e ->
                callback.onError(new Exception("LiteRT initialization failed", e))
        );
    }

    public AnalysisResult analyze(Bitmap bitmap) {
        AnalysisResult r = new AnalysisResult();

        if (!ready) {
            r.setStatus(AnalysisResult.Status.ERROR);
            r.setMessage("AI models are still loading.");
            return r;
        }

        try {
            // 1. Quality
            float[] qLogits = runClassifier(quality, bitmap, config.qualityInputSize, config.qualityClasses.size());
            float[] qProb = softmax(qLogits, 1f);
            int qi = argMax(qProb);

            r.setQualityLabel(config.qualityClasses.get(qi));
            r.setQualityConfidence(qProb[qi]);

            if (!"GOOD".equalsIgnoreCase(r.getQualityLabel())
                    && r.getQualityConfidence() >= config.qualityConfidenceThreshold) {
                r.setStatus(AnalysisResult.Status.RETAKE_IMAGE);
                r.setMessage(retakeMessage(r.getQualityLabel()));
                return r;
            }

            // 2. Segmentation sanity
            ByteBuffer segInput = bitmapToBuffer(bitmap, config.segmenterInputSize);
            float[][][][] segOut = new float[1][config.segmenterInputSize][config.segmenterInputSize][1];
            segmenter.run(segInput, segOut);

            int active = 0;
            int total = config.segmenterInputSize * config.segmenterInputSize;
            for (int y=0; y<config.segmenterInputSize; y++) {
                for (int x=0; x<config.segmenterInputSize; x++) {
                    if (segOut[0][y][x][0] >= config.segmentationThreshold) active++;
                }
            }
            float fraction = active / (float) total;
            r.setLesionFraction(fraction);

            if (fraction < 0.003f) {
                r.setSegmentationUsable(false);
                r.setSegmentationStatus("MASK_TOO_SMALL");
            } else if (fraction > 0.80f) {
                r.setSegmentationUsable(false);
                r.setSegmentationStatus("MASK_TOO_LARGE");
            } else {
                r.setSegmentationUsable(true);
                r.setSegmentationStatus("OK");
            }

            // 3. Dedicated triage
            float[] tLogits = runClassifier(triage, bitmap, config.triageInputSize, 2);
            float[] tProb = softmax(tLogits, config.triageTemperature);
            float higher = tProb[config.higherConcernIndex];

            r.setHigherConcernProbability(higher);
            r.setTriageThreshold(config.triageThreshold);

            String hard = higher >= config.triageThreshold ? "HIGHER_CONCERN" : "LOWER_CONCERN";
            r.setTriageLabel(hard);

            if (Math.abs(higher - config.triageThreshold) <= BORDERLINE_MARGIN) {
                r.setTriageDisplayLabel("BORDERLINE / INCONCLUSIVE");
            } else if ("HIGHER_CONCERN".equals(hard)) {
                r.setTriageDisplayLabel("HIGHER MODEL CONCERN");
            } else {
                r.setTriageDisplayLabel("LOWER MODEL CONCERN");
            }

            // 4. Secondary 11-class resemblance
            float[] cLogits = runClassifier(classifier, bitmap, config.classifierInputSize, config.classifierClasses.size());
            float[] cProb = softmax(cLogits, config.classifierTemperature);

            Integer[] order = new Integer[cProb.length];
            for (int i=0; i<order.length; i++) order[i] = i;
            Arrays.sort(order, Comparator.comparingDouble((Integer i) -> cProb[i]).reversed());

            ArrayList<Prediction> top3 = new ArrayList<>();
            for (int rank=0; rank<Math.min(3, order.length); rank++) {
                int idx = order[rank];
                top3.add(new Prediction(config.classifierClasses.get(idx), cProb[idx]));
            }
            r.setTop3(top3);

            r.setStatus(AnalysisResult.Status.COMPLETE);
            r.setMessage(clinicalMessage(r));
            return r;

        } catch (Exception e) {
            r.setStatus(AnalysisResult.Status.ERROR);
            r.setMessage("Inference failed: " + e.getMessage());
            return r;
        }
    }

    private float[] runClassifier(InterpreterApi interpreter, Bitmap bitmap, int size, int outputs) {
        ByteBuffer input = bitmapToBuffer(bitmap, size);
        float[][] out = new float[1][outputs];
        interpreter.run(input, out);
        return out[0];
    }

    // Matches the validated Python V1 path: raw RGB float values in 0..255.
    private ByteBuffer bitmapToBuffer(Bitmap bitmap, int size) {
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size, size, true);
        int[] pixels = new int[size * size];
        scaled.getPixels(pixels, 0, size, 0, 0, size, size);

        ByteBuffer b = ByteBuffer.allocateDirect(4 * size * size * 3);
        b.order(ByteOrder.nativeOrder());

        for (int p : pixels) {
            b.putFloat(Color.red(p));
            b.putFloat(Color.green(p));
            b.putFloat(Color.blue(p));
        }
        b.rewind();
        return b;
    }

    private float[] softmax(float[] logits, float temperature) {
        float max = -Float.MAX_VALUE;
        for (float v : logits) max = Math.max(max, v / temperature);

        float[] p = new float[logits.length];
        float sum = 0f;
        for (int i=0; i<logits.length; i++) {
            p[i] = (float)Math.exp((logits[i] / temperature) - max);
            sum += p[i];
        }
        if (sum > 0f) for (int i=0; i<p.length; i++) p[i] /= sum;
        return p;
    }

    private int argMax(float[] values) {
        int best = 0;
        for (int i=1; i<values.length; i++) if (values[i] > values[best]) best = i;
        return best;
    }

    private String retakeMessage(String label) {
        if ("BLUR".equalsIgnoreCase(label))
            return "Image appears blurred. Hold the phone steady and retake.";
        if ("DARK".equalsIgnoreCase(label))
            return "Image is too dark. Increase even lighting and retake.";
        if ("OVEREXPOSED".equalsIgnoreCase(label))
            return "Image is overexposed. Avoid glare or direct flash and retake.";
        return "Image quality is unsuitable. Please retake.";
    }

    private String clinicalMessage(AnalysisResult r) {
        if ("BORDERLINE / INCONCLUSIVE".equals(r.getTriageDisplayLabel()))
            return "The AI score is close to its decision boundary. Treat this result as inconclusive; clinical review is appropriate for a new, changing, bleeding, painful, or otherwise concerning lesion.";
        if ("HIGHER_CONCERN".equals(r.getTriageLabel()))
            return "The experimental triage model found a higher-concern visual pattern. Professional dermatology assessment is recommended.";
        return "The experimental model found a lower-concern visual pattern. This does not rule out skin cancer. New, changing, bleeding, symptomatic, or otherwise concerning lesions still require clinical review.";
    }

    @Override
    public void close() {
        if (quality != null) quality.close();
        if (segmenter != null) segmenter.close();
        if (triage != null) triage.close();
        if (classifier != null) classifier.close();
        ready = false;
    }
}
