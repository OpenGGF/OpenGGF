package com.openggf.tests.reel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.Engine;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Actual-fixture frame adapters for the 2026-10-26 TAS test reel. No simulation or trace import. */
public final class TestReelCapture implements AutoCloseable {
    private static final ThreadLocal<TestReelCapture> ACTIVE = new ThreadLocal<>();
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path directory;
    private final Map<String,Object> manifest = new LinkedHashMap<>();
    private final BufferedWriter state;
    private PresentationOffscreenTestContext context;
    private int frames;

    static TestReelCapture begin(String uniqueId, String display, String className, String method,
            List<String> arguments) throws Exception {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested/concurrent recording unsupported");
        if (!"surfaceless-egl".equals(System.getProperty("openggf.headless.gl"))
                || System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null)
            throw new IllegalStateException("Test reel requires explicit surfaceless EGL and no display variables");
        var capture = new TestReelCapture(uniqueId, display, className, method, arguments);
        ACTIVE.set(capture);
        return capture;
    }
    private TestReelCapture(String uniqueId, String display, String className, String method,
            List<String> arguments) throws Exception {
        String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(uniqueId.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,20);
        directory = Path.of(System.getProperty("openggf.testReel.dir")).resolve("cases").resolve(key);
        Files.createDirectories(directory.getParent());
        Files.createDirectory(directory); // Refuse stale frames/overwriting previous evidence.
        Files.createDirectory(directory.resolve("frames"));
        state = Files.newBufferedWriter(directory.resolve("state.jsonl"));
        manifest.put("junit_unique_id", uniqueId); manifest.put("display_name", display);
        manifest.put("class", className); manifest.put("method", method); manifest.put("arguments", arguments);
        manifest.put("source_commit", System.getProperty("openggf.testReel.source", "unknown"));
        manifest.put("source_fingerprint", System.getProperty("openggf.testReel.fingerprint", "unknown"));
        manifest.put("fps",60); manifest.put("audio",false);
        manifest.put("source_kind","executed-junit-fixture");
        manifest.put("body_outcome","interrupted");
        manifest.put("outcome","pending-junit-completion");
    }
    public void bodyOutcome(String outcome, String detail) {
        manifest.put("body_outcome",outcome); manifest.put("body_detail",detail);
    }
    void junitOutcome(String outcome, String detail) {
        manifest.put("outcome",outcome); manifest.put("detail",detail);
        try { JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("case.json").toFile(),manifest); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** Lets an owned component adapter avoid any presentation work in ordinary tests. */
    public static boolean isRecording() { return ACTIVE.get() != null; }

    /** Records a frame already drawn by the fixture's own presentation owner; never advances it. */
    public static void recordFrame(RgbaImage image, String mode, Bk2FrameInput input) {
        var capture = ACTIVE.get();
        if (capture == null) return;
        Objects.requireNonNull(image, "fixture image");
        if (image.width() != 320 || image.height() != 224)
            throw new IllegalArgumentException("Test reel source must be 320x224");
        capture.frame(image, Objects.requireNonNull(mode, "fixture mode"), input);
    }

    /** Use at the exact existing step call: no extra inputs, ticks, settling or pacing. */
    public static void step(GameplayCaptureSession session, Bk2FrameInput input) {
        session.step(input);
        var capture = ACTIVE.get();
        if (capture != null) capture.frame(session.render(), session.loop().getCurrentGameMode().name(),
                input == null ? new Bk2FrameInput(0,0,0,false,"") : input);
    }
    /** Attach after a headless fixture's existing step. Presentation is initialized over that same world. */
    public static void afterHeadlessFrame(HeadlessTestFixture fixture, boolean up, boolean down,
            boolean left, boolean right, boolean jump) {
        int mask = (up?1:0)|(down?2:0)|(left?4:0)|(right?8:0)|(jump?16:0);
        afterHeadlessFrame(fixture,
                new Bk2FrameInput(fixture.frameCount(),mask,jump?1:0,false,"fixture logical input"), "LEVEL");
    }
    /** Preserves the fixture's real Start edge and mode for pause/presentation observations. */
    public static void afterHeadlessFrame(HeadlessTestFixture fixture, Bk2FrameInput input, String mode) {
        var capture = ACTIVE.get();
        if (capture == null) return;
        try {
            var before = playerInvariant(fixture.sprite());
            capture.prepareHeadlessPresentation();
            var graphics = GameServices.graphics(); var level = GameServices.level();
            level.setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            level.drawWithSpritePriority(GameServices.sprites());
            var camera = fixture.camera();
            graphics.flushWithCamera(camera.getXWithShake(), camera.getYWithShake(), camera.getWidth(), camera.getHeight());
            glFinish();
            if (!before.equals(playerInvariant(fixture.sprite())))
                throw new IllegalStateException("Recording presentation mutated the fixture player");
            recordFrame(ScreenshotCapture.captureFramebuffer(320,224), mode, input);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static List<Object> playerInvariant(com.openggf.sprites.playable.AbstractPlayableSprite p) {
        return List.of(p.getCentreX(),p.getCentreY(),p.getXSubpixelRaw(),p.getYSubpixelRaw(),
                p.getXSpeed(),p.getYSpeed(),p.getGSpeed(),p.getAngle(),p.getAir(),p.getRolling(),
                p.getMappingFrame(),p.getAnimationId(),p.getAnimationFrameIndex(),p.getAnimationTick());
    }
    private void prepareHeadlessPresentation() throws IOException {
        if (context != null) return;
        context = PresentationOffscreenTestContext.openConfigured(320,224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
        var graphics = GameServices.graphics();
        var cachedArt = com.openggf.graphics.TestReelPatternTransfer.capture(graphics);
        graphics.setHeadlessMode(false); graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
        cachedArt.upload(graphics);
        glViewport(0,0,320,224); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        graphics.setProjectionWidth(320); graphics.applyResolvedDisplayWidth(320);
        graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0,320,0,224).get(new float[16]));
        graphics.setViewport(0,0,320,224);
        var level = GameServices.level().getCurrentLevel();
        for (int i=0;i<level.getPaletteCount();i++) graphics.cachePaletteTexture(level.getPalette(i),i);
        graphics.beginPatternAtlasBatch();
        try { for(int i=0;i<level.getPatternCount();i++) if(level.getPattern(i)!=null) graphics.cachePatternTexture(level.getPattern(i),i); }
        finally { graphics.endPatternAtlasBatch(); }
        GameServices.level().rebuildLevelDerivedState();
        GameServices.level().initBackgroundRenderer();
    }
    private void frame(RgbaImage image, String mode, Bk2FrameInput input) {
        try {
            ScreenshotCapture.savePNG(image, directory.resolve("frames").resolve("%06d.png".formatted(frames)));
            var row = observeState(frames++,mode,input);
            state.write(JSON.writeValueAsString(row)); state.newLine();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    /** Optional observations: menus/loading can have no player, level, camera or object owner. */
    static Map<String,Object> observeState(int frame, String mode, Bk2FrameInput input) {
        var sprites = GameServices.spritesOrNull();
        var player = sprites == null ? null : sprites.getMainPlayable();
        var camera = GameServices.cameraOrNull();
        var level = GameServices.levelOrNull();
        var objects = level == null ? null : level.getObjectManager();
        var row = new LinkedHashMap<String,Object>();
        row.put("game", level == null ? null : GameServices.module().getGameCode());
        row.put("zone",level == null ? null : level.getCurrentZone());
        row.put("act",level == null ? null : level.getCurrentAct());
        row.put("mapping_frame",player == null ? null : player.getMappingFrame());
        row.put("frame",frame); row.put("mode",mode); row.put("input",input);
        row.put("player_present",player != null); row.put("objects_present",objects != null);
        row.put("x",player == null ? null : player.getCentreX()); row.put("y",player == null ? null : player.getCentreY());
        row.put("x_speed",player == null ? null : player.getXSpeed()); row.put("y_speed",player == null ? null : player.getYSpeed());
        row.put("ground_speed",player == null ? null : player.getGSpeed()); row.put("air",player == null ? null : player.getAir());
        row.put("rolling",player == null ? null : player.getRolling());
        row.put("camera_x",camera == null ? null : camera.getX()); row.put("camera_y",camera == null ? null : camera.getY());
        row.put("level_frame",level == null ? null : level.getFrameCounter());
        row.put("vint",objects == null ? null : objects.getVblaCounter());
        return row;
    }
    @Override public void close() throws Exception {
        ACTIVE.remove();
        try {
            state.close();
            manifest.put("frames",frames);
            manifest.put("capture_status",frames>0?"captured":"no-adapter");
            manifest.put("capture_reason",frames>0?"Actual executed fixture steps":"No frame adapter called; logic/synthetic/menu fixtures need an explicit drawable-state adapter");
            JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("case.json").toFile(),manifest);
        } finally {
            if (context != null) {
                try { GameServices.graphics().cleanup(); }
                finally { context.close(); }
            }
        }
    }
}
