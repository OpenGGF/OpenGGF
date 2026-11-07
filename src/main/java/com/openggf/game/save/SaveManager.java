package com.openggf.game.save;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.dataselect.DataSelectGameProfile;
import com.openggf.util.QuarantineFiles;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.security.MessageDigest.getInstance;

@com.openggf.game.ModApi
public final class SaveManager {

    private static final Logger LOG = Logger.getLogger(SaveManager.class.getName());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SaveFileReader reader;
    private final SaveFileReader profileReader;
    private final ProfileCleanup profileCleanup;

    public SaveManager(Path root) {
        this.root = root;
        this.profileCleanup = Files::deleteIfExists;
        this.reader = file -> mapper.readValue(file.toFile(), MAP_TYPE); // Published legacy entry stays exact.
        this.profileReader = file -> {
            // Actual I/O distinguishes an absent path from denied/unknown access for internal lookup.
            try (var input = Files.newInputStream(file)) {
                return mapper.readValue(input, MAP_TYPE);
            }
        };
    }

    SaveManager(Path root, SaveFileReader reader) {
        this(root, reader, Files::deleteIfExists);
    }

    /** Package-private fault controls affect only internal fresh-profile cleanup, never legacy writes. */
    SaveManager(Path root, SaveFileReader reader, ProfileCleanup profileCleanup) {
        this.root = root;
        this.reader = reader;
        this.profileReader = reader;
        this.profileCleanup = java.util.Objects.requireNonNull(profileCleanup);
    }

    public void writeSlot(String game, int slot, Map<String, Object> payload) throws IOException {
        Path file = slotPath(game, slot);
        byte[] envelope = encodeEnvelope(game, slot, payload);
        submitAndWait(() -> {
            writeAtomically(file, envelope);
            return null;
        });
    }

    /**
     * Frame-thread half of a save: encodes the envelope now, so the file
     * reflects the payload exactly as captured, and hands the disk write to
     * the single save-writer thread. In-game progression saves land on the
     * same frame as an act transition; the temp-file write and atomic rename
     * cost several milliseconds there and have no place in a frame budget.
     *
     * <p>Every save operation shares the writer's submission order. Reads,
     * deletes, synchronous writes, and {@link #flushPendingWrites()} are
     * barriers in that same order, even when their callers use different
     * {@code SaveManager} instances for the same root.
     */
    public void writeSlotAsync(String game, int slot, Map<String, Object> payload) throws IOException {
        Path file = slotPath(game, slot);
        byte[] envelope = encodeEnvelope(game, slot, payload);
        try {
            WRITER.execute(() -> {
                try {
                    writeAtomically(file, envelope);
                } catch (IOException | RuntimeException e) {
                    LOG.log(Level.WARNING, "Failed to write save " + file + ": " + e.getMessage(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            throw new IOException("Save writer is unavailable", e);
        }
    }

    /** Blocks until every save operation queued before this call has finished. */
    public void flushPendingWrites() {
        try {
            submitAndWait(() -> null);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not drain the save writer: " + e.getMessage(), e);
        }
    }

    private byte[] encodeEnvelope(String game, int slot, Map<String, Object> payload) throws IOException {
        String payloadJson = mapper.writeValueAsString(payload);
        // Check the representation the reader actually verifies. Permitted
        // decimals such as BigDecimal("12.3400") decode as Double 12.34 in an
        // untyped JSON map; hashing the original numeric spelling made an
        // untouched save fail its own checksum after that normal conversion.
        String checksumJson = mapper.writeValueAsString(mapper.readValue(payloadJson, MAP_TYPE));
        SaveEnvelope env = new SaveEnvelope(1, game, slot, payload, sha256(checksumJson));
        return mapper.writeValueAsBytes(env);
    }

    private static void writeAtomically(Path file, byte[] envelope) throws IOException {
        writeStaged(file, envelope, (temp, destination) -> {
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }

    /** Same envelope/staging/cleanup; exclusive link publication never replaces an existing name. */
    private static void writeStaged(Path file, byte[] envelope, Publisher publisher) throws IOException {
        writeStaged(file, envelope, publisher, Files::deleteIfExists, null);
    }

    private static void writeStaged(Path file, byte[] envelope, Publisher publisher,
                                    ProfileCleanup cleanup, Publication observed) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, envelope);
            publisher.publish(temp, file);
        } catch (IOException | RuntimeException | Error original) {
            if (observed != null) observed.operationFailure = original;
            throw original;
        } finally {
            try { cleanup.deleteTemporary(temp); }
            catch (IOException | RuntimeException | Error failure) {
                if (observed != null) observed.cleanupFailure = failure;
                throw failure; // Preserve the existing legacy finally/throw behavior.
            }
        }
    }

    private static final class Publication {
        private boolean published;
        private Throwable operationFailure, cleanupFailure;
    }

    @FunctionalInterface
    private interface Publisher { void publish(Path temporary, Path destination) throws IOException; }
    private static final class NewSlotConflict extends IOException {
        private static final long serialVersionUID = 1L;
        private NewSlotConflict(FileAlreadyExistsException original) { super(original); }
    }

    /** One process-wide writer: saves are rare and submission order is part of persistence semantics. */
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "save-writer");
                thread.setDaemon(true);
                return thread;
            });

    static {
        // A daemon writer would otherwise be abandoned by an exiting JVM with a
        // save still queued; drain it before the process goes.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            WRITER.shutdown();
            try {
                WRITER.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "save-writer-drain"));
    }

    public SaveSlotSummary readSlotSummary(String game, int slot) throws IOException {
        return readSlotSummary(game, slot, null);
    }

    public SaveSlotSummary readSlotSummary(String game, int slot, DataSelectGameProfile profile) throws IOException {
        return submitAndWait(() -> readSlotSummaryNow(game, slot, profile));
    }

    private SaveSlotSummary readSlotSummaryNow(String game, int slot, DataSelectGameProfile profile)
            throws IOException {
        Path file = slotPath(game, slot);
        // Preserve the published legacy EMPTY behavior, including this existing existence check.
        if (!Files.exists(file)) return SaveSlotSummary.empty(slot);
        return readSlotObservationNow(game, slot, profile, false).summary;
    }

    /** Owning parser/checksum/quarantine path, shared by legacy summary and internal dispositions. */
    private ProfileLookup readSlotObservationNow(String game, int slot, DataSelectGameProfile profile, boolean internal) {
        Path file = slotPath(game, slot);
        try {
            Map<String, Object> raw = (internal ? profileReader : reader).read(file);
            if (!game.equals(raw.get("game"))) {
                return quarantineCorruptAndObserve(file, game, slot, "wrong game", null, internal);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) raw.get("payload");
            if (payload == null) {
                return quarantineCorruptAndObserve(file, game, slot, "missing payload", null, internal);
            }
            if (profile != null && !profile.isPayloadValid(payload)) {
                return quarantineCorruptAndObserve(file, game, slot, "invalid payload", null, internal);
            }
            String actual = sha256(mapper.writeValueAsString(payload));
            String expected = String.valueOf(raw.get("hash"));
            if (!actual.equals(expected)) LOG.warning("Hash mismatch while reading save " + file);
            boolean valid = actual.equals(expected);
            return new ProfileLookup(this, game, slot,
                    valid ? SaveProfileAccess.Disposition.VALID : SaveProfileAccess.Disposition.HASH_WARNING,
                    new SaveSlotSummary(slot, valid ? SaveSlotState.VALID : SaveSlotState.HASH_WARNING, payload), null, null);
        } catch (SecurityException denied) {
            if (internal) return new ProfileLookup(this, game, slot, SaveProfileAccess.Disposition.ERROR,
                    SaveSlotSummary.unavailable(slot), denied, null);
            return quarantineCorruptAndObserve(file, game, slot, denied.getMessage(), denied, false);
        } catch (JsonProcessingException | RuntimeException ex) {
            return quarantineCorruptAndObserve(file, game, slot, ex.getMessage(), ex, internal);
        } catch (IOException ex) {
            LOG.warning("Transient I/O while reading save " + file + "; leaving it in place: " + ex.getMessage());
            // Legacy callers still receive UNAVAILABLE after their original exists check.
            if (internal && ex instanceof NoSuchFileException absent && file.toString().equals(absent.getFile())) {
                try {
                    var history = quarantineHistory(file);
                    var kind = switch (history) {
                        case NONE -> SaveProfileAccess.Disposition.MISSING;
                        case QUARANTINED -> SaveProfileAccess.Disposition.QUARANTINED_HISTORY;
                        case UNKNOWN -> SaveProfileAccess.Disposition.UNKNOWN_HISTORY;
                    };
                    return new ProfileLookup(this, game, slot, kind, SaveSlotSummary.unavailable(slot), ex, null, Map.of(), ex);
                } catch (IOException | RuntimeException inspection) {
                    return new ProfileLookup(this, game, slot,
                            inspection instanceof IOException ? SaveProfileAccess.Disposition.UNAVAILABLE : SaveProfileAccess.Disposition.ERROR,
                            SaveSlotSummary.unavailable(slot), inspection, null, Map.of(), ex);
                }
            }
            return new ProfileLookup(this, game, slot, SaveProfileAccess.Disposition.UNAVAILABLE,
                    SaveSlotSummary.unavailable(slot), ex, null);
        }
    }

    private ProfileLookup quarantineCorruptAndObserve(Path file, String game, int slot, String reason, Throwable original, boolean internal) {
        Throwable quarantineFailure = null;
        try {
            quarantine(file, reason);
        } catch (IOException qe) {
            quarantineFailure = qe;
            LOG.warning("Failed to quarantine " + file + "; leaving it in place: " + qe.getMessage());
        } catch (RuntimeException qe) {
            if (!internal) throw qe; // Preserve the published legacy path's original failure behavior.
            quarantineFailure = qe;
        }
        return new ProfileLookup(this, game, slot, SaveProfileAccess.Disposition.CORRUPT,
                SaveSlotSummary.empty(slot), original, quarantineFailure);
    }

    private enum History { NONE, QUARANTINED, UNKNOWN }
    /** Exact QuarantineFiles suffix contract; lazy bounded observation only prevents unsafe creation. */
    private static History quarantineHistory(Path file) throws IOException {
        String base = file.getFileName() + ".corrupt";
        final java.nio.file.DirectoryStream<Path> opened;
        try { opened = Files.newDirectoryStream(file.getParent()); }
        catch (NoSuchFileException absentParent) { return History.NONE; }
        try (var children = opened) {
            int observed = 0;
            for (var child : children) {
                if (++observed > 4096) return History.UNKNOWN;
                String name = child.getFileName().toString();
                if (name.equals(file.getFileName().toString())) return History.UNKNOWN;
                if (name.equals(base)) return History.QUARANTINED;
                if (!name.startsWith(base)) continue;
                if (name.startsWith(base + ".")) {
                    String suffix = name.substring(base.length() + 1);
                    try {
                        int value = Integer.parseInt(suffix);
                        if (value > 0 && value < Integer.MAX_VALUE && Integer.toString(value).equals(suffix))
                            return History.QUARANTINED;
                    } catch (NumberFormatException ignored) { }
                }
                return History.UNKNOWN; // Preserve an unknown slot-specific sibling; it supplies no values.
            }
            return History.NONE;
        }
    }

    /** Internal only; direct actual read never treats Files.exists(false) as proven absence. */
    ProfileLookup profileLookup(String game, int slot) {
        try {
            return submitAndWait(() -> {
                var actual = readSlotObservationNow(game, slot, null, true);
                if (actual.disposition != SaveProfileAccess.Disposition.VALID
                        && actual.disposition != SaveProfileAccess.Disposition.HASH_WARNING) return actual;
                return new ProfileLookup(this, game, slot, actual.disposition, actual.summary,
                        actual.failure, actual.quarantineFailure, RuntimeSaveFields.freeze(actual.summary.payload()));
            });
        } catch (IOException | RuntimeException failure) {
            return new ProfileLookup(this, game, slot, SaveProfileAccess.Disposition.ERROR,
                    SaveSlotSummary.unavailable(slot), failure, null);
        }
    }

    /** Original private lookup identity, once, on the same process writer; no replace-on-empty path. */
    ProfileCreation profileCreate(ProfileLookup expected, Map<String,Object> payload) {
        if (expected.owner != this) throw new IllegalArgumentException("Foreign save lookup owner");
        if (expected.disposition != SaveProfileAccess.Disposition.MISSING)
            return new ProfileCreation(SaveProfileAccess.CreateDisposition.NOT_MISSING, null);
        final byte[] envelope;
        try {
            envelope = encodeEnvelope(expected.game, expected.slot, payload);
        } catch (IOException | RuntimeException failure) {
            return new ProfileCreation(SaveProfileAccess.CreateDisposition.FAILED, failure);
        }
        try {
            return submitAndWait(() -> {
                if (expected.creationClaimed) return new ProfileCreation(SaveProfileAccess.CreateDisposition.ALREADY_USED, null);
                expected.creationClaimed = true;
                var recheck = readSlotObservationNow(expected.game, expected.slot, null, true);
                if (recheck.disposition != SaveProfileAccess.Disposition.MISSING) {
                    var kind = recheck.disposition == SaveProfileAccess.Disposition.UNAVAILABLE
                            || recheck.disposition == SaveProfileAccess.Disposition.ERROR
                            ? SaveProfileAccess.CreateDisposition.RECHECK_FAILED : SaveProfileAccess.CreateDisposition.CONFLICT;
                    return new ProfileCreation(kind, recheck.failure, false, null, recheck);
                }
                var observed = new Publication();
                try {
                    writeStaged(slotPath(expected.game, expected.slot), envelope, (temporary, destination) -> {
                        try { Files.createLink(destination, temporary); }
                        catch (FileAlreadyExistsException conflict) { throw new NewSlotConflict(conflict); }
                        observed.published = true; // Actual exclusive link returned; never roll back this final slot.
                    }, profileCleanup, observed);
                    return new ProfileCreation(SaveProfileAccess.CreateDisposition.CREATED, null);
                } catch (IOException | RuntimeException | Error thrown) {
                    Throwable original = observed.operationFailure != null ? observed.operationFailure : thrown;
                    if (observed.published) return new ProfileCreation(SaveProfileAccess.CreateDisposition.CREATED_CLEANUP_FAILED,
                            original, true, observed.cleanupFailure);
                    if (original instanceof NewSlotConflict conflict) return new ProfileCreation(
                            SaveProfileAccess.CreateDisposition.CONFLICT, conflict.getCause(), false, observed.cleanupFailure);
                    return new ProfileCreation(SaveProfileAccess.CreateDisposition.FAILED, original, false, observed.cleanupFailure);
                }
            });
        } catch (IOException | RuntimeException failure) {
            // Existing submitAndWait cancellation cannot prove an already-running operation did not publish.
            var kind = failure instanceof IOException && failure.getCause() instanceof InterruptedException
                    ? SaveProfileAccess.CreateDisposition.COMPLETION_UNKNOWN
                    : SaveProfileAccess.CreateDisposition.SUBMISSION_FAILED;
            return new ProfileCreation(kind, failure);
        }
    }

    /** Private issuance is deliberately not a DTO/serialized lookup entitlement. */
    static final class ProfileLookup {
        final SaveManager owner; final String game; final int slot;
        final SaveProfileAccess.Disposition disposition; final SaveSlotSummary summary;
        final Map<String,Object> payload;
        final Throwable absenceObservation;
        final Throwable failure, quarantineFailure; private boolean creationClaimed;
        private ProfileLookup(SaveManager owner, String game, int slot, SaveProfileAccess.Disposition disposition,
                              SaveSlotSummary summary, Throwable failure, Throwable quarantineFailure) {
            this(owner,game,slot,disposition,summary,failure,quarantineFailure,summary.payload());
        }
        private ProfileLookup(SaveManager owner, String game, int slot, SaveProfileAccess.Disposition disposition,
                              SaveSlotSummary summary, Throwable failure, Throwable quarantineFailure,Map<String,Object> payload) {
            this(owner,game,slot,disposition,summary,failure,quarantineFailure,payload,null);
        }
        private ProfileLookup(SaveManager owner, String game, int slot, SaveProfileAccess.Disposition disposition,
                              SaveSlotSummary summary, Throwable failure, Throwable quarantineFailure,Map<String,Object> payload,Throwable absenceObservation) {
            this.owner=owner;this.game=game;this.slot=slot;this.disposition=disposition;
            this.summary=summary;this.failure=failure;this.quarantineFailure=quarantineFailure;this.payload=payload;this.absenceObservation=absenceObservation;
        }
    }
    static final class ProfileCreation {
        final SaveProfileAccess.CreateDisposition disposition; final Throwable failure, cleanupFailure;
        final boolean persistentPublished; final ProfileLookup recheck;
        private ProfileCreation(SaveProfileAccess.CreateDisposition disposition, Throwable failure) {
            this(disposition, failure, disposition == SaveProfileAccess.CreateDisposition.CREATED, null);
        }
        private ProfileCreation(SaveProfileAccess.CreateDisposition disposition, Throwable failure,
                                boolean persistentPublished, Throwable cleanupFailure) {
            this(disposition,failure,persistentPublished,cleanupFailure,null);
        }
        private ProfileCreation(SaveProfileAccess.CreateDisposition disposition, Throwable failure,
                                boolean persistentPublished, Throwable cleanupFailure,ProfileLookup recheck) {
            this.disposition=disposition;this.failure=failure;this.persistentPublished=persistentPublished;this.cleanupFailure=cleanupFailure;this.recheck=recheck;
        }
    }

    private void quarantine(Path file, String reason) throws IOException {
        LOG.warning("Quarantining corrupt save " + file + ": " + reason);
        Files.move(file, QuarantineFiles.uniqueCorruptSibling(file));
    }

    public void deleteSlot(String game, int slot) {
        Path file = slotPath(game, slot);
        try {
            submitAndWait(() -> {
                Files.deleteIfExists(file);
                return null;
            });
        } catch (IOException e) {
            LOG.warning("Failed to delete save " + file + ": " + e.getMessage());
        }
    }

    private Path slotPath(String game, int slot) {
        return root.resolve(game).resolve("slot" + slot + ".json");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T submitAndWait(Callable<T> operation) throws IOException {
        Future<T> future;
        try {
            future = WRITER.submit(operation);
        } catch (RejectedExecutionException e) {
            throw new IOException("Save writer is unavailable", e);
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            // Do not fall through to an unprotected filesystem operation: the
            // queued operation is cancelled when it has not started, and the
            // caller keeps the interrupt signal for its own shutdown policy.
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the save writer", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Save writer task failed", cause);
        }
    }

    @FunctionalInterface
    interface ProfileCleanup { void deleteTemporary(Path file) throws IOException; }

    @FunctionalInterface
    interface SaveFileReader {
        Map<String, Object> read(Path file) throws IOException;
    }
}
