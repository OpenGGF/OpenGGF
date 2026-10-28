package com.openggf.game.sonic2.competition.specialstage;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.RomByteReader;

import java.util.List;
import java.util.Objects;

/** Native competitive VInt/track clock, separate from the ordinary stage owner. */
public final class CompetitiveHalfpipeTrack {
    private static final int ANIMATIONS = 0x63DC;
    private static final int ANIMATION_LENGTHS = 0x55FE;
    private static final int CURVE_OFFSETS = 0x768A;
    private final CompetitiveHalfpipeRomData data;
    private final RomByteReader rom;
    private final byte[] scrollTable;
    private final int[][] animations = new int[5][];
    private final int duration;
    private List<CompetitiveHalfpipeRomData.Segment> layout;
    private int stage;
    private int frame;
    private int previousFrame;
    private int drawingIndex;
    private int animationTimer;
    private int durationTimer;
    private int segment;
    private int geometry;
    private int animationFrame;
    private int lastAnimationFrame;
    private boolean flipped;
    private boolean alternateScroll;
    private boolean previousAlternateScroll;
    private int normalBgScroll;
    private int alternateBgScroll;
    private int offsetX;
    private int offsetY;
    private int vScroll;
    private boolean ended;

    public record State(int stage, int frame, int previousFrame, int drawingIndex,
                        int animationTimer, int durationTimer, int segment, int geometry,
                        int animationFrame, int lastAnimationFrame, boolean flipped,
                        boolean alternateScroll, boolean previousAlternateScroll,
                        int normalBgScroll, int alternateBgScroll, int offsetX,
                        int offsetY, int vScroll, boolean ended) { }

    public CompetitiveHalfpipeTrack(CompetitiveHalfpipeRomData data) {
        this.data = Objects.requireNonNull(data, "data");
        rom = data.rom();
        scrollTable = data.skydomeScroll();
        for (int geometry = 0; geometry < animations.length; geometry++) {
            int length = rom.readU8(address(rom, ANIMATION_LENGTHS) + geometry);
            int start = address(rom, ANIMATIONS) + rom.readU16BE(address(rom, ANIMATIONS) + geometry * 2);
            animations[geometry] = new int[length];
            for (int i = 0; i < length; i++) animations[geometry][i] = rom.readU8(start + i);
        }
        // SpecialStage requests speed factor twelve. Competitive FD creates a
        // message rather than Obj59, so its ordinary emerald slowdown is absent.
        duration = rom.readU8(address(rom, 0xB46) + (12 >> 1));
        if (duration != 5) throw new IllegalArgumentException("Invalid native halfpipe drawing duration");
        load(0);
    }

    public void load(int stage) {
        layout = data.segments(stage);
        this.stage = stage;
        frame = 0;
        previousFrame = 0;
        drawingIndex = 0;
        animationTimer = 0;
        durationTimer = 0;
        segment = 0;
        geometry = 0;
        animationFrame = 0;
        lastAnimationFrame = 0;
        flipped = false;
        alternateScroll = false;
        previousAlternateScroll = false;
        normalBgScroll = 0;
        alternateBgScroll = 0;
        offsetX = 0;
        offsetY = 0;
        vScroll = 0;
        ended = false;
    }

    /** One Vint_S2SS, followed by SSTrack_Draw, geometry and background scroll. */
    public void tick() {
        tick(true);
    }

    /** SpecialStage's masked bootstrap waits omit geometry and background work. */
    public void tickPreRoll() {
        tick(false);
    }

    /** The single SS_ScrollBG call after native bootstrap's duration-one wait. */
    public void scrollAfterPreRoll() {
        if (!ended) updateScroll();
    }

    private void tick(boolean publishGeometryAndScroll) {
        if (ended) return;
        // SSRun_Animation_Timers (s2.asm:960-982): d1 receives the base
        // duration; only the stored player-animation byte is decremented.
        durationTimer = (durationTimer - 1) & 0xFF;
        int drawingLimit;
        if ((byte) durationTimer <= 0) {
            durationTimer = duration;
            animationTimer = (duration - 1) & 0xFF;
            drawingLimit = duration;
        } else {
            drawingLimit = (animationTimer + 1) & 0xFF;
        }
        drawingIndex = (drawingIndex + 1) & 0xFF;
        if ((byte) drawingIndex >= (byte) drawingLimit) drawingIndex = 0;
        if (drawingIndex == 0) {
            if (segment >= layout.size()) {
                ended = true;
                return;
            }
            // SSTrack_Draw (s2.asm:7033-7091) displays the current counter,
            // then advances it. Objects later see the already advanced segment.
            previousFrame = frame;
            geometry = layout.get(segment).geometry();
            frame = animations[geometry][animationFrame];
            lastAnimationFrame = animationFrame;
            animationFrame++;
            if (animationFrame == animations[geometry].length) {
                animationFrame = 0;
                segment++;
            }
        }
        updateOrientation();
        if (!publishGeometryAndScroll) return;
        if (drawingIndex == animationTimer) {
            int x = (byte) rom.readU8(address(rom, CURVE_OFFSETS) + frame * 2);
            offsetX = flipped ? (byte) -x : x;
            offsetY = (byte) rom.readU8(address(rom, CURVE_OFFSETS) + frame * 2 + 1);
        }
        updateScroll();
    }

    private void updateOrientation() {
        previousAlternateScroll = alternateScroll;
        // SSTrackSetOrientation (s2.asm:9028-9085): Rise14 is mapping $0D,
        // not $0E. The horizontal buffer switch waits for the publication slice.
        if (frame == 0x12 || frame == 0x0D || frame == 0x1A) {
            if (segment < layout.size()) flipped = layout.get(segment).flipped();
            if (drawingIndex >= animationTimer) alternateScroll = flipped;
        }
    }

    private void updateScroll() {
        // SSPlaneB_SetHorizOffset (s2.asm:9238-9310) addresses ROM rows by
        // word offsets. Keep both native buffers across an orientation switch.
        if (geometry <= 2 && lastAnimationFrame < 12) {
            int row = lastAnimationFrame < 1 ? 0 : lastAnimationFrame < 2 ? 2
                    : lastAnimationFrame < 10 ? 4 : lastAnimationFrame < 11 ? 2 : 0;
            int delta = scrollDelta(row);
            if (alternateScroll) alternateBgScroll = word(alternateBgScroll + delta);
            else normalBgScroll = word(normalBgScroll - delta);
        }
        int verticalTable = switch (geometry) {
            case 0 -> 0x6F12;
            case 1 -> 0x6F34;
            case 3 -> 0x6F5A;
            default -> -1;
        };
        if (verticalTable >= 0) {
            int row = (byte) rom.readU8(verticalTable + lastAnimationFrame);
            if (row >= 0) {
                // SSTrack_ApplyVscroll zero-extends each byte, including $FF.
                // Native word wrapping preserves the shipped FixBugs=0 result.
                int delta = scrollDelta(row);
                vScroll = word(vScroll + (geometry == 1 ? delta : -delta));
            }
        }
    }

    private int scrollDelta(int rowOffset) {
        int row = ((scrollTable[rowOffset] & 0xFF) << 8) | (scrollTable[rowOffset + 1] & 0xFF);
        return scrollTable[row + drawingIndex] & 0xFF;
    }

    private static int word(int value) { return (short) value; }
    public int frame() { return frame; }
    public int previousFrame() { return previousFrame; }
    public int drawingIndex() { return drawingIndex; }
    public int animationTimer() { return animationTimer; }
    public int segment() { return segment; }
    public int geometry() { return geometry; }
    public int segmentLength(int geometry) { return animations[geometry].length; }
    public boolean flipped() { return flipped; }
    /** Signed delta; native actor projection adds its fixed centre $80. */
    public int offsetX() { return offsetX; }
    /** Signed delta; native actor projection adds its fixed height $36. */
    public int offsetY() { return offsetY; }
    public int bgScroll() { return alternateScroll ? alternateBgScroll : normalBgScroll; }
    public int vScroll() { return vScroll; }

    public State capture() {
        return new State(stage, frame, previousFrame, drawingIndex, animationTimer, durationTimer,
                segment, geometry, animationFrame, lastAnimationFrame, flipped, alternateScroll,
                previousAlternateScroll, normalBgScroll, alternateBgScroll, offsetX, offsetY, vScroll, ended);
    }

    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        layout = data.segments(state.stage());
        stage = state.stage();
        frame = state.frame();
        previousFrame = state.previousFrame();
        drawingIndex = state.drawingIndex();
        animationTimer = state.animationTimer();
        durationTimer = state.durationTimer();
        segment = state.segment();
        geometry = state.geometry();
        animationFrame = state.animationFrame();
        lastAnimationFrame = state.lastAnimationFrame();
        flipped = state.flipped();
        alternateScroll = state.alternateScroll();
        previousAlternateScroll = state.previousAlternateScroll();
        normalBgScroll = state.normalBgScroll();
        alternateBgScroll = state.alternateBgScroll();
        offsetX = state.offsetX();
        offsetY = state.offsetY();
        vScroll = state.vScroll();
        ended = state.ended();
    }
}
