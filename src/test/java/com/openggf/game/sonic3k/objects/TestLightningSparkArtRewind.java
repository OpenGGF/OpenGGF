package com.openggf.game.sonic3k.objects;

import com.openggf.Engine;
import com.openggf.audio.NullAudioBackend;
import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.DefaultPowerUpSpawner;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.Mockito.*;

/** Exact ROM art and production spark pixels, isolated from any gameplay route or desktop. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledOnOs(OS.LINUX)
class TestLightningSparkArtRewind {
    private static final int WIDTH = 320, HEIGHT = 224;
    private PresentationOffscreenTestContext context;
    private GraphicsManager graphics;

    @BeforeEach
    void openRenderer() throws Exception {
        GameServices.audio().setBackend(new NullAudioBackend());
        context = PresentationOffscreenTestContext.open(WIDTH, HEIGHT);
        graphics = GraphicsManager.getInstance();
        graphics.setHeadlessMode(false);
        graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
        graphics.setViewport(0, 0, WIDTH, HEIGHT);
        graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0, WIDTH, 0, HEIGHT).get(new float[16]));
        graphics.setBatchingEnabled(false);
        glViewport(0, 0, WIDTH, HEIGHT);
        glDisable(GL_DITHER);
        glDisable(GL_BLEND);
        Palette palette = new Palette();
        for (int i = 1; i < 16; i++) {
            palette.colors[i] = new Palette.Color((byte) (i * 16), (byte) (255 - i * 8), (byte) (i * 8));
        }
        graphics.cachePaletteTexture(palette, 0);
    }

    @AfterEach
    void closeRenderer() {
        try {
            if (graphics != null) GraphicsManager.destroyForReinit();
        } finally {
            if (context != null) context.close();
        }
    }

    @Test
    void stockProducerRestoresExactRomTilesAndVisibleBlankFramesThroughRetirement() throws Exception {
        Harness h = new Harness(new Sonic3kGameModule());
        SpriteArtSet art = loadArt(h.services.module);
        assertEquals(5, art.artTiles().length);
        List<List<Integer>> expectedArt = pixels(art.artTiles());
        assertTrue(expectedArt.stream().allMatch(tile -> tile.stream().anyMatch(p -> p != 0)));
        assertEquals(20, art.animationSet().getScript(0).frames().size());
        h.produce();
        assertEquals(4, h.live().size());
        for (LightningSparkObjectInstance spark : h.live()) {
            assertSame(art.artTiles(), field(spark, "sparkTiles"));
            assertEquals(1, spark.getPriorityBucket());
            assertTrue(spark.isHighPriority());
        }
        h.tick(); h.tick(); h.tick(); // progressed visible frame, rather than a constructor-only check
        Frame boundary = h.frame();
        assertTrue(boundary.litPixels() > 0);
        CompositeSnapshot snapshot = h.rewind.capture();
        List<LightningSparkObjectInstance> originals = h.live();
        List<Frame> forward = h.advance(22);
        assertTrue(forward.stream().anyMatch(f -> !f.sparks().isEmpty() && f.litPixels() == 0));
        assertTrue(forward.stream().anyMatch(f -> f.litPixels() > 0));
        assertTrue(forward.getLast().sparks().isEmpty());
        assertTrue(originals.stream().allMatch(s -> s.isDestroyed() && !s.isDestroyedRespawnable()));

        h.services.module = new Sonic3kGameModule();
        SpriteArtSet replacement = loadArt(h.services.module);
        assertNotSame(art.artTiles(), replacement.artTiles());
        assertEquals(expectedArt, pixels(replacement.artTiles()), "current provider has the same ROM recipe");
        var directRecipe = LightningSparkObjectInstance.forRewindRecreate(originals.getFirst().getSpawn(), h.services);
        assertSame(replacement.artTiles(), field(directRecipe, "sparkTiles"));
        assertEquals(expectedArt, pixels((Pattern[]) field(directRecipe, "sparkTiles")),
                "the uncaptured provider-key candidate is valid for this stock art boundary only");
        retireAtlasEntries();
        h.rewind.restore(snapshot);
        for (LightningSparkObjectInstance spark : h.live()) {
            assertFalse(originals.contains(spark), "managed reconstruction must be destructive");
            assertEquals(expectedArt, pixels((Pattern[]) field(spark, "sparkTiles")));
        }
        assertEquals(boundary, h.frame(), "first draw must republish art in the current atlas");
        assertEquals(forward, h.advance(22), "pixels, animation, raw clocks, slots and retirement replay");
    }

    @Test
    void independentConstructorArtAndAnimationAreCapturedAsValuesRatherThanReplacedByStock() throws Exception {
        Harness h = new Harness(new Sonic3kGameModule());
        SpriteArtSet stock = loadArt(h.services.module);
        Pattern[] tiles = copy(stock.artTiles());
        tiles[0].setPixel(0, 0, (byte) 7);
        List<Integer> frames = new ArrayList<>(List.of(1, 2, 0));
        SpriteAnimationSet animation = new SpriteAnimationSet();
        animation.addScript(0, new SpriteAnimationScript(1, frames, SpriteAnimationEndAction.LOOP, 0));
        SpriteAnimationScript extraScript = new SpriteAnimationScript(3, List.of(2, 0),
                SpriteAnimationEndAction.LOOP, 5, List.of(0xFF, 7));
        animation.addScript(7, extraScript);
        LightningSparkObjectInstance source = h.manager.createDynamicObject(() ->
                new LightningSparkObjectInstance(160, 100, 0x100, -0x180, animation, tiles, true));
        h.tick();
        Frame boundary = h.frame();
        List<List<Integer>> expected = pixels(tiles);
        CompositeSnapshot snapshot = h.rewind.capture();
        List<Frame> forward = h.advance(23);
        assertTrue(forward.stream().anyMatch(f -> !f.sparks().isEmpty() && f.litPixels() == 0));
        assertTrue(forward.stream().anyMatch(f -> f.litPixels() > 0));
        tiles[0].clear(); tiles[1] = new Pattern(); frames.set(0, 2);
        h.services.module = null; // This recipe is independent of a live provider.
        retireAtlasEntries();
        h.rewind.restore(snapshot);
        LightningSparkObjectInstance restored = h.live().getFirst();
        assertNotSame(source, restored);
        assertEquals(expected, pixels((Pattern[]) field(restored, "sparkTiles")));
        assertEquals(extraScript, ((SpriteAnimationSet) field(restored, "animSet")).getScript(7));
        assertEquals(boundary, h.frame());
        assertEquals(forward, h.advance(23));
        // Reuse the same snapshot after mutating a previously restored recipe.
        ((Pattern[]) field(restored, "sparkTiles"))[0].clear();
        ((SpriteAnimationSet) field(restored, "animSet")).addScript(0,
                new SpriteAnimationScript(0, List.of(2), SpriteAnimationEndAction.LOOP, 0));
        h.rewind.restore(snapshot);
        retireAtlasEntries();
        assertEquals(expected, pixels((Pattern[]) field(h.live().getFirst(), "sparkTiles")));
        assertEquals(boundary, h.frame());
    }

    @Test
    void donorFactoryOnSonic2HostKeepsItsActualNullArtRecipeAndNativeAllocation() throws Exception {
        // The enabled S3K donor exports the shield factory, but this producer reads
        // the host object-art provider, not donor Insta-Shield art. Document that boundary.
        Harness h = new Harness(new Sonic2GameModule());
        var factory = new Sonic3kGameModule().getCrossGameDonorProvider().getShieldFactory();
        assertNotNull(factory);
        h.services.crossGame = mock(CrossGameFeatureProvider.class);
        when(h.services.crossGame.getDonorShieldFactory()).thenReturn(factory);
        LightningShieldObjectInstance shield = assertInstanceOf(LightningShieldObjectInstance.class,
                new DefaultPowerUpSpawner(h.manager).spawnShield(h.player, ShieldType.LIGHTNING));
        shield.triggerSparks();
        h.manager.removeDynamicObject(shield); // isolate spark recreation from the player-bound shield lifecycle
        assertEquals(4, h.live().size());
        assertTrue(h.live().stream().allMatch(s -> field(s, "sparkTiles") == null));
        Frame boundary = h.frame();
        assertEquals(0, boundary.litPixels());
        CompositeSnapshot snapshot = h.rewind.capture();
        List<LightningSparkObjectInstance> sources = h.live();
        List<Frame> forward = h.advance(23);
        h.services.module = new Sonic3kGameModule();
        loadArt(h.services.module); // A newly available stock provider must not invent donor pixels.
        h.rewind.restore(snapshot);
        assertTrue(h.live().stream().noneMatch(sources::contains));
        assertEquals(boundary, h.frame());
        assertEquals(forward, h.advance(23));
    }

    @Test
    void publicFactoriesRemainContextFreeAndNullArtStillRetires() throws Exception {
        var supplied = new LightningSparkObjectInstance(12, 14, 0, 0, null, null);
        supplied.appendRenderCommands(new ArrayList<>());
        assertFalse(supplied.isHighPriority());
        SpriteArtSet art = loadArt(new Sonic3kGameModule());
        var source = new LightningSparkObjectInstance(12, 14, 0, 0, art.animationSet(), art.artTiles());
        supplied.restoreRewindState(source.captureRewindState());
        assertEquals(pixels(art.artTiles()), pixels((Pattern[]) field(supplied, "sparkTiles")),
                "populated public restore needs neither services nor an identity context");
        var empty = new LightningSparkObjectInstance(12, 14, 0, 0, new SpriteAnimationSet(), new Pattern[0]);
        supplied.restoreRewindState(empty.captureRewindState());
        assertEquals(0, ((Pattern[]) field(supplied, "sparkTiles")).length);
        assertEquals(0, ((SpriteAnimationSet) field(supplied, "animSet")).getScriptCount());
        var spark = LightningSparkObjectInstance.forRewindRecreate(new ObjectSpawn(8, 9, 0, 0, 0, false, 0), null);
        spark.appendRenderCommands(new ArrayList<>());
        for (int i = 0; i < 21; i++) spark.update(0xFC + i, null);
        assertTrue(spark.isDestroyed());
        assertFalse(spark.isDestroyedRespawnable());
        assertEquals(8, spark.getX());
    }

    private static SpriteArtSet loadArt(GameModule module) throws Exception {
        var provider = assertInstanceOf(Sonic3kObjectArtProvider.class, module.getObjectArtProvider());
        var load = Sonic3kObjectArtProvider.class.getDeclaredMethod("loadShieldArt");
        load.setAccessible(true);
        load.invoke(provider); // actual ROM-backed producer, without loading an unrelated zone/route
        SpriteArtSet result = provider.getShieldArtSet(Sonic3kObjectArtKeys.LIGHTNING_SPARK);
        assertNotNull(result);
        return result;
    }

    private void retireAtlasEntries() {
        for (int i = 0; i < 5; i++) graphics.uncachePattern(PatternAtlasRange.TRANSIENT_EFFECTS.base() + i);
        assertNull(graphics.getPatternAtlasEntry(PatternAtlasRange.TRANSIENT_EFFECTS.base()));
    }

    private static Pattern[] copy(Pattern[] tiles) {
        Pattern[] result = new Pattern[tiles.length];
        for (int i = 0; i < tiles.length; i++) { result[i] = new Pattern(); result[i].copyFrom(tiles[i]); }
        return result;
    }

    private static List<List<Integer>> pixels(Pattern[] tiles) {
        if (tiles == null) return List.of();
        List<List<Integer>> result = new ArrayList<>();
        for (Pattern tile : tiles) {
            List<Integer> data = new ArrayList<>();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) data.add((int) tile.getPixel(x, y));
            result.add(List.copyOf(data));
        }
        return List.copyOf(result);
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private record Spark(ObjectRefId id, int slot, int x, int y, int life, int mapping, int cursor,
                         int delay, int xSub, int ySub, int xVel, int yVel, boolean priority, boolean respawn) {}
    private record Image(List<Integer> rgba) {
        @Override public String toString() { return "Image[hash=" + rgba.hashCode() + ", pixels=" + rgba.size() + "]"; }
    }
    private record Frame(List<Spark> sparks, Image image, int litPixels, List<Long> slots,
                         int clock, int vInt) {}

    private final class Harness {
        private final Services services;
        private final ObjectManager manager;
        private final RewindRegistry rewind = new RewindRegistry();
        private final Sonic player = new Sonic("sonic", (short) 160, (short) 100);

        Harness(GameModule module) {
            Camera camera = new Camera() {
                @Override public short getX() { return 0; }
                @Override public short getY() { return 0; }
                @Override public short getWidth() { return WIDTH; }
                @Override public short getHeight() { return HEIGHT; }
            };
            services = new Services(module);
            manager = new ObjectManager(List.of(), null, 0, null, null, graphics, camera, services);
            services.manager = manager;
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.reset(0);
            rewind.register(manager.rewindSnapshottable());
            player.setHighPriority(true);
        }

        void produce() {
            var shield = assertInstanceOf(LightningShieldObjectInstance.class,
                    services.module.getShieldFactory().apply(player, ShieldType.LIGHTNING));
            shield.setServices(services);
            shield.triggerSparks();
        }

        List<LightningSparkObjectInstance> live() {
            return manager.getActiveObjects().stream().filter(LightningSparkObjectInstance.class::isInstance)
                    .map(LightningSparkObjectInstance.class::cast).filter(s -> !s.isDestroyed())
                    .sorted(Comparator.comparingInt(LightningSparkObjectInstance::getSlotIndex)).toList();
        }

        void tick() { manager.update(0, null, List.of(), 0xF8 + manager.getFrameCounter(), false); }

        List<Frame> advance(int frames) {
            List<Frame> result = new ArrayList<>();
            for (int i = 0; i < frames; i++) { tick(); result.add(frame()); }
            return result;
        }

        Frame frame() {
            glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
            List<Spark> states = new ArrayList<>();
            for (var s : live()) {
                s.appendRenderCommands(new ArrayList<>());
                states.add(new Spark(manager.captureIdentityContext().requireIdentityTable().idFor(s),
                        s.getSlotIndex(), s.getX(), s.getY(), (int) field(s, "life"), (int) field(s, "currentMappingFrame"),
                        (int) field(s, "frameIndex"), (int) field(s, "delayCounter"), (int) field(s, "xSub"),
                        (int) field(s, "ySub"), (int) field(s, "xVel"), (int) field(s, "yVel"),
                        s.isHighPriority(), s.isDestroyedRespawnable()));
            }
            graphics.flushWithCamera((short) 0, (short) 0, (short) WIDTH, (short) HEIGHT);
            glFinish();
            var buffer = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
            glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            List<Integer> image = new ArrayList<>(WIDTH * HEIGHT);
            int lit = 0;
            for (int i = 0; i < WIDTH * HEIGHT; i++) {
                int rgb = (buffer.get(i * 4) & 255) << 16 | (buffer.get(i * 4 + 1) & 255) << 8
                        | (buffer.get(i * 4 + 2) & 255);
                image.add(rgb);
                if (rgb != 0) lit++;
            }
            assertEquals(GL_NO_ERROR, glGetError());
            return new Frame(states, new Image(List.copyOf(image)), lit,
                    java.util.Arrays.stream(manager.rewindSnapshottable().capture().usedSlotsBits()).boxed().toList(),
                    manager.getFrameCounter(), manager.getVblaCounter());
        }
    }

    private final class Services extends StubObjectServices {
        private GameModule module;
        private CrossGameFeatureProvider crossGame;
        private ObjectManager manager;
        Services(GameModule module) { this.module = module; }
        @Override public GameModule gameModule() { return module; }
        @Override public ObjectManager objectManager() { return manager; }
        @Override public CrossGameFeatureProvider crossGameFeatures() { return crossGame; }
        @Override public GraphicsManager graphicsManager() { return graphics; }
    }
}
