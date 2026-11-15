package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * File offsets of the tables Sonic CD's player routines read, per stage file, generated
 * from the user's disc by {@code tools/soniccd/player_stage_tables.py}: the four
 * {@code SpecChunks} IDs LevelSizeLoad copies to {@code specialChunks},
 * {@code ObjSonic_Handle3DRamp}'s {@code .ChunkList}, Tidal Tempest's {@code WaterCurrents}
 * records ({@link CdWaterCurrent}), the block check's {@code PlayerBlocks} lists and Stardust
 * Speedway's spark-block lists. The resource holds offsets only;
 * the bytes are read from the loaded stage image.
 */
public final class CdPlayerStageTables {
    private static final String RESOURCE = "/soniccd/player-stage-tables.tsv";
    private static final List<String> COLUMNS = List.of("stage", "specialChunks", "ramp3dChunks", "waterCurrents", "playerBlocks", "sparkBlocks",
            "bumperSlopes");
    /** {@code .ChunkList} ends at the first negative byte ({@code bmi.s .NotFound}, Main.asm:801-802). */
    private static final int MAX_RAMP_CHUNKS = 16;

    /** Offsets in one stage file, or -1 where the stage has no consuming instruction. */
    public record Entry(int specialChunks, int ramp3dChunks, int waterCurrents, int playerBlocks, int sparkBlocks,
                        int bumperSlopes) { }

    private static final Map<CdStagePayload, Entry> ENTRIES = load();

    private CdPlayerStageTables() { }

    public static Entry entry(CdStagePayload payload) {
        return Objects.requireNonNull(ENTRIES.get(Objects.requireNonNull(payload, "payload")),
                () -> "No player table entry for " + payload);
    }

    /** The four {@code specialChunks} bytes: loop, special loop, special roll, roll. */
    public static int[] specialChunks(CdStagePayload payload, CdStageImage image) {
        int at = entry(payload).specialChunks();
        if (at < 0) return null;
        return new int[] {image.u8(at), image.u8(at + 1), image.u8(at + 2), image.u8(at + 3)};
    }

    /** {@code .ChunkList}'s IDs up to its negative terminator. */
    public static int[] ramp3dChunks(CdStagePayload payload, CdStageImage image) {
        int at = entry(payload).ramp3dChunks();
        if (at < 0) return new int[0];
        int count = 0;
        while (count < MAX_RAMP_CHUNKS && (image.u8(at + count) & 0x80) == 0) count++;
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = image.u8(at + i);
        return ids;
    }

    /**
     * One of the block check's {@code PlayerBlocks} lists: the table's word at
     * {@code 2 * list} is the list's offset from the table, and the list is a count word
     * followed by count+1 block IDs ({@code dbf d6}, src/r5/player_block_check.asm:55-69).
     * Null when the stage's block check has no such table.
     */
    public static int[] playerBlocks(CdStagePayload payload, CdStageImage image, int list) {
        int table = entry(payload).playerBlocks();
        if (table < 0) return null;
        int at = table + (short) image.u16(table + 2 * list);
        int[] ids = new int[image.u16(at) + 1];
        for (int i = 0; i < ids.length; i++) ids[i] = image.u16(at + 2 + 2 * i);
        return ids;
    }

    /**
     * Stardust Speedway's spark-block list {@code list} ({@code off_20657A}: the table's word at
     * {@code 2 * list} is the list's offset from the table; the list ends at a negative word,
     * src/r7/player_block_check.asm:28-34). Null when the stage's block check has none.
     */
    public static int[] sparkBlocks(CdStagePayload payload, CdStageImage image, int list) {
        int table = entry(payload).sparkBlocks();
        if (table < 0) return null;
        int at = table + (short) image.u16(table + 2 * list);
        int count = 0;
        while (count < 64 && (image.u16(at + 2 * count) & 0x8000) == 0) count++;
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = image.u16(at + 2 * i);
        return ids;
    }

    /**
     * Collision Chaos's slope-bumper height for column {@code cell} (0-15) of bumper
     * {@code slope} (0-2): {@code PlayerBumperCollide} is three word offsets from the table,
     * each to 16 bytes (src/r3/player_block_check.asm:442-447, 485-497). -1 when the stage has none.
     */
    public static int bumperSlope(CdStagePayload payload, CdStageImage image, int slope, int cell) {
        int table = entry(payload).bumperSlopes();
        if (table < 0) return -1;
        return image.u8(table + (short) image.u16(table + 2 * slope) + cell);
    }

    private static Map<CdStagePayload, Entry> load() {
        Map<CdStagePayload, Entry> entries = new EnumMap<>(CdStagePayload.class);
        try (InputStream stream = CdPlayerStageTables.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
            String line;
            boolean header = false;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                if (!header) {
                    if (!List.of(cells).equals(COLUMNS)) {
                        throw new IllegalStateException(RESOURCE + " has unexpected columns");
                    }
                    header = true;
                    continue;
                }
                CdStagePayload payload = CdStagePayload.valueOf(cells[0]);
                entries.put(payload, new Entry(offset(cells[1]), offset(cells[2]), offset(cells[3]), offset(cells[4]),
                        offset(cells[5]), offset(cells[6])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (entries.size() != CdStagePayload.values().length) {
            throw new IllegalStateException(RESOURCE + " covers " + entries.size() + " of "
                    + CdStagePayload.values().length + " stage payloads");
        }
        return Map.copyOf(entries);
    }

    private static int offset(String cell) {
        return "-".equals(cell) ? -1 : Integer.parseInt(cell, 16);
    }
}
