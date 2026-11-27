package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.DamageCause;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdTimeStars;
import com.openggf.game.soniccd.objects.projector.CdProjector;
import com.openggf.game.soniccd.objects.ttz.*;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.release.CdRetailReleases;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Tidal Tempest objects on the loaded stages (their art, tables and water come from
 * the disc). Each case cites the routine it checks in {@code src/objects/r4/<object>.asm} of the scddisasm pin;
 * the native comparisons are {@link TestTtzObjectsAppearances} and the picture checks.
 */
class TestTtzObjects {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;
    private CdDiscDataSource disc;

    private void boot(CdStagePayload payload) throws Exception {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        EngineContext old = EngineServices.current();
        EngineContext injected = new EngineContext(config, old.graphics(), old.audio(), old.roms(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures());
        EngineServices.configure(injected);
        GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                injected, new SonicCdGameModule(), ModuleResolutionService.LaunchPolicy.DETERMINISTIC, disc);
        GameplaySessionFactory.attachManagers(gameplay, injected);
        injected.graphics().initHeadless();
        GameModuleRegistry.setCurrent(SessionManager.requireCurrentGameModule());
        sonic = GameplayTeamBootstrap.registerActiveTeam(
                SessionManager.requireCurrentGameModule(), GameServices.sprites(), config).mainSprite();
        GameServices.camera().setFocusedSprite(sonic);
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        GroundSensor.setLevelManager(GameServices.level());
        runner = new HeadlessTestRunner(sonic);
        runner.stepIdleFrames(1);
        sonic.setInvulnerableFrames(0x7000);   // keep the player out of the way of the badniks' touch
    }

    @AfterEach
    void cleanup() throws Exception {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
        if (disc != null) disc.close();
    }

    private <T extends ObjectInstance> T add(T object) {
        GameServices.level().getObjectManager().addDynamicObject(object);
        return object;
    }

    private List<ObjectInstance> live(int objectId) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getSpawn().objectId() == objectId).toList();
    }

    private static ObjectSpawn spawn(int x, int y, int id, int subtype) {
        return new ObjectSpawn(x, y, id, subtype, 0, false, 0);
    }

    private CdObjectArtProvider art() {
        return (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
    }

    private int floorDistance(AbstractObjectInstance o, int yRadius) {
        return ObjectTerrainUtils.checkFloorDist(o.getX(), o.getY(), yRadius).distance();
    }

    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = {"R41B", "R42B"})
    void pastProjectorsSpawnBothAnimalForms(CdStagePayload payload) throws Exception {
        CdProjector projector = bootWithProjector(payload);
        assertEquals(List.of(
                new AnimalPosition(projector.getX() - 0x4C, projector.getY() - 0x1C, 5),
                new AnimalPosition(projector.getX() - 0x54, projector.getY() - 0x11, 5)),
                positions());
    }

    @Test
    void projectorAnimalsMatchNativePositionsAcrossAnOrbit() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-long/R41B/movie.bk2");
        Path trace = CdTestMedia.nativeFile("rehost-long/R41B/trace/physics.csv").getParent();
        // Read-only BizHawk 2.11 playback of this movie. Slots 82 and 83, level frames 1750 through 1879.
        record NativeSample(int row, AnimalPosition first, AnimalPosition second) { }
        List<NativeSample> samples = List.of(
                new NativeSample(1844, new AnimalPosition(0x0884, 0x0558, 5), new AnimalPosition(0x087c, 0x0563, 5)),
                new NativeSample(1846, new AnimalPosition(0x08a4, 0x0558, 5), new AnimalPosition(0x0888, 0x0572, 5)),
                new NativeSample(1848, new AnimalPosition(0x08a4, 0x0559, 5), new AnimalPosition(0x088a, 0x0571, 5)),
                new NativeSample(1850, new AnimalPosition(0x08a4, 0x055a, 5), new AnimalPosition(0x088b, 0x0571, 5)),
                new NativeSample(1888, new AnimalPosition(0x0892, 0x0566, 5), new AnimalPosition(0x089c, 0x0564, 3)),
                new NativeSample(1889, new AnimalPosition(0x0890, 0x0567, 5), new AnimalPosition(0x089c, 0x0563, 3)),
                new NativeSample(1897, new AnimalPosition(0x0884, 0x0568, 5), new AnimalPosition(0x089a, 0x055d, 3)),
                new NativeSample(1928, new AnimalPosition(0x0864, 0x0559, 3), new AnimalPosition(0x0871, 0x0554, 3)),
                new NativeSample(1929, new AnimalPosition(0x0864, 0x0558, 3), new AnimalPosition(0x0870, 0x0554, 3)),
                new NativeSample(1954, new AnimalPosition(0x0876, 0x054a, 3), new AnimalPosition(0x085c, 0x0562, 5)),
                new NativeSample(1955, new AnimalPosition(0x0878, 0x0549, 3), new AnimalPosition(0x085c, 0x0563, 5)),
                new NativeSample(1963, new AnimalPosition(0x0884, 0x0548, 3), new AnimalPosition(0x085f, 0x0569, 5)),
                new NativeSample(1994, new AnimalPosition(0x08a4, 0x0557, 5), new AnimalPosition(0x0887, 0x0572, 5)),
                new NativeSample(1995, new AnimalPosition(0x08a4, 0x0558, 5), new AnimalPosition(0x0888, 0x0572, 5)));
        var pending = new ArrayDeque<>(samples);
        try (CdDiscDataSource source = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R41B, trace, movie);
            CdFixtureReplay.replay(source, tempDir, fixture, samples.getLast().row(), false, -1, -1, row -> {
                var sample = pending.peek();
                if (sample == null || row != sample.row()) return;
                assertEquals(List.of(sample.first(), sample.second()), positions(), "native R41B row " + row);
                pending.remove();
            });
        }
        assertTrue(pending.isEmpty(), "all native samples were reached");
    }

    @Test
    void projectorAnimalsRetireWhenTheProjectorShutsDown() throws Exception {
        var projector = bootWithProjector(CdStagePayload.R41B);
        hitProjector(projector);
        assertTrue(projector.childrenInvalid());
        assertEquals(List.of(), positions());
    }

    @Test
    void rewindAcrossProjectorDestructionRestoresTheAnimalsAndTheirMotion() throws Exception {
        var projector = bootWithProjector(CdStagePayload.R41B);
        runner.stepIdleFrames(31);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        var captured = hologramState();
        assertEquals(2, captured.size());
        var forward = new ArrayList<List<AnimalFrame>>();
        for (int frame = 0; frame < 40; frame++) {
            runner.stepIdleFrames(1);
            forward.add(hologramState());
        }
        hitProjector(projector);
        runner.stepIdleFrames(160);
        assertTrue(projector.isDestroyed());
        assertEquals(List.of(), hologramState());
        registry.restore(snapshot);
        assertEquals(captured, hologramState(), "the removed pair returns in its saved slots");
        for (int frame = 0; frame < forward.size(); frame++) {
            runner.stepIdleFrames(1);
            assertEquals(forward.get(frame), hologramState(), "frame " + frame + " after restoration");
        }
    }

    private CdProjector bootWithProjector(CdStagePayload payload) throws Exception {
        boot(payload);
        var projector = add(new CdProjector(spawn(sonic.getCentreX() + 120, sonic.getCentreY(), 0x52, 0)));
        runner.stepIdleFrames(1);
        return projector;
    }

    private void hitProjector(CdProjector projector) {
        sonic.setAnimationId(CdSonicAnimation.ROLL);
        projector.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(2);
    }

    private record AnimalPosition(int x, int y, int layer) { }

    private List<TtzAnimal> holograms() {
        return live(0x2F).stream()
                .filter(o -> (o.getSpawn().subtype() & 0x80) != 0)
                .map(TtzAnimal.class::cast)
                .sorted(Comparator.comparingInt(o -> o.getSpawn().subtype()))
                .toList();
    }

    private List<AnimalPosition> positions() {
        return holograms().stream()
                .map(o -> new AnimalPosition(o.getX(), o.getY(), o.getPriorityBucket())).toList();
    }

    private record AnimalFrame(int slot, int x, int y, String pose) { }

    private List<AnimalFrame> hologramState() {
        return holograms().stream()
                .map(o -> new AnimalFrame(o.getSlotIndex(), o.getX(), o.getY(), o.traceDebugDetails())).toList();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void breakPoleGripsClimbsAndBreaksOnAFreshPress(int approach) throws Exception {
        boot(CdStagePayload.R42A);
        var pole = addBreakPole(0);
        sonic.setXSpeed((short) (approach * 0x100));
        sonic.setYSpeed((short) 0x234);
        sonic.setGSpeed((short) 0x345);
        sonic.setSubpixelRaw(0x1357, 0x2468);
        sonic.setJumpInputPressed(true, false);
        pole.update(0, sonic);
        assertTrue(sonic.isObjectControlled());
        assertTrue(sonic.isObjectControlSuppressesMovement());
        assertFalse(sonic.isTouchResponseSuppressedByObjectControl());
        assertEquals(pole.getX() + approach * 24, sonic.getCentreX());
        assertEquals(approach < 0 ? Direction.LEFT : Direction.RIGHT, sonic.getDirection());
        assertEquals(CdSonicAnimation.HANG.id(), sonic.getAnimationId());
        assertEquals(0x1357, sonic.getXSubpixelRaw());

        sonic.setDirectionalInputPressed(true, true, false, false);
        pole.update(0, sonic);
        assertEquals(pole.getY() - 1, sonic.getCentreY(), "Up wins when both directions are held");
        for (int i = 0; i < 30; i++) pole.update(0, sonic);
        assertTrue(sonic.isObjectControlled(), "a held button supplies no new edge");
        assertEquals(pole.getY() - 24, sonic.getCentreY());
        sonic.setDirectionalInputPressed(false, true, false, false);
        for (int i = 0; i < 50; i++) pole.update(0, sonic);
        assertEquals(pole.getY() + 24, sonic.getCentreY());
        assertEquals(0x2468, sonic.getYSubpixelRaw());

        sonic.setDirectionalInputPressed(true, false, false, false);
        sonic.setJumpInputPressed(true, true);
        pole.update(0, sonic);
        assertFalse(sonic.isObjectControlled());
        assertEquals(pole.getY() + 24, sonic.getCentreY(), "the press takes precedence over climbing");
        assertEquals(approach * 0x100, sonic.getXSpeed());
        assertEquals(0x234, sonic.getYSpeed());
        assertEquals(0x345, sonic.getGSpeed());
        sonic.setCentreX((short) pole.getX());
        sonic.setCentreY((short) pole.getY());
        pole.update(0, sonic);
        assertFalse(sonic.isObjectControlled(), "a broken pole cannot grip again");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 0x80})
    void breakPoleTimesOutOnItsNativeSubtypeTimer(int subtype) throws Exception {
        boot(CdStagePayload.R42A);
        var pole = addBreakPole(subtype);
        pole.update(0, sonic);
        int ticks = subtype == 0 ? 180 : 60;
        for (int i = 1; i < ticks - 1; i++) pole.update(0, sonic);
        sonic.setInvulnerableFrames(0);
        assertTrue(sonic.applyHurtOrDeath(sonic.getCentreX() + 32, DamageCause.SPIKE, true));
        assertTrue(sonic.isHurt());
        pole.update(0, sonic);
        assertTrue(sonic.isObjectControlled(), "hurt leaves the pole's control intact before timeout");
        pole.update(0, sonic);
        assertFalse(sonic.isObjectControlled());
    }

    @Test
    void breakPoleRewindRecreatesGripAndUnloadReleasesIt() throws Exception {
        boot(CdStagePayload.R42A);
        var pole = addBreakPole(1);
        pole.update(0, sonic);
        for (int i = 0; i < 12; i++) pole.update(0, sonic);
        var manager = GameServices.level().getObjectManager();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        int slot = ((AbstractObjectInstance) pole).getSlotIndex();
        var captured = registry.capture();
        manager.removeDynamicObject(pole);
        assertFalse(sonic.isObjectControlled(), "retiring the owner releases its player");

        registry.restore(captured);
        var restored = live(0x05).stream().map(AbstractObjectInstance.class::cast)
                .filter(object -> object.getSlotIndex() == slot).findFirst().orElseThrow();
        assertNotSame(pole, restored);
        assertTrue(sonic.isObjectControlled());
        for (int i = 0; i < 47; i++) restored.update(0, sonic);
        assertTrue(sonic.isObjectControlled(), "the recreated pole retains its timer and player binding");
        restored.update(0, sonic);
        assertFalse(sonic.isObjectControlled());
    }

    @Test
    void breakPoleClimbsAndTimesOutDuringDrowning() throws Exception {
        boot(CdStagePayload.R42A);
        var pole = addBreakPole(1);
        pole.update(0, sonic);
        assertTrue(sonic.applyDrownDeath());
        sonic.setDirectionalInputPressed(true, false, false, false);
        pole.update(0, sonic);
        assertEquals(pole.getY() - 1, sonic.getCentreY(), "Up still climbs during the drowning sink");
        sonic.setDirectionalInputPressed(false, true, false, false);
        pole.update(0, sonic);
        assertEquals(pole.getY(), sonic.getCentreY(), "Down still climbs during the drowning sink");
        sonic.setDirectionalInputPressed(false, false, false, false);
        for (int i = 2; i < 59; i++) pole.update(0, sonic);
        assertTrue(sonic.isObjectControlSuppressesMovement(), "bit 0 stays set until timeout");

        pole.update(0, sonic);
        assertFalse(sonic.isObjectControlSuppressesMovement(), "the pole clears bit 0 on timeout");
        assertTrue(sonic.isTouchResponseSuppressedByObjectControl(), "drowning keeps bit 7 set");
    }

    @Test
    void breakPoleReleaseRestoresAirControlDuringDrowning() throws Exception {
        boot(CdStagePayload.R42A);
        sonic.setCentreX((short) 5170);
        sonic.setCentreY((short) 1608);
        sonic.setAir(true);
        GameServices.camera().updatePosition(true);
        ObjectManagerInternalAccess.refreshPlacementAfterRelocation(GameServices.level().getObjectManager());

        runner.stepIdleFrames(1680);
        for (int i = 0; i < 22; i++) runner.stepFrame(false, false, false, true, false);
        for (int i = 0; i < 180 && !sonic.isDrowningPreDeath(); i++) runner.stepIdleFrames(1);
        assertTrue(sonic.isDrowningPreDeath(), "air runs out while Sonic holds the placed pole");
        assertTrue(sonic.isObjectControlSuppressesMovement());
        for (int i = 0; i < 180 && sonic.isObjectControlSuppressesMovement(); i++) {
            // Climb down during the hold so the first released steps have clear water below.
            runner.stepFrame(false, true, false, false, false);
        }
        assertFalse(sonic.isObjectControlSuppressesMovement());

        // PlayerFallState (objects/player.asm:673-696) moves first and adds $38 - $28.
        // loc_206364 (objects/r4/player_bubbles.asm:249-255) moves again and adds $10.
        // PlayerMoveAir (objects/player.asm:1494-1547) uses twice the water acceleration (6).
        int x = (sonic.getCentreX() << 16) + sonic.getXSubpixelRaw();
        int y = (sonic.getCentreY() << 16) + sonic.getYSubpixelRaw();
        int dy = sonic.getYSpeed();
        int[] steeredSpeeds = {-12, 0, 12, 24};
        for (int vx : steeredSpeeds) {
            x += 2 * vx << 8;
            y += (2 * dy + 0x10) << 8;
            dy += 0x20;
            int expectedX = x;
            int expectedY = y;
            int expectedYSpeed = dy;
            runner.stepFrame(false, false, vx < 0, vx >= 0, false);
            assertAll("released underwater step",
                    () -> assertTrue(sonic.isDrowningPreDeath(), "the bubbles slot still owns the sink"),
                    () -> assertEquals(CdSonicAnimation.WALK.id(), sonic.getAnimationId(), "falling writes Walk"),
                    () -> assertEquals(vx, sonic.getXSpeed(), "directional input steers the fall"),
                    () -> assertEquals(expectedX, (sonic.getCentreX() << 16) + sonic.getXSubpixelRaw(), "two X moves"),
                    () -> assertEquals(expectedYSpeed, sonic.getYSpeed(), "combined underwater gravity"),
                    () -> assertEquals(expectedY, (sonic.getCentreY() << 16) + sonic.getYSubpixelRaw(), "two Y moves"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void breakPoleGripsDeadAndDrowningPlayers(boolean drowning) throws Exception {
        boot(CdStagePayload.R42A);
        var pole = addBreakPole(1);
        if (drowning) {
            assertTrue(sonic.applyDrownDeath());
        } else {
            sonic.setInvulnerableFrames(0);
            assertTrue(sonic.applyHurtOrDeath(sonic.getCentreX() + 32, DamageCause.SPIKE, false));
        }

        pole.update(0, sonic);
        assertEquals(pole.getX() + 24, sonic.getCentreX());
        assertEquals(CdSonicAnimation.HANG.id(), sonic.getAnimationId());
        assertTrue(sonic.isObjectControlSuppressesMovement());
        assertEquals(drowning, sonic.isTouchResponseSuppressedByObjectControl());
        for (int i = 0; i < 60; i++) pole.update(0, sonic);
        assertFalse(sonic.isObjectControlSuppressesMovement());
        assertEquals(drowning, sonic.isTouchResponseSuppressedByObjectControl());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void breakPoleLetsHurtAndDeadPlayersMoveInTheFrameLoop(boolean hadRings) throws Exception {
        boot(CdStagePayload.R42A);
        var placement = GameServices.level().getObjectManager().getAllSpawns().stream()
                .filter(spawn -> spawn.objectId() == 0x05).findFirst().orElseThrow();
        sonic.setCentreX((short) placement.x());
        sonic.setCentreY((short) placement.y());
        GameServices.camera().updatePosition(true);
        ObjectManagerInternalAccess.refreshPlacementAfterRelocation(GameServices.level().getObjectManager());
        runner.stepIdleFrames(1);
        assertEquals(CdSonicAnimation.HANG.id(), sonic.getAnimationId());
        assertTrue(sonic.isObjectControlSuppressesMovement(), "the disc-placed pole gripped Sonic");
        assertTrue(sonic.isInWater(), "the R42A pole is underwater");

        int ticks = placement.subtype() == 0 ? 180 : 60;
        runner.stepIdleFrames(ticks - 2);
        assertTrue(sonic.isObjectControlSuppressesMovement(), "the pole is still intact two ticks before timeout");
        sonic.setSubpixelRaw(0, 0);
        int x = sonic.getCentreX();
        int y = sonic.getCentreY();
        sonic.setInvulnerableFrames(0);
        assertTrue(sonic.applyHurtOrDeath(x + 32, DamageCause.SPIKE, hadRings));
        runner.stepIdleFrames(1);

        // HurtPlayer (r4/player_object_collide.asm:243-263) starts underwater recoil.
        // PlayerHurt (objects/player.asm:2206-2219) moves before adding $10.
        // KillPlayer (r4/player_object_collide.asm:273-288) starts the death bounce.
        // PlayerDead (objects/player.asm:2244-2249) uses MoveObjectFall (common/object.asm:27-61).
        assertEquals(x - (hadRings ? 1 : 0), sonic.getCentreX(), "horizontal recoil while held");
        assertEquals(y - (hadRings ? 2 : 7), sonic.getCentreY(), "vertical recoil while held");
        assertEquals(hadRings ? -0x200 + 0x10 : -0x700 + 0x38, sonic.getYSpeed(), "gravity during recoil");
        assertTrue(sonic.isObjectControlSuppressesMovement(), "the pole still holds during recoil");
        runner.stepIdleFrames(1);
        assertFalse(sonic.isObjectControlSuppressesMovement(), "the pole releases on its original timeout");
    }

    @Test
    void breakPoleUsesTwoDiscBackedMappingFrames() throws Exception {
        boot(CdStagePayload.R42A);
        var provider = art();
        var key = TtzObjectArt.key(provider, TtzObjectArt.BREAK_POLE, 0x42F8, GameServices.camera().getX());
        assertNotNull(key);
        var sheet = provider.getSheet(key);
        assertEquals(2, sheet.getFrameCount());
        assertEquals(2, sheet.getPaletteIndex());
        assertTrue(java.util.Arrays.stream(sheet.getPatterns()).anyMatch(pattern -> {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (pattern.getPixel(x, y) != 0) return true;
                }
            }
            return false;
        }), "the stage PLC supplies nontransparent art at the mapping's tile base");
    }

    private ObjectInstance addBreakPole(int subtype) {
        return add(SessionManager.requireCurrentGameModule().createObjectRegistry()
                .create(spawn(sonic.getCentreX(), sonic.getCentreY(), 0x05, subtype)));
    }

    @Test
    void stationarySpikeBallHurtsAtContactAndRecreatesThroughRewind() throws Exception {
        boot(CdStagePayload.R42A);
        var manager = GameServices.level().getObjectManager();
        var registry = SessionManager.requireCurrentGameModule().createObjectRegistry();
        int x = sonic.getCentreX() + 64;
        int y = sonic.getCentreY() + 11;
        var ball = add(assertInstanceOf(AbstractObjectInstance.class, registry.create(spawn(x, y, 0x37, 0))));
        assertEquals(0, ((TouchResponseProvider) ball).getCollisionFlags());
        runner.stepIdleFrames(2);
        assertEquals(x, ball.getX());
        assertEquals(y, ball.getY());
        assertEquals(3, ball.getPriorityBucket());
        assertEquals(0xA9, ((TouchResponseProvider) ball).getCollisionFlags());
        assertTrue(ball.isOnScreenForTouch());

        sonic.setInvulnerableFrames(0);
        sonic.setInWater(true);
        GameServices.level().getLevelGamestate().setRings(5);
        sonic.setCentreY((short) (y - 11));
        // R42A's two pre-current offsets: -21 clears the 12-pixel half-width, -17 overlaps it.
        sonic.setCentreX((short) (x - 21));
        manager.runTouchResponsesForPlayer(sonic, 1);
        assertFalse(sonic.isHurt());
        assertEquals(5, GameServices.level().getLevelGamestate().getRings());

        int slot = ball.getSlotIndex();
        var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var beforeContact = rewind.capture();
        assertStationarySpikeBallContact(x, 2);

        manager.removeDynamicObject(ball);
        rewind.restore(beforeContact);
        var restored = live(0x37).stream().map(AbstractObjectInstance.class::cast)
                .filter(object -> object.getSlotIndex() == slot).findFirst().orElseThrow();
        assertNotSame(ball, restored);
        assertEquals(x, restored.getX());
        assertEquals(y, restored.getY());
        assertEquals(0xA9, ((TouchResponseProvider) restored).getCollisionFlags());
        assertTrue(restored.isOnScreenForTouch());
        assertFalse(sonic.isHurt());
        assertStationarySpikeBallContact(x, 2);
    }

    private void assertStationarySpikeBallContact(int ballX, int touchFrame) {
        sonic.setCentreX((short) (ballX - 17));
        GameServices.level().getObjectManager().runTouchResponsesForPlayer(sonic, touchFrame);
        assertTrue(sonic.isHurt());
        assertEquals(-0x100, sonic.getXSpeed());
        assertEquals(-0x200, sonic.getYSpeed());
        assertEquals(0, sonic.getGSpeed());
        runner.stepIdleFrames(1); // The shared ring owner processes the queued spill during the level update.
        assertEquals(0, GameServices.level().getLevelGamestate().getRings());
        assertEquals(5, GameServices.level().getRingManager().getActiveLostRings().size());
    }

    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = {"R42A", "R42C", "R42D"})
    void stationarySpikeBallUsesItsDiscMappingAndPlc(CdStagePayload payload) throws Exception {
        boot(payload);
        var provider = art();
        var key = TtzObjectArt.key(provider, TtzObjectArt.STATIONARY_SPIKE_BALL, 0x370, GameServices.camera().getX());
        assertNotNull(key);
        var sheet = provider.getSheet(key);
        assertEquals(1, sheet.getFrameCount());
        assertEquals(0, sheet.getPaletteIndex());
        var piece = sheet.getFrame(0).pieces().getFirst();
        assertEquals(1, sheet.getFrame(0).pieces().size());
        assertEquals(-16, piece.xOffset());
        assertEquals(-16, piece.yOffset());
        assertEquals(4, piece.widthTiles());
        assertEquals(4, piece.heightTiles());
        assertEquals(0, piece.tileIndex());
        var patterns = sheet.getPatterns();
        assertTrue(java.util.Arrays.stream(patterns).anyMatch(pattern -> {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (pattern.getPixel(x, y) != 0) return true;
                }
            }
            return false;
        }), "the stage PLC supplies visible art for the single $370 mapping");
    }

    // ---- Water surface at level start (r4/main.asm:249) ----------------------------------------------

    @Test
    void levelStartCreatesTheWaterSurfaceInTheFirstDynamicSlot() throws Exception {
        boot(CdStagePayload.R41A);
        var surfaces = live(0x04).stream().filter(o -> o instanceof ObjWaterSurface).toList();
        assertEquals(1, surfaces.size(), "InitStage: move.b #4,object_spawn_pool+obj.id (r4/main.asm:249)");
        assertEquals(ObjWaterSurface.LEVEL_START_SLOT, ((AbstractObjectInstance) surfaces.getFirst()).getSlotIndex(),
                "object_spawn_pool is dynObjects, the first dynamic slot (Level/Tidal Tempest/R4 Compatibility.asm:83)");
    }

    // ---- Move wall (objects/r4/move_wall.asm) --------------------------------------------------------

    @Test
    void moveWallWaitsSlidesAndStopsThreeHundredEightyFrames() throws Exception {
        boot(CdStagePayload.R42A);
        int x0 = GameServices.camera().getX() + 0x120;
        ObjMoveWall wall = add(new ObjMoveWall(spawn(x0, sonic.getCentreY() - 0x20, 0x45, 0)));
        runner.stepIdleFrames(1);
        assertEquals(x0, wall.getX(), "Routine0 only initialises (MoveWallObject_0_Routine0, objects/r4/move_wall.asm:21-52)");
        // Routine2 counts $3C down; the frame it reaches 0 starts Routine4 (MoveWallObject_0_Routine2, objects/r4/move_wall.asm:56-60).
        runner.stepIdleFrames(0x3B);
        assertEquals(x0, wall.getX());
        runner.stepIdleFrames(1);
        assertEquals(x0, wall.getX(), "the frame the wait ends only changes the routine");
        // Routine4 adds $8000 (half a pixel) per frame for $180 frames (MoveWallObject_0_Routine4, objects/r4/move_wall.asm:67-81).
        runner.stepIdleFrames(2);
        assertEquals(x0 + 1, wall.getX());
        runner.stepIdleFrames(0x17E);
        assertEquals(x0 + 0xC0, wall.getX());
        runner.stepIdleFrames(5);
        assertEquals(x0 + 0xC0, wall.getX(), "Routine6 stands still");
    }

    // ---- Yago (objects/r4/yago.asm) -----------------------------------------------------------------------

    @Test
    void yagoFallsToTheFloorThenCrawlsAQuarterPixelAFrame() throws Exception {
        boot(CdStagePayload.R41D);
        ObjYago yago = add(new ObjYago(spawn(sonic.getCentreX(), sonic.getCentreY() - 0x30, 0x2E, 0)));
        // Routine0 runs Routine2 in the same frame: one pixel a frame down until the floor is within 5 (yago.asm:32-48).
        int frames = 0;
        while (yago.routine() != 4 && frames++ < 200) runner.stepIdleFrames(1);
        assertEquals(4, yago.routine(), "the crawl routine follows the landing (loc_20DF2E, yago.asm:58-64)");
        assertTrue(floorDistance(yago, 0xC) < 5, "it stops when the floor distance falls below 5 (cmpi.w #5,d1; bmi, :46-47)");
        assertEquals(0x2F, yago.getCollisionFlags(), "collision $2F (:36)");
        int x0 = yago.getX();
        // Routine4 adds -$4000 (16.16) to X a frame (:66-70, :61): -0.25 reads one pixel left at once, and -1.0 after four.
        runner.stepIdleFrames(1);
        assertEquals(x0 - 1, yago.getX());
        runner.stepIdleFrames(3);
        assertEquals(x0 - 1, yago.getX());
        runner.stepIdleFrames(1);
        assertEquals(x0 - 2, yago.getX());
    }

    // ---- Mace (objects/r4/mace.asm) ---------------------------------------------------------------

    @Test
    void maceSpawnsSixPartsOnTheRotatingArm() throws Exception {
        boot(CdStagePayload.R42A);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        add(new ObjMace(spawn(x0, y0, 0x36, 0)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> parts = live(0x36).stream().filter(o -> o.getSpawn().subtype() >= 0xFE).toList();
        assertEquals(6, parts.size(), "five links ($FF) and the ball ($FE) (MaceObject_0_Routine0, objects/r4/mace.asm:39-117)");
        // One step of $100 makes the angle $0100; SineTable[1] = 6 and SineTable[$41] = $FF (Level/Functions (Misc).asm:80-89),
        // so the first link is at hub + (cos * 65536 >> 4, sin * 65536 >> 4) = (+15, +0) (MaceObject_0_Routine2, objects/r4/mace.asm:119-177).
        ObjectInstance first = parts.stream().min(java.util.Comparator.comparingInt(o -> ((AbstractObjectInstance) o).getSlotIndex())).orElseThrow();
        assertEquals(x0 + 15, first.getX());
        assertEquals(y0, first.getY());
        // The ball sits at radius 96: cos 255 * 65536 / 16 * 6 = 6 * 15.94 -> +95 in X, sin 6 * 6 / 16 -> +2 in Y at 1/4 scale.
        ObjectInstance ball = parts.stream().filter(o -> o.getSpawn().subtype() == 0xFE).findFirst().orElseThrow();
        assertEquals(x0 + 95, ball.getX());
        assertEquals(y0 + 2, ball.getY());
        assertTrue(ball instanceof com.openggf.level.objects.TouchResponseProvider t && t.getCollisionFlags() == 0xA9);
    }

    // ---- Shooter (objects/r4/shooter.asm) ---------------------------------------------------------

    @Test
    void shooterFiresAMissileEveryThirtyOneFrames() throws Exception {
        boot(CdStagePayload.R42A);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        add(new ObjShooter(spawn(x0, y0, 0x42, 0)));
        runner.stepIdleFrames(1);
        // The counter starts at 0, so the first frame fires (addi.w #-1; bpl, loc_20F2A6, objects/r4/shooter.asm:29-31).
        List<ObjectInstance> missiles = live(0x42).stream().filter(o -> o.getSpawn().subtype() == 0xFF).toList();
        assertEquals(1, missiles.size());
        // Spawned at x + $20 for subtype 0, which moves left (d6 stays +$20, ShooterObject, objects/r4/shooter.asm:17-27), at y + 4,
        // then -3 pixels in the same frame (loc_20F32E, objects/r4/shooter.asm:64-70).
        assertEquals(x0 + 0x20 - 3, missiles.get(0).getX());
        assertEquals(y0 + 4, missiles.get(0).getY());
        runner.stepIdleFrames(30);
        assertEquals(1, live(0x42).stream().filter(o -> o.getSpawn().subtype() == 0xFF).count());
        runner.stepIdleFrames(1);
        assertEquals(2, live(0x42).stream().filter(o -> o.getSpawn().subtype() == 0xFF).count(), "the 32nd frame fires again");
    }

    // ---- Water surface (objects/r4/water_surface.asm) ---------------------------------------------

    @Test
    void waterSurfaceAlternatesTwoScreenOffsetsOnTheWaterLine() throws Exception {
        boot(CdStagePayload.R42A);
        ObjWaterSurface surface = add(new ObjWaterSurface(spawn(0x100, 0x302, 0x04, 0)));
        runner.stepIdleFrames(1);
        int cam = GameServices.camera().getX();
        // First frame: toggle 0 -> 1, table entry 1 is $60: camera X + $A0 + $60 (WaterSurfaceObject_0_Routine2, objects/r4/water_surface.asm:24-34).
        assertEquals(cam + 0x100, surface.getX());
        runner.stepIdleFrames(1);
        // Second frame: toggle wraps to 0, byte_20695E[0] = $A0 sign-extended = -$60: camera X + $40.
        assertEquals(GameServices.camera().getX() + 0x40, surface.getX());
        int waterY = GameServices.water().getGameplayWaterLevelY(
                GameServices.level().getFeatureZoneId(), GameServices.level().getFeatureActId());
        assertEquals(waterY + 2, surface.getY(), "move.w water_y,obj.y(a0); addq.w #2 (water_surface.asm:34-37)");
    }

    // ---- Big door (objects/r4/big_door.asm) -------------------------------------------------------

    @Test
    void bigDoorStaysClosedAndSolidUntilItsSwitchIsSet() throws Exception {
        boot(CdStagePayload.R42A);
        ObjBigDoor door = add(new ObjBigDoor(spawn(GameServices.camera().getX() + 0x100, sonic.getCentreY() - 0x80, 0x25, 0)));
        runner.stepIdleFrames(5);
        assertFalse(door.isDestroyed());
        assertEquals(1, live(0x25).size(), "no panels while switch_flags[subtype] is clear");
    }


    // ---- Tonbo (objects/r4/tonbo.asm) --------------------------------------------------------------

    @Test
    void tonboFliesLeftBobsAndTurnsAfterHalfAPeriod() throws Exception {
        boot(CdStagePayload.R42B);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        ObjTonbo tonbo = add(new ObjTonbo(spawn(x0, y0, 0x2B, 0)));
        runner.stepIdleFrames(1);
        // Init falls into Routine2: one step of -$10000 (TonboObject_0_Routine0/2, objects/r4/tonbo.asm:21-72).
        assertEquals(x0 - 1, tonbo.getX());
        assertEquals(y0, tonbo.getY(), "phase 0: SineTable[0] = 0");
        assertEquals(0x2C, tonbo.getCollisionFlags(), "move.b #$2C,obj.collide_type(a0) (objects/r4/tonbo.asm:24)");
        runner.stepIdleFrames(1);
        // Phase 4: SineTable[4] = $19, $19 * 65536 / 16 = 1.56 pixels -> y0 + 1 (TonboObject_0_Routine2, objects/r4/tonbo.asm:51-72).
        assertEquals(x0 - 2, tonbo.getX());
        assertEquals(y0 + 1, tonbo.getY());
        // oVar36 starts at $80 and the turn happens on the frame it reaches 0: frame $80 (TonboObject_0_Routine2, objects/r4/tonbo.asm:60-69).
        runner.stepIdleFrames(0x7D);
        assertEquals(x0 - 0x7F, tonbo.getX());
        assertEquals(0, tonbo.spriteFlip() & 1);
        runner.stepIdleFrames(1);
        assertEquals(x0 - 0x80, tonbo.getX());
        assertEquals(1, tonbo.spriteFlip() & 1, "bchg #0,obj.sprite_flags(a0) (objects/r4/tonbo.asm:66-69)");
        runner.stepIdleFrames(1);
        assertEquals(x0 - 0x7F, tonbo.getX(), "the velocity is negated");
    }

    // ---- Circling spike balls (objects/r4/circle_spike_ball.asm) -----------------------------------

    @Test
    void circleSpikeBallMakesFourBallsOnOneCircle() throws Exception {
        boot(CdStagePayload.R42C);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        ObjCircleSpikeBall entry = add(new ObjCircleSpikeBall(spawn(x0, y0, 0x38, 3)));
        runner.stepIdleFrames(1);
        assertEquals(4, live(0x38).size(), "the entry and three more (objects/r4/circle_spike_ball.asm:27-60)");
        runner.stepIdleFrames(1);
        // Phase $100 -> angle 1: SineTable[1] = 6, SineTable[$41] = $FF; shifted right by the subtype (3):
        // x0 + 31, y0 + 0 (CircleSpikeBallObject_0_Routine2, objects/r4/circle_spike_ball.asm:117-131).
        assertEquals(x0 + 31, entry.getX());
        assertEquals(y0, entry.getY());
        assertEquals(0xA9, entry.getCollisionFlags());
    }

    // ---- Fan (objects/r4/fan_a_b.asm, fan_c_d.asm) ---------------------------------------------------

    @Test
    void fanVariantComesFromTheStageFile() throws Exception {
        boot(CdStagePayload.R42B);
        assertTrue(TtzObjectCatalogue.offset(CdStagePayload.R42B, "fanABMappings1") >= 0, "A/B fans in R42B");
        assertEquals(-1, TtzObjectCatalogue.offset(CdStagePayload.R42B, "fanCDMappings1"));
        assertEquals(-1, TtzObjectCatalogue.offset(CdStagePayload.R42C, "fanABMappings1"));
        assertTrue(TtzObjectCatalogue.offset(CdStagePayload.R42C, "fanCDMappings1") >= 0, "C/D fans in R42C");
    }


    // ---- Twin move block (objects/r4/twin_move_block.asm) ----------------------------------------------

    @Test
    void twinMoveBlocksMoveInOppositionThroughTheCycle() throws Exception {
        boot(CdStagePayload.R42C);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x100;
        ObjTwinMoveBlock first = add(new ObjTwinMoveBlock(spawn(x0, y0, 0x48, 0)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> pair = live(0x48);
        assertEquals(2, pair.size(), "the entry makes a twin $20 to its right (objects/r4/twin_move_block.asm:39-53)");
        ObjectInstance twin = pair.stream().filter(o -> o != first).findFirst().orElseThrow();
        assertEquals(x0 + 0x20, twin.getX());
        // Frame 2 loads cycle row 0 (count $80, acceleration -4, speed +$200); the move starts the frame after.
        runner.stepIdleFrames(2);
        // Speed $200 (8.8) is 2 pixels a frame, 4 less per frame ($FC): after one move y0 + 2 (objects/r4/twin_move_block.asm:70-84).
        assertEquals(y0 + 2, first.getY());
        assertEquals(y0 - 2, twin.getY(), "the twin's speed and acceleration are negated");
    }

    // ---- Crush blocks (objects/r4/crush_blocks.asm) -----------------------------------------------------

    @Test
    void crushBlocksMakeEightBlocksThatHoldThenClose() throws Exception {
        boot(CdStagePayload.R42B);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x100;
        add(new ObjCrushBlocks(spawn(x0, y0, 0x33, 0)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> blocks = live(0x33).stream().filter(o -> o.getSpawn().subtype() >= 0xFE).toList();
        assertEquals(8, blocks.size(), "four rows of two (objects/r4/crush_blocks.asm:44-95)");
        // The first speed is -0.5 pixel a frame (outward): after the first update the pairs have moved 0.5 pixel apart,
        // which truncates to 0 on the right-hand blocks' 16.16 positions that began on whole pixels (objects/r4/crush_blocks.asm:100-128).
        int minX = blocks.stream().mapToInt(ObjectInstance::getX).min().orElseThrow();
        int maxX = blocks.stream().mapToInt(ObjectInstance::getX).max().orElseThrow();
        runner.stepIdleFrames(0xBF);
        assertTrue(blocks.stream().mapToInt(ObjectInstance::getX).min().orElseThrow() <= minX - 0x5F,
                "after $C0 frames at 0.5 pixel the blocks are $60 pixels apart");
        assertTrue(blocks.stream().mapToInt(ObjectInstance::getX).max().orElseThrow() >= maxX + 0x5F);
    }

    // ---- Pressure elevator (objects/r4/pressure_elevator.asm) --------------------------------------------

    @Test
    void pressureElevatorWaitsForThePlayerToBeMoreThanTwoHundredAndTwentyBelow() throws Exception {
        boot(CdStagePayload.R42C);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x100;
        ObjPressureElevator elevator = add(new ObjPressureElevator(spawn(x0, y0, 0x49, 0)));
        runner.stepIdleFrames(3);
        // The player is $100 below: Routine2 needs player.y - $E0 > start y (objects/r4/pressure_elevator.asm:46-56), so it drops to
        // player.y - $E0 = y0 + $20 and starts falling.
        assertTrue(elevator.getY() >= y0 + 0x20, "dropped to $E0 above the player, then falling");
    }

    // ---- Geyser (objects/r4/geyser.asm) ----------------------------------------------------------------

    @Test
    void geyserSitsOnTheWaterAndThrowsADropEverySixteenFrames() throws Exception {
        boot(CdStagePayload.R41A);
        int x0 = GameServices.camera().getX() + 0x100;
        ObjGeyser geyser = add(new ObjGeyser(spawn(x0, sonic.getCentreY(), 0x2A, 0)));
        runner.stepIdleFrames(1);
        int waterY = GameServices.water().getGameplayWaterLevelY(
                GameServices.level().getFeatureZoneId(), GameServices.level().getFeatureActId());
        assertEquals(waterY - 0x30, geyser.getY(), "move.w water_y,obj.y(a0); subi.w #$30 (geyser.asm:44-50)");
        runner.stepIdleFrames(14);
        assertEquals(0, live(0x2A).stream().filter(o -> o.getSpawn().subtype() == 0xFF).count());
        runner.stepIdleFrames(1);
        assertEquals(1, live(0x2A).stream().filter(o -> o.getSpawn().subtype() == 0xFF).count(),
                "addi.b #$10,obj.var_3b(a0) carries on the sixteenth frame (geyser.asm:48-51)");
    }

    // ---- Platform chain (objects/r4/platform_chain.asm) ----------------------------------------------------

    @Test
    void platformChainMakesNinePiecesOnOneCircle() throws Exception {
        boot(CdStagePayload.R42C);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x100;
        ObjPlatformChain head = add(new ObjPlatformChain(spawn(x0, y0, 0x3E, 0)));
        runner.stepIdleFrames(1);
        assertEquals(9, live(0x3E).size(), "the entry and eight more (platform_chain.asm:53-82)");
        // The head starts at angle $20 and the first Routine2 call (the init falls into it) adds one: angle $21, where the
        // sine table gives sin $B9 and (at $61) cos $B0 (Level/Functions (Misc).asm:83,87); times 6 over 16 that is
        // (+66, +69) from the centre (PlatformChainObject_0_Routine2, objects/r4/platform_chain.asm:123-150).
        assertEquals(x0 + 66, head.getX());
        assertEquals(y0 + 69, head.getY());
    }

    // ---- Amenbo (objects/r4/amenbo.asm) -------------------------------------------------------------------

    @Test
    void amenboSkatesOnTheWaterAndAcceleratesToItsLimit() throws Exception {
        boot(CdStagePayload.R42B);
        int x0 = GameServices.camera().getX() + 0x100;
        ObjAmenbo amenbo = add(new ObjAmenbo(spawn(x0, sonic.getCentreY() - 0x80, 0x2C, 0)));
        runner.stepIdleFrames(1);
        int waterY = GameServices.water().getGameplayWaterLevelY(
                GameServices.level().getFeatureZoneId(), GameServices.level().getFeatureActId());
        assertEquals(waterY - 8, amenbo.getY(), "move.w water_y,obj.y(a0); subq.w #8 (amenbo.asm:155-163)");
        assertEquals(0x31, amenbo.getCollisionFlags());
        // Speed gains -$600 a frame (16.16) up to the -$18000 limit, reached on the 64th frame (amenbo.asm:53-84); the distance
        // after n frames is $600 * n * (n + 1) / 2: 41 frames cover $14.1 pixels.
        runner.stepIdleFrames(40);
        assertTrue(amenbo.getX() <= x0 - 19 && amenbo.getX() >= x0 - 21, "x after 41 frames: " + amenbo.getX());
        assertEquals(2, amenbo.routine());
        runner.stepIdleFrames(23);
        assertEquals(4, amenbo.routine(), "routine 2 ends at the -1.5 pixel limit and brakes (routine 4)");
    }

    @Test
    void timeStarsAnimateAndRetireInR42a() throws Exception {
        boot(CdStagePayload.R42A);
        assertEquals(CdRetailReleases.USA, CdRetailReleases.find(disc.assets().identity()).orElseThrow());
        var timeTravel = CdTimeTravel.of(SessionManager.requireCurrentGameModule());
        assertEquals(CdStagePayload.R42A, timeTravel.loadedPayload());

        // Enter the ordinary warp routine at running speed after a past-post touch.
        timeTravel.touchTimePost(true);
        sonic.setGSpeed((short) 0x600);
        sonic.setXSpeed((short) 0x600);
        runner.stepFrame(false, false, false, true, false);
        assertTrue(timeTravel.starsActive());
        var stars = live(0x03).stream().filter(CdTimeStars.class::isInstance).map(CdTimeStars.class::cast).toList();
        assertEquals(List.of(5, 6, 7, 8), stars.stream().map(CdTimeStars::animation).sorted().toList());

        // PowerupAnims (r4/data_2a.asm:426-428) selects .Anim5 through .Anim8 in anims/powerup.asm.
        // These are the first twelve displayed frames, including .Anim5's $FF loop.
        int[][] mappingFrames = {
                {8, 9, 10, 11, 12, 11, 10, 9, 8, 0, 8, 9},
                {8, 8, 0, 8, 8, 0, 9, 9, 0, 9, 9, 0},
                {8, 8, 0, 8, 0, 0, 9, 9, 0, 9, 0, 0},
                {8, 0, 0, 8, 0, 0, 9, 0, 0, 9, 0, 0}
        };
        for (int i = 0; i < mappingFrames[0].length; i++) {
            // Keep the warp speed on the opening slope so the production star pass can run.
            sonic.setGSpeed((short) 0x700);
            runner.stepFrame(false, false, false, true, false);
            assertTrue(timeTravel.starsActive(), "warp stars should stay active during the run");
            for (var star : stars) {
                assertEquals(mappingFrames[star.animation() - 5][i], star.mappingFrame(),
                        "animation " + star.animation() + ", tick " + (i + 1));
            }
        }
        assertTrue(stars.stream().noneMatch(CdTimeStars::isDestroyed));
        timeTravel.stopTimeWarp();
        runner.stepIdleFrames(1);
        assertTrue(stars.stream().allMatch(CdTimeStars::isDestroyed));
    }

    // ---- Vertical move blocks ----

    @Test
    void verticalBlockSheetsUseDiscMappingsAndVisibleTiles() throws Exception {
        boot(CdStagePayload.R42B);
        var provider = art();
        // First piece of VMoveBlockSprites1/2/3/7 (objects/r4/v_move_block.asm:149-151,
        // 193-195, 297-299, 798-800) and their disc-backed five-byte mappings.
        for (int[] shape : new int[][]{
                {1, 4, -16, -64, 0}, {2, 12, -32, -96, 0},
                {3, 3, -16, -48, 0}, {7, 1, -16, -16, 0x10}}) {
            int x = shape[0] == 3 ? 0x1940 : 0x570;
            var sheet = provider.getSheet(TtzObjectArt.key(provider, "vmove" + shape[0], 0x446A, x));
            assertNotNull(sheet, "vertical block sheet " + shape[0]);
            var pieces = sheet.getFrame(0).pieces();
            var piece = pieces.getFirst();
            assertEquals(List.of(shape[1], shape[2], shape[3], shape[4], 4, 4, 2),
                    List.of(pieces.size(), piece.xOffset(), piece.yOffset(), piece.tileIndex(),
                            piece.widthTiles(), piece.heightTiles(), sheet.getPaletteIndex()));
            assertTrue(java.util.Arrays.stream(sheet.getPatterns()).anyMatch(pattern -> {
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        if (pattern.getPixel(col, row) != 0) return true;
                    }
                }
                return false;
            }), "stage PLC supplies visible art for shape " + shape[0]);
        }
    }

    @Test
    void verticalWaveCarriesSonicAndRepeatsAfterRewind() throws Exception {
        boot(CdStagePayload.R41B);
        var manager = GameServices.level().getObjectManager();
        var block = add(new ObjVerticalMoveBlock(spawn(0xC0, 0x53, 0x23, 0xA)));
        runner.stepIdleFrames(2);
        sonic.setCentreX((short) block.getX());
        sonic.setCentreY((short) (block.getY() - 16 - sonic.getYRadius()));
        sonic.setYSpeed((short) 0);
        sonic.setAir(true);
        runner.stepIdleFrames(1);
        assertTrue(sonic.isOnObject(), "Sonic lands on the short moving block");
        int sonicY = sonic.getCentreY();
        runner.stepIdleFrames(12);
        assertTrue(sonic.getCentreY() > sonicY, "the short block carries Sonic as it descends");

        int slot = block.getSlotIndex();
        var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var checkpoint = rewind.capture();
        int cycleY = block.getY();
        runner.stepIdleFrames(256);
        assertEquals(cycleY, block.getY());
        var positions = verticalBlockPositions();
        for (var object : live(0x23)) {
            manager.removeDynamicObject((AbstractObjectInstance) object);
        }
        rewind.restore(checkpoint);
        var restored = live(0x23).stream().map(AbstractObjectInstance.class::cast)
                .filter(object -> object.getSlotIndex() == slot).findFirst().orElseThrow();
        assertNotSame(block, restored);
        runner.stepIdleFrames(256);
        assertEquals(positions, verticalBlockPositions());
    }

    private List<List<Integer>> verticalBlockPositions() {
        return live(0x23).stream().map(AbstractObjectInstance.class::cast)
                .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .map(object -> List.of(object.getSlotIndex(), object.getX(), object.getY())).toList();
    }

    @Test
    void verticalSpikedBlocksCarryAndRetireTheirHurtSpikes() throws Exception {
        boot(CdStagePayload.R41B);
        var manager = GameServices.level().getObjectManager();
        var block = add(new ObjVerticalMoveBlock(spawn(0xE0, 0x60, 0x23, 4)));
        runner.stepIdleFrames(2);
        var spike = live(0x22).stream().filter(object -> object.getX() == block.getX())
                .map(ObjSpikes.class::cast).findFirst().orElseThrow();
        int blockSlot = block.getSlotIndex();
        int spikeSlot = spike.getSlotIndex();
        var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var attached = rewind.capture();
        manager.removeDynamicObject(spike);
        rewind.restore(attached);
        var restoredBlock = live(0x23).stream().map(ObjVerticalMoveBlock.class::cast)
                .filter(object -> object.getSlotIndex() == blockSlot).findFirst().orElseThrow();
        var restoredSpike = live(0x22).stream().map(ObjSpikes.class::cast)
                .filter(object -> object.getSlotIndex() == spikeSlot).findFirst().orElseThrow();
        assertNotSame(spike, restoredSpike);
        runner.stepIdleFrames(28);
        assertEquals(List.of(0x83, restoredBlock.getY() + 0x40),
                List.of(restoredSpike.getCollisionFlags(), restoredSpike.getY()));
        sonic.setInvulnerableFrames(0);
        sonic.setCentreX((short) restoredSpike.getX());
        sonic.setCentreY((short) restoredSpike.getY());
        sonic.giveShield();
        // The nearby ring takes the first touch pass. The second reaches the attached spikes.
        manager.runTouchResponsesForPlayer(sonic, 1);
        manager.runTouchResponsesForPlayer(sonic, 2);
        assertTrue(sonic.isHurt(), "the attached spikes damage Sonic on contact");
        manager.removeDynamicObject(restoredBlock);
        runner.stepIdleFrames(1);
        assertTrue(restoredSpike.isDestroyed(), "the spikes retire when the block's slot clears");
    }

}
