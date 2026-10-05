package com.openggf.mods.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameModule;
import com.openggf.game.patch.PatchContext;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.code.ModBackedGamePatch;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModRegistrationException;
import com.openggf.mods.code.ModRegistrationPlan;
import com.openggf.mods.ModStateSaveResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            canvas.draw(ctx.art().image(2, 1, new int[] {0xFFFF0000, 0x00000000}), 50, 50,
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

    private SceneServices services(List<String> exits) {
        return new SceneServices(null, null, temp, null, () -> exits.add("game"), () -> exits.add("master"));
    }

    private static InputHandler input() {
        InputHandler input = mock(InputHandler.class);
        when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        return input;
    }

    @Test
    void registeredStartupSceneIsServedThroughTheFaultBoundary() {
        ProbeScene probe = new ProbeScene();
        ModRegistrationPlan plan = ModContextTestAccess.freezeWithStartupScene("cards", "s3k", () -> probe);
        GameModule base = mock(GameModule.class);
        GameModule module = new ModBackedGamePatch(plan, boundary(new ModRuntimeFindingStore()))
                .apply(base, mock(PatchContext.class));
        OwnedSceneFactory factory = assertInstanceOf(OwnedSceneFactory.class,
                module.getGameService(ModSceneFactory.class));
        assertEquals("cards", factory.ownerModId());
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
        host.open(new OwnedSceneFactory("cards", () -> probe, (owner, r) -> r.run()), services(exits), 400, 224);
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
        host.open(new OwnedSceneFactory("cards", () -> probe, boundary::run), services(new ArrayList<>()), 320, 224);
        assertThrows(ModFaultBoundary.CallbackAborted.class, () -> host.update(input()));
        host.close();
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
