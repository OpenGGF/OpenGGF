package com.openggf.mods.scene.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import com.openggf.audio.AudioManager;
import com.openggf.audio.StreamedMusicPort;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameModule;
import com.openggf.game.patch.PatchContext;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModBackedGamePatch;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModRegistrationException;
import com.openggf.mods.code.ModRegistrationPlan;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.ModSceneFactory;
import com.openggf.mods.scene.SceneArt;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mod scenes: registration, fault boundary, host lifecycle, canvas recording, storage, rasteriser. */
class TestModSceneHost {
    @TempDir
    Path temp;

    /** A scene that counts ticks, draws a little, and can be told to fail or leave. */
    static final class ProbeScene implements ModScene {
        final List<String> calls = new ArrayList<>();
        boolean failOnUpdate;
        boolean leaveOnUpdate;
        String stored;

        @Override
        public void enter(SceneContext ctx) {
            calls.add("enter");
            ctx.storage().write("progress.txt", "level=3");
            stored = ctx.storage().read("progress.txt").orElse(null);
        }

        @Override
        public void update(SceneContext ctx) {
            calls.add("update:" + ctx.ticks());
            if (failOnUpdate) {
                throw new IllegalStateException("boom");
            }
            if (leaveOnUpdate) {
                ctx.exitToGameTitle();
            }
        }

        @Override
        public void draw(SceneContext ctx, SceneCanvas canvas) {
            canvas.clear(0x102040);
            canvas.fill(10, 10, 20, 5, 0x80FF0000);
            canvas.text("Hi!", 4, 4, 0xFFFFFFFF);
            canvas.draw(new SceneImage(2, 1, new int[] {0xFFFF0000, 0x00000000}), 50, 50,
                    SceneDraw.plain().withFlipX(true).withScale(2));
        }

        @Override
        public void exit(SceneContext ctx) {
            calls.add("exit");
        }
    }

    private static ModFaultBoundary boundary(ModRuntimeFindingStore findings) {
        return new ModFaultBoundary(Map.of(), findings, owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    private static OwnedSceneFactory owned(ModSceneFactory factory) {
        return ModContextTestAccess.ownedScene("cards", factory, boundary(new ModRuntimeFindingStore()));
    }

    private SceneServices services(List<String> exits) {
        return new SceneServices(null, null, temp, null, () -> exits.add("game"), () -> exits.add("master"));
    }

    private static InputHandler input() {
        InputHandler input = mock(InputHandler.class);
        when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        return input;
    }

    @Test
    void sceneSfxUsesTheHostOwnerAndCannotOutliveItsContext() {
        AudioManager audio = mock(AudioManager.class);
        var ref = new StreamedMusicPort.SfxRef("cards", "voice-alert");
        when(audio.playNamespacedSfx(ref)).thenReturn(true);
        SceneContext[] retained = new SceneContext[1];
        ModSceneHost host = new ModSceneHost();
        host.open(owned(() -> new ModScene() {
            @Override public void enter(SceneContext ctx) { retained[0] = ctx; }
            @Override public void update(SceneContext ctx) { }
            @Override public void draw(SceneContext ctx, SceneCanvas canvas) { }
        }), new SceneServices(audio, null, temp, null, () -> { }, () -> { }), 320, 224);
        assertTrue(retained[0].audio().playSfx("voice-alert"));
        assertFalse(retained[0].audio().playSfx("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> retained[0].audio().playSfx("other-mod:voice-alert"));
        verify(audio).playNamespacedSfx(ref);
        host.close();
        assertFalse(retained[0].audio().playSfx("after-close"));
        verify(audio, never()).playNamespacedSfx(new StreamedMusicPort.SfxRef("cards", "after-close"));
    }

    @Test
    void sceneSfxWithoutAnAudioBackendIsSilentButStillValidatesNames() {
        SceneContext[] retained = new SceneContext[1];
        ModSceneHost host = new ModSceneHost();
        host.open(owned(() -> new ModScene() {
            @Override public void enter(SceneContext ctx) { retained[0] = ctx; }
            @Override public void update(SceneContext ctx) { }
            @Override public void draw(SceneContext ctx, SceneCanvas canvas) { }
        }), services(new ArrayList<>()), 320, 224);
        assertFalse(retained[0].audio().playSfx("voice-alert"));
        assertThrows(IllegalArgumentException.class, () -> retained[0].audio().playSfx("../alert"));
        host.close();
    }

    @Test
    void registeredStartupSceneIsServedOnlyUnderTheEngineKey() {
        ProbeScene probe = new ProbeScene();
        ModRegistrationPlan plan = ModContextTestAccess.freezeWithStartupScene("cards", "s3k", () -> probe);
        GameModule base = mock(GameModule.class);
        GameModule module = new ModBackedGamePatch(plan, boundary(new ModRuntimeFindingStore()))
                .apply(base, mock(PatchContext.class));
        OwnedSceneFactory factory = assertInstanceOf(OwnedSceneFactory.class,
                module.getGameService(OwnedSceneFactory.class));
        assertEquals("cards", factory.ownerModId());
        assertSame(probe, OwnedSceneFactory.unwrap(factory.create()), "the creator's scene, wrapped");
        assertNull(module.getGameService(ModSceneFactory.class),
                "the creator's raw factory is not served; only the owned wrapper opens");
    }

    @Test
    void aFailingSceneFactoryIsCaughtByTheFaultBoundary() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        OwnedSceneFactory factory = ModContextTestAccess.ownedScene("cards", () -> {
            throw new IllegalStateException("no scene today");
        }, boundary(findings));
        assertThrows(ModFaultBoundary.CallbackAborted.class, factory::create);
        assertTrue(findings.snapshot().containsKey("cards"), "the failure is recorded against the owner");
    }

    @Test
    void aNullReturningSceneFactoryDisablesItsOwnerAndDependentsAndLeavesHostClosed() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> saved = new ArrayList<>();
        List<Set<String>> disabled = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of("dependent", Set.of("cards")), findings,
                owners -> {
                    saved.add(owners);
                    return new ModStateSaveResult.Saved();
                }, disabled::add);
        OwnedSceneFactory factory = ModContextTestAccess.ownedScene("cards", () -> null, boundary);
        ModSceneHost host = new ModSceneHost();

        ModFaultBoundary.CallbackAborted failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> host.open(factory, services(new ArrayList<>()), 320, 224));

        assertInstanceOf(NullPointerException.class, failure.getCause());
        assertEquals("cards", failure.owner());
        assertEquals(Set.of("cards", "dependent"), failure.disabledOwners());
        assertEquals(List.of(failure.disabledOwners()), disabled);
        assertEquals(disabled, saved, "the same owners are disabled in process and persisted");
        assertEquals(List.of("MOD_CALLBACK_FAILED"),
                findings.findingsFor("cards").stream().map(finding -> finding.code()).toList());
        assertFalse(host.isOpen());
        host.close();
    }

    @Test
    void legacyServicesKeepRunningRomAvailableThroughNewArtMethods() {
        SceneRomArt running = mock(SceneRomArt.class);
        when(running.gameId()).thenReturn("s2");
        SceneArt[] observed = new SceneArt[1];
        ModSceneFactory factory = () -> new ProbeSceneBase() {
            @Override
            public void enter(SceneContext ctx) {
                observed[0] = ctx.art();
            }
        };
        ModSceneHost host = new ModSceneHost();
        try {
            host.open(owned(factory), new SceneServices(null, running, temp, null, () -> { }, () -> { }),
                    320, 224);
            assertSame(running, observed[0].rom());
            assertEquals(List.of("s2"), observed[0].availableGames());
            assertSame(running, observed[0].rom("s2"));
            assertNull(observed[0].rom("s1"));
            assertThrows(IllegalArgumentException.class, () -> observed[0].rom("bogus"));
            assertThrows(IllegalArgumentException.class, () -> observed[0].rom(null));

            host.open(owned(factory), services(new ArrayList<>()), 320, 224);
            assertEquals(List.of(), observed[0].availableGames());
            assertNull(observed[0].rom("s2"));
            assertThrows(IllegalArgumentException.class, () -> observed[0].rom("bogus"),
                    "game-code validation applies even when no ROM art is available");
        } finally {
            host.close();
        }
    }

    @Test
    void aRequiredDisplayWidthBecomesTheModulesAspect() {
        GameModule base = mock(GameModule.class);
        when(base.requiredDisplayAspect()).thenReturn("SUPER_32_9");
        ModFaultBoundary boundary = boundary(new ModRuntimeFindingStore());
        GameModule wide = new ModBackedGamePatch(ModContextTestAccess.freezeWithDisplayWidth("cards", "s3k", 400),
                boundary).apply(base, mock(PatchContext.class));
        assertEquals("WIDE_16_9", wide.requiredDisplayAspect());
        GameModule plain = new ModBackedGamePatch(ModContextTestAccess.freezeWithStartupScene("cards", "s3k",
                ProbeScene::new), boundary).apply(base, mock(PatchContext.class));
        assertEquals("SUPER_32_9", plain.requiredDisplayAspect(), "without a request the base module decides");
        assertThrows(ModRegistrationException.class,
                () -> ModContextTestAccess.freezeWithDisplayWidth("cards", "s3k", 401), "only the presets");
        assertThrows(ModRegistrationException.class,
                () -> ModContextTestAccess.freezeStandaloneWithDisplayWidth("solo", 400), "patch mods only");
    }

    @Test
    void debugJumpsReachADebuggableSceneInsideItsFaultBoundary() {
        final class Debuggable extends ProbeSceneBase implements DebuggableScene {
            final List<String> jumps = new ArrayList<>();

            @Override
            public boolean debugJump(String command) {
                if (command.equals("boom")) {
                    throw new IllegalStateException("bad jump");
                }
                jumps.add(command);
                return command.startsWith("go:");
            }
        }
        Debuggable scene = new Debuggable();
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        ModSceneHost host = new ModSceneHost();
        assertFalse(host.debugJump("go:shop"), "no scene open");
        host.open(ModContextTestAccess.ownedScene("cards", () -> scene, boundary(findings)),
                services(new ArrayList<>()), 320, 224);
        assertTrue(host.debugJump("go:shop"));
        assertFalse(host.debugJump("nonsense"), "the scene says it did not understand");
        assertEquals(List.of("go:shop", "nonsense"), scene.jumps);
        assertThrows(ModFaultBoundary.CallbackAborted.class, () -> host.debugJump("boom"));
        assertTrue(findings.snapshot().containsKey("cards"), "a throwing jump is a mod fault like any other");
        host.close();

        ModSceneHost plain = new ModSceneHost();
        plain.open(owned(ProbeScene::new), services(new ArrayList<>()), 320, 224);
        assertFalse(plain.debugJump("go:shop"), "a scene without the interface has no debug entry");
        plain.close();
    }

    /** A do-nothing scene to extend in tests. */
    abstract static class ProbeSceneBase implements ModScene {
        @Override
        public void enter(SceneContext ctx) {
        }

        @Override
        public void update(SceneContext ctx) {
        }

        @Override
        public void draw(SceneContext ctx, SceneCanvas canvas) {
        }
    }

    @Test
    void standaloneModsCannotRegisterAStartupScene() {
        assertThrows(ModRegistrationException.class,
                () -> ModContextTestAccess.freezeStandaloneWithStartupScene("solo", () -> new ProbeScene()));
    }

    @Test
    void hostRunsTheSceneLifecycleAndRecordsDrawing() {
        ProbeScene probe = new ProbeScene();
        List<String> exits = new ArrayList<>();
        ModSceneHost host = new ModSceneHost();
        host.open(owned(() -> probe), services(exits), 400, 224);
        assertEquals("level=3", probe.stored);
        assertTrue(temp.resolve("mods/cards/progress.txt").toFile().isFile());

        host.update(input());
        host.update(input());
        host.draw(null, null);
        assertEquals(List.of("enter", "update:0", "update:1"), probe.calls);
        List<SceneDrawOp> ops = host.lastFrame();
        assertEquals(400f, ops.get(0).x1(), "clear fills the logical width");
        assertTrue(ops.size() >= 5, "clear, fill, three glyphs and an image were recorded");
        SceneDrawOp image = ops.get(ops.size() - 1);
        assertEquals(2f, image.u0(), "flipX swaps the source columns");
        assertEquals(54f, image.x1(), "scale 2 doubles the width");

        probe.leaveOnUpdate = true;
        host.update(input());
        host.update(input());
        assertEquals(List.of("game"), exits, "exit requested once; later ticks are ignored");

        host.close();
        assertFalse(host.isOpen());
        assertEquals("exit", probe.calls.get(probe.calls.size() - 1));
    }

    @Test
    void sceneFailuresDisableTheModInsteadOfCrashing() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        ModFaultBoundary boundary = boundary(findings);
        ProbeScene probe = new ProbeScene();
        probe.failOnUpdate = true;
        ModSceneHost host = new ModSceneHost();
        host.open(ModContextTestAccess.ownedScene("cards", () -> probe, boundary), services(new ArrayList<>()), 320,
                224);
        assertThrows(ModFaultBoundary.CallbackAborted.class, () -> host.update(input()));
        host.close();
    }

    @Test
    void engineShutdownRunsTheOpenScenesExit() {
        ProbeScene probe = new ProbeScene();
        ModSceneHost host = new ModSceneHost();
        host.open(owned(() -> probe), services(new ArrayList<>()), 320, 224);
        host.update(input());
        host.cleanup();
        assertFalse(host.isOpen());
        assertEquals(List.of("enter", "update:0", "exit"), probe.calls, "exit runs once, at shutdown");
        host.cleanup();
        assertEquals(3, probe.calls.size(), "a second cleanup has nothing left to close");
    }

    @Test
    void theMouseReportsBothButtonsEdgesAndWhetherItWasTheLastInput() {
        List<SceneMouse> seen = new ArrayList<>();
        ModSceneHost host = new ModSceneHost();
        host.open(owned(() -> new ModScene() {
            @Override
            public void enter(SceneContext ctx) {
            }

            @Override
            public void update(SceneContext ctx) {
                seen.add(ctx.mouse());
            }

            @Override
            public void draw(SceneContext ctx, SceneCanvas canvas) {
            }
        }), new SceneServices(null, null, temp, (x, y) -> new int[] {(int) x / 2, (int) y / 2, 1}, () -> { },
                () -> { }), 320, 224);
        InputHandler input = new InputHandler();
        Runnable tick = () -> {
            input.refreshLogicalSnapshot();
            host.update(input);
            input.update();
        };
        input.handleMouseMove(100, 60);
        tick.run();
        input.handleMouseButton(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        tick.run();
        tick.run();
        input.handleMouseButton(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        tick.run();
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_E, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        tick.run();

        SceneMouse moved = seen.get(0);
        assertEquals(50, moved.x(), "mapped to logical pixels");
        assertTrue(moved.moved() && moved.inside() && moved.lastInputWasMouse());
        assertTrue(seen.get(1).rightPressed() && seen.get(1).rightDown());
        assertTrue(seen.get(2).rightDown() && !seen.get(2).rightPressed(), "held, not pressed again");
        assertTrue(seen.get(3).rightReleased() && !seen.get(3).rightDown());
        assertFalse(seen.get(4).lastInputWasMouse(), "a key press hands the last input back to the keyboard");
        host.close();
    }

    @Test
    void buttonsRepeatLikeTheEngineMenusAndBGoesBack() {
        List<String> seen = new ArrayList<>();
        ModSceneHost host = new ModSceneHost();
        host.open(owned(() -> new ModScene() {
            @Override
            public void enter(SceneContext ctx) {
            }

            @Override
            public void update(SceneContext ctx) {
                String tick = ctx.ticks() + ":";
                if (ctx.buttonPressed(SceneButtons.DOWN)) tick += "pressed ";
                if (ctx.buttonRepeated(SceneButtons.DOWN)) tick += "repeat ";
                if (ctx.buttonDown(SceneButtons.DOWN | SceneButtons.UP)) tick += "held ";
                if (ctx.input().menuBack()) tick += "back ";
                if (ctx.input().menuAccept()) tick += "accept ";
                seen.add(tick.trim());
            }

            @Override
            public void draw(SceneContext ctx, SceneCanvas canvas) {
            }
        }), services(new ArrayList<>()), 320, 224);
        InputHandler input = mock(InputHandler.class);
        int down = com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN;
        for (int tick = 0; tick < 30; tick++) {
            PlayerInputState pad = PlayerInputState.of(down, tick == 0 ? down : 0, 0, 0, false, false);
            when(input.logical()).thenReturn(LogicalInputSnapshot.ofPlayers(pad, PlayerInputState.neutral()));
            host.update(input);
        }
        assertEquals("0:pressed repeat held", seen.get(0));
        assertEquals("1:held", seen.get(1));
        assertEquals("24:repeat held", seen.get(MenuRepeatTimings.DELAY), "repeats after the engine's menu delay");
        assertEquals("28:repeat held", seen.get(MenuRepeatTimings.DELAY + MenuRepeatTimings.INTERVAL));
        assertEquals("25:held", seen.get(25));

        PlayerInputState b = PlayerInputState.of(0, 0, com.openggf.control.InputActionMasks.ACTION_B,
                com.openggf.control.InputActionMasks.ACTION_B, false, false);
        when(input.logical()).thenReturn(LogicalInputSnapshot.ofPlayers(b, PlayerInputState.neutral()));
        host.update(input);
        assertEquals("30:back", seen.get(30), "in a scene B is back and not accept");
        PlayerInputState c = PlayerInputState.of(0, 0, com.openggf.control.InputActionMasks.ACTION_C,
                com.openggf.control.InputActionMasks.ACTION_C, false, false);
        when(input.logical()).thenReturn(LogicalInputSnapshot.ofPlayers(c, PlayerInputState.neutral()));
        host.update(input);
        assertEquals("31:accept", seen.get(31), "C confirms");
        host.close();
    }

    /** The engine's shared menu repeat timings. */
    private static final class MenuRepeatTimings {
        static final int DELAY = com.openggf.control.MenuRepeat.DELAY;
        static final int INTERVAL = com.openggf.control.MenuRepeat.INTERVAL;
    }

    @Test
    void storageRejectsUnsafeNamesAndListsFiles() {
        FileSceneStorage storage = new FileSceneStorage(temp.resolve("mods/x"));
        assertTrue(storage.list().isEmpty());
        assertTrue(storage.write("save.txt", "hello"));
        assertEquals(Optional.of("hello"), storage.read("save.txt"));
        assertEquals(List.of("save.txt"), storage.list());
        assertThrows(IllegalArgumentException.class, () -> storage.write("../escape.txt", "x"));
        assertThrows(IllegalArgumentException.class, () -> storage.read("Upper.txt"));
        assertTrue(storage.delete("save.txt"));
        assertEquals(Optional.empty(), storage.read("save.txt"));
    }

    @Test
    void rasteriserLaysOutTilesColumnMajorWithFlipsAndTransparency() {
        Pattern a = new Pattern();
        Pattern b = new Pattern();
        a.setPixel(0, 0, (byte) 1);
        b.setPixel(7, 7, (byte) 2);
        int[] palette = new int[64];
        palette[16 + 1] = 0xFF112233;
        palette[16 + 2] = 0xFF445566;
        // One piece, 1 tile wide and 2 tall: tile 0 on top, tile 1 below; palette line 1.
        SpriteMappingFrame frame = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -8, 1, 2, 0, false, false, 0)));
        SceneSprite sprite = SpriteRasterizer.rasterize(frame, new Pattern[] {a, b}, palette, 1, 0);
        assertEquals(8, sprite.width());
        assertEquals(16, sprite.height());
        assertEquals(4, sprite.originX());
        assertEquals(8, sprite.originY());
        assertEquals(0xFF112233, sprite.image().pixel(0, 0));
        assertEquals(0xFF445566, sprite.image().pixel(7, 15));
        assertEquals(0, sprite.image().pixel(1, 0), "colour 0 is transparent");

        SpriteMappingFrame flipped = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0, 0, 1, 2, 0, true, true, 0)));
        SceneSprite f = SpriteRasterizer.rasterize(flipped, new Pattern[] {a, b}, palette, 1, 0);
        assertEquals(0xFF112233, f.image().pixel(7, 15), "both flips move tile 0's corner pixel");
        assertEquals(0xFF445566, f.image().pixel(0, 0));
    }

    @Test
    void megaDriveColoursDecode() {
        int[] colours = SpriteRasterizer.colors(new byte[] {0x0E, (byte) 0xEE, 0x00, 0x00});
        assertEquals(0xFFFFFFFF, colours[0]);
        assertEquals(0xFF000000, colours[1]);
    }

    @Test
    void emptyFramesBecomeATransparentPixel() {
        SceneSprite empty = SpriteRasterizer.rasterize(null, new Pattern[0], new int[64], 0, 0);
        assertEquals(1, empty.width());
        assertEquals(0, empty.image().pixel(0, 0));
    }
}
