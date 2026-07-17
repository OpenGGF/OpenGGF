package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Hash-bound view of the reviewed FBZ visual checkpoint manifest. */
public final class FbzVisualManifest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> STATE_TYPE =
            new TypeReference<>() { };

    private final Path source;
    private final String sha256;
    private final Map<String, Recipe> recipes;

    private FbzVisualManifest(Path source, String sha256, Map<String, Recipe> recipes) {
        this.source = source;
        this.sha256 = sha256;
        this.recipes = Map.copyOf(recipes);
    }

    public static FbzVisualManifest load(Path path, String expectedSha256) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        Path source = path.toAbsolutePath().normalize();
        byte[] bytes = Files.readAllBytes(source);
        String actual = sha256(bytes);
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("FBZ visual manifest hash mismatch: expected "
                    + expectedSha256 + ", got " + actual);
        }

        JsonNode root = MAPPER.readTree(bytes);
        JsonNode setupRecipes = root.path("setup_recipes");
        JsonNode checkpoints = root.path("checkpoints");
        if (!setupRecipes.isObject() || !checkpoints.isArray()) {
            throw new IOException("FBZ visual manifest is missing recipes/checkpoints");
        }

        Map<String, JsonNode> checkpointNodes = new LinkedHashMap<>();
        for (JsonNode checkpoint : checkpoints) {
            String id = requiredText(checkpoint, "id");
            if (checkpointNodes.putIfAbsent(id, checkpoint) != null) {
                throw new IOException("Duplicate FBZ visual checkpoint: " + id);
            }
        }

        Map<String, Recipe> recipes = new LinkedHashMap<>();
        setupRecipes.properties().forEach(entry -> {
            String id = entry.getKey();
            JsonNode setup = entry.getValue();
            JsonNode checkpoint = checkpointNodes.get(id);
            if (checkpoint == null) {
                throw new ManifestStructureException("Recipe has no checkpoint: " + id);
            }
            recipes.put(id, parseRecipe(id, setup, checkpoint));
        });
        if (!recipes.keySet().equals(checkpointNodes.keySet())) {
            Set<String> missing = new LinkedHashSet<>(checkpointNodes.keySet());
            missing.removeAll(recipes.keySet());
            throw new IOException("FBZ checkpoints missing recipes: " + missing);
        }
        return new FbzVisualManifest(source, actual, recipes);
    }

    public Path source() {
        return source;
    }

    public String sha256() {
        return sha256;
    }

    public Set<String> checkpointIds() {
        return new LinkedHashSet<>(recipes.keySet());
    }

    public List<Recipe> recipes() {
        return new ArrayList<>(recipes.values());
    }

    public Recipe recipe(String checkpointId) {
        Recipe recipe = recipes.get(checkpointId);
        if (recipe == null) {
            throw new IllegalArgumentException("Unknown FBZ visual checkpoint: " + checkpointId);
        }
        return recipe;
    }

    private static Recipe parseRecipe(String id, JsonNode setup, JsonNode checkpoint) {
        int act = setup.path("act").asInt(-1);
        if (act != 1 && act != 2) {
            throw new ManifestStructureException("Invalid FBZ act for " + id + ": " + act);
        }
        JsonNode centre = setup.path("centre");
        int centreX = centre.path("x").isIntegralNumber() ? centre.path("x").asInt() : -1;
        int centreY = centre.path("y").isIntegralNumber() ? centre.path("y").asInt() : -1;
        if (centreX < 0 || centreY < 0) {
            JsonNode checkpointPlayer = checkpoint.path("player");
            if (centreX < 0 && checkpointPlayer.path("x").isIntegralNumber()) {
                centreX = checkpointPlayer.path("x").asInt();
            }
            if (centreY < 0 && checkpointPlayer.path("y").isIntegralNumber()) {
                centreY = checkpointPlayer.path("y").asInt();
            }
        }
        Map<String, Object> state = setup.path("state").isObject()
                ? MAPPER.convertValue(setup.path("state"), STATE_TYPE)
                : Map.of();
        Path reference = Path.of(requiredText(checkpoint, "reference"));
        Path output = Path.of(requiredText(checkpoint, "output"));
        List<String> assertions = new ArrayList<>();
        checkpoint.path("assertions").forEach(node -> assertions.add(node.asText()));
        return new Recipe(id, act, centreX, centreY,
                Collections.unmodifiableMap(new LinkedHashMap<>(state)),
                setup.deepCopy(), reference, output, List.copyOf(assertions));
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new ManifestStructureException("Missing FBZ manifest field: " + field);
        }
        return value;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return java.util.HexFormat.of().withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
    }

    public record Recipe(
            String id,
            int act,
            int centreX,
            int centreY,
            Map<String, Object> state,
            JsonNode setup,
            Path reference,
            Path output,
            List<String> assertions) {
    }

    private static final class ManifestStructureException extends RuntimeException {
        private ManifestStructureException(String message) {
            super(message);
        }
    }
}
