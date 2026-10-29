package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Pure UpdateHUD projection, N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/_Objects/HUD and Points/Main.asm:186-717. Called at actual selector8
 * AFTER slow PLC and BEFORE Finish, pause, levelFrames, placement and Body.
 *
 * The caller alone owns source/World/service admission and adopts returned values
 * once in its existing aggregate. This class has no mutable owner, input latch,
 * service, clock, capture, renderer, physical-register protocol or default state.
 * Calls describe completed work and never authorize replay. On UnsupportedHud,
 * adopt its complete prefix before closing actual admission; do not retry it as
 * an unconsumed service. Max-time deliberately stops before the existing shared
 * KillPlayer owner until a real Frame service-position publication is installed.
 */
public final class CdLevelHud {
    public record Assets(CdAssetBytes numbers, CdAssetBytes resetTiles, CdAssetBytes divisors) {
        public Assets {
            Objects.requireNonNull(numbers, "HUD number source");
            Objects.requireNonNull(resetTiles, "HUD reset source");
            Objects.requireNonNull(divisors, "HUD divisors");
            // Format admission, not eagerly executed native table/digit reads. These
            // finite views also ensure source-fault publication cuts are32-byte aligned.
            if (numbers.length() != 1024 || resetTiles.length() != 12 || divisors.length() != 40)
                throw new IllegalArgumentException("HUD requires explicit1024/12/40-byte raw views");
        }
        public static Assets fromAssets(CdR11bAssets assets) {
            Objects.requireNonNull(assets, "actual Past assets");
            return new Assets(assets.view(CdR11bAssets.Asset.ART_HUD_NUMBERS).bytes(),
                    assets.view(CdR11bAssets.Asset.HUD_RESET_TILES).bytes(),
                    assets.view(CdR11bAssets.Asset.HUD_DIVISORS).bytes());
        }
    }

    /** Existing authoritative values borrowed for one pure call; never another persistent bank. */
    public record State(CdR11bSectionArt.State section, CdRingGlobals ring, CdLevelGlobals level,
                        CdOpeningObjectRuntime.ScoreGlobals score, CdCheckpointLiveAux.State liveTime,
                        Optional<BonusValues> bonus) {
        public State {
            Objects.requireNonNull(section); Objects.requireNonNull(ring); Objects.requireNonNull(level);
            Objects.requireNonNull(score); Objects.requireNonNull(liveTime); Objects.requireNonNull(bonus);
        }
    }
    /** Known together only when their actual writer/carry has supplied the three-field projection. */
    public record BonusValues(int timeBonusWord, int ringBonusWord, int updateHudBonusByte) {
        public BonusValues {
            word(timeBonusWord); word(ringBonusWord); byteValue(updateHudBonusByte);
        }
    }
    public record Globals(int debugCheatWord, int pausedWord, int controlLockedByte, int rawTimeZoneByte,
                          int primaryXWord, int primaryYWord, int zoneActWord) {
        public Globals {
            word(debugCheatWord); word(pausedWord); byteValue(controlLockedByte); byteValue(rawTimeZoneByte);
            word(primaryXWord); word(primaryYWord); word(zoneActWord);
        }
    }
    /** Absence stays unknown. Normal HUD never reads this conditional debug projection. */
    public record DebugData(OptionalInt savedFlagsHeadByte, Optional<CdAssetBytes> layoutRam,
                            OptionalInt debugBlockWord) {
        public DebugData {
            Objects.requireNonNull(savedFlagsHeadByte); Objects.requireNonNull(layoutRam);
            Objects.requireNonNull(debugBlockWord);
            if (savedFlagsHeadByte.isPresent()) byteValue(savedFlagsHeadByte.getAsInt());
            if (debugBlockWord.isPresent()) word(debugBlockWord.getAsInt());
        }
    }
    public record Inputs(Globals globals, DebugData debug) {
        public Inputs { Objects.requireNonNull(globals); Objects.requireNonNull(debug); }
    }
    public record Result(State state, List<NativeCall> calls) {
        public Result { Objects.requireNonNull(state); calls = List.copyOf(calls); }
    }
    public enum NativeCall {
        DRAW_LIVES, RESET_SCORE, DRAW_SCORE, RESET_RINGS, DRAW_RINGS,
        DRAW_POS, DRAW_MINS, DRAW_SECS, DRAW_BONUS
    }
    public enum Boundary {
        FONT_ADDRESS, LIVE_TIME, BONUS_VALUES, DEBUG_SAVED_FLAGS,
        DEBUG_LAYOUT, DEBUG_BLOCK, MAX_TIME_KILL, NON_RETURNING_DIGIT
    }

    /**
     * Complete prior state/calls, never a successful Result. sourceOffset is a
     * relative font/divisor/layout byte offset, or a canonical native scalar
     * address for missing-value boundaries. -1 denotes no relevant address.
     * destinationWord is a native BYTE address, as in SectionArt.writeCurrentTile.
     */
    public static final class UnsupportedHud extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final Boundary boundary;
        private final State state;
        private final List<NativeCall> calls;
        private final int sourceOffset;
        private final int destinationWord;
        private UnsupportedHud(Boundary boundary, State state, List<NativeCall> calls,
                               int sourceOffset, int destinationWord, Throwable cause) {
            super(boundary + " at HUD source " + sourceOffset + ", destination " + destinationWord, cause);
            this.boundary = boundary; this.state = state; this.calls = List.copyOf(calls);
            this.sourceOffset = sourceOffset; this.destinationWord = destinationWord;
        }
        public Boundary boundary() { return boundary; }
        public State state() { return state; }
        public List<NativeCall> calls() { return calls; }
        public int sourceOffset() { return sourceOffset; }
        public int destinationWord() { return destinationWord; }
    }

    private final Assets assets;
    private final CdR11bSectionArt art;
    public CdLevelHud(Assets assets, CdR11bSectionArt art) {
        this.assets = Objects.requireNonNull(assets); this.art = Objects.requireNonNull(art);
    }

    /** Pure one-call projection. It cannot grant invocation, source, callback or exactly-once authority. */
    public Result run(State state, Inputs inputs) {
        Objects.requireNonNull(state); Objects.requireNonNull(inputs);
        if (state.section().stopped()) throw new IllegalStateException("Stopped section cannot resume HUD admission");
        var c = new Cursor(state, inputs);
        if (inputs.globals().debugCheatWord() == 0) { c.score(); c.rings(); }
        else c.debug();
        if (!c.time()) return c.result(); // Native max-time active-warp return skips lives/bonus.
        if (c.state.ring().updateHudLives() != 0) {
            c.ring(c.state.ring().rings(), c.state.ring().updateHudRings(), 0);
            c.lives();
        }
        c.bonus();
        return c.result();
    }

    private enum Digits { SKIP_LEADING, ALWAYS, BLANK_LEADING }
    /** Ephemeral local cursor; no mutable field is retained by the component. */
    private final class Cursor {
        private State state;
        private final Inputs inputs;
        private final List<NativeCall> calls = new ArrayList<>();
        private Cursor(State state, Inputs inputs) { this.state = state; this.inputs = inputs; }
        private Result result() { return new Result(state, calls); }
        private UnsupportedHud stop(Boundary boundary, int source, int destination) {
            return new UnsupportedHud(boundary, state, calls, source, destination, null);
        }
        private void section(CdR11bSectionArt.State section) {
            state = new State(section, state.ring(), state.level(), state.score(), state.liveTime(), state.bonus());
        }
        private void ring(int count, int ringsFlag, int livesFlag) {
            var r = state.ring();
            state = new State(state.section(), new CdRingGlobals(count, r.lives(), r.livesFlags(), ringsFlag,
                    livesFlag, r.timeStopTimer(), r.ringAnimationTimer(), r.ringAnimationFrame()),
                    state.level(), state.score(), state.liveTime(), state.bonus());
        }
        private void live(CdCheckpointLiveAux.State live) {
            state = new State(state.section(), state.ring(), state.level(), state.score(), live, state.bonus());
        }
        private void bonus(BonusValues bonus) {
            state = new State(state.section(), state.ring(), state.level(), state.score(), state.liveTime(), Optional.of(bonus));
        }
        private void score() {
            int flag = state.score().updateHudScore();
            if (flag == 0) return;
            if ((flag & 0x80) != 0) {
                lives(); // HUD_ResetScore calls this BEFORE any score-reset tile.
                reset(0, 7, 0xB060); calls.add(NativeCall.RESET_SCORE);
            }
            var s = state.score();
            state = new State(state.section(), state.ring(), state.level(),
                    new CdOpeningObjectRuntime.ScoreGlobals(s.score(), s.nextLifeScore(), 0), state.liveTime(), state.bonus());
            number(s.score(), 0, 6, 0xB060, Digits.SKIP_LEADING);
            calls.add(NativeCall.DRAW_SCORE);
        }
        private void rings() {
            int flag = state.ring().updateHudRings();
            if (flag == 0) return;
            if ((flag & 0x80) != 0) { reset(8, 3, 0xB360); calls.add(NativeCall.RESET_RINGS); }
            ring(state.ring().rings(), 0, state.ring().updateHudLives());
            int count = state.ring().rings();
            if (count >= 1000) { count = 999; ring(count, 0, state.ring().updateHudLives()); }
            number(count, 12, 3, 0xB360, Digits.SKIP_LEADING); calls.add(NativeCall.DRAW_RINGS);
        }
        private void lives() {
            int local = Math.min(state.ring().lives(), 9); // CMP.B/BCS caps only the local display value.
            number(local, 20, 1, 0xB4A0, Digits.ALWAYS); calls.add(NativeCall.DRAW_LIVES);
        }
        private int timeLong() {
            try { return CdCheckpointLiveAux.read(state.liveTime(), CdCheckpointLiveAux.Field.TIME_LONG); }
            catch (CdCheckpointLiveAux.UnknownLiveByte unknown) {
                throw new UnsupportedHud(Boundary.LIVE_TIME, state, calls, unknown.nativeAddress(), -1, unknown);
            }
        }
        private void timeByte(CdCheckpointLiveAux.TimeByte field, int value) {
            live(CdCheckpointLiveAux.writeTimeByte(state.liveTime(), field, value));
        }
        private boolean time() {
            var g = inputs.globals();
            if (g.debugCheatWord() != 0 || state.level().updateHudTime() == 0 || g.pausedWord() != 0) return true;
            int old = timeLong(); // Native CMP.L reads all four bytes, including the leading byte.
            if (old == 0x00093B3B) {
                if ((g.rawTimeZoneByte() & 0x80) != 0) return false;
                var l = state.level();
                state = new State(state.section(), state.ring(), new CdLevelGlobals(l.levelRestart(), 0,
                        l.checkpoint(), l.spawnMode(), l.plcLoadFlags(), l.flowerCount(), l.fadeEnableDisplay(),
                        l.powerup(), l.timeOver()), state.score(), state.liveTime(), state.bonus());
                live(CdCheckpointLiveAux.writeTimeLong(state.liveTime(), 0));
                // HUD:347 calls the existing shared KillPlayer. Frame's closed-cut
                // Hurt port is illegal here; retain this exact pre-call prefix.
                throw stop(Boundary.MAX_TIME_KILL, -1, -1);
            }
            if (g.controlLockedByte() == 0) {
                int frame = ((old & 0xFF) + 1) & 0xFF;
                timeByte(CdCheckpointLiveAux.TimeByte.FRAMES, frame);
                if (frame >= 60) {
                    timeByte(CdCheckpointLiveAux.TimeByte.FRAMES, 0);
                    int second = (((old >>> 8) & 0xFF) + 1) & 0xFF;
                    timeByte(CdCheckpointLiveAux.TimeByte.SECONDS, second);
                    if (second >= 60) {
                        timeByte(CdCheckpointLiveAux.TimeByte.SECONDS, 0);
                        int minute = (((old >>> 16) & 0xFF) + 1) & 0xFF;
                        timeByte(CdCheckpointLiveAux.TimeByte.MINUTES, minute);
                        if (minute >= 9) timeByte(CdCheckpointLiveAux.TimeByte.MINUTES, 9);
                    }
                }
            }
            int current = timeLong();
            number((current >>> 16) & 0xFF, 20, 1, 0xB220, Digits.ALWAYS); calls.add(NativeCall.DRAW_MINS);
            number((current >>> 8) & 0xFF, 16, 2, 0xB260, Digits.ALWAYS); calls.add(NativeCall.DRAW_SECS);
            int centiseconds = (current & 0xFF) * 100 / 60;
            if (current == 0x00093B3B) centiseconds = 99;
            number(centiseconds, 16, 2, 0xB2E0, Digits.ALWAYS); calls.add(NativeCall.DRAW_SECS);
            return true;
        }
        private void bonus() {
            if (state.bonus().isEmpty()) throw stop(Boundary.BONUS_VALUES, 0xFFF7D6, -1);
            var b = state.bonus().orElseThrow();
            if (b.updateHudBonusByte() == 0) return;
            bonus(new BonusValues(b.timeBonusWord(), b.ringBonusWord(), 0));
            boolean special = inputs.globals().zoneActWord() == 0x0502;
            number(b.timeBonusWord(), 4, 5, special ? 0x6D40 : 0x8780, Digits.BLANK_LEADING);
            calls.add(NativeCall.DRAW_BONUS);
            number(b.ringBonusWord(), 4, 5, special ? 0x6E80 : 0x88C0, Digits.BLANK_LEADING);
            calls.add(NativeCall.DRAW_BONUS);
        }
        private void debug() {
            var g = inputs.globals(); var d = inputs.debug();
            number(g.primaryXWord(), 24, 4, 0xB0E0, Digits.ALWAYS);
            number(g.primaryYWord(), 24, 4, 0xB260, Digits.ALWAYS); calls.add(NativeCall.DRAW_POS);
            if (d.savedFlagsHeadByte().isEmpty()) throw stop(Boundary.DEBUG_SAVED_FLAGS, 0xFF1200, -1);
            // This native value is overwritten, but its read is genuinely reached.
            int savedHead = d.savedFlagsHeadByte().getAsInt();
            int index = ((g.primaryYWord() >>> 1) & 0x380) + ((g.primaryXWord() >>> 8) & 0x7F);
            if (d.layoutRam().isEmpty() || index >= d.layoutRam().orElseThrow().length())
                throw stop(Boundary.DEBUG_LAYOUT, index, -1);
            int discardedChunk = d.layoutRam().orElseThrow().unsignedByte(index) & 0x7F;
            if (d.debugBlockWord().isEmpty()) throw stop(Boundary.DEBUG_BLOCK, 0xFF194E, -1);
            number(d.debugBlockWord().getAsInt() & 0x7FF, 12, 3, 0xB360, Digits.ALWAYS);
        }
        private void reset(int selectorOffset, int count, int destination) {
            for (int i = 0; i < count; i++) {
                int selector = assets.resetTiles().signedByte(selectorOffset + i);
                if (selector < 0) blank(destination + i * 64);
                else glyph((short) (selector << 5), destination + i * 64);
            }
        }
        private void number(int raw, int divisorOffset, int count, int destination, Digits policy) {
            long remaining = Integer.toUnsignedLong(raw); boolean found = false;
            for (int i = 0; i < count; i++) {
                int offset = divisorOffset + i * 4;
                long divisor = assets.divisors().unsignedLong(offset);
                if (divisor == 0) throw stop(Boundary.NON_RETURNING_DIGIT, offset, destination + i * 64);
                // Exact unsigned repeated-SUB.L/BCS result. The digit increment was
                // WORD-sized even when the quotient requires more than16 bits.
                int digitWord = (int) (remaining / divisor) & 0xFFFF;
                remaining %= divisor;
                if (digitWord != 0) found = true;
                int address = destination + i * 64;
                if (policy == Digits.SKIP_LEADING && !found) continue;
                if (policy == Digits.BLANK_LEADING && !found && i + 1 < count) blank(address);
                else glyph((short) (digitWord << 6), address);
            }
        }
        private void glyph(int source, int destination) {
            // Actual immutable format extents and offsets are32-byte aligned. No
            // source fault is hidden inside a committed half; second-half faults
            // retain exactly the prior native32 bytes and every earlier operation.
            for (int half = 0; half < 64; half += 32) {
                int at = source + half;
                if (at < 0 || at > assets.numbers().length() - 32)
                    throw stop(Boundary.FONT_ADDRESS, at, destination + half);
                section(art.writeCurrentTile(state.section(), destination + half, assets.numbers().slice(at, 32)));
            }
        }
        private void blank(int destination) {
            section(art.writeCurrentTile(state.section(), destination, CdAssetBytes.copyOf(new byte[32])));
            section(art.writeCurrentTile(state.section(), destination + 32, CdAssetBytes.copyOf(new byte[32])));
        }
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("HUD WORD exceeds native width");
    }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("HUD BYTE exceeds native width");
    }
}
