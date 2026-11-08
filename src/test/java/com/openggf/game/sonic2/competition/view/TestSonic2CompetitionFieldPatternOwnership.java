package com.openggf.game.sonic2.competition.view;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2PaletteCycleInternalAccess;
import com.openggf.game.sonic2.competition.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionFieldPatternOwnership {
    @Test void nativeWorldOwnsEveryFieldPatternAfterLoadAnd120FramesAndBothAnimationOwnersRestore() throws Exception {
        GameServices.audio().setBackend(new NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var services = EngineServices.current();
        var root = SessionManager.getCurrentWorldSession().rootGameModule();
        try (var gl = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
             AutoCloseable cleanup = () -> {
                 try { SessionManager.closeGameplaySession(); }
                 finally { services.graphics().cleanup(); }
             }) {
            var graphics = services.graphics();
            graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            var session = new Sonic2CompetitionSession();
            var host = new Sonic2NativeCompetitionHost(root, services, ignored -> { }, new SilentViews(),
                    new Sonic2NativeCompetitionHost.NativeState() {
                        public boolean swapActorWorldState(GameplayModeContext context, Sonic2CompetitionSession session) { return false; }
                        public void freezeObjects(GameplayModeContext context, int ticks) { }
                    });
            var input = new InputHandler(); input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical, () -> false);
            session.updateMenu(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
            host.loadAct(session, 0, 0);
            var context = host.context();
            graphics.bindRuntimeManagedReferences(context.getCamera(), context.getFadeManager());
            var art = context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionArtResidency.class);
            assertNotNull(art);
            assertInstanceOf(Sonic2CompetitionAnimatedArt.class, context.getLevelManager().getAnimatedPatternManager());
            var palettes = assertInstanceOf(Sonic2PaletteCycleInternalAccess.Owner.class,
                    context.getLevelManager().getAnimatedPaletteManager());
            assertNotSame(context.getLevelManager().getAnimatedPatternManager(), palettes);
            assertEquals(2048, context.getLevelManager().getCurrentLevel().getPatternCount());
            for (int i = 0; i < 2048; i++) assertSame(art.fields().live()[i],
                    context.getLevelManager().getCurrentLevel().getPattern(i));
            art.fields().flush(graphics);
            assertAtlas(art, graphics);
            int completed = 0;
            for (int attempt = 0; completed < 120 && attempt < 122; attempt++) {
                LevelFrameStep.updateTimers(LevelFrameContext.from(context));
                var result = context.plcFrameLifecycle().runLogicalIteration(context::updateFade, frame -> {
                    var value = new LevelFrameResult[1];
                    NativeCompetitionIterationAccess.runAdmitted(frame, () -> value[0] = host.step(input, frame));
                    return value[0];
                });
                if (result == LevelFrameResult.GAMEPLAY_FRAME) completed++;
                art.fields().flush(graphics);
            }
            assertEquals(120, completed);
            assertInstanceOf(NullAudioBackend.class, services.audio().getBackend());
            assertInstanceOf(Sonic2CompetitionAnimatedArt.class, context.getLevelManager().getAnimatedPatternManager());
            assertAtlas(art, graphics);
            // Continue with real P2 input: jump and hold Down in place.
            // This exercises changing directional/rolling tail cues without a fabricated art stream.
            var tailFrames = new java.util.HashSet<Integer>();
            boolean sawRolling = false;
            int previous = 0;
            for (int tick = 0; tick < 600; tick++) {
                int phase = tick % 120;
                int held = 0;
                if (phase >= 35 && phase < 40) held |= com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_JUMP;
                if (phase >= 70 && phase < 100) held |= 2;
                var secondInput = PlayerInputState.of(held, held & ~previous, held, held & ~previous, false, false);
                previous = held;
                input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), secondInput));
                LevelFrameStep.updateTimers(LevelFrameContext.from(context));
                context.plcFrameLifecycle().runLogicalIteration(context::updateFade, frame -> {
                    NativeCompetitionIterationAccess.runAdmitted(frame, () -> host.step(input, frame));
                    return null;
                });
                tailFrames.add(art.capture().tailLast());
                sawRolling |= session.player(1).getRolling();
                art.fields().flush(graphics);
                assertAtlas(art, graphics);
            }
            assertTrue(sawRolling, "P2 input must exercise a rolling body/tail pose");
            assertTrue(tailFrames.size() > 2, "tail DPLC must select distinct stored frames: " + tailFrames);
            var registry = new RewindRegistry();
            registry.register(art); registry.register((Sonic2CompetitionAnimatedArt)context.getLevelManager().getAnimatedPatternManager()); registry.register(palettes);
            var saved = registry.capture();
            byte[] paletteState = palettes.capture().bytes();
            var artState = art.capture();
            byte[] changed = new byte[32]; java.util.Arrays.fill(changed, (byte) 0xFE);
            art.writePatterns(0, changed);
            for (int i = 0; i < 11; i++) palettes.update();
            art.fields().flush(graphics);
            registry.restore(saved);
            assertEquals(artState, art.capture());
            assertArrayEquals(paletteState, palettes.capture().bytes());
            art.fields().flush(graphics);
            assertAtlas(art, graphics);
            assertEquals(GL_NO_ERROR, glGetError());
        } finally { TestEnvironment.resetAll(); }
    }
    @Test void animatedMapOverrunSeedsOnlyTheActuallyLoadedDeformationWordsInAllSixActs() throws Exception {
        var rom = TestEnvironment.currentRom();
        for (int zone : new int[]{0, 11, 12}) for (int act = 0; act < 2; act++) {
            var source = new Sonic2CompetitionScrollSourceAdapter(rom,
                    new com.openggf.game.sonic2.Sonic2GameModule().getScrollHandlerProvider().getZoneConstants());
            var state = source.owner().capture();
            short[] retained = new short[24]; java.util.Arrays.fill(retained, (short) 0x5A5A);
            source.owner().restore(new com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.State(
                    state.p1(), state.p2(), retained, state.horizontal(), state.v1(), state.v2()));
            var profile = Sonic2CompetitionLevelProfile.load(rom, zone, act);
            var art = Sonic2CompetitionNativeArt.load(rom, profile);
            source.applyLoadedLayerDefinition(art);
            short[] actual = source.owner().capture().scratch();
            if (art.animationCopiedWords() == 0) assertArrayEquals(retained, actual);
            else for (int word = 0; word < actual.length; word++)
                assertEquals(art.readWord(0xA800 + word * 2), actual[word] & 65535,
                        "loaded LayerDef word " + word + " in " + profile.zoneAct());
            for (short word : retained) assertEquals((short) 0x5A5A, word, "seed input is immutable");
        }
    }
    static void assertAtlas(Sonic2CompetitionArtResidency art, GraphicsManager graphics) {
        int active = glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        int texture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try { assertAtlasContents(art, graphics); }
        finally {
            org.lwjgl.opengl.GL13.glActiveTexture(active);
            glBindTexture(GL_TEXTURE_2D, texture);
        }
    }
    private static void assertAtlasContents(Sonic2CompetitionArtResidency art, GraphicsManager graphics) {
        var atlas = graphics.getPatternAtlas();
        var pages = new HashMap<Integer, java.nio.ByteBuffer>();
        byte[] source = new byte[65536];
        for (int slot = 0; slot < 2048; slot++) System.arraycopy(art.patternBytes(slot), 0, source, slot * 32, 32);
        var patterns = art.fields().live();
        for (int id = 0; id < 2048; id++) {
            var entry = atlas.getEntry(id); assertNotNull(entry, "resident atlas ID " + id);
            var bytes = pages.computeIfAbsent(entry.atlasIndex(), page -> {
                var buffer = BufferUtils.createByteBuffer(atlas.getAtlasWidth() * atlas.getAtlasHeight());
                glBindTexture(GL_TEXTURE_2D, atlas.getTextureId(page));
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_BYTE, buffer);
                return buffer;
            });
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int expected = Im2FieldOracle.pixel(source, id & 1023, x, y * 2 + (id >>> 10));
                int cpu = patterns[id].getPixel(x, y);
                if (expected != cpu) fail("CPU field pattern " + id + " pixel " + x + "," + y
                        + ": expected " + expected + ", actual " + cpu);
                int offset = (entry.tileY() * 8 + y) * atlas.getAtlasWidth() + entry.tileX() * 8 + x;
                int gpu = bytes.get(offset) & 255;
                if (expected != gpu) fail("GPU field pattern " + id + " pixel " + x + "," + y
                        + ": expected " + expected + ", actual " + gpu);
            }
        }
    }
    private static final class SilentViews implements Sonic2NativeCompetitionHost.Views {
        public void prepare(GameplayModeContext context, Sonic2CompetitionSession session,
                            Sonic2CompetitionLevelProfile profile, Sonic2CompetitionNativeArt art) { }
        public void draw(GameplayModeContext context, Sonic2CompetitionSession session) { }
    }
}
