package com.openggf.mods.state;

import com.openggf.mods.ModStorage;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import static com.openggf.mods.state.ModSaveLoadResult.Status.*;

/**
 * One owner-local text slot with optional validated recovery generation. Individual
 * storage writes are atomic; the primary/backup sequence is not a crash transaction.
 * Keep this instance in the owning scene/module, and call it from that owner's callback.
 * Retries publish the exact encoded checkpoint, without repeating gameplay changes.
 */
@com.openggf.game.ModApi
public final class ModSaveSlot<T> {
    private final ModStorage storage;
    private final String primary;
    private final String backup;
    private final ModSaveCodec<T> codec;
    private boolean loaded;
    private boolean writable;
    private boolean replacementAuthorized;
    private String validated;
    private String pending;

    public ModSaveSlot(ModStorage storage, String primary, String backup, ModSaveCodec<T> codec) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.primary = name(primary);
        this.backup = backup == null ? null : name(backup);
        if (primary.equals(backup)) throw new IllegalArgumentException("Backup must have a distinct name");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public ModSaveSlot(ModStorage storage, String primary, ModSaveCodec<T> codec) {
        this(storage, primary, null, codec);
    }

    private static String name(String name) {
        if (name == null || !name.matches("[a-z0-9][a-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("Invalid owner-local save name");
        return name;
    }

    public ModSaveLoadResult<T> load() {
        loaded = true;
        try {
            Optional<String> text = read(primary);
            if (text.isEmpty() && list().contains(primary)) return reject(UNREADABLE);
            if (text.isPresent()) {
                var decoded = decode(text.get());
                if (decoded.status() == VALID) {
                    validated = text.get();
                    writable = true;
                    return decoded;
                }
                // A newer primary is authoritative: do not silently roll its format back.
                if (decoded.status() == INCOMPATIBLE) return reject(INCOMPATIBLE);
            }
            if (backup != null) {
                Optional<String> recovery = read(backup);
                if (recovery.isEmpty() && list().contains(backup)) return reject(UNREADABLE);
                if (recovery.isPresent()) {
                    var decoded = decode(recovery.get());
                    if (decoded.status() == VALID) {
                        validated = recovery.get();
                        writable = true;
                        return new ModSaveLoadResult<>(RECOVERED, decoded.value());
                    }
                    return reject(decoded.status());
                }
            }
            if (text.isEmpty()) {
                writable = true;
                return ModSaveLoadResult.rejected(ABSENT);
            }
            return reject(CORRUPT);
        } catch (StorageUnavailable unavailable) {
            return reject(UNREADABLE);
        }
    }

    private static final class StorageUnavailable extends RuntimeException {
        StorageUnavailable(UncheckedIOException cause) { super(cause); }
    }

    private Optional<String> read(String name) {
        try { return storage.read(name); }
        catch (UncheckedIOException unavailable) { throw new StorageUnavailable(unavailable); }
    }

    private java.util.List<String> list() {
        try { return storage.list(); }
        catch (UncheckedIOException unavailable) { throw new StorageUnavailable(unavailable); }
    }

    private boolean write(String name, String text) {
        try { return storage.write(name, text); }
        catch (UncheckedIOException unavailable) { throw new StorageUnavailable(unavailable); }
    }

    private ModSaveLoadResult<T> reject(ModSaveLoadResult.Status status) {
        writable = replacementAuthorized;
        return ModSaveLoadResult.rejected(status);
    }

    private ModSaveLoadResult<T> decode(String text) {
        var result = Objects.requireNonNull(codec.decode(text), "codec result");
        if (result.status() != VALID && result.status() != CORRUPT && result.status() != INCOMPATIBLE)
            throw new IllegalStateException("A save codec must return VALID, CORRUPT or INCOMPATIBLE");
        return result;
    }

    /** Call only after the owning UI/command explicitly elects to replace rejected bytes. */
    public void allowReplacement() {
        if (!loaded) load();
        replacementAuthorized = true;
        writable = true;
        pending = null;
    }

    public boolean writable() { return writable; }
    public boolean pending() { return pending != null; }

    public ModSaveWriteResult save(T value) {
        if (!loaded) load();
        if (!writable) return ModSaveWriteResult.REPLACEMENT_REQUIRED;
        String encoded = Objects.requireNonNull(codec.encode(Objects.requireNonNull(value, "value")), "encoded text");
        if (decode(encoded).status() != VALID)
            throw new IllegalArgumentException("Codec encoded an invalid save value");
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ModStorage.MAX_TEXT_BYTES)
            return ModSaveWriteResult.TOO_LARGE;
        pending = encoded;
        return retry();
    }

    /** Retries the retained checkpoint; this never calls the encoder or reapplies awards. */
    public ModSaveWriteResult retry() {
        if (!writable) return ModSaveWriteResult.REPLACEMENT_REQUIRED;
        if (pending == null) return ModSaveWriteResult.SAVED;
        String previous = validated;
        try {
            Optional<String> old = read(primary);
            if (old.isPresent()) {
                var decoded = decode(old.get());
                if (decoded.status() == INCOMPATIBLE && !replacementAuthorized) {
                    writable = false;
                    return ModSaveWriteResult.REPLACEMENT_REQUIRED;
                }
                if (decoded.status() == VALID) previous = old.get();
            }
        } catch (StorageUnavailable unavailable) {
            return ModSaveWriteResult.PRIMARY_FAILED;
        }
        if (backup != null && previous != null) {
            try {
                if (!read(backup).filter(previous::equals).isPresent()
                        && !write(backup, previous)) return ModSaveWriteResult.BACKUP_FAILED;
            } catch (StorageUnavailable unavailable) { return ModSaveWriteResult.BACKUP_FAILED; }
        }
        try {
            if (!write(primary, pending)) return ModSaveWriteResult.PRIMARY_FAILED;
        } catch (StorageUnavailable unavailable) { return ModSaveWriteResult.PRIMARY_FAILED; }
        validated = pending;
        pending = null;
        replacementAuthorized = false;
        return ModSaveWriteResult.SAVED;
    }
}
