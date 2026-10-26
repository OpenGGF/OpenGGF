package com.openggf.game.sonic2.competition.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Independent human checkpoint RAM banks, without actor, camera or campaign checkpoint ownership.
 *
 * <p>Obj79_SaveData / Obj79_SaveDataPlayer2 (s2.asm:44733-44772) save each human's full subtype,
 * the POST's center position, art word, solid-bit pair and counter values. Only P1 also saves the
 * primary environment. Independent 2P respawn reads position/art/solid bits, never the saved timer,
 * rings or environment: Obj01_ResetLevel_Part2 (s2.asm:38319-38337) and
 * Obj02_ResetLevel_Part3 (s2.asm:41211-41226). The host owns those live writes and camera settling.
 * The layer byte is the engine's collision-layer projection accompanying the raw solid-bit pair.
 *
 * <p>Seed both initial banks after native player initialization, before either can die. This models
 * Obj01_Init (s2.asm:36190-36199) and Obj02_Init_Continued (s2.asm:38934-38938). A null snapshot entry
 * means that slot has not yet been seeded; it is never an implicit checkpoint at coordinate zero.
 */
public final class Sonic2CompetitionCheckpoints {
    private static final int SLOT_COUNT = 2;
    private final SavedPlayer[] players = new SavedPlayer[SLOT_COUNT];

    /** Values saved by Obj79; storing a timer does not authorize restoring it on independent death. */
    public record SavedPlayer(int lastStarPost, int x, int y, int artTile, int solidBits, byte layer,
                              int rings, int extraLifeFlags, long timerFrames,
                              PrimaryEnvironment primaryEnvironment) {
        public SavedPlayer {
            lastStarPost &= 0xFF;
            x &= 0xFFFF;
            y &= 0xFFFF;
            artTile &= 0xFFFF;
            solidBits &= 0xFFFF;
            rings &= 0xFFFF;
            extraLifeFlags &= 0xFF;
            if (timerFrames < 0) throw new IllegalArgumentException("Negative saved checkpoint timer");
        }
    }

    /** P1-only save fields; there is no corresponding native P2 environment bank. */
    public record PrimaryEnvironment(int dynamicResizeRoutine, int cameraMaxY, int cameraX, int cameraY,
                                     int bgX, int bgY, int bg2X, int bg2Y, int bg3X, int bg3Y,
                                     int waterLevel, int waterRoutine, int waterFullscreen) {
        public PrimaryEnvironment {
            dynamicResizeRoutine &= 0xFF;
            cameraMaxY &= 0xFFFF;
            cameraX &= 0xFFFF;
            cameraY &= 0xFFFF;
            bgX &= 0xFFFF;
            bgY &= 0xFFFF;
            bg2X &= 0xFFFF;
            bg2Y &= 0xFFFF;
            bg3X &= 0xFFFF;
            bg3Y &= 0xFFFF;
            waterLevel &= 0xFFFF;
            waterRoutine &= 0xFF;
            waterFullscreen &= 0xFF;
        }
    }

    /** Initial coordinates are each player's own center, before any checkpoint activation. */
    public void seed(int slot, int x, int y, int artTile, int solidBits, byte layer) {
        players[Objects.checkIndex(slot, SLOT_COUNT)] =
                new SavedPlayer(0, x, y, artTile, solidBits, layer, 0, 0, 0, null);
    }

    public SavedPlayer saved(int slot) {
        SavedPlayer saved = players[Objects.checkIndex(slot, SLOT_COUNT)];
        if (saved == null) throw new IllegalStateException("Native checkpoint slot is not seeded: " + slot);
        return saved;
    }

    /** Obj79_CheckActivation compares seven-bit indices independently for both humans. */
    public boolean canActivate(int slot, int subtype) {
        return (saved(slot).lastStarPost() & 0x7F) < (subtype & 0x7F);
    }

    /** A previously activated or earlier post cannot replace this player's bank. */
    public void save(int slot, int subtype, int postX, int postY, int artTile, int solidBits, byte layer,
                     int rings, int extraLifeFlags, long timerFrames, PrimaryEnvironment environment) {
        SavedPlayer previous = saved(slot);
        requireSlotEnvironment(slot, environment);
        var next = new SavedPlayer(subtype, postX, postY, artTile, solidBits, layer,
                rings, extraLifeFlags, timerFrames, environment);
        if ((previous.lastStarPost() & 0x7F) < (next.lastStarPost() & 0x7F)) players[slot] = next;
    }

    /** Immutable, independent two-entry container; null entries retain unseeded slots. */
    public List<SavedPlayer> capture() {
        return Collections.unmodifiableList(Arrays.asList(players.clone()));
    }

    /** Validate the entire incoming bank before replacing either live slot. */
    public void restore(List<SavedPlayer> snapshot) {
        var incoming = new ArrayList<>(Objects.requireNonNull(snapshot, "checkpointSnapshot"));
        if (incoming.size() != SLOT_COUNT) throw new IllegalArgumentException("Expected two checkpoint slots");
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            SavedPlayer player = incoming.get(slot);
            if (player != null) requireSlotEnvironment(slot, player.primaryEnvironment());
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) players[slot] = incoming.get(slot);
    }

    public void clear() {
        Arrays.fill(players, null);
    }

    /** Obj39_TimeOver clears Saved_Timer (P1), even when only P2 timed out. */
    public void clearPrimarySavedTimer() {
        SavedPlayer old = saved(0);
        players[0] = new SavedPlayer(old.lastStarPost(), old.x(), old.y(), old.artTile(), old.solidBits(),
                old.layer(), old.rings(), old.extraLifeFlags(), 0, old.primaryEnvironment());
    }

    private static void requireSlotEnvironment(int slot, PrimaryEnvironment environment) {
        if (slot == 1 && environment != null)
            throw new IllegalArgumentException("Player two has no primary checkpoint environment bank");
    }
}
