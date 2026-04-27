package com.voicelog.stt;

import android.util.Log;

import com.voicelog.util.InferenceBackend;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.GpuDelegate;

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
    private GpuDelegate gpuDelegate;
    private String loadedModelPath;
    private boolean initialized;
    private int outputTokenCount = 448;
    private String activeSignature = TRANSCRIBE_SIGNATURE;
    private InferenceBackend requestedBackend = InferenceBackend.CPU;
    private InferenceBackend effectiveBackend = InferenceBackend.CPU;
    private String backendFallbackReason;
    private long lastInitializeMs;
    private long lastModelLoadMs;
    private long lastVocabLoadMs;
    private long lastDelegateInitMs;
    private long lastPreprocessingMs;
    private long lastInferenceMs;
    private long lastDecodeMs;

    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        return initialize(modelPath, vocabPath, multilingual, InferenceBackend.CPU);
    }

    public boolean initialize(
        String modelPath,
        String vocabPath,
        boolean multilingual,
        InferenceBackend backend
    ) throws IOException {
        close();
        requestedBackend = backend == null ? InferenceBackend.CPU : backend;
        long initStartedAt = System.nanoTime();

        long modelLoadStartedAt = System.nanoTime();
        loadModel(modelPath, requestedBackend);
        lastModelLoadMs = nanosToMillis(System.nanoTime() - modelLoadStartedAt);

        long vocabLoadStartedAt = System.nanoTime();
        initialized = whisperUtil.loadFiltersAndVocab(multilingual, vocabPath);
        lastVocabLoadMs = nanosToMillis(System.nanoTime() - vocabLoadStartedAt);

        if (!initialized) {
            close();
            return false;
        }
        resolveSignatureMetadata();
        lastInitializeMs = nanosToMillis(System.nanoTime() - initStartedAt);
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
        long preprocessStartedAt = System.nanoTime();
        float[] melSpectrogram = whisperUtil.getMelSpectrogram(inputSamples, inputSamples.length, cores);
        lastPreprocessingMs = nanosToMillis(System.nanoTime() - preprocessStartedAt);
        return runInference(melSpectrogram);
    }

    public long getLastInitializeMs() {
        return lastInitializeMs;
    }

    public long getLastModelLoadMs() {
        return lastModelLoadMs;
    }

    public long getLastVocabLoadMs() {
        return lastVocabLoadMs;
    }

    public long getLastDelegateInitMs() {
        return lastDelegateInitMs;
    }

    public long getLastPreprocessingMs() {
        return lastPreprocessingMs;
    }

    public long getLastInferenceMs() {
        return lastInferenceMs;
    }

    public long getLastDecodeMs() {
        return lastDecodeMs;
    }

    public InferenceBackend getRequestedBackend() {
        return requestedBackend;
    }

    public InferenceBackend getEffectiveBackend() {
        return effectiveBackend;
    }

    public String getBackendFallbackReason() {
        return backendFallbackReason;
    }

    @Override
    public void close() {
        initialized = false;
        outputTokenCount = 448;
        activeSignature = TRANSCRIBE_SIGNATURE;
        requestedBackend = InferenceBackend.CPU;
        effectiveBackend = InferenceBackend.CPU;
        backendFallbackReason = null;
        lastInitializeMs = 0L;
        lastModelLoadMs = 0L;
        lastVocabLoadMs = 0L;
        lastDelegateInitMs = 0L;
        lastPreprocessingMs = 0L;
        lastInferenceMs = 0L;
        lastDecodeMs = 0L;
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
        loadedModelPath = null;
    }

    private void loadModel(String modelPath, InferenceBackend backend) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(modelPath);
             FileChannel fileChannel = fileInputStream.getChannel()) {
            ByteBuffer modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                0,
                fileChannel.size()
            );

            loadedModelPath = modelPath;
            try {
                interpreter = new Interpreter(modelBuffer, createOptions(backend));
                effectiveBackend = backend;
            } catch (Throwable acceleratorError) {
                closeInterpreterOnly();
                backendFallbackReason = backend.getDisplayName() + " initialization failed: " +
                    safeMessage(acceleratorError);
                Log.w(TAG, backendFallbackReason, acceleratorError);
                interpreter = new Interpreter(modelBuffer, createOptions(InferenceBackend.CPU));
                effectiveBackend = InferenceBackend.CPU;
            }
        }
    }

    private Interpreter.Options createOptions(InferenceBackend backend) {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 4)));

        long delegateStartedAt = System.nanoTime();
        if (backend == InferenceBackend.GPU) {
            gpuDelegate = new GpuDelegate();
            options.addDelegate(gpuDelegate);
        } else if (backend == InferenceBackend.NPU) {
            options.setUseNNAPI(true);
        }
        lastDelegateInitMs = nanosToMillis(System.nanoTime() - delegateStartedAt);
        return options;
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

        long inferenceStartedAt = System.nanoTime();
        try {
            interpreter.runSignature(inputs, outputs, activeSignature);
        } catch (RuntimeException acceleratorError) {
            if (effectiveBackend == InferenceBackend.CPU || loadedModelPath == null) {
                throw acceleratorError;
            }
            backendFallbackReason = effectiveBackend.getDisplayName() + " inference failed: " +
                safeMessage(acceleratorError);
            Log.w(TAG, backendFallbackReason, acceleratorError);
            try {
                closeInterpreterOnly();
                loadModel(loadedModelPath, InferenceBackend.CPU);
                resolveSignatureMetadata();
                interpreter.runSignature(inputs, outputs, activeSignature);
            } catch (IOException cpuLoadError) {
                throw new RuntimeException("CPU fallback failed", cpuLoadError);
            }
        }
        lastInferenceMs = nanosToMillis(System.nanoTime() - inferenceStartedAt);

        long decodeStartedAt = System.nanoTime();
        List<Integer> decodedTokens = new ArrayList<>();
        for (int token : output[0]) {
            if (token == whisperUtil.getTokenEOT()) {
                break;
            }
            if (token < whisperUtil.getTokenEOT()) {
                decodedTokens.add(token);
            }
        }

        String text = whisperUtil.decodeTokenSequence(decodedTokens);
        lastDecodeMs = nanosToMillis(System.nanoTime() - decodeStartedAt);
        return text;
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private void closeInterpreterOnly() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
