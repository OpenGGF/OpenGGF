package com.openggf.game.sonic2.competition;

import com.openggf.level.objects.NativeCompetitionObjectStreaming;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * REV01 ObjectsManager_2P_Init/Main (s2.asm:33145-33580).
 * Six shared 12-slot banks hold 256px columns; each player owns three column
 * interests and independent layout/respawn cursors. This is not a view rectangle.
 */
public final class Sonic2CompetitionObjectStreaming implements NativeCompetitionObjectStreaming {
    private static final int FIRST_BANK_SLOT = 56;
    private static final int BANK_SLOTS = 12;
    private static final int GENERAL_FIRST_SLOT = 16;
    private static final int GENERAL_SLOTS = FIRST_BANK_SLOT - GENERAL_FIRST_SLOT;

    private List<ObjectSpawn> spawns = List.of();
    private final int[] banks = new int[6];
    private Cursor[] cursors = {new Cursor(), new Cursor()};
    private boolean initialized;

    private static final class Cursor {
        int front, back, forwardCounter = 1, backwardCounter = 1, cameraBlock;
        final int[] blocks = {0xff, 0xff, 0xff};
        Cursor copy() { return from(snapshot()); }
        CursorState snapshot() {
            return new CursorState(front, back, forwardCounter, backwardCounter, cameraBlock, blocks.clone());
        }
        static Cursor from(CursorState state) {
            Cursor c = new Cursor();
            c.front = state.front; c.back = state.back;
            c.forwardCounter = state.forwardCounter; c.backwardCounter = state.backwardCounter;
            c.cameraBlock = state.cameraBlock;
            System.arraycopy(state.blocks, 0, c.blocks, 0, c.blocks.length);
            return c;
        }
    }

    private record CursorState(int front, int back, int forwardCounter, int backwardCounter,
                               int cameraBlock, int[] blocks) { }
    private record State(CursorState first, CursorState second, int[] banks, boolean initialized) { }

    @Override public int generalAllocationSlotLimitExclusive() { return FIRST_BANK_SLOT; }

    @Override
    public void reset(List<ObjectSpawn> placements, int initialCameraX) {
        // The shared ordinary loader orders 128px columns. Native 2P walks
        // whole 256px columns in ROM record order, recovered from parser ordinals.
        var ordered = new ArrayList<>(placements);
        if (ordered.stream().allMatch(spawn -> spawn.layoutIndex() >= 0)) {
            ordered.sort(Comparator.comparingInt(ObjectSpawn::layoutIndex));
        }
        this.spawns = List.copyOf(ordered);
        Arrays.fill(banks, 0xff);
        cursors = new Cursor[]{new Cursor(), new Cursor()};
        // ObjectsManager_Init (s2.asm:32983-33035) seeds both layout address
        // pairs from P1. Native init copies P1's counters and loads columns 0,1,2.
        int edge = Math.max(0, (initialCameraX & 0xffff) - 0x80) & 0xff80;
        Cursor c = cursors[0];
        while (c.front < spawns.size() && spawns.get(c.front).x() < edge) {
            if (spawns.get(c.front).respawnTracked()) c.forwardCounter = increment(c.forwardCounter);
            c.front++;
        }
        edge -= 0x80;
        while (c.back < spawns.size() && spawns.get(c.back).x() < edge) {
            if (spawns.get(c.back).respawnTracked()) c.backwardCounter = increment(c.backwardCounter);
            c.back++;
        }
        cursors[1] = c.copy();
        initialized = false;
    }

    @Override
    public void update(int firstCameraX, int secondCameraX, Loader loader) {
        if (!initialized) {
            for (int slot = 0; slot < 2; slot++) {
                for (int block = -2; block <= 0; block++) advance(slot, block, true, loader);
            }
            initialized = true;
        }
        updateSlot(0, firstCameraX, loader);
        updateSlot(1, secondCameraX, loader);
    }

    private void updateSlot(int slot, int cameraX, Loader loader) {
        Cursor c = cursors[slot];
        int block = (cameraX & 0xffff) >>> 8;
        if (block == c.cameraBlock) return;
        boolean forward = block > c.cameraBlock;
        c.cameraBlock = block; // ROM stores this even when allocation later fails.
        advance(slot, block, forward, loader);
    }

    private void advance(int slot, int cameraBlock, boolean forward, Loader loader) {
        Cursor c = cursors[slot];
        int target = forward ? cameraBlock + 2 : cameraBlock;
        int outgoing = c.blocks[forward ? 0 : 2];
        if (forward) {
            c.blocks[0] = c.blocks[1]; c.blocks[1] = c.blocks[2]; c.blocks[2] = target & 0xff;
        } else {
            c.blocks[2] = c.blocks[1]; c.blocks[1] = c.blocks[0]; c.blocks[0] = target & 0xff;
        }
        int bank;
        if (contains(cursors[1 - slot].blocks, outgoing)) {
            bank = emptyBank();
        } else {
            bank = findBank(outgoing);
            if (bank < 0) throw new IllegalStateException("Missing outgoing native object block " + outgoing);
            banks[bank] = 0xff;
            loader.unload(FIRST_BANK_SLOT + bank * BANK_SLOTS, BANK_SLOTS);
        }
        boolean alreadyLoaded = findBank(target & 0xff) >= 0;
        if (!alreadyLoaded) {
            if (bank < 0) throw new IllegalStateException("No free native object block");
            banks[bank] = target & 0xff;
        }
        if (forward) {
            while (c.front < spawns.size() && blockOf(c.front) == (target & 0xff)) {
                ObjectSpawn spawn = spawns.get(c.front);
                int respawn = spawn.respawnTracked() ? c.forwardCounter : -1;
                if (spawn.respawnTracked()) c.forwardCounter = increment(c.forwardCounter);
                if (!alreadyLoaded && !load(spawn, respawn, bank, loader)) break;
                c.front++;
            }
            int trim = target - 3;
            if (trim >= 0) {
                while (c.back < spawns.size() && blockOf(c.back) == (trim & 0xff)) {
                    if (spawns.get(c.back).respawnTracked()) c.backwardCounter = increment(c.backwardCounter);
                    c.back++;
                }
            }
        } else {
            while (c.back > 0 && blockOf(c.back - 1) == (target & 0xff)) {
                ObjectSpawn spawn = spawns.get(--c.back);
                if (spawn.respawnTracked()) c.backwardCounter = decrement(c.backwardCounter);
                if (!alreadyLoaded && !load(spawn, spawn.respawnTracked() ? c.backwardCounter : -1, bank, loader)) {
                    // ObjMan2P_GoingBackward.fullSST rolls back pointer/counter,
                    // but ChkLoadObj_2P's bset latch is deliberately NOT rolled back.
                    if (spawn.respawnTracked()) c.backwardCounter = increment(c.backwardCounter);
                    c.back++;
                    break;
                }
            }
            int trim = target + 3;
            while (c.front > 0 && blockOf(c.front - 1) == (trim & 0xff)) {
                if (spawns.get(c.front - 1).respawnTracked()) c.forwardCounter = decrement(c.forwardCounter);
                c.front--;
            }
        }
    }

    private boolean load(ObjectSpawn spawn, int respawn, int bank, Loader loader) {
        // ChkLoadObj_2P (s2.asm:33631-33672): raw Y bit12 chooses
        // AllocateObject's general pool instead of AllocateObject_2P's bank.
        return (spawn.rawYWord() & 0x1000) != 0
                ? loader.load(spawn, respawn, GENERAL_FIRST_SLOT, GENERAL_SLOTS)
                : loader.load(spawn, respawn, FIRST_BANK_SLOT + bank * BANK_SLOTS, BANK_SLOTS);
    }

    private int blockOf(int index) { return spawns.get(index).x() >>> 8; }
    private int findBank(int block) {
        for (int i = 0; i < banks.length; i++) if (banks[i] == block) return i;
        return -1;
    }
    private int emptyBank() {
        // FindEmptyObjectBlock tests the sign bit, including its shipped byte semantics.
        for (int i = 0; i < banks.length; i++) if ((banks[i] & 0x80) != 0) return i;
        return -1;
    }
    private static boolean contains(int[] blocks, int block) {
        for (int value : blocks) if (value == block) return true;
        return false;
    }
    private static int increment(int value) { return (value + 1) & 0xff; }
    private static int decrement(int value) { return (value - 1) & 0xff; }

    @Override public Object capture() {
        return new State(cursors[0].snapshot(), cursors[1].snapshot(), banks.clone(), initialized);
    }
    @Override public void restore(Object snapshot) {
        State s = (State) snapshot;
        cursors = new Cursor[]{Cursor.from(s.first), Cursor.from(s.second)};
        System.arraycopy(s.banks, 0, banks, 0, banks.length);
        initialized = s.initialized;
    }
    @Override public void swapSlots() {
        // swap_players (s2.asm:26042-26050) exchanges the block interests;
        // teleport_swap_table (s2.asm:26173-26178) exchanges the cursor pairs.
        Cursor c = cursors[0]; cursors[0] = cursors[1]; cursors[1] = c;
    }
}
