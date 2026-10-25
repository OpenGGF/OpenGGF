package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.MultiBucketRenderable;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.ObjectSpriteCapture;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Retirement and native art-word priority remain correct while the selected world is held. */
class TestTypingHedgehogPresentationOwnership {
    @TempDir static Path work;
    static ExampleModHarness harness;
    static Class<?> handType;
    static Class<?> puppetType;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"), work.resolve("mod"));
        handType = harness.loader().loadClass("typinghog.play.Stagehand");
        puppetType = harness.loader().loadClass("typinghog.play.Puppet");
    }

    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void heldWorldRetiresRemovedAndOldGenerationPuppetsWithoutAdvancingNativeObjects() throws Exception {
        try (World world = world()) {
            var manager = world.services().objectManager();
            Sentinel foreign = ObjectConstructionContext.construct(world.services(), Sentinel::new);
            manager.addAuxiliaryDynamicObject(foreign);
            int base = manager.getActiveObjects().size();
            int clock = manager.getFrameCounter(), vInt = manager.getVblaCounter();
            int nativeSlots = manager.getAllocatedSlotCount();
            for (int cycle = 0; cycle < 1000; cycle++) {
                Object first = call(world.hand(), "add", "monitor", 32, 64);
                call(world.hand(), "add", "monitor", 64, 64);
                call(world.hand(), "add", "monitor", 96, 64);
                present(world);
                assertEquals(base + 3, manager.getActiveObjects().size(), "spawn cycle " + cycle);
                call(world.hand(), "remove", first);
                present(world);
                assertEquals(base + 2, manager.getActiveObjects().size(), "removed visual cycle " + cycle);
                List<ObjectInstance> old = puppets(world);
                call(world.hand(), "invalidatePuppets");
                present(world);
                assertEquals(base + 2, manager.getActiveObjects().size(), "generation cycle " + cycle);
                assertTrue(old.stream().allMatch(ObjectInstance::isDestroyed), "retired through unload callbacks");
                assertTrue(old.stream().noneMatch(manager.getActiveObjects()::contains));
                call(world.hand(), "clear");
                present(world);
                assertEquals(base, manager.getActiveObjects().size(), "clear cycle " + cycle);
                assertEquals(0, tracked(world.hand()), "tracked references retire with registrations");
            }
            // Native/rewind removal of a current puppet must not strand its served-visual marker.
            call(world.hand(), "add", "monitor", 32, 64);
            present(world);
            ObjectInstance removed = puppets(world).getFirst();
            manager.removeDynamicObject(removed);
            present(world);
            assertEquals(1, puppets(world).size());
            assertNotSame(removed, puppets(world).getFirst());
            assertEquals(1, tracked(world.hand()));
            call(world.course(), "close");
            assertEquals(base, manager.getActiveObjects().size(), "leave/session close retires immediately");
            assertEquals(0, tracked(world.hand()));
            // A load that yields no active intent also retires the prior presentation, even
            // though afterTick will return before the next spawn/cleanup boundary.
            call(world.hand(), "add", "monitor", 32, 64);
            present(world);
            assertEquals(base + 1, manager.getActiveObjects().size());
            call(world.course(), "detectLevel");
            assertEquals(base, manager.getActiveObjects().size(), "inactive level entry retires immediately");
            assertEquals(0, tracked(world.hand()));
            assertTrue(manager.getActiveObjects().contains(foreign), "unknown auxiliary owner is untouched");
            assertEquals(0, foreign.updates);
            assertEquals(0, foreign.unloads);
            assertEquals(clock, manager.getFrameCounter());
            assertEquals(vInt, manager.getVblaCounter());
            assertEquals(nativeSlots, manager.getAllocatedSlotCount());
            manager.removeDynamicObject(foreign);
            assertTrue(harness.findings().isEmpty(), harness.findings().toString());
        }
    }

    private record Drawn(int x, boolean shaderHigh, Boolean pieceFilter, int occlusionMask) { }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void sharedSheetRolesAndMappingBitsRetainIndependentPriorityAndOcclusion() throws Exception {
        try (World world = world()) {
            Pattern pixels = new Pattern();
            pixels.setPixel(0, 0, (byte) 1);
            var low = new SpriteMappingFrame(List.of(new SpriteMappingPiece(0, 0, 1, 1, 0,
                    false, false, 0, false)));
            var high = new SpriteMappingFrame(List.of(new SpriteMappingPiece(0, 0, 1, 1, 0,
                    false, false, 0, true)));
            var sheet = new ObjectSpriteSheet(new Pattern[] {pixels}, List.of(low, high), 0, 1);
            Class<?> artType = harness.loader().loadClass("typinghog.play.SectionArt");
            var constructor = artType.getDeclaredConstructor(Map.class, List.class);
            constructor.setAccessible(true);
            Object art = constructor.newInstance(Map.of("shared", sheet), List.of());
            handType.getMethod("sectionArt", artType).invoke(world.hand(), art);
            Object visual = call(world.hand(), "add", "shared", 32, 64);
            set(visual, "highPriority", false);
            sprite(visual, 40, 0, false); // DDZ target-like low owner
            sprite(visual, 60, 0, true);  // missile/turret-like high owner, same key and frame
            Object mappingRaised = sprite(visual, 80, 1, false); // mapping raises a low owner's bit
            present(world);
            var puppet = (MultiBucketRenderable) puppets(world).getFirst();
            assertEquals(MultiBucketRenderable.LOW_PARTS | MultiBucketRenderable.HIGH_PARTS,
                    puppet.partTilePriorities(puppet.getPriorityBucket()));
            assertFalse(puppet.isHighPriority(), "compound parent does not override its parts");
            var graphics = world.services().graphicsManager();
            boolean oldHigh = graphics.getCurrentSpriteHighPriority();
            int oldMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
            List<Drawn> drawn = new ArrayList<>();
            ObjectSpriteCapture.observe(draw -> {
                // The observer also sees an empty filtered call; count only the pieces it admits.
                if (draw.pieces().stream().noneMatch(piece -> draw.priorityFilter() == null
                        || piece.priority() == draw.priorityFilter())) return;
                drawn.add(new Drawn(draw.x(), graphics.getCurrentSpriteHighPriority(),
                        draw.priorityFilter(), draw.occlusionMask()));
            }, () -> {
                puppet.appendRenderCommands(new ArrayList<>(), puppet.getPriorityBucket(), false);
                puppet.appendRenderCommands(new ArrayList<>(), puppet.getPriorityBucket(), true);
            });
            assertEquals(List.of(new Drawn(40, false, false, 0xF), new Drawn(60, true, null, 0),
                    new Drawn(80, true, true, 0)), drawn);
            // GraphicsManager derives shaderHigh from a zero occlusion mask. The mapping
            // raises the effective priority while the visual's native owner bit remains low.
            assertEquals(false, mappingRaised.getClass().getField("highPriority").get(mappingRaised));
            assertEquals(oldHigh, graphics.getCurrentSpriteHighPriority());
            assertEquals(oldMask, graphics.getCurrentSpriteTileOcclusionPaletteMask());
            assertTrue(sheet.getFrame(1).pieces().getFirst().priority(), "raw mapping bit is preserved");
        }
    }

    @Test void nativeBossOwnerPrioritiesAndBothLavaReefHeadPalettesArePinned() throws Exception {
        Class<?> rigType = harness.loader().loadClass("typinghog.play.BossRig");
        @SuppressWarnings("unchecked") Map<String, Object> rigs = (Map<String, Object>) rigType
                .getMethod("parse", String.class).invoke(null, Files.readString(Path.of(
                        "examples/typing-of-the-hedgehog/src/main/resources/bosses/s3k.txt")));
        for (String name : List.of("body", "charge", "charge2", "charge3", "turbine", "housing")) {
            assertEquals(true, call(part(rigs.get("1"), name), "highPriority"), "HCZ " + name);
        }
        for (String name : List.of("face", "ship", "flame")) {
            assertEquals(false, call(part(rigs.get("1"), name), "highPriority"), "HCZ " + name);
        }
        assertEquals(false, call(part(rigs.get("12"), "target"), "highPriority"));
        for (String name : List.of("turret0", "turret1", "turret2", "exhaust0", "exhaust1", "exhaust2")) {
            assertEquals(true, call(part(rigs.get("12"), name), "highPriority"), "DDZ " + name);
        }
        for (String name : List.of("hull0", "hull1", "hull2", "hull3")) {
            assertEquals(false, call(part(rigs.get("12"), name), "highPriority"), "DDZ hull base only");
        }
        assertEquals(0, call(part(rigs.get("22"), "face"), "palette"));
        assertEquals(0, call(part(rigs.get("9@1"), "face"), "palette"));
        Class<?> moodType = harness.loader().loadClass("typinghog.play.BossRig$Mood");
        Object flee = java.util.Arrays.stream(moodType.getEnumConstants()).filter(it -> it.toString().equals("FLEE"))
                .findFirst().orElseThrow();
        Object mgzPod = part(rigs.get("2"), "pod");
        assertEquals(true, call(mgzPod, "highPriority"));
        assertEquals(false, call(mgzPod, "highPriority", flee), "native MGZ escape owner clears bit 15");
    }

    private static Object part(Object rig, String name) throws Exception {
        for (Object part : (List<?>) call(rig, "parts")) if (name.equals(call(part, "name"))) return part;
        throw new AssertionError("Missing boss part " + name);
    }

    private static Object sprite(Object visual, int x, int frame, boolean high) throws Exception {
        Object sprite = call(visual, "sprite", "shared");
        set(sprite, "x", x);
        set(sprite, "y", 64);
        set(sprite, "frame", frame);
        set(sprite, "highPriority", high);
        return sprite;
    }

    private static int tracked(Object hand) throws Exception {
        var field = hand.getClass().getDeclaredField("ownedPuppets");
        field.setAccessible(true);
        return ((List<?>) field.get(hand)).size();
    }

    private static List<ObjectInstance> puppets(World world) {
        return world.services().objectManager().getActiveObjects().stream().filter(puppetType::isInstance).toList();
    }

    private static void present(World world) throws Exception { call(world.course(), "spawnPuppets"); }

    private record World(SharedLevel bootstrap, ObjectServices services, Object hand, Object course)
            implements AutoCloseable {
        @Override public void close() throws Exception {
            call(course, "close");
            bootstrap.dispose();
            GameServices.configuration().clearSessionOverrides();
        }
    }

    private static World world() throws Exception {
        SharedLevel bootstrap = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
        try {
            var module = harness.apply(GameServices.module());
            SessionManager.clear();
            GameModuleRegistry.setCurrent(module);
            TestEnvironment.activeGameplayMode();
            HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            return new World(bootstrap, GameServices.level().getObjectManager().getObjectServices(),
                    module.getGameService(handType), module.getGameService(harness.loader().loadClass("typinghog.play.Course")));
        } catch (Exception | Error failure) {
            bootstrap.dispose();
            throw failure;
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        object.getClass().getField(name).set(object, value);
    }

    private static Object call(Object object, String name, Object... args) throws Exception {
        Method method = java.util.Arrays.stream(object.getClass().getDeclaredMethods())
                .filter(it -> it.getName().equals(name) && it.getParameterCount() == args.length).findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(object, args);
    }

    private static final class Sentinel extends AbstractObjectInstance {
        int updates, unloads;
        Sentinel() { super(new ObjectSpawn(16, 16, 0, 0, 0, false, 16), "foreign overlay"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { updates++; }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
        @Override public void onUnload() { unloads++; }
    }
}
