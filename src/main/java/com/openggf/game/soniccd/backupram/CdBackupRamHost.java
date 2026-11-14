package com.openggf.game.soniccd.backupram;

import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveSlotSummary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Host persistence, the third backup-RAM owner: it keeps the devices' bytes between runs of the program
 * and nothing else. It never decodes the game's record and never writes a device. {@link CdBackupRam}
 * hands it the device images after a game-driven change; restoring a rewind snapshot does not, so a
 * rewind or replay never writes the host file again.
 */
public interface CdBackupRamHost {
    /** The devices' bytes; {@code cartridge} is null when no RAM cartridge is attached. */
    record Images(byte[] internal, byte[] cartridge) { }

    Optional<Images> load();

    void commit(Images images);

    /** No host file: the backup RAM lives for this session only (tests, replays, capture tools). */
    CdBackupRamHost SESSION = new CdBackupRamHost() {
        @Override public Optional<Images> load() { return Optional.empty(); }
        @Override public void commit(Images images) { }
    };

    /**
     * Atomic durable commits through the engine's save owner: one {@link SaveManager} slot whose payload is
     * the raw device images (Base64). {@code writeSlotAsync} encodes on the calling frame and renames a
     * finished temporary file into place on the save writer.
     */
    static CdBackupRamHost durable(SaveManager saves) {
        return durable(saves, null);
    }

    /**
     * {@link #durable(SaveManager)} on {@code <root>/soniccd/slot0.json}. A slot the save owner cannot read is
     * quarantined by it; one it reads whose images are damaged is moved aside here ({@code .corrupt}), and
     * the next commit writes a fresh slot.
     */
    static CdBackupRamHost durable(java.nio.file.Path root) {
        return durable(new SaveManager(root), root.resolve(Durable.GAME).resolve("slot" + Durable.SLOT + ".json"));
    }

    private static CdBackupRamHost durable(SaveManager saves, java.nio.file.Path slotFile) {
        return new Durable(saves, slotFile);
    }

    /** The durable host; {@code slotFile} is null when the caller supplied only the save owner. */
    final class Durable implements CdBackupRamHost {
        private static final String GAME = "soniccd";
        private static final int SLOT = 0;
        private static final String FORMAT = "soniccd-backup-ram-1";
        private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(Durable.class.getName());
        private final SaveManager saves;
        private final java.nio.file.Path slotFile;

        private Durable(SaveManager saves, java.nio.file.Path slotFile) {
            this.saves = java.util.Objects.requireNonNull(saves, "saves");
            this.slotFile = slotFile;
        }

        @Override public Optional<Images> load() {
            SaveSlotSummary summary;
            try {
                summary = saves.readSlotSummary(GAME, SLOT);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            if (!summary.hasRecoverablePayload()) return Optional.empty();
            if (!FORMAT.equals(summary.payload().get("format"))) {
                reject("unknown format " + summary.payload().get("format"));
                return Optional.empty();
            }
            Object internal = summary.payload().get("internal");
            Object cartridge = summary.payload().get("cartridge");
            if (!(internal instanceof String text)) {
                reject("no internal backup RAM image");
                return Optional.empty();
            }
            Images images;
            try {
                images = new Images(Base64.getDecoder().decode(text),
                        cartridge instanceof String c ? Base64.getDecoder().decode(c) : null);
            } catch (IllegalArgumentException damaged) {
                reject("the image is not Base64: " + damaged.getMessage());
                return Optional.empty();
            }
            // A file BRAMINIT would stop on (unformatted, unreadable record) cannot come from this host's own
            // commits: it is damage. Set it aside and start as a console with fresh backup RAM does.
            String damage = CdBackupRam.damage(images);
            if (damage != null) {
                reject(damage);
                return Optional.empty();
            }
            return Optional.of(images);
        }

        @Override public void commit(Images images) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("format", FORMAT);
            payload.put("internal", Base64.getEncoder().encodeToString(images.internal()));
            if (images.cartridge() != null) payload.put("cartridge", Base64.getEncoder().encodeToString(images.cartridge()));
            try {
                saves.writeSlotAsync(GAME, SLOT, payload);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        /** The stored file was unusable: keep it aside ({@code .corrupt}) rather than losing it. */
        private void reject(String reason) {
            LOG.warning("Sonic CD backup RAM save rejected (" + reason + "); starting from formatted backup RAM");
            if (slotFile == null) return;
            saves.flushPendingWrites();
            try {
                if (java.nio.file.Files.exists(slotFile)) {
                    java.nio.file.Path aside = com.openggf.util.QuarantineFiles.uniqueCorruptSibling(slotFile);
                    java.nio.file.Files.move(slotFile, aside);
                    LOG.warning("Kept the rejected save as " + aside);
                }
            } catch (IOException failure) {
                LOG.warning("Could not move the rejected save aside: " + failure.getMessage());
            }
        }
    }
}
