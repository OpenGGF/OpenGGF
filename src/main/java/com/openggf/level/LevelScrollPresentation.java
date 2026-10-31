package com.openggf.level;

import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.graphics.ArenaMaskState;

import java.util.Arrays;
import java.util.Objects;

/** Immutable scroll registers/buffers paired with a prepared sprite table. */
final class LevelScrollPresentation {
    record Registers(int cameraX, int cameraY, int cameraXWithShake,
                     short foregroundY, short backgroundY, int backgroundX, int backgroundPeriod,
                     boolean heatHaze, boolean perLineForeground, boolean reversePlanes,
                     boolean overrideForegroundY, short overriddenForegroundY,
                     boolean overrideBackgroundY, short overriddenBackgroundY, ArenaMaskState arenaMask) { }

    private final BackgroundBuildParameters backgroundBuild;
    private final Registers registers;
    private final int[] horizontal;
    private final short[] backgroundLines, backgroundColumns, foregroundColumns, overrideColumns;
    private final com.openggf.level.scroll.ScrollProjectionPhase phase;
    private final int columnPhaseX;
    private final com.openggf.level.scroll.BgTilemapUpdateMode tilemapMode;
    private final com.openggf.game.internal.BackgroundColumnProjection columnProjection;
    private final com.openggf.game.internal.BackgroundColumnRemap.Columns columnsRemap;
    private final java.util.Map<
                    com.openggf.level.scroll.ScrollSpriteKey,
                    com.openggf.level.scroll.ScrollSpritePosition>
            spritePositions;

    LevelScrollPresentation(Registers registers, int[] horizontal, short[] backgroundLines,
                            short[] backgroundColumns, short[] foregroundColumns, short[] overrideColumns) {
        this(
                registers,
                horizontal,
                backgroundLines,
                backgroundColumns,
                foregroundColumns,
                overrideColumns,
                null,
                0,
                com.openggf.level.scroll.BgTilemapUpdateMode.STATIC_WINDOW,
                null,
                null,
                java.util.Map.of(),
                null);
    }

    private LevelScrollPresentation(
            Registers registers,
            int[] horizontal,
            short[] backgroundLines,
            short[] backgroundColumns,
            short[] foregroundColumns,
            short[] overrideColumns,
            com.openggf.level.scroll.ScrollProjectionPhase phase,
            int columnPhaseX,
            com.openggf.level.scroll.BgTilemapUpdateMode tilemapMode,
            com.openggf.game.internal.BackgroundColumnProjection columnProjection,
            com.openggf.game.internal.BackgroundColumnRemap.Columns columnsRemap,
            java.util.Map<
                            com.openggf.level.scroll.ScrollSpriteKey,
                            com.openggf.level.scroll.ScrollSpritePosition>
                    spritePositions,
            BackgroundBuildParameters backgroundBuild) {
        this.backgroundBuild = backgroundBuild;
        this.registers = registers;
        this.phase = phase;
        this.columnPhaseX = columnPhaseX;
        this.tilemapMode = tilemapMode;
        this.columnProjection = columnProjection;
        this.columnsRemap = columnsRemap;
        this.spritePositions = java.util.Map.copyOf(spritePositions);
        this.horizontal = horizontal.clone();
        this.backgroundLines = copy(backgroundLines);
        this.backgroundColumns = copy(backgroundColumns);
        this.foregroundColumns = copy(foregroundColumns);
        this.overrideColumns = copy(overrideColumns);
    }

    static LevelScrollPresentation capture(LevelManager level, AdvancedRenderFrameState mode) {
        var camera = level.camera;
        var scroll = level.parallaxManager;
        return new LevelScrollPresentation(new Registers(camera.getX(), camera.getY(), camera.getXWithShake(),
                scroll.getVscrollFactorFG(), scroll.getVscrollFactorBG(), scroll.getBgCameraX(),
                scroll.getBgPeriodWidth(), mode.enableForegroundHeatHaze(), mode.enablePerLineForegroundScroll(),
                mode.reversePlaneAssignment(), mode.hasForegroundVScrollOverride(), mode.foregroundVScrollOverride(),
                mode.hasBackgroundVScrollOverride(), mode.backgroundVScrollOverride(), level.spritePresentationRenderer().boundsMask.sample()),
                scroll.getHScrollForShader(), scroll.getVScrollPerLineBGForShader(),
                scroll.getVScrollPerColumnBGForShader(), scroll.getVScrollPerColumnFGForShader(),
                mode.foregroundPerColumnVScrollOverride(),
                level.authoringView ? authoringPhase(scroll) : scroll.projectionPhase(),
                0,
                scroll.getBgTilemapUpdateMode(),
                scroll.backgroundColumnProjection(),
                null,
                java.util.Map.of(),
                level.authoringView ? BackgroundBuildParameters.capture(level) : scroll.backgroundBuildParameters());
    }

    /** The paused editor has no native scroll dispatch. Sample its frozen background
     * and straight world-space foreground without advancing a zone handler. */
    private static com.openggf.level.scroll.ScrollProjectionPhase authoringPhase(ParallaxManager scroll) {
        var frozen = new com.openggf.level.scroll.ScrollProjection(
                scroll.getHScrollForShader(), scroll.getVScrollPerLineBGForShader(),
                scroll.getVScrollPerColumnBGForShader(), scroll.getVScrollPerColumnFGForShader(),
                scroll.getVscrollFactorFG(), scroll.getVscrollFactorBG(), scroll.getBgCameraX(),
                scroll.getBgPeriodWidth(), 0, 0, scroll.getBgTilemapUpdateMode());
        return view -> {
            int[] nativeWords = frozen.horizontal();
            short[] nativeLines = frozen.backgroundLines();
            int[] words = new int[view.height()];
            short[] lines = nativeLines == null ? null : new short[view.height()];
            for (int row = 0; row < words.length; row++) {
                int nativeRow = Math.min(row, nativeWords.length - 1);
                words[row] = ((short) -view.cameraX() << 16) | (nativeWords[nativeRow] & 0xffff);
                if (lines != null) lines[row] = (short) (nativeLines[nativeRow] + nativeRow - row);
            }
            return new com.openggf.level.scroll.ScrollProjection(words, lines,
                    frozen.backgroundColumns(), frozen.foregroundColumns(), (short) view.cameraY(),
                    frozen.backgroundY(), frozen.backgroundX(), frozen.backgroundPeriod(), 0, 0, frozen.tilemapMode());
        };
    }

    /** Capture the bounds in the same generation as the displayed camera and sprite table.
     * ROM camera bounds remain gameplay-owned; this only hides newly exposed wide pixels.
     * No zone ID, boss flag or explicit mask activation decides whether an edge is visible.
     */
    static ArenaMaskState captureArenaMask(LevelManager level) {
        var camera = level.camera;
        int currentMaxX = camera.getMaxX();
        var rules = level.gameModule == null ? null : level.gameModule.getRules().playerMovement();
        if (rules != null && !rules.levelBoundaryRightStrict()) {
            var state = level.boundaryGameState();
            boolean locked = rules.levelBoundaryLockUsesScreenLockFlag()
                    ? state.isScreenLocked() : state.isBossFightActive();
            // S1/S2 permit another $40 pixels in ordinary play. Do not conceal
            // playable space merely because the camera itself stops earlier.
            if (!locked && !state.isEndOfLevelActive()) {
                currentMaxX += 64;
            }
        }
        var bounds = LevelBoundsMaskGeometry.select(level, currentMaxX);
        return ArenaMaskState.fromBounds(bounds.minX(), bounds.maxX(), camera.getXWithShake(),
                camera.getWidth(), level.objectManager == null ? level.frameCounter : level.objectManager.getFrameCounter(),
                level.zoneFeatureProvider != null && level.zoneFeatureProvider.foregroundWrapsHorizontally());
    }

    /** Native publication only. The ordinary sampler intentionally retains its exact
     * getter/callback order; only its bounds-selection prefix is duplicated here.
     */
    static BackgroundBuildParameters.ArenaBounds captureArenaBounds(LevelManager level, boolean foregroundWraps) {
        var camera = level.camera;
        int currentMaxX = camera.getMaxX();
        var rules = level.gameModule == null ? null : level.gameModule.getRules().playerMovement();
        if (rules != null && !rules.levelBoundaryRightStrict()) {
            var state = level.boundaryGameState();
            boolean locked = rules.levelBoundaryLockUsesScreenLockFlag()
                    ? state.isScreenLocked() : state.isBossFightActive();
            // S1/S2 permit another $40 pixels in ordinary play. Do not conceal
            // playable space merely because the camera itself stops earlier.
            if (!locked && !state.isEndOfLevelActive()) {
                currentMaxX += 64;
            }
        }
        var bounds = LevelBoundsMaskGeometry.select(level, currentMaxX);
        return new BackgroundBuildParameters.ArenaBounds(bounds.minX(), bounds.maxX(),
                level.objectManager == null ? level.frameCounter : level.objectManager.getFrameCounter(),
                foregroundWraps);
    }

    private ArenaMaskState arenaMaskForView(int xWithShake, int width) {
        if (backgroundBuild == null || backgroundBuild.arenaBounds() == null)
            throw new IllegalStateException("Cinematic view requires native-captured arena bounds");
        var bounds = backgroundBuild.arenaBounds();
        return ArenaMaskState.fromBounds(bounds.minX(), bounds.maxX(), xWithShake, width,
                bounds.noiseFrame(), bounds.wraps());
    }

    BackgroundBuildParameters backgroundBuildParameters() {
        return backgroundBuild;
    }

    Registers registers() { return registers; }

    /** Reframe the foreground without rerunning native deformation or advancing its owners.
     * Background registers keep their native phase (including deliberately stationary backgrounds).
     * Column scrolls are deltas, so only the scalar/override origin moves vertically.
     */
    LevelScrollPresentation forCinematicCamera(int x, int y, int width) {
        int xWithShake = registers.cameraXWithShake() + (x - registers.cameraX());
        return forCinematicCamera(x, y, arenaMaskForView(xWithShake, width));
    }

    LevelScrollPresentation forCinematicCamera(int x, int y, ArenaMaskState mask) {
        int dx = x - registers.cameraX(), dy = y - registers.cameraY();
        int[] reframed = horizontal.clone();
        for (int i = 0; i < reframed.length; i++) {
            short foreground = (short) ((short) (reframed[i] >>> 16) - dx);
            reframed[i] = (foreground << 16) | (reframed[i] & 0xffff);
        }
        var view = new Registers(x, y, registers.cameraXWithShake() + dx,
                (short) (registers.foregroundY() + dy), registers.backgroundY(),
                registers.backgroundX(), registers.backgroundPeriod(), registers.heatHaze(),
                registers.perLineForeground(), registers.reversePlanes(), registers.overrideForegroundY(),
                registers.overrideForegroundY() ? (short) (registers.overriddenForegroundY() + dy)
                        : registers.overriddenForegroundY(),
                registers.overrideBackgroundY(), registers.overriddenBackgroundY(), mask);
        return new LevelScrollPresentation(view, reframed, backgroundLines, backgroundColumns,
                foregroundColumns, overrideColumns, phase, columnPhaseX, tilemapMode,
                columnProjection, columnsRemap, spritePositions, backgroundBuild);
    }

    record BackgroundMotion(short[] words, short[] lines, short[] columns,
                            short y, short overrideY, short x, int largestStep) { }

    BackgroundMotion backgroundMotionSince(LevelScrollPresentation previous, int bound, int width) {
        if (registers.backgroundPeriod() != previous.registers.backgroundPeriod()
                || registers.reversePlanes() != previous.registers.reversePlanes()
                || registers.overrideBackgroundY() != previous.registers.overrideBackgroundY()
                || tilemapMode != previous.tilemapMode || columnPhaseX != previous.columnPhaseX
                || horizontal.length != previous.horizontal.length
                || !sameSize(backgroundLines, previous.backgroundLines)
                || !sameSize(backgroundColumns, previous.backgroundColumns)
                || (registers.backgroundX() == Integer.MIN_VALUE) != (previous.registers.backgroundX() == Integer.MIN_VALUE)
                || !sameBackgroundResidency(previous)) return null;
        short[] words = new short[horizontal.length];
        for (int i = 0; i < words.length; i++) words[i] = (short) (previous.horizontal[i] - horizontal[i]);
        short[] lines = differences(previous.backgroundLines, backgroundLines);
        short[] columns = differences(previous.backgroundColumns, backgroundColumns);
        short y = (short) (previous.registers.backgroundY() - registers.backgroundY());
        short overrideY = registers.overrideBackgroundY()
                ? (short) (previous.registers.overriddenBackgroundY() - registers.overriddenBackgroundY()) : 0;
        short x = registers.backgroundX() == Integer.MIN_VALUE ? 0
                : (short) (previous.registers.backgroundX() - registers.backgroundX());
        int largest = Math.max(Math.max(Math.abs(y), Math.abs(x)), Math.abs(overrideY));
        for (short[] steps : new short[][]{words, lines, columns}) {
            if (steps != null) for (short step : steps) largest = Math.max(largest, Math.abs(step));
        }
        if (largest > bound || tilemapMode == com.openggf.level.scroll.BgTilemapUpdateMode.PERSISTENT_NAMETABLE_64X32
                && width + largest > 512) return null;
        return new BackgroundMotion(words, lines, columns, y, overrideY, x, largest);
    }

    private boolean sameBackgroundResidency(LevelScrollPresentation previous) {
        var now = backgroundBuild; var old = previous.backgroundBuild;
        if (now == null || old == null) return now == old;
        if (now.wraps() != old.wraps() || now.linearOverflow() != old.linearOverflow()
                || now.fullWidthRequired() != old.fullWidthRequired() || now.persistentPlane() != old.persistentPlane()
                || now.fullWidthWindow() != old.fullWidthWindow() || now.loopBandY() != old.loopBandY()
                || now.introOcean() != old.introOcean()
                || now.retainedBackground().revision() != old.retainedBackground().revision()
                || now.retainedBackground().widthTiles() != old.retainedBackground().widthTiles()
                || now.retainedBackground().heightTiles() != old.retainedBackground().heightTiles()) return false;
        var a = now.projection(); var b = old.projection();
        if (a == null || b == null) return a == b;
        return a.descriptorRevision() == b.descriptorRevision()
                && a.nametableBase().applyAsDouble(registers.cameraX())
                == b.nametableBase().applyAsDouble(previous.registers.cameraX());
    }

    private static boolean sameSize(short[] a, short[] b) {
        return a == null ? b == null : b != null && a.length == b.length;
    }
    private static short[] differences(short[] previous, short[] current) {
        if (current == null) return null;
        short[] result = new short[current.length];
        for (int i = 0; i < result.length; i++) result[i] = (short) (previous[i] - current[i]);
        return result;
    }
    private static int weighted(int value, double weight) { return (int) Math.rint(value * weight); }
    private static short[] blended(short[] current, short[] steps, double weight) {
        if (current == null) return null;
        short[] result = current.clone();
        for (int i = 0; i < result.length; i++) result[i] += (short) weighted(steps[i], weight);
        return result;
    }

    LevelScrollPresentation forMotion(int dx, int dy, int width, BackgroundMotion background, double weight) {
        if (dx == 0 && dy == 0 && (background == null || background.largestStep() == 0)) return this;
        var view = forCinematicCamera(registers.cameraX() + dx, registers.cameraY() + dy,
                motionArenaMask(dx, width));
        if (background == null || background.largestStep() == 0) return view;
        int[] words = view.horizontal.clone();
        for (int i = 0; i < words.length; i++) words[i] = (words[i] & 0xffff0000)
                | ((short) (words[i] + weighted(background.words()[i], weight)) & 0xffff);
        var r = view.registers;
        var mixed = new Registers(r.cameraX(), r.cameraY(), r.cameraXWithShake(), r.foregroundY(),
                (short) (r.backgroundY() + weighted(background.y(), weight)),
                r.backgroundX() == Integer.MIN_VALUE ? Integer.MIN_VALUE : r.backgroundX() + weighted(background.x(), weight),
                r.backgroundPeriod(), r.heatHaze(), r.perLineForeground(), r.reversePlanes(),
                r.overrideForegroundY(), r.overriddenForegroundY(), r.overrideBackgroundY(),
                (short) (r.overriddenBackgroundY() + weighted(background.overrideY(), weight)), r.arenaMask());
        return new LevelScrollPresentation(mixed, words, blended(backgroundLines, background.lines(), weight),
                blended(backgroundColumns, background.columns(), weight), foregroundColumns, overrideColumns,
                phase, columnPhaseX, tilemapMode, columnProjection, columnsRemap, spritePositions, backgroundBuild);
    }

    private ArenaMaskState motionArenaMask(int dx, int width) {
        var current = registers.arenaMask();
        if (current == null) return null;
        var geometry = backgroundBuild == null || backgroundBuild.arenaBounds() == null
                ? new ArenaMaskState(Math.clamp(current.left() - dx, 0, width),
                        Math.clamp(current.right() - dx, 0, width), current.noiseFrame())
                : arenaMaskForView(registers.cameraXWithShake() + dx, width);
        float[] opacity = current.opacity();
        if (opacity == null) return geometry;
        // Keep the native tick's fade sample. A director reframe would replace it with opaque edges.
        float[] shifted = new float[width];
        for (int x = 0; x < width; x++) shifted[x] = opacity[Math.clamp(x + dx, 0, opacity.length - 1)];
        return new ArenaMaskState(geometry.left(), geometry.right(), current.noiseFrame(), shifted);
    }

    /** Samples a frozen native phase once for the complete director raster. */
    LevelScrollPresentation forCinematicView(int x, int y, int width, int height) {
        return forCinematicView(x, y, width, height, null, true);
    }

    LevelScrollPresentation forCinematicView(
            int x, int y, int width, int height, ArenaMaskState mask) {
        return forCinematicView(x, y, width, height, mask, false);
    }

    private LevelScrollPresentation forCinematicView(
            int x, int y, int width, int height, ArenaMaskState mask, boolean capturedArena) {
        if (phase == null) {
            throw new IllegalStateException(
                    "This scroll handler has no directed presentation phase");
        }
        var output = phase.sample(new com.openggf.level.scroll.ScrollView(x, y, width, height));
        if (output.horizontal().length != height) {
            throw new IllegalStateException(
                    "Directed scroll phase returned the wrong raster height");
        }
        int[] directedWords = output.horizontal();
        short[] directedLines = output.backgroundLines();
        if (height > 224 && !registers.reversePlanes()) {
            // Native presentation is always 224 logical backdrop rows, even on
            // tall displays. Extra world rows repeat the bottom native pattern
            // band instead of revealing another turn of the finite VDP plane.
            // Keeping all pattern scanlines avoids stretching individual edge
            // pixels into vertical stripes. Foreground rows remain independent.
            short[] extendedLines = new short[height];
            for (int row = 0; row < height; row++) {
                int nativeRow = row < 224 ? row
                        : 224 - Pattern.PATTERN_HEIGHT + (row - 224) % Pattern.PATTERN_HEIGHT;
                int nativeWord = directedWords[nativeRow];
                directedWords[row] = (directedWords[row] & 0xffff0000) | (nativeWord & 0xffff);
                extendedLines[row] = (short) ((directedLines == null ? 0 : directedLines[nativeRow]) + nativeRow - row);
            }
            directedLines = extendedLines;
        }
        int dy = y - registers.cameraY();
        int xWithShake = x + output.shakeX();
        var directed =
                new Registers(
                        x,
                        y,
                        xWithShake,
                        output.foregroundY(),
                        output.backgroundY(),
                        output.backgroundX(),
                        output.backgroundPeriod(),
                        registers.heatHaze(),
                        registers.perLineForeground(),
                        registers.reversePlanes(),
                        registers.overrideForegroundY(),
                        registers.overrideForegroundY()
                                ? (short) (registers.overriddenForegroundY() + dy)
                                : registers.overriddenForegroundY(),
                        registers.overrideBackgroundY(),
                        registers.overriddenBackgroundY(),
                        capturedArena ? arenaMaskForView(xWithShake, width) : mask);
        return new LevelScrollPresentation(
                directed,
                directedWords,
                directedLines,
                output.backgroundColumns(),
                output.foregroundColumns(),
                shiftedColumns(overrideColumns, 0, (width + 15) / 16, 0),
                phase,
                0,
                output.tilemapMode(),
                columnProjection,
                columnProjection == null
                        ? null
                        : columnProjection.sample(
                                new com.openggf.level.scroll.ScrollView(x, y, width, height)),
                output.spritePositions(),
                backgroundBuild);
    }

    /** Rebase a raster tile without resampling its camera-dependent bands or event phase. */
    LevelScrollPresentation slice(int left, int top, int width, int height) {
        return slice(left, top, width, height,
                arenaMaskForView(registers.cameraXWithShake() + left, width));
    }

    LevelScrollPresentation slice(int left, int top, int width, int height, ArenaMaskState mask) {
        if (left < 0 || top < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid cinematic scroll tile");
        }
        int[] words = new int[height];
        for (int row = 0; row < height; row++) {
            int value = horizontal[Math.min(top + row, horizontal.length - 1)];
            words[row] =
                    ((short) ((short) (value >>> 16) - left) << 16)
                            | ((short) ((short) value - left) & 0xffff);
        }
        int columns = (width + Math.floorMod(left, 16) + 15) / 16;
        var view =
                new Registers(
                        registers.cameraX() + left,
                        registers.cameraY() + top,
                        registers.cameraXWithShake() + left,
                        (short) (registers.foregroundY() + top),
                        registers.backgroundY(),
                        registers.backgroundX() == Integer.MIN_VALUE
                                ? Integer.MIN_VALUE
                                : registers.backgroundX() + left,
                        registers.backgroundPeriod(),
                        registers.heatHaze(),
                        registers.perLineForeground(),
                        registers.reversePlanes(),
                        registers.overrideForegroundY(),
                        (short) (registers.overriddenForegroundY() + top),
                        registers.overrideBackgroundY(),
                        registers.overriddenBackgroundY(),
                        mask);
        return new LevelScrollPresentation(
                view,
                words,
                backgroundTileLines(top, height),
                shiftedColumns(backgroundColumns, left / 16, columns, 0),
                shiftedColumns(foregroundColumns, left / 16, columns, 0),
                shiftedColumns(overrideColumns, left / 16, columns, 0),
                phase,
                Math.floorMod(left, 16),
                tilemapMode,
                columnProjection,
                sliceRemap(left, top, width),
                spritePositions,
                backgroundBuild);
    }

    com.openggf.level.scroll.ScrollSpritePosition spritePositionForDraw(
            com.openggf.level.scroll.ScrollSpriteKey key) {
        return spritePositions.get(key);
    }

    com.openggf.game.internal.BackgroundColumnRemap.Columns backgroundColumnsRemap() {
        return columnsRemap;
    }

    private com.openggf.game.internal.BackgroundColumnRemap.Columns sliceRemap(
            int left, int top, int width) {
        if (columnsRemap == null) return null;
        int cutoff = columnsRemap.sourceYEndExclusive();
        return new com.openggf.game.internal.BackgroundColumnRemap.Columns(
                sliceView(columnsRemap.sourceX(), left, width),
                sliceView(columnsRemap.yOffsets(), left, width),
                cutoff);
    }

    private static com.openggf.util.ShortIndexedView sliceView(
            com.openggf.util.ShortIndexedView source, int first, int count) {
        short[] copy = new short[count];
        for (int i = 0; i < count; i++)
            copy[i] = source.get(Math.min(first + i, source.size() - 1));
        return new com.openggf.util.ShortIndexedView() {
            public int size() {
                return copy.length;
            }

            public short get(int index) {
                return copy[index];
            }
        };
    }

    com.openggf.level.scroll.BgTilemapUpdateMode tilemapMode() {
        return tilemapMode;
    }

    int columnPhaseX() {
        return columnPhaseX;
    }

    private short[] backgroundTileLines(int top, int height) {
        if (top == 0) return slicedLines(backgroundLines, 0, height);
        short[] result = slicedLines(backgroundLines, top, height);
        if (result == null) result = new short[height];
        for (int row = 0; row < height; row++) result[row] += (short) top;
        return result;
    }

    private static short[] slicedLines(short[] source, int first, int count) {
        if (source == null || source.length == 0) return null;
        short[] result = new short[count];
        for (int i = 0; i < count; i++) result[i] = source[Math.min(first + i, source.length - 1)];
        return result;
    }

    private static short[] shiftedColumns(short[] source, int first, int count, int offset) {
        short[] result = slicedLines(source, first, count);
        if (result != null) for (int i = 0; i < result.length; i++) result[i] += (short) offset;
        return result;
    }
    int[] horizontal() { return horizontal.clone(); }
    short[] backgroundLines() { return copy(backgroundLines); }
    short[] backgroundColumns() { return copy(backgroundColumns); }
    short[] foregroundColumns() { return copy(foregroundColumns); }

    AdvancedRenderFrameState renderMode() {
        var builder = AdvancedRenderFrameState.builder();
        if (registers.heatHaze()) builder.enableForegroundHeatHaze();
        if (registers.perLineForeground()) builder.enablePerLineForegroundScroll();
        if (registers.reversePlanes()) builder.reversePlaneAssignment();
        if (registers.overrideForegroundY()) builder.setForegroundVScrollOverride(registers.overriddenForegroundY());
        if (registers.overrideBackgroundY()) builder.setBackgroundVScrollOverride(registers.overriddenBackgroundY());
        builder.setForegroundPerColumnVScrollOverride(copy(overrideColumns));
        return builder.build();
    }

    private static short[] copy(short[] source) { return source == null ? null : source.clone(); }

    @Override public boolean equals(Object other) {
        return other instanceof LevelScrollPresentation state && registers.equals(state.registers)
                && Arrays.equals(horizontal, state.horizontal)
                && Arrays.equals(backgroundLines, state.backgroundLines)
                && Arrays.equals(backgroundColumns, state.backgroundColumns)
                && Arrays.equals(foregroundColumns, state.foregroundColumns)
                && Arrays.equals(overrideColumns, state.overrideColumns);
    }

    @Override public int hashCode() {
        return Objects.hash(registers, Arrays.hashCode(horizontal), Arrays.hashCode(backgroundLines),
                Arrays.hashCode(backgroundColumns), Arrays.hashCode(foregroundColumns), Arrays.hashCode(overrideColumns));
    }
}
