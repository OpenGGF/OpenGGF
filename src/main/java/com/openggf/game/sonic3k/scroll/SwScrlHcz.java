package com.openggf.game.sonic3k.scroll;

import static com.openggf.level.scroll.M68KMath.asrWord;
import static com.openggf.level.scroll.M68KMath.negWord;

import com.openggf.game.internal.ExplicitForegroundVerticalScroll;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents.ScreenPublication;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.scroll.compose.DeformationPlan;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.level.scroll.compose.ScrollValueTable;
import com.openggf.level.scroll.compose.WaterlineBlendComposer;

import java.util.function.Supplier;

/**
 * Hydrocity Zone (HCZ) scroll handler for Sonic 3K.
 *
 * <p>HCZ1 deformation is local. HCZ2 consumes the event owner's completed
 * camera words and applies its horizontal deformation table; it owns no event
 * phase, wall accumulator or shake setup.
 */
public class SwScrlHcz extends AbstractZoneScrollHandler
        implements com.openggf.level.scroll.ScrollProjectionSource, ScrollOutputRebuilder,
        ExplicitForegroundVerticalScroll {

    static final int[] HCZ1_DEFORM_HEIGHTS = {
            0x40, 8, 8, 5, 5, 6, 0xF0, 6, 5, 5, 8, 8, 0x30, 0x80C0, 0x7FFF
    };
    private static final int EQUILIBRIUM_Y = 0x610;
    private static final int BG_Y_OFFSET = 0x190;
    private static final int WATERLINE_THRESHOLD = 0x60;
    private static final int HCZ1_HSCROLL_SIZE = 206;
    static final int HCZ1_WATERLINE_START = 13;
    static final int HCZ1_WATERLINE_MIDPOINT = 109;
    static final int HCZ1_WATERLINE_END = 205;

    private static final int[] HCZ2_DEFORM_HEIGHTS = {
            8, 8, 0x90, 0x10, 8, 0x30, 0x18, 8, 8, 0xA8, 0x30, 0x18,
            8, 8, 0xA8, 0x30, 0x18, 8, 8, 0xB0, 0x10, 8, 0x7FFF
    };
    private static final int[] HCZ2_DEFORM_INDEX = {
            4 - 1, 0x0A, 0x14, 0x1E, 0x2C,
            3 - 1, 0x0C, 0x16, 0x20,
            6 - 1, 0x00, 0x08, 0x0E, 0x18, 0x22, 0x2A,
            4 - 1, 0x02, 0x10, 0x1A, 0x24,
            2 - 1, 0x12, 0x1C,
            2 - 1, 0x06, 0x28,
            2 - 1, 0x04, 0x26,
            0xFF
    };
    private static final int HCZ2_HSCROLL_SIZE = 24;

    private static final DeformationPlan.ScrollValueTransform NEGATE_WORD = value -> negWord(value);
    private static final WaterlineBlendComposer WATERLINE_BLEND = new WaterlineBlendComposer(
            HCZ1_WATERLINE_START,
            HCZ1_WATERLINE_MIDPOINT,
            HCZ1_WATERLINE_END,
            WATERLINE_THRESHOLD
    );

    public enum Hcz2BgPhase {
        WALL_CHASE,
        NORMAL
    }

    private final ScrollEffectComposer composer;
    private final ScrollValueTable hcz1HScroll = ScrollValueTable.ofLength(HCZ1_HSCROLL_SIZE);
    private final ScrollValueTable hcz2HScroll = ScrollValueTable.ofLength(HCZ2_HSCROLL_SIZE);
    private final byte[] waterlineData;
    private final Supplier<ScreenPublication> publication;

    // Derived picture output only. The event owner captures the publication;
    // rebuilding or freezing this cache never advances that owner.
    private ScreenPublication composedPublication;
    private int lastBgCameraX = Integer.MIN_VALUE;
    private short vscrollFactorFG;
    private int shakeOffsetY;
    private boolean explicitForeground;

    public SwScrlHcz(byte[] waterlineData) {
        this(waterlineData, null);
    }

    public SwScrlHcz(byte[] waterlineData, Supplier<ScreenPublication> publication) {
        this(waterlineData, publication, 224);
    }

    private SwScrlHcz(byte[] waterlineData, Supplier<ScreenPublication> publication, int height) {
        this.waterlineData = waterlineData;
        this.publication = publication;
        composer = new ScrollEffectComposer(height);
    }

    public SwScrlHcz() {
        this(null);
    }

    @Override
    public Object captureRewindState() {
        return null;
    }

    /** Diagnostic view of the producer's current publication; never initializes it. */
    public Hcz2BgPhase getHcz2BgPhase() {
        ScreenPublication current = publication != null ? publication.get() : null;
        return current != null && current.valid() && current.wallPlane()
                ? Hcz2BgPhase.WALL_CHASE : Hcz2BgPhase.NORMAL;
    }

    @Override
    public short getVscrollFactorFG() {
        return vscrollFactorFG;
    }

    @Override
    public boolean foregroundVerticalScrollExplicit() {
        return explicitForeground;
    }

    @Override
    public int getBgCameraX() {
        return lastBgCameraX;
    }

    @Override
    public int getShakeOffsetY() {
        return shakeOffsetY;
    }

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter, int actId) {
        ScreenPublication current = actId != 0 && publication != null ? publication.get() : null;
        composeCamera(horizScrollBuf, cameraX, cameraY, actId, current, publication != null);
    }

    @Override
    public void rebuildOutput(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        update(horizScrollBuf, cameraX, cameraY, frameCounter, actId);
    }

    private void composeCamera(int[] horizScrollBuf, int cameraX, int cameraY,
                               int actId, ScreenPublication current, boolean hasPublicationOwner) {
        resetScrollTracking();
        composer.reset();
        composer.setVscrollFactorFG((short) 0);
        explicitForeground = false;
        shakeOffsetY = 0;
        composedPublication = actId == 0 ? null : current;

        if (actId == 0) {
            lastBgCameraX = Integer.MIN_VALUE;
            updateHcz1(cameraX, cameraY);
        } else if (current != null && current.valid()) {
            explicitForeground = true;
            shakeOffsetY = current.shakeOffset();
            composer.setVscrollFactorFG((short) current.foregroundY());
            if (current.completingActOneDeformation()) {
                lastBgCameraX = Integer.MIN_VALUE;
                updateHcz1(current.foregroundX(), current.foregroundY());
            } else {
                updateHcz2(current);
            }
        } else if (!hasPublicationOwner) {
            // Standalone deformation math has no gameplay owner or shake. A
            // production handler always has a supplier, even before its first
            // completed publication, and never enters this convenience path.
            updateHcz2Normal(cameraX, asrWord(cameraY, 2));
        } else {
            // No producer publication yet: no camera-derived phase or setup.
            // The level-load owner decides when canonical words become available.
            explicitForeground = true;
            lastBgCameraX = Integer.MIN_VALUE;
            composer.setVscrollFactorBG((short) 0);
            composer.fillPackedScrollWords(0, composer.visibleLineCount(), (short) 0, (short) 0);
        }

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
        if (minScrollOffset == Integer.MAX_VALUE) {
            minScrollOffset = 0;
            maxScrollOffset = 0;
        }
        vscrollFactorBG = composer.getVscrollFactorBG();
        vscrollFactorFG = composer.getVscrollFactorFG();
    }

    private void updateHcz1(int cameraX, int cameraY) {
        hcz1HScroll.clear();

        short delta = (short) (cameraY - EQUILIBRIUM_Y);
        short quarterDelta = (short) (delta >> 2);
        short equilibriumDelta = (short) (quarterDelta - delta);
        composer.setVscrollFactorBG((short) (quarterDelta + BG_Y_OFFSET));

        int scrollValue = ((short) cameraX) << 16;
        if (equilibriumDelta != 0) {
            int step = scrollValue >> 7;
            if (equilibriumDelta <= -WATERLINE_THRESHOLD) {
                fillMiddleForward(scrollValue, step);
            } else {
                fillMiddleBackward(scrollValue, step);
            }
        }

        fillCaveBands(scrollValue);
        short fastestCaveValue = hcz1HScroll.get(0);
        short slowestCaveValue = hcz1HScroll.get(6);
        WATERLINE_BLEND.apply(hcz1HScroll, equilibriumDelta, fastestCaveValue, slowestCaveValue, waterlineData);

        DeformationPlan.applyFlaggedTableBands(
                composer,
                composer.getVscrollFactorBG(),
                negWord(cameraX),
                hcz1HScroll,
                HCZ1_DEFORM_HEIGHTS,
                0,
                NEGATE_WORD);
    }

    private void fillMiddleForward(int value, int step) {
        int index = HCZ1_WATERLINE_START;
        for (int i = 0; i < 48; i++) {
            hcz1HScroll.set(index++, (short) (value >> 16));
            value -= step;
            hcz1HScroll.set(index++, (short) (value >> 16));
            value -= step;
        }
    }

    private void fillMiddleBackward(int value, int step) {
        int index = HCZ1_WATERLINE_END;
        for (int i = 0; i < 48; i++) {
            hcz1HScroll.set(--index, (short) (value >> 16));
            value -= step;
            hcz1HScroll.set(--index, (short) (value >> 16));
            value -= step;
        }
    }

    private void fillCaveBands(int cameraXFixed) {
        int value = cameraXFixed >> 2;
        int step = cameraXFixed >> 5;
        for (int level = 0; level < 7; level++) {
            short scroll = (short) (value >> 16);
            hcz1HScroll.set(level, scroll);
            hcz1HScroll.set(12 - level, scroll);
            value -= step;
        }
        hcz1HScroll.set(HCZ1_HSCROLL_SIZE - 1, hcz1HScroll.get(6));
    }

    private void updateHcz2(ScreenPublication current) {
        if (current.wallPlane()) {
            // Also covers the stage-8 held picture: BG copies need not follow
            // this dispatch's foreground camera (sonic3k.asm:106136-106148).
            lastBgCameraX = current.backgroundX();
            composer.setVscrollFactorBG((short) current.backgroundY());
            short fgScroll = negWord(current.foregroundX());
            short bgScroll = negWord(current.backgroundX());
            composer.fillPackedScrollWords(0, composer.visibleLineCount(), fgScroll, bgScroll);
            return;
        }
        updateHcz2Normal(current.foregroundX(), current.backgroundY());
    }

    private void updateHcz2Normal(int foregroundX, int backgroundY) {
        hcz2HScroll.clear();
        lastBgCameraX = Integer.MIN_VALUE;
        composer.setVscrollFactorBG((short) backgroundY);

        buildHcz2HScroll(foregroundX);
        DeformationPlan.applyFlaggedTableBands(
                composer,
                composer.getVscrollFactorBG(),
                negWord(foregroundX),
                hcz2HScroll,
                HCZ2_DEFORM_HEIGHTS,
                0,
                NEGATE_WORD);
    }

    private void buildHcz2HScroll(int cameraX) {
        int value = ((short) cameraX) << 16;
        value >>= 1;
        int step = value >> 3;

        int position = 0;
        while (position < HCZ2_DEFORM_INDEX.length) {
            int count = HCZ2_DEFORM_INDEX[position++];
            if ((count & 0x80) != 0) {
                break;
            }

            short scroll = (short) (value >> 16);
            for (int i = 0; i <= count; i++) {
                int wordIndex = HCZ2_DEFORM_INDEX[position++] >> 1;
                if (wordIndex < hcz2HScroll.size()) {
                    hcz2HScroll.set(wordIndex, scroll);
                }
            }
            value -= step;
        }
    }

    @Override
    public com.openggf.level.scroll.ScrollProjectionPhase captureProjection(
            com.openggf.level.scroll.ScrollProjectionContext context) {
        byte[] blend = waterlineData == null ? null : waterlineData.clone();
        ScreenPublication frozen = composedPublication;
        boolean hasPublicationOwner = publication != null;
        // ScreenEvents copied these words before a held director could move the
        // camera. Translate the immutable publication, excluding its shake from
        // the view origin, rather than using newer camera metadata.
        int originX = frozen != null && frozen.valid() ? frozen.foregroundX() : context.cameraX();
        int originY = frozen != null && frozen.valid()
                ? (short) (frozen.foregroundY() - frozen.shakeOffset()) : context.cameraY();
        return view -> {
            SwScrlHcz scratch = new SwScrlHcz(blend, null, view.height());
            int[] words = new int[view.height()];
            ScreenPublication projected = projectPublication(frozen,
                    view.cameraX() - originX, view.cameraY() - originY);
            scratch.composeCamera(words, view.cameraX(), view.cameraY(), context.act(),
                    projected, hasPublicationOwner);
            short foregroundY =
                    !scratch.explicitForeground && scratch.vscrollFactorFG == 0
                            ? (short) view.cameraY() : scratch.vscrollFactorFG;
            return new com.openggf.level.scroll.ScrollProjection(
                    words,
                    null,
                    null,
                    null,
                    foregroundY,
                    scratch.vscrollFactorBG,
                    scratch.lastBgCameraX,
                    context.nativeOutput().backgroundPeriod(),
                    context.nativeOutput().shakeX(),
                    scratch.shakeOffsetY,
                    context.nativeOutput().tilemapMode());
        };
    }

    private static ScreenPublication projectPublication(ScreenPublication frozen, int deltaX, int deltaY) {
        if (frozen == null || !frozen.valid()) {
            return frozen;
        }
        int foregroundX = (short) (frozen.foregroundX() + deltaX);
        int foregroundY = (short) (frozen.foregroundY() + deltaY);
        int backgroundX = frozen.backgroundX();
        int backgroundY;
        if (frozen.completingActOneDeformation()) {
            backgroundX = foregroundX;
            backgroundY = (short) (((short) (foregroundY - EQUILIBRIUM_Y) >> 2) + BG_Y_OFFSET);
        } else if (frozen.wallPlane()) {
            backgroundX = (short) (backgroundX + deltaX);
            backgroundY = (short) (frozen.backgroundY() + deltaY);
        } else {
            // Presentation-only translation of the frozen normal deformation.
            // Subtracting the two word-ratio terms preserves native output at
            // delta 0, including its rounding (sonic3k.asm:106230-106236).
            int oldRatio = asrWord(frozen.foregroundY() - frozen.shakeOffset(), 2);
            int newRatio = asrWord(foregroundY - frozen.shakeOffset(), 2);
            backgroundY = (short) (frozen.backgroundY() + newRatio - oldRatio);
        }
        return new ScreenPublication(true, frozen.wallPlane(), foregroundX, foregroundY,
                backgroundX, backgroundY, frozen.shakeOffset(), frozen.completingActOneDeformation());
    }
}
