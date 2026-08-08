package com.openggf.game.sonic3k.audio.smps;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-backed fixed-point reachability inventory for S3K FF meta coordination
 * commands.
 *
 * <p>The three commands in scope are deliberately not inferred from arbitrary
 * bytes in the SFX bank. The bank contains voices and unrelated streams, so a
 * byte-pattern scan produces false positives. Each loaded track entry is
 * instead decoded to a fixed point over its command/control-flow graph. The
 * worklist follows note durations, jumps, calls, returns, counted loops,
 * conditional loop exits, and continuous-SFX edges until every reachable edge
 * is closed or a malformed/unexplored frontier is reported.</p>
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestSonic3kSmpsMetaCommandReachability {

    private static final Set<Integer> TARGET_META_SUBCOMMANDS = Set.of(0x01, 0x02, 0x03);

    @Test
    void loaderScopedSkSfxAndSkS3MusicHaveClosedControlFlowAndDoNotReachTargetMetaCommands() {
        Rom rom = TestEnvironment.currentRom();
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(rom);
        Set<Integer> metaSubcommands = new HashSet<>();

        int skMusicCount = 0;
        for (int id = 0x01; id <= 0x33; id++) {
            AbstractSmpsData data = loader.loadMusic(id);
            assertNotNull(data, "Missing S&K music stream 0x" + hex(id));
            ControlFlowInventory inventory = inventory(data);
            assertClosed(inventory, "S&K music 0x" + hex(id));
            assertNoMusicMetaPair(data, id, "S&K");
            metaSubcommands.addAll(inventory.metaSubcommands);
            skMusicCount++;
        }

        int s3MusicCount = 0;
        for (int id = 0x01; id <= 0x32; id++) {
            AbstractSmpsData data = loader.loadS3Music(id);
            assertNotNull(data, "Missing S3 music stream 0x" + hex(id));
            ControlFlowInventory inventory = inventory(data);
            assertClosed(inventory, "S3 music 0x" + hex(id));
            assertNoMusicMetaPair(data, id, "S3");
            metaSubcommands.addAll(inventory.metaSubcommands);
            s3MusicCount++;
        }

        int skSfxCount = 0;
        for (int id = Sonic3kSfx.ID_BASE; id <= Sonic3kSfx.ID_MAX; id++) {
            AbstractSmpsData data = loader.loadSfx(id);
            assertNotNull(data, "Missing SFX stream 0x" + hex(id));
            ControlFlowInventory inventory = inventory(data);
            assertClosed(inventory, "SFX 0x" + hex(id));
            metaSubcommands.addAll(inventory.metaSubcommands);
            skSfxCount++;
        }

        assertEquals(0x33, skMusicCount);
        assertEquals(0x32, s3MusicCount);
        assertEquals(Sonic3kSfx.ID_MAX - Sonic3kSfx.ID_BASE + 1, skSfxCount);
        assertTrue(metaSubcommands.contains(0x00),
                "The inventory must observe a live FF00 tempo command");
        assertTrue(metaSubcommands.contains(0x07),
                "The inventory must observe the live SFX FF07 command");
        assertFalse(metaSubcommands.stream().anyMatch(TARGET_META_SUBCOMMANDS::contains),
                () -> "Shipped ROM reached an unimplemented meta command: " + metaSubcommands);
    }

    private static void assertClosed(ControlFlowInventory inventory, String stream) {
        assertTrue(inventory.reachableOffsets > 0, stream + " has no reachable track bytes");
        assertTrue(inventory.terminalOrCycle, stream + " has neither a terminal nor a closed cycle");
        assertTrue(inventory.frontier.isEmpty(),
                () -> stream + " left unexplored control-flow frontier: " + inventory.frontier);
    }

    private static ControlFlowInventory inventory(AbstractSmpsData data) {
        byte[] bytes = data.getData();
        boolean bankSpace = data instanceof Sonic3kSmpsData s3 && s3.getBankData() != null;
        if (bankSpace) bytes = ((Sonic3kSmpsData) data).getBankData();
        ControlFlowInventory result = new ControlFlowInventory();
        ArrayDeque<Integer> work = new ArrayDeque<>();
        for (int start : trackStarts(data, bankSpace)) {
            // Zero/foreign pointers are non-track header slots (for example,
            // DAC data owned by the driver); only resolved stream entries are
            // active control-flow roots.
            if (start >= 0) work.add(start);
        }

        while (!work.isEmpty()) {
            int pos = work.removeFirst();
            if (pos == bytes.length) {
                result.terminalOrCycle = true;
                continue;
            }
            if (pos < 0 || pos > bytes.length) {
                result.frontier.add("offset 0x" + Integer.toHexString(pos));
                continue;
            }
            if (!result.reachable.add(pos)) {
                result.terminalOrCycle = true;
                continue;
            }
            result.reachableOffsets++;

            int command = bytes[pos] & 0xFF;
            if (command < 0x80) {
                enqueue(result, work, pos + 1, bytes.length, "duration");
                continue;
            }
            if (command < 0xE0) {
                int next = pos + 1;
                if (next < bytes.length && (bytes[next] & 0xFF) < 0x80) {
                    next++;
                }
                enqueue(result, work, next, bytes.length, "note");
                continue;
            }

            switch (command) {
                case 0xE3, 0xF2, 0xF9 -> result.terminalOrCycle = true;
                case 0xF6 -> addPointerEdge(result, work, data, pos + 1, false, "goto");
                case 0xF7 -> addPointerEdge(result, work, data, pos + 3, true, "loop");
                case 0xF8 -> addPointerEdge(result, work, data, pos + 1, true, "call");
                case 0xEB -> addPointerEdge(result, work, data, pos + 3, true, "loop-exit");
                case 0xFC -> addPointerEdge(result, work, data, pos + 1, true, "continuous");
                case 0xFF -> {
                    if (pos + 1 >= bytes.length) {
                        result.frontier.add("FF at 0x" + Integer.toHexString(pos) + " lacks subcommand");
                        continue;
                    }
                    int subcommand = bytes[pos + 1] & 0xFF;
                    result.metaSubcommands.add(subcommand);
                    int operands = switch (subcommand) {
                        case 0x00, 0x01, 0x02, 0x04 -> 1;
                        case 0x03 -> 3;
                        case 0x05 -> 4;
                        case 0x06 -> 2;
                        default -> 0;
                    };
                    enqueue(result, work, pos + 2 + operands, bytes.length, "meta");
                }
                default -> enqueue(result, work, pos + 1 + parameterLength(command),
                        bytes.length, "command");
            }
        }
        return result;
    }

    private static void addPointerEdge(ControlFlowInventory result, ArrayDeque<Integer> work,
            AbstractSmpsData data, int pointerOffset, boolean fallThrough, String edgeName) {
        byte[] bytes = data instanceof Sonic3kSmpsData s3 && s3.getBankData() != null
                ? s3.getBankData() : data.getData();
        int afterPointer = pointerOffset + 2;
        if (afterPointer > bytes.length) {
            result.frontier.add(edgeName + " at 0x" + Integer.toHexString(pointerOffset - 1)
                    + " has truncated pointer");
            return;
        }
        int raw = (bytes[pointerOffset] & 0xFF) | ((bytes[pointerOffset + 1] & 0xFF) << 8);
        int base = data instanceof Sonic3kSmpsData s3 && s3.getBankData() != null
                ? s3.getBankZ80Base() : data.getZ80StartAddress();
        int target = raw - base;
        if (target < 0 || target >= bytes.length) {
            target = raw < bytes.length ? raw : -1;
        }
        if (target < 0) {
            if ("call".equals(edgeName)) {
                // S3 music can call a shared bank routine that is outside the
                // loader's per-song blob. It is a closed external edge, not
                // an unexplored in-stream frontier.
                result.terminalOrCycle = true;
            } else {
                result.frontier.add(edgeName + " target 0x" + Integer.toHexString(raw)
                        + " is outside stream");
            }
        } else {
            enqueue(result, work, target, bytes.length, edgeName + " target");
        }
        if (fallThrough) {
            enqueue(result, work, afterPointer, bytes.length, edgeName + " fallthrough");
        }
    }

    private static void enqueue(ControlFlowInventory result, ArrayDeque<Integer> work,
            int next, int length, String edgeName) {
        if (next < 0 || next > length) {
            result.frontier.add(edgeName + " reaches 0x" + Integer.toHexString(next));
        } else {
            work.add(next);
        }
    }

    private static List<Integer> trackStarts(AbstractSmpsData data, boolean bankSpace) {
        List<Integer> starts = new ArrayList<>();
        if (data instanceof SmpsSfxData sfx) {
            for (SmpsSfxData.SmpsSfxTrack track : sfx.getTrackEntries()) {
                starts.add(track.pointer());
            }
            return starts;
        }
        for (int pointer : data.getFmPointers()) {
            if (pointer > 0) starts.add(resolvePointer(pointer, data, bankSpace));
        }
        for (int pointer : data.getPsgPointers()) {
            if (pointer > 0) starts.add(resolvePointer(pointer, data, bankSpace));
        }
        return starts;
    }

    private static int resolvePointer(int pointer, AbstractSmpsData data, boolean bankSpace) {
        if (pointer <= 0) return -1;
        if (bankSpace) {
            Sonic3kSmpsData s3 = (Sonic3kSmpsData) data;
            int relative = pointer - s3.getBankZ80Base();
            return relative >= 0 && relative < s3.getBankData().length ? relative : -1;
        }
        if (pointer < data.getData().length) return pointer;
        int relative = pointer - data.getZ80StartAddress();
        return relative >= 0 && relative < data.getData().length ? relative : -1;
    }

    private static int parameterLength(int command) {
        return switch (command) {
            case 0xE0, 0xE1, 0xE2, 0xE4, 0xE6, 0xE8, 0xEA, 0xEC, 0xED, 0xEF,
                    0xF3, 0xF4, 0xF5, 0xFB, 0xFD -> 1;
            case 0xE5, 0xEE, 0xF1, 0xF6, 0xF8 -> 2;
            case 0xEB, 0xF7, 0xF0, 0xFE -> 4;
            default -> 0;
        };
    }

    private static void assertNoMusicMetaPair(AbstractSmpsData data, int id, String table) {
        byte[] bytes = data.getData();
        for (int pos = 0; pos + 1 < bytes.length; pos++) {
            if ((bytes[pos] & 0xFF) != 0xFF) continue;
            int sub = bytes[pos + 1] & 0xFF;
            int blobOffset = pos;
            assertFalse(TARGET_META_SUBCOMMANDS.contains(sub),
                    () -> table + " music 0x" + hex(id) + " contains FF" + hex(sub)
                            + " at blob offset 0x" + Integer.toHexString(blobOffset));
        }
    }

    private static String hex(int value) {
        return String.format("%02X", value & 0xFF);
    }

    private static final class ControlFlowInventory {
        private final Set<Integer> reachable = new HashSet<>();
        private final Set<String> frontier = new HashSet<>();
        private final Set<Integer> metaSubcommands = new HashSet<>();
        private int reachableOffsets;
        private boolean terminalOrCycle;
    }
}
