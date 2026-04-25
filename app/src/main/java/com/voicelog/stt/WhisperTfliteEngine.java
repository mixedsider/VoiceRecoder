package com.voicelog.stt;

import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WhisperTfliteEngine implements AutoCloseable {
    private static final String TAG = "WhisperTfliteEngine";
    private static final String INPUT_NAME = "input_features";
    private static final String OUTPUT_NAME = "sequences";
    private static final String TRANSCRIBE_SIGNATURE = "serving_transcribe";
    private static final String DEFAULT_SIGNATURE = "serving_default";

    private final WhisperUtil whisperUtil = new WhisperUtil();

    private Interpreter interpreter;
    private boolean initialized;
    private int outputTokenCount = 448;
    private String activeSignature = TRANSCRIBE_SIGNATURE;

    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        close();
        loadModel(modelPath);
        initialized = whisperUtil.loadFiltersAndVocab(multilingual, vocabPath);
        if (!initialized) {
            close();
            return false;
        }
        resolveSignatureMetadata();
        return true;
    }

    public boolean isInitialized() {
        return initialized && interpreter != null;
    }

    public String transcribe(float[] samples) {
        if (!isInitialized()) {
            throw new IllegalStateException("Whisper TFLite engine is not initialized");
        }

        int fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE;
        float[] inputSamples = new float[fixedInputSize];
        int copyLength = Math.min(samples.length, fixedInputSize);
        System.arraycopy(samples, 0, inputSamples, 0, copyLength);

        int cores = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 4));
        float[] melSpectrogram = whisperUtil.getMelSpectrogram(inputSamples, inputSamples.length, cores);
        return runInference(melSpectrogram);
    }

    @Override
    public void close() {
        initialized = false;
        outputTokenCount = 448;
        activeSignature = TRANSCRIBE_SIGNATURE;
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }

    private void loadModel(String modelPath) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(modelPath);
             FileChannel fileChannel = fileInputStream.getChannel()) {
            ByteBuffer modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                0,
                fileChannel.size()
            );

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 4)));
            interpreter = new Interpreter(modelBuffer, options);
        }
    }

    private void resolveSignatureMetadata() {
        if (interpreter == null) {
            return;
        }

        try {
            Tensor outputTensor = interpreter.getOutputTensorFromSignature(OUTPUT_NAME, TRANSCRIBE_SIGNATURE);
            if (outputTensor.shape().length > 1) {
                outputTokenCount = outputTensor.shape()[1];
            }
            activeSignature = TRANSCRIBE_SIGNATURE;
        } catch (IllegalArgumentException signatureError) {
            Log.w(TAG, "serving_transcribe signature is unavailable, falling back to serving_default", signatureError);
            Tensor outputTensor = interpreter.getOutputTensorFromSignature(OUTPUT_NAME, DEFAULT_SIGNATURE);
            if (outputTensor.shape().length > 1) {
                outputTokenCount = outputTensor.shape()[1];
            }
            activeSignature = DEFAULT_SIGNATURE;
        }
    }

    private String runInference(float[] inputData) {
        float[][][] input = new float[1][WhisperUtil.WHISPER_N_MEL][WhisperUtil.WHISPER_MEL_LEN];
        for (int mel = 0; mel < WhisperUtil.WHISPER_N_MEL; mel++) {
            System.arraycopy(
                inputData,
                mel * WhisperUtil.WHISPER_MEL_LEN,
                input[0][mel],
                0,
                WhisperUtil.WHISPER_MEL_LEN
            );
        }

        int[][] output = new int[1][outputTokenCount];
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(INPUT_NAME, input);
        Map<String, Object> outputs = new HashMap<>();
        outputs.put(OUTPUT_NAME, output);

        interpreter.runSignature(inputs, outputs, activeSignature);

        List<Integer> decodedTokens = new ArrayList<>();
        for (int token : output[0]) {
            if (token == whisperUtil.getTokenEOT()) {
                break;
            }
            if (token < whisperUtil.getTokenEOT()) {
                decodedTokens.add(token);
            }
        }

        return whisperUtil.decodeTokenSequence(decodedTokens);
    }
}
