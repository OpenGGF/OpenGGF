package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestS1FzNativeComponents {
    @BeforeEach void setup() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void cleanup() { GraphicsManager.getInstance().resetState(); }

    @Test
    void firstPassUsesSixNativeRowsThenLauncherThenFourCylinders() throws Exception {
        Harness h = new Harness(33);
        h.manager.reserveDynamicSlot(32);
        h.manager.reserveDynamicSlot(34); // The native route has a stomper in this hole.
        assertEquals(1, h.objects().size(), "routine 0 has not allocated components in construction");
        h.step();
        assertEquals(List.of(33, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44),
                h.objects().stream().map(AbstractObjectInstance::getSlotIndex).toList());
        RomByteReader rom = h.rom();
        List<AbstractObjectInstance> visuals = h.visuals();
        assertEquals(5, visuals.size());
        assertEquals(0x100, h.boss.getX(), "the first ROM row is Eggman, not the panel row");
        assertEquals(0x100, h.boss.getY());
        for (int row = 1; row < 6; row++) {
            AbstractObjectInstance part = visuals.get(row - 1);
            int data = 0x1A524 + row * 10;
            int flags = 0x1A560 + row * 5;
            assertEquals(rom.readU16BE(data), part.getX());
            assertEquals(rom.readU16BE(data + 2), part.getY());
            assertEquals(rom.readU8(flags), part.getSpawn().subtype(), "independent native routine");
            assertEquals(rom.readU8(flags + 2), part.getPriorityBucket());
            assertEquals(rom.readU8(flags + 3), part.getOnScreenHalfWidth(), "FixBugs=0 zero widths are live");
        }
        assertInstanceOf(FZPlasmaLauncher.class, h.objects().get(6));
        assertTrue(h.objects().subList(7, 11).stream().allMatch(FZCylinder.class::isInstance));
        assertTrue(h.boss.getChildComponents().isEmpty(), "every child executes at its own SST position");
    }

    @Test
    void launcherFindFreeMayUseALowerHoleButDoesNotExecuteFromTheBossSlot() {
        Harness h = new Harness(60);
        h.step();
        FZPlasmaLauncher launcher = h.launcher();
        assertEquals(32, launcher.getSlotIndex(), "launcher uses FindFreeObj, unlike the visual rows");
        assertEquals(0, launcher.getX(), "a slot behind the cursor has not run its native init yet");
        assertEquals(List.of(66, 67, 68, 69), h.cylinders().stream().map(AbstractObjectInstance::getSlotIndex).toList(),
                "cylinders search after the controller, not after the lower-slot launcher");
        h.step();
        assertEquals(0x2588, launcher.getX());
        assertEquals(0x053C, launcher.getY());
    }

    @Test
    void fullRamRetainsTheSuccessfulPrefixWithoutInventingOrRetryingChildren() {
        for (int tailFree = 0; tailFree <= 9; tailFree++) {
            int parentSlot = 127 - tailFree;
            Harness h = new Harness(parentSlot);
            for (int slot = 32; slot < parentSlot; slot++) h.manager.reserveDynamicSlot(slot);
            h.step();
            assertEquals(1 + tailFree, h.objects().size(), "native partial prefix, free tail=" + tailFree);
            assertEquals(Math.min(5, tailFree), h.visuals().size());
            assertEquals(Math.max(0, tailFree - 6), h.cylinders().size());
            assertEquals(tailFree >= 6 ? 1 : 0,
                    h.objects().stream().filter(FZPlasmaLauncher.class::isInstance).count());
            if (tailFree > 0) h.manager.removeDynamicObject(h.objects().get(1));
            int count = h.objects().size();
            h.step();
            assertEquals(count, h.objects().size(), "routine 0 does not retry the missing tail");
        }
        Harness lowerHole = new Harness(127);
        for (int slot = 33; slot < 127; slot++) lowerHole.manager.reserveDynamicSlot(slot);
        lowerHole.step();
        assertEquals(2, lowerHole.objects().size(), "visual allocation failure still reaches FindFreeObj launcher");
        assertEquals(32, lowerHole.launcher().getSlotIndex());
        assertTrue(lowerHole.cylinders().isEmpty(), "a lower hole cannot satisfy FindNextFreeObj cylinders");
    }

    @Test
    void eggmanRunsRomScriptsOnlyAtNativeAnimateCallSites() throws Exception {
        Harness h = new Harness(33);
        for (int i = 0; i < 12; i++) h.step();
        assertEquals(0, readInt(h.boss, "seggAnimScriptFrame"), "WAIT never calls AnimateSprite");
        h.boss.getState().routineSecondary = 2;
        write(h.boss, "cylinderState", 0);
        write(h.boss, "activeCylinderCount", 1);
        int script = h.rom().readPointer16(0x1A1B2, 1);
        assertEquals(6, h.rom().readU8(script));
        for (int tick = 0; tick < 21; tick++) {
            h.step();
            assertEquals((tick / 7 & 1) == 0 ? 1 : 2, readInt(h.boss, "seggFrame"));
        }
        int timer = readInt(h.boss, "seggAnimTimeFrame");
        int frame = readInt(h.boss, "seggFrame");
        h.boss.getState().routineSecondary = 4;
        write(h.boss, "cylinderState", 0);
        for (int i = 0; i < 8; i++) h.step();
        assertEquals(timer, readInt(h.boss, "seggAnimTimeFrame"), "PLASMA freezes this owner's animation clock");
        assertEquals(frame, readInt(h.boss, "seggFrame"));
        h.boss.getState().routineSecondary = 6;
        int animation = readInt(h.boss, "seggAnim");
        h.step();
        assertEquals(6, readInt(h.boss, "seggFrame"), "fall writes the mapping frame directly");
        assertEquals(animation, readInt(h.boss, "seggAnim"), "fall does not write an animation ID");
    }

    @Test
    void fixedVisualsFollowOnlyTheirNativeBranchesAndLegsRetireAfterSixtyPasses() throws Exception {
        Harness h = new Harness(33);
        h.step();
        AbstractObjectInstance cockpit = h.visual(8), legs = h.visual(6), empty = h.visual(10), flame = h.visual(12);
        assertEquals(10, readInt(cockpit, "mappingFrame"));
        assertEquals(0, readInt(flame, "mappingFrame"), "pre-ship flame skips AnimateSprite, preserving cleared frame0");
        h.boss.getState().x = 0x2600;
        h.boss.getState().y = 0x0590;
        h.boss.getState().xFixed = 0x26000000;
        h.boss.getState().yFixed = 0x05900000;
        h.boss.getState().routineSecondary = 12;
        h.boss.getState().hitCount = 1;
        write(h.boss, "escapingInShip", true);
        empty.update(100, null);
        assertTrue(empty.isDestroyed(), "empty ship retires when controller mappings become Map_Eggman in phase12");
        flame.update(100, null);
        assertEquals(10, readInt(flame, "mappingFrame"), "vertical takeoff has zero X velocity, so blank flame");
        cockpit.update(100, null);
        assertEquals(6, readInt(cockpit, "mappingFrame"));
        assertEquals(0x2600, cockpit.getX());
        for (int tick = 1; tick <= 60; tick++) {
            legs.update(100, null); // V-int equality does not stop native per-pass animation.
            if (tick < 60) assertFalse(legs.isDestroyed());
        }
        assertTrue(legs.isDestroyed(), "three frame transitions at 20 passes each delete the legs");
    }

    @Test
    void panelAndDamagedCockpitUseTheirOwnPreviousPostCameraVisibility() throws Exception {
        Harness h = new Harness(33);
        h.step();
        AbstractObjectInstance panel = h.visual(4), cockpit = h.visual(8);
        var player = new TestablePlayableSprite("sonic", (short) 0x25A0, (short) 0x0590);
        panel.update(100, player);
        assertFalse(panel.isDestroyed(), "offscreen panel persists while Sonic is left of its fixed X");
        player.setCentreX((short) 0x25B0);
        panel.update(100, player);
        assertTrue(panel.isDestroyed(), "offscreen panel retires once Sonic passes it");
        assertFalse(panel.isDestroyedRespawnable(), "native self-deletion permanently retires the panel");
        write(h.boss, "escapingInShip", true);
        h.boss.getState().hitCount = 0;
        cockpit.update(101, null);
        assertTrue(cockpit.isDestroyed(), "FixBugs=0 deletes an offscreen damaged cockpit before its follow-position write");
        assertFalse(cockpit.isDestroyedRespawnable(), "damaged cockpit deletion must not mark an offscreen respawn");
        assertFalse(h.boss.isDestroyed());
    }

    @Test
    void launcherBecomesFiveFrameExplosionInItsOwnSlotEvenWithFullRam() throws Exception {
        Harness h = new Harness(33);
        h.manager.reserveDynamicSlot(32);
        h.step();
        FZPlasmaLauncher launcher = h.launcher();
        int slot = launcher.getSlotIndex();
        h.manager.reserveAllButNFreeSlots(0);
        int allocated = h.manager.getAllocatedSlotCount();
        h.boss.getState().routineSecondary = 6;
        h.step();
        assertEquals(0x3F, launcher.getSpawn().objectId(), "native ID transformation never requests a fresh slot");
        assertEquals(slot, launcher.getSlotIndex());
        assertEquals(allocated, h.manager.getAllocatedSlotCount());
        h.boss.getState().routineSecondary = 0;
        h.step(); // Expl_Main initializes; no animation or display fallthrough.
        for (int pass = 1; pass < 40; pass++) {
            h.step();
            assertFalse(launcher.isDestroyed(), "five frames of eight passes retain the transformed SST");
        }
        h.step();
        assertTrue(launcher.isDestroyed());
        assertEquals(allocated - 1, h.manager.getAllocatedSlotCount());
    }

    @Test
    void cylinderAndLauncherWaitUntilSonicPassesByOneScreenBeforeOffscreenDeletion() {
        Harness h = new Harness(33);
        h.step();
        h.step(); // Lower-slot launcher now executes its Main and post-camera pass.
        FZCylinder cylinder = h.cylinders().get(0);
        var player = new TestablePlayableSprite("sonic", (short) (cylinder.getX() + 319), (short) 0);
        cylinder.update(100, player);
        assertFalse(cylinder.isDestroyed(), "out of view alone does not delete a native cylinder");
        player.setCentreX((short) (cylinder.getX() + 320));
        cylinder.update(101, player);
        assertTrue(cylinder.isDestroyed());

        FZPlasmaLauncher launcher = h.launcher();
        player.setCentreX((short) (launcher.getX() + 319));
        launcher.update(100, player);
        assertFalse(launcher.isDestroyed());
        player.setCentreX((short) (launcher.getX() + 320));
        launcher.update(101, player);
        assertTrue(launcher.isDestroyed());
    }

    @Test
    void cylinderMappingAdvancesAtTheInclusiveNativeEightAndThirtyNinePixelEdges() throws Exception {
        Harness h = new Harness(33);
        h.step();
        FZCylinder floor = h.cylinders().get(0);
        write(floor, "active", true);
        write(floor, "direction", -1);
        write(floor, "activationDeferred", false);
        write(floor, "extensionFixed", -0x78000); // Native next half-pixel step reaches -8.
        floor.update(100, null);
        assertEquals(1, readInt(floor, "currentFrame"));

        FZCylinder ceiling = h.cylinders().get(2);
        h.boss.getState().hitCount = 0;
        write(ceiling, "active", true);
        write(ceiling, "direction", 0);
        write(ceiling, "activationDeferred", false);
        write(ceiling, "extensionFixed", 40 << 16); // Defeated retraction applies +1, then -2.
        ceiling.update(100, null);
        assertEquals(1, readInt(ceiling, "currentFrame"));
        assertEquals(0x51416514, h.rom().readU32BE(0x1ACFA), "SUBQ.W #8,D1; BCS skips only borrow");
        assertEquals(0x04410027, h.rom().readU32BE(0x1AD06), "SUBI.W #$27,D1");
        assertEquals(0x6506, h.rom().readU16BE(0x1AD0A), "ceiling uses BCS, not equality-inclusive skip");
    }

    @Test
    void fullReconstructionRestoresAllIndependentRolesAndTheirForwardClocks() throws Exception {
        Harness h = new Harness(33);
        h.step();
        h.boss.getState().routineSecondary = 2;
        write(h.boss, "cylinderState", 0);
        write(h.boss, "activeCylinderCount", 1);
        for (int i = 0; i < 10; i++) h.step();
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        RewindRegistry registry = new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        var snapshot = registry.capture();
        List<String> expected = h.forward(20);
        registry.restore(snapshot);
        h.boss = h.objects().stream().filter(Sonic1FZBossInstance.class::isInstance)
                .map(Sonic1FZBossInstance.class::cast).findFirst().orElseThrow();
        for (var visual : h.visuals()) assertSame(h.boss, read(visual, "parent"));
        assertEquals(5, h.visuals().size());
        assertEquals(expected, h.forward(20));
        assertTrue(h.boss.getChildComponents().isEmpty());
        assertEquals(11, h.objects().size(), "restoring an initialized controller never recreates a duplicate graph");
    }

    @Test
    void cockpitBurstsAllocateBeforeRngAndUseTheNativeDownwardFiveFrameEffect() {
        Harness h = new Harness(33);
        h.step();
        h.rng.setSeed(0x100); // Native RandomNumber produces low word $2900.
        h.boss.triggerBossDefeatedExplosion(8, 0x2600, 0x0590);
        S1BossExplosion explosion = h.objects().stream().filter(S1BossExplosion.class::isInstance)
                .map(S1BossExplosion.class::cast).findFirst().orElseThrow();
        assertEquals(0x25E0, explosion.getX());
        assertEquals(0x0595, explosion.getY());
        explosion.update(8, null);
        for (int pass = 1; pass < 40; pass++) {
            explosion.update(pass + 8, null);
            assertFalse(explosion.isDestroyed());
        }
        explosion.update(48, null);
        assertTrue(explosion.isDestroyed(), "S1 explosion deletes at frame5 rather than generic frame7");

        Harness full = new Harness(127);
        full.manager.reserveAllButNFreeSlots(0);
        full.rng.setSeed(0x100);
        full.boss.triggerBossDefeatedExplosion(8, 0x2600, 0x0590);
        assertEquals(0x100, full.rng.getSeed(), "failed native allocation consumes no RNG");
        assertTrue(full.objects().stream().noneMatch(S1BossExplosion.class::isInstance));
    }

    @Test
    void damagedCockpitStartsItsOwnRomScriptAndSurvivesRestoreWithTheSamePhase() throws Exception {
        Harness h = new Harness(33);
        h.camera.setX((short) 0x25D0);
        h.camera.setY((short) 0x0510);
        h.step();
        AbstractObjectInstance cockpit = h.visual(8);
        assertEquals(0x00020300, h.rom().readU32BE(0x1AB8A), "independent Ani_FZEgg table and delay");
        write(h.boss, "escapingInShip", true);
        h.boss.getState().x = 0x26E0;
        h.boss.getState().y = 0x0596;
        h.boss.getState().hitCount = 1;
        cockpit.update(100, null);
        cockpit.refreshPostCameraRenderState();
        h.boss.getState().hitCount = 0;
        for (int tick = 0; tick < 12; tick++) {
            cockpit.update(101, null); // No BossDefeated allocation on this V-int phase.
            assertEquals(tick / 4 & 1, readInt(cockpit, "mappingFrame"));
            assertEquals(2, cockpit.getPriorityBucket(), "damaged cockpit owns front-of-old-ship priority");
            cockpit.refreshPostCameraRenderState();
        }
        assertFalse(cockpit.isDestroyed());
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        RewindRegistry registry = new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        var snapshot = registry.capture();
        List<Integer> expected = new ArrayList<>();
        for (int tick = 0; tick < 10; tick++) {
            cockpit.update(101, null);
            expected.add(readInt(cockpit, "mappingFrame"));
        }
        registry.restore(snapshot);
        h.boss = h.objects().stream().filter(Sonic1FZBossInstance.class::isInstance)
                .map(Sonic1FZBossInstance.class::cast).findFirst().orElseThrow();
        cockpit = h.visual(8);
        for (int wanted : expected) {
            cockpit.update(101, null);
            assertEquals(wanted, readInt(cockpit, "mappingFrame"));
        }
    }

    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object read(Object target, String name) throws Exception { return field(target, name).get(target); }
    private static int readInt(Object target, String name) throws Exception { return field(target, name).getInt(target); }
    private static void write(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }

    private static final class Harness {
        final Camera camera = new Camera();
        final ObjectManager manager;
        final GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
        Sonic1FZBossInstance boss;
        int clock;

        Harness(int slot) {
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                final GameStateManager state = new GameStateManager();
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GameRng rng() { return rng; }
                @Override public com.openggf.game.GameModule gameModule() { return new Sonic1GameModule(); }
                @Override public GameStateManager gameState() { return state; }
                @Override public RomByteReader romReader() { return Harness.this.rom(); }
            };
            manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(), 0,
                    null, null, GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(0);
            boss = manager.createDynamicObjectAtSlot(() -> new Sonic1FZBossInstance(
                    new ObjectSpawn(0x2450, 0x0510, 0x85, 0, 0, false, 0)), slot);
        }
        RomByteReader rom() {
            try { return RomByteReader.fromRom(TestEnvironment.currentRom()); }
            catch (IOException e) { throw new AssertionError(e); }
        }
        void step() {
            manager.update(camera.getX(), null, List.of(), ++clock);
            manager.refreshPostCameraRenderState();
        }
        List<AbstractObjectInstance> objects() {
            return manager.getActiveObjects().stream().filter(AbstractObjectInstance.class::isInstance)
                    .map(AbstractObjectInstance.class::cast)
                    .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
        }
        List<AbstractObjectInstance> visuals() {
            return objects().stream().filter(o -> o != boss && o.getSpawn().objectId() == 0x85).toList();
        }
        AbstractObjectInstance visual(int routine) {
            return visuals().stream().filter(o -> o.getSpawn().subtype() == routine).findFirst().orElseThrow();
        }
        List<FZCylinder> cylinders() { return objects().stream().filter(FZCylinder.class::isInstance).map(FZCylinder.class::cast).toList(); }
        FZPlasmaLauncher launcher() { return objects().stream().filter(FZPlasmaLauncher.class::isInstance).map(FZPlasmaLauncher.class::cast).findFirst().orElseThrow(); }
        List<String> forward(int passes) throws Exception {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < passes; i++) {
                step();
                StringBuilder row = new StringBuilder();
                row.append(readInt(boss, "seggFrame")).append(':').append(readInt(boss, "seggAnimTimeFrame"));
                for (var visual : visuals()) row.append('|').append(visual.getSlotIndex()).append(':')
                        .append(visual.getX()).append(':').append(visual.getY()).append(':')
                        .append(readInt(visual, "mappingFrame"));
                result.add(row.toString());
            }
            return result;
        }
    }
}
