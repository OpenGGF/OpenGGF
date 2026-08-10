package com.openggf.tools.audio.completerun;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Baseline;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CaptureCounts;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Frame;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Lifecycle;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Metadata;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Record;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Terminal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Strict bounded storage and create-new publication for complete-run audio captures. */
public final class CompleteRunAudioCaptureStore {
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final ObjectMapper JSON = new ObjectMapper(JSON_FACTORY);
    private final AtomicMover atomicMover;

    public CompleteRunAudioCaptureStore() {
        this(Files::move);
    }

    CompleteRunAudioCaptureStore(AtomicMover atomicMover) {
        this.atomicMover = Objects.requireNonNull(atomicMover, "atomic mover");
    }

    public void writeNew(Path output, Metadata metadata, Iterator<Record> records) throws IOException {
        try (Writer writer = writeNew(output, metadata)) {
            while (records.hasNext()) {
                writer.append(records.next());
            }
        }
    }

    public Writer writeNew(Path output, Metadata metadata) throws IOException {
        Path destination = output.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("capture output must have a parent directory");
        }
        if (Files.exists(destination)) {
            throw new FileAlreadyExistsException(destination.toString());
        }
        return new StoreWriter(destination, Files.createTempDirectory(parent, ".audio-staging-"), metadata);
    }

    public Reader read(Path capture) throws IOException {
        return new StoreReader(capture.toAbsolutePath().normalize());
    }

    @FunctionalInterface
    interface AtomicMover {
        Path move(Path source, Path target, java.nio.file.CopyOption... options) throws IOException;
    }

    public interface Writer extends CompleteRunAudioRecordSink { }

    public interface Reader extends Iterator<Record>, AutoCloseable {
        Metadata metadata();
        @Override void close() throws IOException;
    }

    private final class StoreWriter implements Writer {
        private final Path destination;
        private final Path staging;
        private final Path chunks;
        private final Metadata metadata;
        private final MessageDigest rootDigest = sha256();
        private final List<Chunk> completeChunks = new ArrayList<>();
        private ChunkWriter current;
        private boolean baselineSeen;
        private boolean terminalSeen;
        private boolean closed;
        private String root;
        private int frames;
        private long requests;
        private long services;
        private long decisions;
        private long ym;
        private long psg;
        private long lifecycles;

        StoreWriter(Path destination, Path staging, Metadata metadata) throws IOException {
            this.destination = destination;
            this.staging = staging;
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.chunks = Files.createDirectory(staging.resolve("chunks"));
        }

        @Override
        public void append(Record record) throws IOException {
            requireOpen();
            if (terminalSeen) {
                throw new IllegalArgumentException("terminal must be the final complete-run record");
            }
            if (record instanceof Baseline) {
                if (baselineSeen || frames != 0) {
                    throw new IllegalArgumentException("one baseline must precede all frame rows");
                }
                baselineSeen = true;
            } else if (record instanceof Frame frame) {
                if (!baselineSeen) {
                    throw new IllegalArgumentException("baseline is required before frames");
                }
                if (frame.absoluteFrame() != metadata.fixture().firstFrame() + frames) {
                    throw new IllegalArgumentException("frame rows must be contiguous from metadata first frame");
                }
                if (current != null && current.frameRows == CompleteRunAudioTrace.CHUNK_FRAME_ROWS) {
                    finishChunk();
                }
                ChunkWriter chunk = current();
                count(frame);
                frames++;
                chunk.frameRows++;
                current.frameEnd = frame.absoluteFrame() + 1;
            } else if (record instanceof Lifecycle) {
                if (!baselineSeen) {
                    throw new IllegalArgumentException("baseline is required before lifecycle records");
                }
                lifecycles++;
            } else if (record instanceof Terminal terminal) {
                if (!baselineSeen) {
                    throw new IllegalArgumentException("baseline is required before terminal");
                }
                root = digest(rootDigest);
                if (!root.equals(terminal.rootDigest())) {
                    throw new IllegalArgumentException("terminal root digest does not match emitted canonical records");
                }
                metadata.validateTerminal(terminal, counts());
                terminalSeen = true;
            } else {
                throw new IllegalArgumentException("unsupported complete-run record type: " + record.getClass().getName());
            }
            write(record);
            if (!(record instanceof Terminal)) {
                update(rootDigest, CompleteRunAudioJson.writeRecord(record));
            }
        }

        private void count(Frame frame) {
            requests = Math.addExact(requests, frame.requests().size());
            for (var service : frame.services()) {
                services = Math.addExact(services, 1);
                decisions = Math.addExact(decisions, service.decisions().size());
                for (var event : service.chipEvents()) {
                    if (event instanceof CompleteRunAudioTrace.YmWrite) ym = Math.addExact(ym, 1);
                    else psg = Math.addExact(psg, 1);
                }
            }
        }

        private CaptureCounts counts() {
            return new CaptureCounts(frames, requests, services, decisions, ym, psg, lifecycles);
        }

        private ChunkWriter current() throws IOException {
            if (current == null) {
                String file = String.format("%06d.jsonl.gz", completeChunks.size());
                Path path = chunks.resolve(file);
                MessageDigest raw = sha256();
                MessageDigest compressed = sha256();
                OutputStream rawOutput = new DigestOutputStream(Files.newOutputStream(path), compressed);
                // JDK GZIPOutputStream emits MTIME=0; the fixed default buffer and level make output repeatable.
                GZIPOutputStream gzip = new GZIPOutputStream(rawOutput, 8192, true);
                current = new ChunkWriter(file, path, rawOutput, gzip, raw, compressed,
                        metadata.fixture().firstFrame() + frames);
            }
            return current;
        }

        private void write(Record record) throws IOException {
            ChunkWriter writer = current();
            String json = CompleteRunAudioJson.writeRecord(record);
            byte[] bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
            writer.gzip.write(bytes);
            writer.raw.update(bytes);
        }

        private void finishChunk() throws IOException {
            if (current == null) return;
            ChunkWriter writer = current;
            writer.gzip.close();
            String compressed = digest(writer.compressedDigest);
            String raw = digest(writer.raw);
            completeChunks.add(new Chunk(writer.file, writer.frameRows, writer.frameStart, writer.frameEnd,
                    compressed, raw));
            current = null;
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                if (!baselineSeen || !terminalSeen) {
                    throw new IllegalArgumentException("capture must contain a baseline and terminal");
                }
                finishChunk();
                writeManifest(staging.resolve("manifest.json"), metadata, completeChunks, root);
                try (Reader reader = new StoreReader(staging)) {
                    while (reader.hasNext()) reader.next();
                }
                atomicMover.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException failure) {
                deleteTree(staging);
                throw failure;
            } catch (Throwable failure) {
                deleteTree(staging);
                if (failure instanceof IOException io) throw io;
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IOException("capture publication failed", failure);
            }
        }

        private void requireOpen() throws IOException {
            if (closed) throw new IOException("capture writer is closed");
        }
    }

    private static final class StoreReader implements Reader {
        private final Path root;
        private final Metadata metadata;
        private final List<Chunk> chunks;
        private final String expectedRoot;
        private final MessageDigest rootDigest = sha256();
        private final Validator validator;
        private int chunkIndex;
        private Chunk expectedChunk;
        private BufferedReader input;
        private DigestInputStream compressedInput;
        private MessageDigest rawDigest;
        private Record next;
        private boolean complete;

        StoreReader(Path root) throws IOException {
            this.root = root;
            Manifest manifest = parseManifest(root.resolve("manifest.json"));
            metadata = manifest.metadata;
            chunks = manifest.chunks;
            expectedRoot = manifest.root;
            validator = new Validator(metadata);
        }

        @Override public Metadata metadata() { return metadata; }

        @Override
        public boolean hasNext() {
            if (next != null) return true;
            if (complete) return false;
            try {
                while (true) {
                    if (input == null) openChunk();
                    String line = input.readLine();
                    if (line != null) {
                        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                        rawDigest.update(bytes);
                        next = CompleteRunAudioJson.readRecord(line);
                        return true;
                    }
                    closeChunk();
                    if (chunkIndex == chunks.size()) {
                        validator.finish(digest(rootDigest), expectedRoot);
                        complete = true;
                        return false;
                    }
                }
            } catch (IOException failure) {
                throw new IllegalArgumentException("cannot read complete-run capture", failure);
            }
        }

        @Override
        public Record next() {
            if (!hasNext()) throw new NoSuchElementException();
            Record result = next;
            next = null;
            validator.accept(result);
            if (!(result instanceof Terminal)) update(rootDigest, recordJson(result));
            return result;
        }

        private void openChunk() throws IOException {
            if (chunkIndex == chunks.size()) return;
            expectedChunk = chunks.get(chunkIndex++);
            Path file = root.resolve("chunks").resolve(expectedChunk.file);
            if (!file.normalize().getParent().equals(root.resolve("chunks"))) {
                throw new IllegalArgumentException("chunk path escapes capture chunks directory");
            }
            compressedInput = new DigestInputStream(Files.newInputStream(file), sha256());
            input = new BufferedReader(new InputStreamReader(new GZIPInputStream(compressedInput), StandardCharsets.UTF_8));
            rawDigest = sha256();
        }

        private void closeChunk() throws IOException {
            input.close();
            input = null;
            if (!digest(rawDigest).equals(expectedChunk.raw)) {
                throw new IllegalArgumentException("uncompressed chunk digest mismatch");
            }
            if (!digest(compressedInput.getMessageDigest()).equals(expectedChunk.compressed)) {
                throw new IllegalArgumentException("compressed chunk digest mismatch");
            }
            validator.completeChunk(expectedChunk);
            compressedInput = null;
            expectedChunk = null;
        }

        @Override
        public void close() throws IOException {
            complete = true;
            if (input != null) input.close();
        }
    }

    private static final class Validator {
        private final Metadata metadata;
        private boolean baseline;
        private boolean terminal;
        private int frames;
        private int currentChunkFrames;
        private int currentChunkFirst = -1;
        private int currentChunkEnd = -1;
        private long requests, services, decisions, ym, psg, lifecycles;

        Validator(Metadata metadata) { this.metadata = metadata; }

        void accept(Record record) {
            if (terminal) throw new IllegalArgumentException("records follow terminal");
            if (record instanceof Baseline) {
                if (baseline || frames != 0) throw new IllegalArgumentException("baseline must be first");
                baseline = true;
            } else if (record instanceof Frame frame) {
                if (!baseline || frame.absoluteFrame() != metadata.fixture().firstFrame() + frames) {
                    throw new IllegalArgumentException("frame coordinates are not contiguous");
                }
                if (currentChunkFirst < 0) currentChunkFirst = frame.absoluteFrame();
                currentChunkFrames++;
                currentChunkEnd = frame.absoluteFrame() + 1;
                frames++;
                requests = Math.addExact(requests, frame.requests().size());
                for (var service : frame.services()) {
                    services = Math.addExact(services, 1);
                    decisions = Math.addExact(decisions, service.decisions().size());
                    for (var event : service.chipEvents()) {
                        if (event instanceof CompleteRunAudioTrace.YmWrite) ym = Math.addExact(ym, 1); else psg = Math.addExact(psg, 1);
                    }
                }
            } else if (record instanceof Lifecycle) {
                if (!baseline) throw new IllegalArgumentException("lifecycle precedes baseline");
                lifecycles = Math.addExact(lifecycles, 1);
            } else if (record instanceof Terminal value) {
                if (!baseline) throw new IllegalArgumentException("terminal precedes baseline");
                metadata.validateTerminal(value, new CaptureCounts(frames, requests, services, decisions, ym, psg, lifecycles));
                terminal = true;
            }
        }

        void completeChunk(Chunk chunk) {
            if (currentChunkFrames != chunk.frames || currentChunkFirst != chunk.first || currentChunkEnd != chunk.end) {
                throw new IllegalArgumentException("chunk frame bounds or frame-row count mismatch");
            }
            currentChunkFrames = 0;
            currentChunkFirst = -1;
            currentChunkEnd = -1;
        }

        void finish(String root, String expectedRoot) {
            if (!baseline || !terminal) throw new IllegalArgumentException("capture is missing baseline or terminal");
            if (!root.equals(expectedRoot)) throw new IllegalArgumentException("capture root digest mismatch");
        }
    }

    private record Chunk(String file, int frames, int first, int end, String compressed, String raw) { }
    private record Manifest(Metadata metadata, List<Chunk> chunks, String root) { }
    private static final class ChunkWriter {
        private final String file;
        private final Path path;
        private final OutputStream compressedOutput;
        private final GZIPOutputStream gzip;
        private final MessageDigest raw;
        private final MessageDigest compressedDigest;
        private final int frameStart;
        private int frameRows;
        private int frameEnd;

        private ChunkWriter(String file, Path path, OutputStream compressedOutput, GZIPOutputStream gzip,
                MessageDigest raw, MessageDigest compressedDigest, int frameStart) {
            this.file = file;
            this.path = path;
            this.compressedOutput = compressedOutput;
            this.gzip = gzip;
            this.raw = raw;
            this.compressedDigest = compressedDigest;
            this.frameStart = frameStart;
            this.frameEnd = frameStart;
        }
    }

    private static void writeManifest(Path file, Metadata metadata, List<Chunk> chunks, String root) throws IOException {
        try (JsonGenerator json = JSON_FACTORY.createGenerator(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            json.writeStartObject();
            json.writeStringField("schema", CompleteRunAudioTrace.SCHEMA);
            json.writeFieldName("metadata");
            JSON.writeValue(json, metadata);
            json.writeArrayFieldStart("chunks");
            for (Chunk chunk : chunks) {
                json.writeStartObject();
                json.writeStringField("file", chunk.file);
                json.writeNumberField("frame_rows", chunk.frames);
                json.writeNumberField("first_frame", chunk.first);
                json.writeNumberField("exclusive_end", chunk.end);
                json.writeStringField("compressed_sha256", chunk.compressed);
                json.writeStringField("uncompressed_sha256", chunk.raw);
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeStringField("root_digest", root);
            json.writeEndObject();
        }
    }

    private static Manifest parseManifest(Path file) throws IOException {
        try (JsonParser parser = JSON_FACTORY.createParser(Files.newInputStream(file))) {
            JsonNode root = JSON.readTree(parser);
            if (parser.nextToken() != null) throw new IllegalArgumentException("trailing JSON after capture manifest");
            exact(root, Set.of("schema", "metadata", "chunks", "root_digest"), "capture manifest");
            if (!CompleteRunAudioTrace.SCHEMA.equals(text(root, "schema", "capture manifest"))) {
                throw new IllegalArgumentException("unknown capture manifest schema");
            }
            Metadata metadata = JSON.treeToValue(root.required("metadata"), Metadata.class);
            if (!root.required("chunks").isArray() || root.required("chunks").isEmpty()) {
                throw new IllegalArgumentException("capture manifest chunks must be a non-empty array");
            }
            List<Chunk> chunks = new ArrayList<>();
            int expectedFirst = metadata.fixture().firstFrame();
            for (JsonNode node : root.required("chunks")) {
                exact(node, Set.of("file", "frame_rows", "first_frame", "exclusive_end", "compressed_sha256", "uncompressed_sha256"), "chunk manifest entry");
                String name = text(node, "file", "chunk manifest entry");
                int frames = integer(node, "frame_rows", "chunk manifest entry");
                int first = integer(node, "first_frame", "chunk manifest entry");
                int end = integer(node, "exclusive_end", "chunk manifest entry");
                if (!name.matches("[0-9]{6}\\.jsonl\\.gz") || frames < 0 || frames > CompleteRunAudioTrace.CHUNK_FRAME_ROWS || first != expectedFirst || end != first + frames) {
                    throw new IllegalArgumentException("invalid deterministic chunk manifest bounds");
                }
                chunks.add(new Chunk(name, frames, first, end, hash(node, "compressed_sha256"), hash(node, "uncompressed_sha256")));
                expectedFirst = end;
            }
            for (int index = 0; index < chunks.size() - 1; index++) if (chunks.get(index).frames != CompleteRunAudioTrace.CHUNK_FRAME_ROWS) throw new IllegalArgumentException("non-final chunks must contain exactly 4,096 frame rows");
            return new Manifest(metadata, List.copyOf(chunks), hash(root, "root_digest"));
        }
    }

    private static String recordJson(Record record) {
        try { return CompleteRunAudioJson.writeRecord(record); }
        catch (IOException failure) { throw new IllegalArgumentException("cannot canonicalize complete-run record", failure); }
    }
    private static void update(MessageDigest digest, String record) { digest.update((record + "\n").getBytes(StandardCharsets.UTF_8)); }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
    private static String digest(MessageDigest digest) { return HexFormat.of().formatHex(digest.digest()); }
    private static void exact(JsonNode node, Set<String> expected, String label) { if (node == null || !node.isObject() || !node.fieldNames().hasNext() && !expected.isEmpty()) throw new IllegalArgumentException(label + " must be object"); java.util.Set<String> actual = new java.util.LinkedHashSet<>(); node.fieldNames().forEachRemaining(actual::add); if (!actual.equals(expected)) throw new IllegalArgumentException(label + " contains unknown or missing fields"); }
    private static String text(JsonNode node, String name, String label) { JsonNode value = node.get(name); if (value == null || !value.isTextual()) throw new IllegalArgumentException(label + " " + name + " must be text"); return value.textValue(); }
    private static int integer(JsonNode node, String name, String label) { JsonNode value=node.get(name); if(value==null||!value.isIntegralNumber()||!value.canConvertToInt()) throw new IllegalArgumentException(label+" "+name+" must be int"); return value.intValue(); }
    private static String hash(JsonNode node, String name) { String value=text(node,name,"manifest"); if(!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("manifest hash must be canonical SHA-256"); return value; }
    private static void deleteTree(Path root) { try { if (!Files.exists(root)) return; try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } } catch (IOException ignored) { } }
}
