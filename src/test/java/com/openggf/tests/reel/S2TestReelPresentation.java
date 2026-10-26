package com.openggf.tests.reel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.Engine;
import com.openggf.audio.NullAudioBackend;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageDataLoader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageResultsScreen;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Palette;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11.*;

/** Actual S2 component owners for the 2026-10-26 full TAS reel; never advances simulation. */
public final class S2TestReelPresentation implements AutoCloseable {
    private static final ObjectMapper SNAPSHOT = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private final PresentationOffscreenTestContext context;
    private Runnable retireWorld = () -> { };
    private Runnable restoreServices = () -> { };

    private S2TestReelPresentation() throws Exception {
        if (!TestReelCapture.isRecording()) { context = null; return; }
        // Component contexts do not install an audio owner. Retire any inherited device first.
        GameServices.audio().setBackend(new NullAudioBackend());
        context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
        var graphics = GameServices.graphics();
        graphics.setHeadlessMode(false);
        graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
        glViewport(0, 0, 320, 224);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        graphics.setProjectionWidth(320); graphics.applyResolvedDisplayWidth(320);
        graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0, 320, 0, 224).get(new float[16]));
        graphics.setViewport(0, 0, 320, 224);
    }

    public static S2TestReelPresentation open() throws Exception { return new S2TestReelPresentation(); }

    public void ownStage(Sonic2SpecialStageManager manager) {
        if (context != null) retireWorld = manager::reset;
    }

    /** The supplier exposes the fixture's complete rewind snapshot, including native slots and players. */
    public static void stageFrame(Sonic2SpecialStageManager manager, Supplier<?> snapshot, int buttons) {
        if (!TestReelCapture.isRecording()) return;
        var before = SNAPSHOT.valueToTree(snapshot.get());
        clear(); manager.draw(); finish("SPECIAL_STAGE", buttons);
        var after = SNAPSHOT.valueToTree(snapshot.get());
        if (!before.equals(after)) throw new IllegalStateException("S2 stage drawing mutated rewind state: "
                + differingFields(before, after));
    }

    /** Resolve only the missing presentation dependencies, using this fixture's S2 ROM and state. */
    public void attachResults(Sonic2SpecialStageResultsScreen screen, TestObjectServices services) throws Exception {
        if (context == null) return;
        var graphics = services.graphicsManager();
        var romManager = services.romManager();
        // These fixture fields are intentionally absent before this presentation-only scope.
        var romField = TestObjectServices.class.getDeclaredField("rom"); romField.setAccessible(true);
        var rom = (com.openggf.data.Rom) romField.get(services);
        services.withGraphicsManager(GameServices.graphics()).withRom(GameServices.rom().getRom())
                .withRomManager(GameServices.rom());
        restoreServices = () -> services.withGraphicsManager(graphics).withRom(rom).withRomManager(romManager);
        // The uninitialized module manager has no loader. Use the production S2 loader on the
        // same ROM, without initializing another stage or touching its gameplay/PLC ownership.
        var data = Sonic2SpecialStageResultsScreen.class.getDeclaredField("presentationData");
        var resolved = Sonic2SpecialStageResultsScreen.class.getDeclaredField("presentationResolved");
        data.setAccessible(true); resolved.setAccessible(true);
        data.set(screen, new Sonic2SpecialStageDataLoader(GameServices.rom().getRom()));
        resolved.setBoolean(screen, true);
    }

    public static void resultsFrame(Sonic2SpecialStageResultsScreen screen, TestObjectServices services) {
        if (!TestReelCapture.isRecording()) return;
        var before = resultState(screen, services);
        clear(); screen.appendRenderCommands(new ArrayList<>()); finish("SPECIAL_STAGE_RESULTS", 0);
        if (!before.equals(resultState(screen, services)))
            throw new IllegalStateException("S2 results drawing mutated tally state");
    }

    /** One current-sheet observation, with the native DEZ palette; no invented scene or extra tick. */
    public static void explosionSheet(PatternSpriteRenderer renderer) throws Exception {
        if (!TestReelCapture.isRecording()) return;
        try (var presentation = open()) {
            var rom = GameServices.rom().getRom();
            var graphics = GameServices.graphics();
            var player = new Palette();
            player.fromSegaFormat(rom.readBytes(Sonic2Constants.SONIC_TAILS_PALETTE_ADDR, 32));
            graphics.cachePaletteTexture(player, 0);
            // Same native palette directory calculation as Sonic2.getLevelPaletteInfo,
            // native zone $0E (DEZ). Only palettes are decoded; no level is loaded.
            int paletteIndex = rom.read32BitAddr(Sonic2Constants.LEVEL_DATA_DIR
                    + 14 * Sonic2Constants.LEVEL_DATA_DIR_ENTRY_SIZE + 8) >>> 24;
            int entry = Sonic2Constants.LEVEL_PALETTE_DIR + paletteIndex * 8;
            int address = rom.read32BitAddr(entry);
            int bytes = (rom.read16BitAddr(entry + 6) + 1) * 4;
            for (int line = 0; line < Math.min(3, bytes / 32); line++) {
                var palette = new Palette(); palette.fromSegaFormat(rom.readBytes(address + line * 32, 32));
                graphics.cachePaletteTexture(palette, line + 1);
            }
            renderer.ensurePatternsCached(graphics, 0x10000);
            clear(); renderer.drawFrameIndex(0, 160, 112); finish("DEZ_EXPLOSION_SHEET", 0);
        }
    }

    private static List<Integer> resultState(Sonic2SpecialStageResultsScreen screen, TestObjectServices services) {
        return List.of(screen.getState(), screen.getDisplayedRingCount(), screen.getDisplayedRingCountP2(),
                screen.getEmeraldBonus(), screen.getTotalBonus(), services.gameState().getScore());
    }
    private static List<String> differingFields(com.fasterxml.jackson.databind.JsonNode before,
                                               com.fasterxml.jackson.databind.JsonNode after) {
        var fields = new ArrayList<String>();
        before.fieldNames().forEachRemaining(name -> { if (!before.get(name).equals(after.get(name))) fields.add(name); });
        return fields;
    }
    private static void clear() { glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); }
    private static void finish(String mode, int buttons) {
        GameServices.graphics().flushWithCamera((short)0, (short)0, (short)320, (short)224);
        glFinish();
        TestReelCapture.recordFrame(ScreenshotCapture.captureFramebuffer(320, 224), mode,
                new Bk2FrameInput(0, (buttons & 0x0F) | ((buttons & 0x70) != 0 ? 16 : 0),
                        ((buttons & 0x40) != 0 ? 1 : 0) | ((buttons & 0x10) != 0 ? 2 : 0)
                                | ((buttons & 0x20) != 0 ? 4 : 0), (buttons & 0x80) != 0,
                        "actual component native buttons=$%02X; no extra update".formatted(buttons)));
    }
    @Override public void close() throws Exception {
        if (context == null) return;
        try { retireWorld.run(); }
        finally {
            try { restoreServices.run(); GameServices.graphics().cleanup(); }
            finally { context.close(); }
        }
    }
}
