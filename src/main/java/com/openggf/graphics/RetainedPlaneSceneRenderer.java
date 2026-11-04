package com.openggf.graphics;

import com.openggf.game.internal.RetainedPlaneDescriptorSource;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.level.Palette;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.util.EngineCallerAccess;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33.glBindSampler;

/**
 * Engine-internal production rasterization of two already selected physical planes
 * and one immutable native sprite picture. This class selects no gameplay values,
 * executes no source routine and creates no World, clock or capture authority.
 *
 * <p>The bounded capability is a 320x224 raster over two complete 64x32 rings.
 * Native 11-bit pattern IDs map injectively into one exclusively registered range.
 * Current pixels and selected geometry arrive as immutable values; a caller-owned
 * source/lifetime predicate is rechecked before and after the old-image FIFO drain.
 * No level-layout/static-art fallback or hardware execution protocol exists here.
 */
public final class RetainedPlaneSceneRenderer implements AutoCloseable {
    private static final int WIDTH = 320, HEIGHT = 224;
    private static final int COLUMNS = 64, ROWS = 32, CELLS = COLUMNS * ROWS;
    private static final int PATTERNS = 2048;
    private static final String BACKEND_PROPERTY = "openggf.headless.gl";
    private static final String TILEMAP_SHADER = "shaders/shader_tilemap.glsl";

    /** Immutable native descriptor words; word zero still names physical pattern zero. */
    public record PlaneImage(int[] words64x32) implements RetainedPlaneDescriptorSource.Plane {
        public PlaneImage {
            Objects.requireNonNull(words64x32, "complete physical plane");
            if (words64x32.length != CELLS) throw new IllegalArgumentException("Physical plane requires 64x32 words");
            words64x32 = words64x32.clone();
            for (int word : words64x32) {
                if ((word & ~0xFFFF) != 0) throw new IllegalArgumentException("Plane descriptors are unsigned native WORDs");
            }
        }
        @Override public int[] words64x32() { return words64x32.clone(); }
        @Override public int widthTiles() { return COLUMNS; }
        @Override public int heightTiles() { return ROWS; }
        @Override public long revision() { return 1; }
        @Override public int descriptorAt(int pixelX, int pixelY) {
            return words64x32[Math.floorMod(Math.floorDiv(pixelY, 8), ROWS) * COLUMNS
                    + Math.floorMod(Math.floorDiv(pixelX, 8), COLUMNS)];
        }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof PlaneImage image && Arrays.equals(words64x32, image.words64x32);
        }
        @Override public int hashCode() { return Arrays.hashCode(words64x32); }
    }

    /**
     * Selected values, not admission. HScroll keeps all 256 packed LONGs, including
     * the untransferred tail; only a private GPU staging value is reduced modulo512.
     * Disabled display may retain absent unsampled planes and never samples pixels.
     */
    public record Scene(PlaneImage foreground, PlaneImage background, int[] packedHscroll256,
                        int vscrollLongBits, byte[] selectedPalette128, boolean displayEnabled,
                        SpritePresentation.Frame sprites,
                        Map<Integer, SpritePresentation.PatternVersion> nativePatterns) {
        public Scene {
            Objects.requireNonNull(packedHscroll256, "selected HScroll");
            Objects.requireNonNull(selectedPalette128, "selected palette");
            Objects.requireNonNull(sprites, "selected native sprite picture");
            Objects.requireNonNull(nativePatterns, "known selected pattern versions");
            if (packedHscroll256.length != 256) throw new IllegalArgumentException("Selected HScroll requires 256 LONGs");
            if (selectedPalette128.length != 128) throw new IllegalArgumentException("Selected palette requires 128 bytes");
            if (displayEnabled) {
                Objects.requireNonNull(foreground, "enabled physical foreground");
                Objects.requireNonNull(background, "enabled physical background");
            }
            packedHscroll256 = packedHscroll256.clone();
            selectedPalette128 = selectedPalette128.clone();
            var frozen = new TreeMap<Integer, SpritePresentation.PatternVersion>();
            for (var entry : nativePatterns.entrySet()) {
                Integer id = Objects.requireNonNull(entry.getKey(), "native pattern ID");
                if (id < 0 || id >= PATTERNS) throw new IllegalArgumentException("Expected final 11-bit native pattern ID");
                frozen.put(id, Objects.requireNonNull(entry.getValue(), "known immutable pattern version"));
            }
            nativePatterns = Collections.unmodifiableMap(frozen);
        }
        @Override public int[] packedHscroll256() { return packedHscroll256.clone(); }
        @Override public byte[] selectedPalette128() { return selectedPalette128.clone(); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Scene scene
                    && Objects.equals(foreground, scene.foreground) && Objects.equals(background, scene.background)
                    && Arrays.equals(packedHscroll256, scene.packedHscroll256)
                    && vscrollLongBits == scene.vscrollLongBits
                    && Arrays.equals(selectedPalette128, scene.selectedPalette128)
                    && displayEnabled == scene.displayEnabled && sprites.equals(scene.sprites)
                    && nativePatterns.equals(scene.nativePatterns);
        }
        @Override public int hashCode() {
            int hash = Objects.hash(foreground, background, vscrollLongBits, displayEnabled, sprites, nativePatterns);
            hash = 31 * hash + Arrays.hashCode(packedHscroll256);
            return 31 * hash + Arrays.hashCode(selectedPalette128);
        }
    }

    private record OwnedPattern(PatternAtlas.Entry entry, SpritePresentation.PatternVersion version) { }
    private final GraphicsManager graphics;
    private final PatternAtlas atlas;
    private final PatternAtlasRange range;
    private final PatternAtlas.PatternRange registration;
    private final int base;
    private final Thread thread;
    private final GLCapabilities capabilities;
    private final String backend;
    private final float[] projection;
    private final Map<Integer, OwnedPattern> touched = new LinkedHashMap<>();
    private TilemapGpuRenderer tilemap;
    private HScrollBuffer backgroundScroll;
    private Scene residentScene;
    private boolean drawing, closing, closed;

    /** Claims an actual empty governed range; logical coordinates alone never grant ownership. */
    public RetainedPlaneSceneRenderer(GraphicsManager graphics, PatternAtlasRange ownedRange) {
        engineCaller();
        this.graphics = Objects.requireNonNull(graphics, "graphics owner");
        range = Objects.requireNonNull(ownedRange, "owned logical range");
        if (graphics.getClass().getClassLoader() != GraphicsManager.class.getClassLoader())
            throw new SecurityException("Retained rendering requires an engine graphics owner");
        if (range.size() < PATTERNS) throw new IllegalArgumentException("Logical range cannot contain native 11-bit IDs");
        if (!graphics.isGlInitialized() || graphics.isHeadlessMode())
            throw new IllegalStateException("Retained scene requires actual initialized GL, not logic-only graphics");
        graphics.requireUncapturedCommandScope();
        thread = Thread.currentThread(); capabilities = GL.getCapabilities();
        atlas = Objects.requireNonNull(graphics.getPatternAtlas(), "actual initialized atlas");
        if (!atlas.isInitialized()) throw new IllegalStateException("Retained scene requires an initialized atlas");
        atlas.requireUploadIdle();
        backend = System.getProperty(BACKEND_PROPERTY);
        if (backend != null && !backend.isEmpty() && !backend.equals("surfaceless-egl"))
            throw new IllegalStateException("Unsupported selected headless graphics backend");
        projection = checkedProjection(graphics);
        requireViewport();
        if (graphics.getSpritePriorityShaderProgram() == null)
            throw new IllegalStateException("Native sprite priority renderer is unavailable");
        base = range.base();
        // A permanent enum's numerical governance is not a producer's range claim.
        // Reject even unregistered old entries rather than overwrite their pixels.
        for (int id = base; id < range.endExclusive(); id++) {
            if (atlas.getEntry(id) != null)
                throw new IllegalArgumentException("Logical range already contains another producer's patterns");
        }
        atlas.registerRange(range);
        registration = atlas.registeredRangesForTesting().getLast();
    }

    /** Draws only the supplied immutable selection; callback validates the caller's actual native cut. */
    public void draw(Scene selected, int cameraX, int cameraY, Runnable validateOwner) {
        engineCaller();
        if (closed || closing || drawing) throw new IllegalStateException("Retained renderer is closed or already active");
        Objects.requireNonNull(selected, "selected scene"); Objects.requireNonNull(validateOwner, "actual owner validation");
        requireCurrent();
        validateScene(selected);
        drawing = true;
        GraphicsState state = null;
        Throwable failure = null;
        try {
            revalidate(validateOwner);
            graphics.drainBeforeBackgroundScope(cameraX, cameraY, WIDTH, HEIGHT);
            revalidate(validateOwner); // Actual old-image work completed before any new upload.
            state = new GraphicsState(graphics);
            state.prepareDraw();
            PaletteUploadPresentation.withoutDisplayedFade(graphics, () -> {
                if (!selected.displayEnabled) {
                    paintBackdrop(0, cameraX, cameraY);
                    return;
                }
                initializePrivateGpu();
                uploadPalette(selected.selectedPalette128);
                int texture = uploadPatterns(selected);
                Integer palette = graphics.getCombinedPaletteTextureId();
                if (palette == null || palette == 0 || !glIsTexture(palette))
                    throw new IllegalStateException("Selected palette has no actual GPU residency");
                preparePlaneImages(selected);
                int[] packed = gpuScroll(selected.packedHscroll256);
                backgroundScroll.upload(packed);
                TilePriorityFBO priority = requirePriorityFbo();
                revalidate(validateOwner);
                paintBackdrop(backdrop(selected.selectedPalette128), cameraX, cameraY);
                prepareRasterState();
                drawPlane(TilemapGpuRenderer.Layer.BACKGROUND, 0, false, selected, packed, texture, palette);
                drawPlane(TilemapGpuRenderer.Layer.FOREGROUND, 0, false, selected, packed, texture, palette);
                drawPlane(TilemapGpuRenderer.Layer.BACKGROUND, 1, false, selected, packed, texture, palette);
                drawPlane(TilemapGpuRenderer.Layer.FOREGROUND, 1, false, selected, packed, texture, palette);
                priority.begin();
                try {
                    glEnable(GL_BLEND); glBlendEquation(GL_MAX); glBlendFunc(GL_ONE, GL_ONE);
                    drawPlane(TilemapGpuRenderer.Layer.BACKGROUND, 1, true, selected, packed, texture, palette);
                    drawPlane(TilemapGpuRenderer.Layer.FOREGROUND, 1, true, selected, packed, texture, palette);
                } finally { priority.end(); }
                prepareRasterState();
                var nativeFrame = nativeOcclusionFrame(selected.sprites);
                graphics.withoutVerticalWrapAdjust(() -> graphics.executeCapturedCommands(() -> {
                    SpritePresentationRenderer.drawNative(graphics, nativeFrame, cameraX, cameraY, layer -> true);
                    graphics.flushPatternBatch();
                }, cameraX, cameraY, WIDTH, HEIGHT));
            });
            revalidate(validateOwner);
        } catch (RuntimeException | Error problem) {
            failure = problem; residentScene = null;
            throw problem;
        } finally {
            try {
                if (state != null) {
                    try { state.close(); }
                    catch (RuntimeException | Error cleanup) {
                        if (failure != null) { if (failure != cleanup) failure.addSuppressed(cleanup); }
                        else throw cleanup;
                    }
                }
            } finally { drawing = false; }
        }
    }

    private static void engineCaller() {
        if (EngineCallerAccess.callerOutside(RetainedPlaneSceneRenderer.class).getClassLoader()
                != RetainedPlaneSceneRenderer.class.getClassLoader())
            throw new SecurityException("Retained scene rendering belongs to the engine");
    }

    private void requireContext() {
        if (Thread.currentThread() != thread || GL.getCapabilities() != capabilities)
            throw new IllegalStateException("Retained renderer belongs to another GL thread/context");
    }

    private void requireCurrent() {
        if (closed || closing || !graphics.isGlInitialized() || graphics.isHeadlessMode()
                || graphics.getPatternAtlas() != atlas || !atlas.isInitialized()
                || !Objects.equals(backend, System.getProperty(BACKEND_PROPERTY)))
            throw new IllegalStateException("Retained graphics/atlas/backend lifetime changed");
        requireContext(); requireViewport(); atlas.requireUploadIdle();
        if (!Arrays.equals(projection, checkedProjection(graphics)))
            throw new IllegalStateException("Retained projection changed");
        if (atlas.registeredRangesForTesting().stream().noneMatch(value -> value == registration))
            throw new IllegalStateException("Retained logical range registration was retired");
        graphics.requireUncapturedCommandScope();
        for (var entry : touched.entrySet()) {
            if (atlas.getEntry(entry.getKey()) != entry.getValue().entry()
                    || !Objects.equals(atlas.scenePatternSample(entry.getKey()), entry.getValue().version()))
                throw new IllegalStateException("Retained pattern range was overwritten by another producer");
        }
    }

    private void revalidate(Runnable owner) { owner.run(); requireCurrent(); }

    private static float[] checkedProjection(GraphicsManager graphics) {
        float[] matrix = graphics.getProjectionMatrixBuffer();
        var viewport = PresentationGraphicsAccess.viewport(graphics);
        if (graphics.getProjectionWidth() != WIDTH || matrix == null || matrix.length != 16 || viewport == null
                || Math.abs(viewport.left()) > 0.001f || Math.abs(viewport.top()) > 0.001f
                || Math.abs(viewport.right() - WIDTH) > 0.001f || Math.abs(viewport.bottom() - HEIGHT) > 0.001f)
            throw new IllegalStateException("Retained scene requires the actual native orthographic projection");
        return matrix.clone();
    }

    private void requireViewport() {
        int[] viewport = integers(GL_VIEWPORT, 4);
        if (viewport[0] != 0 || viewport[1] != 0 || viewport[2] != WIDTH || viewport[3] != HEIGHT
                || graphics.getViewportX() != 0 || graphics.getViewportY() != 0
                || graphics.getViewportWidth() != WIDTH || graphics.getViewportHeight() != HEIGHT)
            throw new IllegalStateException("Retained scene requires matching actual and cached 320x224 viewports at zero origin");
    }

    private void validateScene(Scene selected) {
        if (!selected.displayEnabled) return; // Unknown initial planes/pixels are deliberately unsampled.
        requirePlanePatterns(selected.foreground, selected.nativePatterns);
        requirePlanePatterns(selected.background, selected.nativePatterns);
        var picture = selected.sprites;
        if (!picture.primitives().isEmpty() || picture.tiles().size() > 80 * 16)
            throw new IllegalArgumentException("Retained native SAT cannot contain overlays or unbounded geometry");
        for (var entry : picture.patternVersions().entrySet()) {
            int nativeId = entry.getKey() - base;
            if (nativeId < 0 || nativeId >= PATTERNS
                    || !entry.getValue().equals(selected.nativePatterns.get(nativeId)))
                throw new IllegalArgumentException("Selected sprite pixels differ from the owned physical pattern mapping");
        }
        for (var tile : picture.tiles()) {
            int nativeId = tile.patternId() - base;
            if (nativeId < 0 || nativeId >= PATTERNS || !selected.nativePatterns.containsKey(nativeId)
                    || !selected.nativePatterns.get(nativeId).equals(picture.patternVersions().get(tile.patternId())))
                throw new IllegalArgumentException("Selected sprite has no matching owned immutable pattern version");
            if (tile.layer() != SpritePresentation.Layer.OBJECT || !tile.priorityShader()
                    || tile.palette() < 0 || tile.palette() > 3 || tile.width() != 8 || tile.height() != 8
                    || !Float.isFinite(tile.x()) || !Float.isFinite(tile.y())
                    || tile.x() != Math.rint(tile.x()) || tile.y() != Math.rint(tile.y())
                    || tile.rowStart() != 0 || tile.rowEnd() != 8 || tile.ghost() || tile.ghostAlpha() != 1
                    || tile.ghostColour() || !SpritePresentation.Subject.WORLD.equals(tile.subject())
                    || tile.pixelOperation() != SpriteSatEntry.PixelOperation.NORMAL
                    || (tile.occlusionMask() != 0 && tile.occlusionMask() != 0xF))
                throw new IllegalArgumentException("Only already selected normal native SAT tiles are supported");
        }
    }

    private static void requirePlanePatterns(PlaneImage plane,
            Map<Integer, SpritePresentation.PatternVersion> patterns) {
        for (int descriptor : plane.words64x32) {
            if (!patterns.containsKey(descriptor & 0x7FF))
                throw new IllegalArgumentException("Physical plane references an unknown selected pattern " + (descriptor & 0x7FF));
        }
    }

    private void initializePrivateGpu() {
        if (tilemap != null) return;
        var next = new TilemapGpuRenderer(WIDTH);
        var scroll = new HScrollBuffer(false);
        try {
            next.init(TILEMAP_SHADER); scroll.init();
            tilemap = next; backgroundScroll = scroll;
        } catch (IOException | RuntimeException | Error problem) {
            try { next.cleanup(); } catch (RuntimeException | Error cleanup) { if (problem != cleanup) problem.addSuppressed(cleanup); }
            try { scroll.cleanup(); } catch (RuntimeException | Error cleanup) { if (problem != cleanup) problem.addSuppressed(cleanup); }
            if (problem instanceof IOException io) throw new UncheckedIOException("Cannot initialize retained plane renderer", io);
            if (problem instanceof RuntimeException runtime) throw runtime;
            throw (Error) problem;
        }
    }

    private void uploadPalette(byte[] selected) {
        for (int line = 0; line < 4; line++) {
            var palette = new Palette();
            palette.fromSegaFormat(Arrays.copyOfRange(selected, line * 32, line * 32 + 32));
            graphics.cachePaletteTexture(palette, line);
        }
    }

    private int uploadPatterns(Scene selected) {
        graphics.beginPatternAtlasBatch();
        try {
            for (var version : selected.nativePatterns.entrySet()) {
                int logical = base + version.getKey();
                try { graphics.restoreFrozenPattern(version.getValue(), logical); }
                finally {
                    var actual = atlas.getEntry(logical);
                    if (actual != null) touched.put(logical, new OwnedPattern(actual, atlas.scenePatternSample(logical)));
                }
            }
        } finally { graphics.endPatternAtlasBatch(); }
        byte[] lookup = new byte[PATTERNS * 4];
        for (var version : selected.nativePatterns.entrySet()) {
            int logical = base + version.getKey();
            var entry = atlas.getEntry(logical);
            if (entry == null || entry.patternId() != logical || entry.atlasIndex() != 0
                    || entry.tileX() < 0 || entry.tileY() < 0 || entry.tileX() > 255 || entry.tileY() > 255
                    || (entry.tileX() + 1) * 8 > atlas.getAtlasWidth() || (entry.tileY() + 1) * 8 > atlas.getAtlasHeight()
                    || !version.getValue().equals(atlas.scenePatternSample(logical)))
                throw new IllegalStateException("Selected physical pattern has no coherent page-zero GPU residency");
            int at = version.getKey() * 4;
            lookup[at] = (byte) entry.tileX(); lookup[at + 1] = (byte) entry.tileY(); lookup[at + 3] = (byte) 0xFF;
        }
        int texture = atlas.getTextureId(0);
        if (texture == 0 || !glIsTexture(texture)) throw new IllegalStateException("Owned indexed atlas texture is unavailable");
        tilemap.setPatternLookupData(lookup, PATTERNS);
        return texture;
    }

    private void preparePlaneImages(Scene selected) {
        if (residentScene != selected) {
            tilemap.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND,
                    TilemapGpuRenderer.packWindowDescriptors(selected.foreground.words64x32), COLUMNS, ROWS);
            tilemap.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND,
                    TilemapGpuRenderer.packWindowDescriptors(selected.background.words64x32), COLUMNS, ROWS);
            tilemap.setBgVdpWrapHeight(ROWS);
            residentScene = selected;
        }
    }

    private TilePriorityFBO requirePriorityFbo() {
        int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = integers(GL_VIEWPORT, 4);
        TilePriorityFBO fbo;
        try { fbo = graphics.getTilePriorityFBO(WIDTH, HEIGHT); }
        finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw); glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
        if (fbo == null || !fbo.isInitialized() || fbo.getWidth() != WIDTH || fbo.getHeight() != HEIGHT
                || !glIsTexture(fbo.getTextureId()))
            throw new IllegalStateException("Native terrain-priority mask is unavailable");
        return fbo;
    }

    private static int[] gpuScroll(int[] selected) {
        int[] packed = new int[selected.length];
        for (int line = 0; line < packed.length; line++) {
            // HScrollBuffer clamps -32768/32767. Equivalent physical-ring coordinates
            // avoid that edge while ALL original selected bits remain in Scene.
            int foreground = Math.floorMod((short) (selected[line] >>> 16), COLUMNS * 8);
            int background = Math.floorMod((short) selected[line], COLUMNS * 8);
            packed[line] = (foreground << 16) | background;
        }
        return packed;
    }

    private void drawPlane(TilemapGpuRenderer.Layer layer, int priority, boolean mask, Scene selected,
                           int[] packed, int atlasTexture, int paletteTexture) {
        if (layer == TilemapGpuRenderer.Layer.FOREGROUND) tilemap.enablePerLineForegroundScroll(packed);
        else tilemap.enablePerLineScroll(backgroundScroll.getTextureId(), HEIGHT, 0, 0, 0);
        tilemap.setClipHorizontal(false); tilemap.setForegroundWindow(null);
        int word = layer == TilemapGpuRenderer.Layer.FOREGROUND ? selected.vscrollLongBits >>> 16 : selected.vscrollLongBits;
        int vertical = Math.floorMod((short) word, ROWS * 8);
        tilemap.render(layer, WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT, 0, vertical,
                atlas.getAtlasWidth(), atlas.getAtlasHeight(), atlasTexture, paletteTexture,
                0, priority, true, mask, false, 0);
    }

    private int backdrop(byte[] palette) {
        var colour = new Palette.Color(); colour.fromSegaFormat(palette, 0);
        return DisplayColorConverter.toPackedRgb(colour.r & 255, colour.g & 255, colour.b & 255,
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
    }

    private void paintBackdrop(int rgb, int cameraX, int cameraY) {
        graphics.executeCapturedCommands(() -> graphics.registerCommand(
                GLCommand.screenSpaceRect(graphics, HEIGHT, 0, 0, WIDTH, HEIGHT, 0xFF000000 | rgb)),
                cameraX, cameraY, WIDTH, HEIGHT);
    }

    private static SpritePresentation.Frame nativeOcclusionFrame(SpritePresentation.Frame selected) {
        var tiles = new ArrayList<SpritePresentation.Tile>(selected.tiles().size());
        for (var tile : selected.tiles()) {
            // The older generic CPU converter emitted mask0 for its explicitly bounded
            // overlay path. This native plane renderer owns normal low-SAT occlusion.
            // Geometry/order/pixel versions/palette/priority remain exactly selected.
            tiles.add(new SpritePresentation.Tile(tile.layer(), tile.patternId(), tile.palette(), tile.hFlip(), tile.vFlip(),
                    tile.priority(), tile.x(), tile.y(), tile.width(), tile.height(), true, tile.priority() ? 0 : 0xF,
                    tile.ghost(), tile.ghostAlpha(), tile.rowStart(), tile.rowEnd(), tile.subject(),
                    tile.ghostColour(), tile.pixelOperation()));
        }
        return new SpritePresentation.Frame(tiles, selected.primitives(), selected.patternVersions());
    }

    private static void prepareRasterState() {
        glDisable(GL_DEPTH_TEST); glDepthMask(false); glDisable(GL_STENCIL_TEST); glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE); glDisable(GL_DITHER); glDisable(GL_FRAMEBUFFER_SRGB);
        glColorMask(true, true, true, true); glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glEnable(GL_BLEND); glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Retires owned resources only; a replacement atlas can contain the same numerical IDs. */
    @Override public void close() {
        if (closed) return;
        engineCaller();
        if (drawing || closing) throw new IllegalStateException("Cannot retire an active retained draw");
        requireContext();
        graphics.requireUncapturedCommandScope();
        // An external transaction keeps its nesting and this owner keeps its retirement ledger.
        if (graphics.getPatternAtlas() == atlas) atlas.requireUploadIdle();
        closing = true;
        GraphicsState state = null;
        Throwable failure = null;
        try {
            try {
                if (graphics.getPatternAtlas() == atlas && graphics.isGlInitialized() && !graphics.isHeadlessMode())
                    graphics.drainBeforeBackgroundScope(0, 0, WIDTH, HEIGHT);
            } catch (RuntimeException | Error problem) { failure = merge(failure, problem); }
            // The FIFO executor already unwinds its failed command and tail. A barrier fault
            // must not prevent independent retirement of entries that we still actually own.
            try { state = new GraphicsState(graphics); glActiveTexture(GL_TEXTURE0); }
            catch (RuntimeException | Error problem) { failure = merge(failure, problem); }
            try {
                if (graphics.getPatternAtlas() == atlas) {
                    atlas.requireUploadIdle(); // The drained FIFO may itself have opened a batch.
                    var entries = touched.entrySet().iterator();
                    while (entries.hasNext()) {
                        var entry = entries.next(); int id = entry.getKey(); var owned = entry.getValue();
                        if (atlas.getEntry(id) != owned.entry() || !Objects.equals(atlas.scenePatternSample(id), owned.version())) {
                            failure = merge(failure, new IllegalStateException("Foreign replacement inside retained pattern range was preserved"));
                            entries.remove(); // Those resources no longer belong to this renderer.
                            continue;
                        }
                        try { atlas.removeEntry(id); atlas.releaseScenePatternSample(id); entries.remove(); }
                        catch (RuntimeException | Error cleanup) { failure = merge(failure, cleanup); }
                    }
                } else {
                    // The former atlas was retired by its graphics owner; never touch replacement IDs.
                    touched.clear();
                }
            } catch (RuntimeException | Error problem) { failure = merge(failure, problem); }
        } finally {
            try {
                // Unbind before deletion; restoring a deleted private handle must not recreate it.
                if (state != null) { glUseProgram(0); glBindVertexArray(0); }
                if (tilemap != null) {
                    try { tilemap.cleanup(); tilemap = null; }
                    catch (RuntimeException | Error cleanup) { failure = merge(failure, cleanup); }
                }
                if (backgroundScroll != null) {
                    try { backgroundScroll.cleanup(); backgroundScroll = null; }
                    catch (RuntimeException | Error cleanup) { failure = merge(failure, cleanup); }
                }
                if (state != null) {
                    try { state.close(); } catch (RuntimeException | Error cleanup) { failure = merge(failure, cleanup); }
                }
            } finally {
                residentScene = null;
                closed = touched.isEmpty() && tilemap == null && backgroundScroll == null;
                closing = false;
            }
        }
        rethrow(failure);
    }

    private static Throwable merge(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }
    private static int[] integers(int name, int count) { int[] values = new int[count]; glGetIntegerv(name, values); return values; }

    /** Derived graphics state only; no source state, callback or capture owner survives the draw. */
    private static final class GraphicsState implements AutoCloseable {
        private final GraphicsManager graphics;
        private final boolean waterEnabled, underwaterSelection;
        private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        private final int renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING), program = glGetInteger(GL_CURRENT_PROGRAM);
        private final int vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING), arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        private final int pack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING), unpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        private final int unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT), unpackRow = glGetInteger(GL_UNPACK_ROW_LENGTH);
        private final int unpackSkipRows = glGetInteger(GL_UNPACK_SKIP_ROWS), unpackSkipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        private final int[] texture1d = new int[7], texture2d = new int[7], samplers = new int[7];
        private final int[] viewport = integers(GL_VIEWPORT, 4), scissor = integers(GL_SCISSOR_BOX, 4);
        private final int[] polygon = integers(GL_POLYGON_MODE, 2);
        private final float[] clear = new float[4];
        private final ByteBuffer colourMask = BufferUtils.createByteBuffer(4);
        private final boolean depth = glIsEnabled(GL_DEPTH_TEST), depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
        private final boolean stencil = glIsEnabled(GL_STENCIL_TEST), cull = glIsEnabled(GL_CULL_FACE);
        private final boolean blend = glIsEnabled(GL_BLEND), scissored = glIsEnabled(GL_SCISSOR_TEST);
        private final boolean dither = glIsEnabled(GL_DITHER), srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        private final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        private final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        private final int eqRgb = glGetInteger(GL_BLEND_EQUATION_RGB), eqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        private GraphicsState(GraphicsManager graphics) {
            this.graphics = graphics;
            waterEnabled = graphics.isWaterEnabled();
            underwaterSelection = graphics.isUseUnderwaterPaletteForBackground();
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clear); glGetBooleanv(GL_COLOR_WRITEMASK, colourMask);
            try {
                for (int unit = 0; unit < texture1d.length; unit++) {
                    glActiveTexture(GL_TEXTURE0 + unit);
                    texture1d[unit] = glGetInteger(GL_TEXTURE_BINDING_1D);
                    texture2d[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
                    samplers[unit] = glGetIntegeri(GL_SAMPLER_BINDING, unit);
                }
            } finally { glActiveTexture(activeTexture); }
        }
        private void prepareDraw() {
            // Lazy texture/cache helpers must operate only on the units saved by this scope.
            glActiveTexture(GL_TEXTURE0);
            graphics.setWaterEnabled(false);
            graphics.setUseUnderwaterPaletteForBackground(false);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0); glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1); glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0); glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            for (int unit = 0; unit < samplers.length; unit++) glBindSampler(unit, 0);
            prepareRasterState();
        }
        @Override public void close() {
            try {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw); glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            glClearColor(clear[0], clear[1], clear[2], clear[3]);
            glColorMask(colourMask.get(0) != 0, colourMask.get(1) != 0, colourMask.get(2) != 0, colourMask.get(3) != 0);
            glDepthMask(depthWrite); enable(GL_DEPTH_TEST, depth); enable(GL_STENCIL_TEST, stencil);
            enable(GL_CULL_FACE, cull); enable(GL_BLEND, blend); enable(GL_SCISSOR_TEST, scissored);
            enable(GL_DITHER, dither); enable(GL_FRAMEBUFFER_SRGB, srgb);
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha); glBlendEquationSeparate(eqRgb, eqAlpha);
            if (polygon[0] == polygon[1]) glPolygonMode(GL_FRONT_AND_BACK, polygon[0]);
            else { glPolygonMode(GL_FRONT, polygon[0]); glPolygonMode(GL_BACK, polygon[1]); }
            for (int unit = 0; unit < texture1d.length; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                glBindTexture(GL_TEXTURE_1D, texture1d[unit] == 0 || glIsTexture(texture1d[unit]) ? texture1d[unit] : 0);
                glBindTexture(GL_TEXTURE_2D, texture2d[unit] == 0 || glIsTexture(texture2d[unit]) ? texture2d[unit] : 0);
                glBindSampler(unit, samplers[unit]);
            }
            glActiveTexture(activeTexture);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pack); glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
            glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment); glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRow);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows); glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
            glBindVertexArray(vertexArray == 0 || glIsVertexArray(vertexArray) ? vertexArray : 0);
            glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer == 0 || glIsBuffer(arrayBuffer) ? arrayBuffer : 0);
            glUseProgram(program == 0 || glIsProgram(program) ? program : 0);
            } finally {
                graphics.setWaterEnabled(waterEnabled);
                graphics.setUseUnderwaterPaletteForBackground(underwaterSelection);
            }
        }
        private static void enable(int capability, boolean enabled) {
            if (enabled) glEnable(capability); else glDisable(capability);
        }
    }
}
