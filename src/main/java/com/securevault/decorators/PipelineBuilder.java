package com.securevault.decorators;

import com.securevault.encryption.EncryptionFactory;
import com.securevault.encryption.EncryptionStrategy;
import com.securevault.exceptions.EncryptionException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code pipeline.json} from the classpath and constructs a
 * decorator chain automatically.
 *
 * <p><b>Design Decision:</b> Building the pipeline from a JSON file makes the
 * application configurable without recompilation. This is "enterprise-like"
 * behaviour: operators can change the processing pipeline by editing a file.
 *
 * <p><b>Pipeline execution order (encrypt):</b>
 * Steps are applied left-to-right as listed in {@code pipeline.json}.
 * Decryption automatically reverses this by the nature of decorator nesting.
 *
 * <p><b>Example pipeline.json:</b>
 * <pre>
 *   { "steps": ["COMPRESS", "AES", "CHECKSUM"] }
 * </pre>
 *
 * <p>Produces the chain:
 * <pre>
 *   ChecksumDecorator(AESDecorator(CompressionDecorator(base)))
 * </pre>
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class PipelineBuilder {

    private PipelineBuilder() {}

    /**
     * Builds a pipeline from the bundled {@code pipeline.json}.
     *
     * @param baseStrategy the innermost strategy (algorithm to use at the core)
     * @return the fully assembled decorator chain
     */
    public static EncryptionStrategy buildFromConfig(EncryptionStrategy baseStrategy) {
        List<String> steps = readStepsFromJson();
        return buildChain(baseStrategy, steps);
    }

    /**
     * Builds a pipeline from an explicit list of step names.
     *
     * @param baseStrategy the core strategy
     * @param steps        ordered list of decorator names
     * @return the fully assembled decorator chain
     */
    public static EncryptionStrategy buildChain(EncryptionStrategy baseStrategy, List<String> steps) {
        EncryptionStrategy current = baseStrategy;

        // Wrap in reverse order so first step in list executes first on encrypt
        for (int i = steps.size() - 1; i >= 0; i--) {
            current = wrapStep(steps.get(i), current);
        }
        return current;
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    private static EncryptionStrategy wrapStep(String step, EncryptionStrategy inner) {
        return switch (step.toUpperCase().trim()) {
            case "COMPRESS"  -> new CompressionDecorator(inner);
            case "AES"       -> new AESDecorator(inner);
            case "CHECKSUM"  -> new ChecksumDecorator(inner);
            case "REVERSE"   -> new ReverseDecorator(inner);
            case "XOR"       -> {
                // Create a new XOR decorator wrapping the inner strategy
                yield new com.securevault.decorators.EncryptionDecorator(inner) {
                    private final com.securevault.encryption.XORStrategy xor =
                            new com.securevault.encryption.XORStrategy();
                    @Override public byte[] encrypt(byte[] d) { return xor.encrypt(inner.encrypt(d)); }
                    @Override public byte[] decrypt(byte[] d) { return inner.decrypt(xor.decrypt(d)); }
                    @Override public String getAlgorithmName() { return "XOR+" + inner.getAlgorithmName(); }
                };
            }
            default -> throw new EncryptionException(
                    "Unknown pipeline step: " + step,
                    EncryptionException.INVALID_PIPELINE
            );
        };
    }

    /**
     * Minimal JSON parser for the steps array — avoids pulling in a JSON library.
     *
     * <p>Parses a JSON string like {@code {"steps":["A","B","C"]}} and extracts
     * the string array values.
     */
    private static List<String> readStepsFromJson() {
        try (InputStream in = PipelineBuilder.class.getClassLoader()
                .getResourceAsStream("pipeline.json")) {
            if (in == null) {
                // Fallback: COMPRESS → AES → CHECKSUM
                return List.of("COMPRESS", "AES", "CHECKSUM");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            // Extract the "steps" array content
            int start = json.indexOf("[");
            int end   = json.indexOf("]");
            if (start == -1 || end == -1) {
                return List.of("COMPRESS", "AES", "CHECKSUM");
            }
            String arrayContent = json.substring(start + 1, end);
            List<String> steps  = new ArrayList<>();
            for (String token : arrayContent.split(",")) {
                String cleaned = token.replaceAll("[\"\\s]", "").trim();
                if (!cleaned.isEmpty()) steps.add(cleaned);
            }
            return steps;
        } catch (Exception e) {
            throw new EncryptionException("Failed to read pipeline.json: " + e.getMessage(),
                    EncryptionException.INVALID_PIPELINE, e);
        }
    }
}
