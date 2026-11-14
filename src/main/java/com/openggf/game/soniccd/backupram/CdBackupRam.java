package com.openggf.game.soniccd.backupram;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Sonic CD's backup RAM for one session: the internal device and an optional RAM cartridge
 * ({@link CdBackupRamVolume}), the {@code saveDisabled} fallback and its temporary save buffer, and the
 * game's save operations in the order the native programs run them. Records are encoded by
 * {@link CdSaveRecord}; durable storage is {@link CdBackupRamHost}'s. After each game-driven change the
 * changed device bytes go to the host once; a rewind restore replaces the bytes and commits nothing.
 */
public final class CdBackupRam {
    /** How {@code BRAMINIT} (Backup RAM/Initialization/Main.asm:51-110) ends. */
    public enum Init {
        /** Save data found and read, or created; saving enabled. */
        READY,
        /** {@code BuRAMFull} (Initialization/Main.asm:146-161): no room; the message waits for a button, then saving is disabled. */
        FULL,
        /** {@code BuRAMCorrupted} (Initialization/Main.asm:119-125): the message, then {@code ErrorLoop} forever. */
        CORRUPTED,
        /** {@code InternalUnformatted} (Initialization/Main.asm:128-134), then {@code ErrorLoop}. */
        INTERNAL_UNFORMATTED,
        /** {@code CartUnformatted} (Initialization/Main.asm:137-143), then {@code ErrorLoop}. */
        CART_UNFORMATTED;

        /** The native program never returns from these. */
        public boolean fatal() { return this != READY && this != FULL; }
    }

    private final CdBackupRamVolume internal;
    private final CdBackupRamVolume cartridge;
    private final byte[] initialRecord;
    private final CdBackupRamHost host;
    /** {@code saveDisabled} bit 0 (Main CPU Variables.inc:37). */
    private boolean saveDisabled;
    /** {@code SaveDataTemp} (System.inc:372): the save while backup RAM is disabled. */
    private final byte[] temp = new byte[CdSaveRecord.FILE_BYTES];
    private byte[] committedInternal;
    private byte[] committedCartridge;
    private int commits;

    private CdBackupRam(CdBackupRamVolume internal, CdBackupRamVolume cartridge, byte[] initialRecord, CdBackupRamHost host) {
        this.internal = internal;
        this.cartridge = cartridge;
        this.initialRecord = initialRecord.clone();
        this.host = host;
        committedInternal = internal.image();
        committedCartridge = cartridge == null ? null : cartridge.image();
    }

    /**
     * The session's devices: the host's stored images, else a freshly formatted 8 KiB internal device
     * (and a formatted cartridge of {@code cartridgeBytes} when one is attached, 0 for none).
     *
     * @param initialRecord {@link CdBackupRamAssets#initialRecord}, the {@code InitSaveData} bytes from the disc
     */
    public static CdBackupRam open(byte[] initialRecord, CdBackupRamHost host, int cartridgeBytes) {
        Objects.requireNonNull(host, "host");
        Optional<CdBackupRamHost.Images> stored = host.load();
        CdBackupRamVolume internal = stored.map(i -> CdBackupRamVolume.of(i.internal()))
                .orElseGet(() -> CdBackupRamVolume.formatted(CdBackupRamVolume.INTERNAL_BYTES));
        CdBackupRamVolume cartridge = null;
        if (stored.isPresent() && stored.get().cartridge() != null) cartridge = CdBackupRamVolume.of(stored.get().cartridge());
        else if (cartridgeBytes > 0) cartridge = CdBackupRamVolume.formatted(cartridgeBytes);
        return new CdBackupRam(internal, cartridge, initialRecord, host);
    }

    /**
     * Why stored images would stop BRAMINIT ({@link #initialize}), or null when they pass its checks. The durable
     * host uses it to set a damaged save file aside (host policy, not the firmware's: native BRAMINIT stops on its
     * unformatted or corrupted message, Initialization/Main.asm:119-163).
     */
    static String damage(CdBackupRamHost.Images images) {
        try {
            if (images.internal() == null || images.internal().length != CdBackupRamVolume.INTERNAL_BYTES) {
                return "internal image is " + (images.internal() == null ? "absent" : images.internal().length + " bytes");
            }
            CdBackupRamVolume internal = CdBackupRamVolume.of(images.internal());
            if (!internal.isFormatted()) return "internal backup RAM is not formatted";
            if (internal.search(CdSaveRecord.FILE_NAME).isPresent()) {
                Optional<byte[]> read = internal.read(CdSaveRecord.FILE_NAME);
                if (read.isEmpty() || read.get().length != CdSaveRecord.FILE_BYTES) return "the save record does not read back";
            }
            if (images.cartridge() != null && !CdBackupRamVolume.of(images.cartridge()).isFormatted()) {
                return "cartridge backup RAM is not formatted";
            }
            return null;
        } catch (IllegalArgumentException | IndexOutOfBoundsException unreadable) {
            // A user's save file is outside input: a bad size or directory reads as damage, not a crash.
            return "unreadable image: " + unreadable.getMessage();
        }
    }

    public CdBackupRamVolume internal() { return internal; }

    /** The RAM cartridge, or null when none is attached ({@code DetectRAMCart} not found, Main CPU Functions.asm:364-470). */
    public CdBackupRamVolume cartridge() { return cartridge; }

    public boolean saveDisabled() { return saveDisabled; }

    /** Device images sent to the host so far (each one a game-driven change). */
    public int commits() { return commits; }

    /**
     * {@code BRAMINIT} as IPX runs it at start-up ({@code SCMD_BURAMINIT}, IPX.asm:41-45): {@code InitBuRAM},
     * then search, read or create {@code SONICCD____}. IPX sets {@code saveDisabled} when it does not return 0.
     */
    public Init initialize() {
        // InitBuRAM (Main CPU Functions.asm:307-361): a found but unformatted cartridge is -2; then internal.
        if (cartridge != null && !cartridge.isFormatted()) return Init.CART_UNFORMATTED;
        if (!internal.isFormatted()) return Init.INTERNAL_UNFORMATTED;
        Init result;
        if (internal.search(CdSaveRecord.FILE_NAME).isPresent()) {
            Optional<byte[]> read = internal.read(CdSaveRecord.FILE_NAME);           // ReadBuRAM; bne BuRAMCorrupted
            if (read.isEmpty() || read.get().length != CdSaveRecord.FILE_BYTES) return Init.CORRUPTED;
            saveDisabled = false;
            System.arraycopy(read.get(), 0, temp, 0, temp.length);                    // Cmd_ReadSaveData: WriteTempSaveData
            result = Init.READY;
        } else {
            // .NotFound (Initialization/Main.asm:90-102): InitSaveData, WriteBuRAM (BRMWRITE then BRMVERIFY).
            boolean written = internal.write(CdSaveRecord.FILE_NAME, 0, CdSaveRecord.FILE_BLOCKS, initialRecord)
                    && internal.verify(CdSaveRecord.FILE_NAME, CdSaveRecord.FILE_BLOCKS, initialRecord);
            System.arraycopy(initialRecord, 0, temp, 0, temp.length);                 // JmpTo_WriteSaveData writes temp either way
            if (written) {
                saveDisabled = false;
                internal.write(CdSaveRecord.FILE_NAME, 0, CdSaveRecord.FILE_BLOCKS, initialRecord);
                result = Init.READY;
            } else {
                saveDisabled = true;
                internal.delete(CdSaveRecord.FILE_NAME);                              // DeleteBuRAM
                result = Init.FULL;
            }
        }
        commitIfChanged();
        return result;
    }

    /**
     * IPX {@code GetBuRAMData} (IPX.asm:1437-1446): {@code SCMD_READSAVE} ({@code SPCmd_ReadSaveData},
     * SPX.asm:668-685, BRMREAD into Word RAM) or {@code SCMD_RDTEMPSAVE} when saving is disabled.
     */
    public CdSaveRecord readSave() {
        if (saveDisabled) return CdSaveRecord.decode(temp);
        Optional<byte[]> read = internal.read(CdSaveRecord.FILE_NAME);
        // .ReadData: bcs .ReadData -- native retries a failed read forever; it never returns.
        if (read.isEmpty()) throw new IllegalStateException("SONICCD____ is missing: SPCmd_ReadSaveData retries BRMREAD forever (SPX.asm:675-683)");
        return CdSaveRecord.decode(read.get());
    }

    /**
     * {@code SCMD_WRITESAVE} ({@code SPCmd_WriteSaveData}, SPX.asm:689-706: BRMWRITE "SONICCD____", mode 0, 11
     * blocks) or {@code SCMD_WRTEMPSAVE} (SPX.asm:728-741) when saving is disabled.
     */
    public void writeSave(CdSaveRecord record) {
        byte[] bytes = record.encode();
        if (saveDisabled) {
            System.arraycopy(bytes, 0, temp, 0, temp.length);
            return;
        }
        // .WriteData: bcs .WriteData -- native retries forever when the write does not fit.
        if (!internal.write(CdSaveRecord.FILE_NAME, 0, CdSaveRecord.FILE_BLOCKS, bytes)) {
            throw new IllegalStateException("No room for SONICCD____: SPCmd_WriteSaveData retries BRMWRITE forever (SPX.asm:696-703)");
        }
        commitIfChanged();
    }

    /** IPX {@code ReadSaveData} (IPX.asm:1420-1432). */
    public CdSaveVariables readVariables() { return readSave().variables(); }

    /** IPX {@code WriteSaveData} (IPX.asm:1448-1475): read the record back, store the variables, write it. */
    public void writeVariables(CdSaveVariables variables) {
        CdSaveRecord record = readSave();
        record.store(variables);
        writeSave(record);
    }

    /** A manager operation changed a device: send it to the host. */
    public void deviceChanged() { commitIfChanged(); }

    private void commitIfChanged() {
        byte[] now = internal.image();
        byte[] cart = cartridge == null ? null : cartridge.image();
        if (Arrays.equals(now, committedInternal) && Arrays.equals(cart, committedCartridge)) return;
        committedInternal = now;
        committedCartridge = cart;
        commits++;
        host.commit(new CdBackupRamHost.Images(now, cart));
    }

    /** Device bytes, cartridge bytes, the disabled flag and the temporary buffer. */
    public record Snapshot(byte[] internal, byte[] cartridge, boolean saveDisabled, byte[] temp) { }

    /**
     * The rewind adapter. Restoring puts the devices back as they were and marks those bytes as the
     * committed ones, so the restore itself and the frames replayed after it send nothing to the host until
     * the game changes the devices again.
     */
    public RewindSnapshottable<Snapshot> rewindAdapter() {
        return new RewindSnapshottable<>() {
            @Override public String key() { return "soniccd.backupRam"; }

            private Snapshot last;
            private int lastInternal = -1, lastCartridge = -1;

            @Override public Snapshot capture() {
                // Devices change only on saves; reuse the previous images while their versions stand.
                int cart = cartridge == null ? 0 : cartridge.version();
                if (last == null || lastInternal != internal.version() || lastCartridge != cart
                        || last.saveDisabled() != saveDisabled || !Arrays.equals(last.temp(), temp)) {
                    last = new Snapshot(internal.image(), cartridge == null ? null : cartridge.image(), saveDisabled, temp.clone());
                    lastInternal = internal.version();
                    lastCartridge = cart;
                }
                return last;
            }

            @Override public void restore(Snapshot s) {
                internal.restore(s.internal());
                if (cartridge != null && s.cartridge() != null) cartridge.restore(s.cartridge());
                saveDisabled = s.saveDisabled();
                System.arraycopy(s.temp(), 0, temp, 0, temp.length);
                committedInternal = internal.image();
                committedCartridge = cartridge == null ? null : cartridge.image();
            }
        };
    }
}
