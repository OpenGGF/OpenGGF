package com.openggf.game.sonic1.scroll;

import com.openggf.game.internal.CheckpointBackgroundScroll;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollProjectionContext;
import com.openggf.level.scroll.ScrollProjectionPhase;
import com.openggf.level.scroll.ScrollProjectionSource;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import static com.openggf.level.scroll.M68KMath.negWord;
import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;

/**
 * The original retail background algorithms, before REV01's zone-wide rewrite.
 * Deform_GHZ/LZ/MZ/SYZ/SBZ (_inc/DeformLayers (REV00).asm); BgScroll_* in
 * _inc/LevelSizeLoad & BgScrollSpeed.asm. SLZ retains its existing shared algorithm.
 * State is owned by one zone handler, including its fractional scroll and rewind.
 */
final class Sonic1Rev00Scroll extends AbstractZoneScrollHandler
        implements Sonic1CheckpointScrollHandler, ScrollProjectionSource {
    private final int zone;
    private long bgX, bgY, bg2X;
    private int lastX, lastY;
    private int bg2YSeed, bg3XSeed, bg3YSeed;
    private boolean initialized;

    Sonic1Rev00Scroll(int zone) {
        this.zone = zone;
    }

    @Override
    public void init(int actId, int cameraX, int cameraY) {
        bgX = (long) cameraX << 16;
        bg2X = bgX;
        int y = switch (zone) {
            case Sonic1ZoneConstants.ZONE_LZ -> cameraY >> 1;
            case Sonic1ZoneConstants.ZONE_SYZ -> cameraY * 48 >> 8;
            case Sonic1ZoneConstants.ZONE_SBZ, Sonic1ZoneConstants.ZONE_FZ -> cameraY >> 3;
            case Sonic1ZoneConstants.ZONE_ENDING -> 0x1E;
            default -> cameraY;
        };
        bgY = (long) y << 16;
        bg2YSeed = zone == Sonic1ZoneConstants.ZONE_SYZ || zone == Sonic1ZoneConstants.ZONE_ENDING ? y : cameraY;
        bg3XSeed = cameraX;
        bg3YSeed = 0;
        lastX = cameraX;
        lastY = cameraY;
        initialized = true;
    }

    private int xRate() {
        return switch (zone) {
            case Sonic1ZoneConstants.ZONE_GHZ, Sonic1ZoneConstants.ZONE_ENDING -> 96;
            case Sonic1ZoneConstants.ZONE_LZ -> 128;
            case Sonic1ZoneConstants.ZONE_MZ -> 192;
            default -> 64;
        };
    }

    private int yRate() {
        return switch (zone) {
            case Sonic1ZoneConstants.ZONE_LZ -> 128;
            case Sonic1ZoneConstants.ZONE_SYZ -> 48;
            case Sonic1ZoneConstants.ZONE_SBZ, Sonic1ZoneConstants.ZONE_FZ -> 32;
            default -> 0;
        };
    }

    private boolean isGhz() {
        return zone == Sonic1ZoneConstants.ZONE_GHZ || zone == Sonic1ZoneConstants.ZONE_ENDING;
    }

    @Override
    public void update(int[] buffer, int cameraX, int cameraY, int frameCounter, int actId) {
        if (!initialized) init(actId, cameraX, cameraY);
        int dx = (short) ((cameraX - lastX) << 8);
        int dy = (short) ((cameraY - lastY) << 8);
        bgX = (int) (bgX + (long) dx * xRate());
        bgY = (int) (bgY + (long) dy * yRate());
        if (isGhz()) bg2X = (int) (bg2X + (long) dx * 128);
        lastX = cameraX;
        lastY = cameraY;
        if (isGhz()) bgY = (long) (0x26 - ((cameraY & 0x7FF) >> 5)) << 16;
        if (zone == Sonic1ZoneConstants.ZONE_MZ) {
            bgY = (long) (512 + (cameraY >= 456 ? ((cameraY - 456) * 3 >> 2) : 0)) << 16;
        }
        var output = compose(cameraX, (int) (bgX >> 16), (int) (bgY >> 16), (int) (bg2X >> 16), VISIBLE_LINES);
        output.copyPackedScrollWordsTo(buffer);
        vscrollFactorBG = output.getVscrollFactorBG();
        minScrollOffset = output.getMinScrollOffset();
        maxScrollOffset = output.getMaxScrollOffset();
    }

    private ScrollEffectComposer compose(int cameraX, int x, int y, int hillX, int height) {
        var output = new ScrollEffectComposer(height);
        output.setVscrollFactorBG((short) y);
        if (!isGhz()) {
            output.fillPackedScrollWords(0, height, negWord(cameraX), negWord(x));
            return output;
        }
        // REV00 has no autonomous cloud counters: clouds and mountains share X.
        int clouds = Math.max(0, 112 - y);
        int hillsEnd = clouds + 40;
        int cloudLines = Math.min(height, clouds);
        if (cloudLines > 0) output.fillPackedScrollWords(0, cloudLines, negWord(cameraX), negWord(x));
        int hillLines = Math.min(height - cloudLines, Math.max(0, hillsEnd - cloudLines));
        if (hillLines > 0) output.fillPackedScrollWords(cloudLines, hillLines, negWord(cameraX), negWord(hillX));
        // divs.w $68 truncates a signed word quotient before the second shift.
        short difference = (short) (cameraX - 0x200 - hillX);
        int increment = ((short) ((difference << 8) / 0x68)) << 8;
        int position = hillX << 16;
        for (int line = hillsEnd; line < height; line++) {
            if (line >= 0) output.fillPackedScrollWords(line, 1, negWord(cameraX), negWord(position >> 16));
            position += increment;
        }
        return output;
    }

    @Override public int getBgCameraX() { return (int) (bgX >> 16); }

    @Override public int getBgPeriodWidth() {
        if (!isGhz()) return super.getBgPeriodWidth();
        int viewportWidth;
        try {
            viewportWidth = com.openggf.game.GameServices.configuration().getInt(
                    com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS);
        } catch (RuntimeException ignored) {
            viewportWidth = 320;
        }
        return requiredBgPeriodWidth(minScrollOffset, maxScrollOffset, viewportWidth);
    }

    static int requiredBgPeriodWidth(int minScroll, int maxScroll, int viewportWidth) {
        // The actual line span includes REV00's -$200 water perspective input.
        int coverage = Math.max(0, maxScroll - minScroll) + Math.max(320, viewportWidth);
        int width = 512;
        while (width < coverage && width < 8192) width <<= 1;
        return width;
    }

    @Override
    public CheckpointBackgroundScroll captureBackgroundScroll() {
        return new CheckpointBackgroundScroll((int) (bgX >> 16), (int) (bgY >> 16),
                (int) (bg2X >> 16), isGhz() || zone == Sonic1ZoneConstants.ZONE_MZ ? (int) (bgY >> 16) : bg2YSeed,
                bg3XSeed, bg3YSeed);
    }

    @Override
    public void restoreBackgroundScroll(CheckpointBackgroundScroll state) {
        bgX = (long) state.bgX() << 16;
        bg2X = (long) state.bg2X() << 16;
        bg3XSeed = state.bg3X();
        bg3YSeed = state.bg3Y();
        if (zone != Sonic1ZoneConstants.ZONE_SYZ && zone != Sonic1ZoneConstants.ZONE_ENDING) bg2YSeed = state.bg2Y();
        // BgScroll_LZ/SYZ/SBZ recomputes Y on load; MZ keeps checkpoint Y
        // until its first Deform_MZ. GHZ recomputes its absolute Y each pass.
        if (zone == Sonic1ZoneConstants.ZONE_MZ) bgY = (long) state.bgY() << 16;
    }

    private record State(long bgX, long bgY, long bg2X, int lastX, int lastY,
                         int bg2YSeed, int bg3XSeed, int bg3YSeed, boolean initialized, short vscroll, int minScroll, int maxScroll) { }

    @Override public Object captureRewindState() {
        return new State(bgX, bgY, bg2X, lastX, lastY, bg2YSeed, bg3XSeed, bg3YSeed, initialized,
                vscrollFactorBG, minScrollOffset, maxScrollOffset);
    }

    @Override public void restoreRewindState(Object state) {
        if (state instanceof State s) {
            bgX = s.bgX; bgY = s.bgY; bg2X = s.bg2X; lastX = s.lastX; lastY = s.lastY;
            bg2YSeed = s.bg2YSeed; bg3XSeed = s.bg3XSeed; bg3YSeed = s.bg3YSeed;
            initialized = s.initialized; vscrollFactorBG = s.vscroll;
            minScrollOffset = s.minScroll; maxScrollOffset = s.maxScroll;
        }
    }

    @Override
    public ScrollProjectionPhase captureProjection(ScrollProjectionContext context) {
        long capturedX = bgX, capturedY = bgY, captured2X = bg2X;
        return view -> {
            int dx = view.cameraX() - context.cameraX(), dy = view.cameraY() - context.cameraY();
            int x = (int) ((capturedX + (long) dx * xRate() * 256) >> 16);
            int y = (int) ((capturedY + (long) dy * yRate() * 256) >> 16);
            int hill = (int) ((captured2X + (long) dx * 32768) >> 16);
            if (isGhz()) y = 0x26 - ((view.cameraY() & 0x7FF) >> 5);
            if (zone == Sonic1ZoneConstants.ZONE_MZ) {
                y = 512 + (view.cameraY() >= 456 ? ((view.cameraY() - 456) * 3 >> 2) : 0);
            }
            var output = compose(view.cameraX(), x, y, hill, view.height());
            return Sonic1ProjectionOutput.finish(context, view, output, x,
                    context.nativeOutput().backgroundPeriod());
        };
    }
}
