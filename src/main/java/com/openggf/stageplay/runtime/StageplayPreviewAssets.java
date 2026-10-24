package com.openggf.stageplay.runtime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.resources.AuthoredLevelResources;
import com.openggf.stageplay.StageplayDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/** Internal preview-only asset boundary: confined editor root or owner-prepared authored-project bytes. */
public final class StageplayPreviewAssets {
    private static final int MAX_TOTAL_BYTES = AuthoredLevelResources.MAX_ENCODED_BYTES;
    // Include worst-case JSON escaping for every declared portable path and a local root.
    public static final int MAX_DESCRIPTOR_CHARS = (MAX_TOTAL_BYTES + 2) / 3 * 4
            + StageplayAssets.MAX_ASSETS * (1024 * 6 + 16) + 4096 * 6 + 128;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxDocumentLength(MAX_DESCRIPTOR_CHARS)
                    .maxStringLength((MAX_TOTAL_BYTES + 2) / 3 * 4).maxNameLength(1024)
                    .maxNestingDepth(4).maxTokenCount(4096).maxNumberLength(10).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private StageplayPreviewAssets() { }

    public static Source source(Path projectRoot, StageplayAssets.Reader reader) {
        return new Source(projectRoot, reader);
    }

    /** Reader authority takes precedence over a local root, matching registered asset ownership. */
    public record Source(Path projectRoot, StageplayAssets.Reader reader) {
        public String encode(StageplayDocument document) throws IOException {
            var result = JSON.createObjectNode().put("version", 1).put("root", "");
            var projects = result.putObject("projects");
            var paths = projectPaths(document);
            if (!paths.isEmpty() && reader == null) {
                if (projectRoot == null) throw new IOException("Save the scene before previewing its authored set");
                Path root = projectRoot.toRealPath();
                if (!Files.isDirectory(root)) throw new IOException("Scene asset root must be a directory");
                result.put("root", root.toString());
            } else if (reader != null) {
                long total = 0;
                for (String path : paths) {
                    byte[] bytes = reader.read(path);
                    if (bytes == null || bytes.length == 0 || bytes.length > AuthoredLevelResources.MAX_ENCODED_BYTES)
                        throw new IOException("Invalid prepared Stageplay project size: " + path);
                    total += bytes.length;
                    if (total > MAX_TOTAL_BYTES) throw new IOException("Preview projects exceed the 64 MiB budget");
                    projects.put(path, Base64.getEncoder().encodeToString(bytes));
                }
            }
            String encoded = JSON.writeValueAsString(result);
            if (encoded.length() > MAX_DESCRIPTOR_CHARS) throw new IOException("Preview asset descriptor exceeds limit");
            return encoded;
        }

        /** Only engine-packaged decoders consume prepared bytes; no creator callbacks cross processes. */
        public void apply(StageplayResources resources) {
            if (reader != null) resources.setAssetReader(reader);
            else if (projectRoot != null) resources.setProjectRoot(projectRoot);
        }
    }

    public static Source decode(String encoded, StageplayDocument document) throws IOException {
        if (encoded == null || encoded.length() > MAX_DESCRIPTOR_CHARS) throw new IOException("Missing/big preview asset descriptor");
        var root = JSON.readTree(encoded);
        if (root == null || !root.isObject() || root.size() != 3 || !root.path("version").isInt()
                || root.path("version").intValue() != 1 || !root.path("root").isTextual()
                || !root.path("projects").isObject()) throw new IOException("Invalid preview asset descriptor");
        String directory = root.get("root").textValue();
        var projects = root.get("projects");
        if (!directory.isEmpty()) {
            if (directory.length() > 4096 || !projects.isEmpty()) throw new IOException("Invalid preview asset root");
            Path path;
            try { path = Path.of(directory); }
            catch (IllegalArgumentException failure) { throw new IOException("Invalid preview asset root", failure); }
            if (!path.isAbsolute() || !Files.isDirectory(path)) throw new IOException("Invalid preview asset root");
            return source(path.toRealPath(), null);
        }
        var paths = projectPaths(document);
        if (projects.size() != paths.size()) throw new IOException("Preview project snapshots do not match the scene");
        var snapshots = new LinkedHashMap<String, byte[]>();
        long total = 0;
        for (String path : paths) {
            var value = projects.get(path);
            if (value == null || !value.isTextual() || value.textValue().length() > MAX_DESCRIPTOR_CHARS)
                throw new IOException("Missing/invalid preview project snapshot: " + path);
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(value.textValue()); }
            catch (IllegalArgumentException failure) { throw new IOException("Invalid preview project encoding", failure); }
            total += bytes.length;
            if (bytes.length == 0 || bytes.length > AuthoredLevelResources.MAX_ENCODED_BYTES || total > MAX_TOTAL_BYTES)
                throw new IOException("Preview projects exceed the 64 MiB budget");
            snapshots.put(path, bytes);
        }
        return source(null, path -> {
            byte[] bytes = snapshots.get(path);
            if (bytes == null) throw new IOException("Asset was not supplied to the preview worker: " + path);
            return bytes.clone();
        });
    }

    private static List<String> projectPaths(StageplayDocument document) throws IOException {
        var paths = new LinkedHashSet<String>();
        if (!document.set().levelProject().isEmpty()) paths.add(document.set().levelProject());
        document.sceneChanges().stream().map(change -> change.set().levelProject()).filter(path -> !path.isEmpty()).forEach(paths::add);
        if (paths.size() > StageplayAssets.MAX_ASSETS) throw new IOException("Too many preview projects");
        for (String path : paths) ModAssetRoot.requireNormalizedEntry(path);
        return List.copyOf(paths);
    }
}
