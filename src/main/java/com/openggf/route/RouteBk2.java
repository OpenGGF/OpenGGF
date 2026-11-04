package com.openggf.route;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Derives BizHawk movies from tapes. The key is BizHawk's Genesis three-button key
 * ({@code #Power|Reset|#P1 Up|...|P1 A|P1 B|P1 C|P1 Start|#P2 ...}), the header uses BizHawk's
 * {@code Key value} lines, and entry timestamps are fixed so output is byte-deterministic.
 */
final class RouteBk2 {
    static final String LOG_KEY = "#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";
    /** gpgx three-button pads on both ports, as the committed BizHawk 2.11 trace movies use. */
    static final String SYNC_SETTINGS = "{\"o\":{\"$type\":\"BizHawk.Emulation.Cores.Consoles.Sega.gpgx.GPGX+GPGXSyncSettings, "
            + "BizHawk.Emulation.Cores\",\"UseSixButton\":false,\"ControlTypeLeft\":1,\"ControlTypeRight\":1,\"Region\":0,"
            + "\"ForceVDP\":0,\"LoadBIOS\":false,\"Overscan\":3,\"GGExtra\":false,\"SMSFMSoundChip\":1,\"GenesisFMSoundChip\":0,"
            + "\"Filter\":0,\"LowPassRange\":26214,\"LowFreq\":880,\"HighFreq\":5000,\"LowGain\":1.0,\"MidGain\":1.0,"
            + "\"HighGain\":1.0,\"BackdropColor\":4294902015,\"SpritesAlwaysOnTop\":false}}";
    private static final long FIXED_TIME = 315532800000L; // 1980-01-01, the zip epoch

    private RouteBk2() {
    }

    static String pad(int lane) {
        char[] field = "........".toCharArray();
        if ((lane & PhysicalButton.UP.bit()) != 0) field[0] = 'U';
        if ((lane & PhysicalButton.DOWN.bit()) != 0) field[1] = 'D';
        if ((lane & PhysicalButton.LEFT.bit()) != 0) field[2] = 'L';
        if ((lane & PhysicalButton.RIGHT.bit()) != 0) field[3] = 'R';
        if ((lane & PhysicalButton.A.bit()) != 0) field[4] = 'A';
        if ((lane & PhysicalButton.B.bit()) != 0) field[5] = 'B';
        if ((lane & PhysicalButton.C.bit()) != 0) field[6] = 'C';
        if ((lane & PhysicalButton.START.bit()) != 0) field[7] = 'S';
        return new String(field);
    }

    static String line(PadRow row) {
        return "|..|" + pad(row.p1()) + "|" + pad(row.p2()) + "|";
    }

    /** Writes a standalone movie of {@code tape} for {@code rom} and proves the loader reads every row back. */
    static void write(Path out, RomIdentity rom, InputTape tape) throws IOException {
        String header = "MovieVersion BizHawk v2.0.0\n"
                + "Author OpenGGF RouteFiles\n"
                + "emuVersion Version 2.11\n"
                + "OriginalEmuVersion Version 2.11\n"
                + "Platform GEN\n"
                + "GameName " + rom.game() + "\n"
                + "SHA1 " + rom.sha1() + "\n"
                + "Core Genplus-gx\n";
        var log = new StringBuilder("[Input]\nLogKey:").append(LOG_KEY).append('\n');
        for (int i = 0; i < tape.size(); i++) log.append(line(tape.row(i))).append('\n');
        log.append("[/Input]\n");
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream stream = Files.newOutputStream(out); var zip = new ZipOutputStream(stream)) {
            entry(zip, "Header.txt", header);
            entry(zip, "Comments.txt", "");
            entry(zip, "Subtitles.txt", "");
            entry(zip, "SyncSettings.json", SYNC_SETTINGS);
            entry(zip, "Input Log.txt", log.toString());
        }
        Bk2Movie loaded = new Bk2MovieLoader().load(out);
        if (loaded.getFrameCount() != tape.size()) {
            throw new IOException("exported " + tape.size() + " rows but the loader read " + loaded.getFrameCount());
        }
        for (int i = 0; i < tape.size(); i++) {
            if (!PadRow.fromEngine(loaded.getFrames().get(i)).equals(tape.row(i))) {
                throw new IOException("export round trip differs at row " + i + ": " + loaded.getFrames().get(i).rawLine());
            }
        }
        if (!rom.sha1().equals(loaded.getHeaderMetadata().get("SHA1"))) {
            throw new IOException("exported header does not carry the ROM SHA1");
        }
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        var entry = new ZipEntry(name);
        entry.setTime(FIXED_TIME);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
