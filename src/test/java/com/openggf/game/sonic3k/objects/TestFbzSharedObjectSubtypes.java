package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.LevelState;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.Sonic3kRingAwardService;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRoundTripHarness;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Locked-on FBZ coverage for every counted shared-family subtype. */
class TestFbzSharedObjectSubtypes {
    private static final Map<Integer, String> S3KL_NAMES = Map.ofEntries(
            Map.entry(0x00, "Ring"), Map.entry(0x01, "Monitor"),
            Map.entry(0x02, "PathSwap"), Map.entry(0x07, "Spring"),
            Map.entry(0x08, "Spikes"), Map.entry(0x0F, "CollapsingBridge"),
            Map.entry(0x26, "AutoSpin"), Map.entry(0x28, "InvisibleBlock"),
            Map.entry(0x2A, "CorkFloor"), Map.entry(0x2F, "StillSprite"),
            Map.entry(0x33, "Button"), Map.entry(0x34, "StarPost"),
            Map.entry(0x3D, "RetractingSpring"),
            Map.entry(0x6A, "InvisibleHurtBlockHorizontal"),
            Map.entry(0x6B, "InvisibleHurtBlockVertical"),
            Map.entry(0x80, "HiddenMonitor"), Map.entry(0x85, "SSEntryRing"));

    private static final Map<Integer, Set<Integer>> COUNTED_SUBTYPES = matrix(
            "00:00;01:01,03,05,06,08;02:01,02,09,0A,0D,0E,11,12,15,16,21,61,91;"
                    + "07:00,02,04,10,20;08:00,03,10,20,30,40;0F:00;26:04,80;"
                    + "28:11,17,21,22,31,41,61;2A:10;2F:28,29,2A,2C;"
                    + "33:20,21,22,23,24,25,26,27,28,29,2A,2B,2C,2D,2F;"
                    + "34:01,02,03,04,05,06;3D:04;6A:71;"
                    + "6B:11,13,41,51,61,F1;80:05,06;85:01,02,03,04");

    @Test
    void everyCountedSharedSubtypeUsesTheExactS3klPointerAndConcreteFactory() {
        FbzTestRegistry registry = new FbzTestRegistry();
        COUNTED_SUBTYPES.forEach((id, subtypes) -> {
            assertEquals(S3KL_NAMES.get(id), registry.getPrimaryName(id, S3kZoneSet.S3KL),
                    "locked-on Sprite_Listing3 name for $" + hex(id));
            for (int subtype : subtypes) {
                ObjectSpawn spawn = spawn(id, subtype, true);
                ObjectInstance instance = registry.create(spawn);
                assertFalse(instance instanceof PlaceholderObjectInstance,
                        "FBZ shared $" + hex(id) + " subtype $" + hex(subtype));
                assertEquals(spawn, instance.getSpawn(),
                        "factory must preserve remembered placement identity/configuration");
            }
        });
    }

    @Test
    void placedRingIsTheRealObjRingAndNotTheSixByteTerminator() throws Exception {
        ObjectSpawn placed = TestFbzObjectInventory.load("2.bin").get(0);
        assertEquals(0x80, placed.x());
        assertEquals(0x80, placed.y());
        assertEquals(0, placed.objectId());
        assertEquals(0, placed.subtype(), "FBZ2's first six-byte record is a real Obj_Ring");
        assertEquals(1, TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.objectId() == 0).count(),
                "FFFF 0000 0000 terminator must never become a second subtype-0 ring");

        ObjectInstance ring = new FbzTestRegistry().create(placed);
        TouchResponseProvider touch = assertInstanceOf(TouchResponseProvider.class, ring);
        assertEquals(0x47, touch.getCollisionFlags(),
                "Obj_RingInit publishes collision_flags=$47 before falling through to animate");
        assertFalse(ring instanceof SolidObjectProvider);
    }

    @Test
    void subtype04RetractingSpringUsesOrdinarySpringCollisionAndRomOffsetCadence() {
        ObjectInstance instance = new FbzTestRegistry().create(spawn(0x3D, 0x04, false));
        assertEquals("Sonic3kRetractingSpringObjectInstance", instance.getClass().getSimpleName());
        SolidObjectProvider solid = assertInstanceOf(SolidObjectProvider.class, instance);
        assertEquals(27, solid.getSolidParams().halfWidth());
        assertEquals(8, solid.getSolidParams().airHalfHeight());
        assertEquals(16, solid.getSolidParams().groundHalfHeight());

        assertInstanceOf(AbstractObjectInstance.class, instance).setServices(
                new TestObjectServices().withGameState(new GameStateManager()));
        int anchorY = instance.getSpawn().y();
        for (int expectedOffset : new int[]{8, 16, 24, 32}) {
            instance.update(0, null);
            assertEquals(anchorY + expectedOffset, instance.getY(),
                    "sub_23AB8 extends by $800 fixed-point (=8px) per object tick");
        }
        for (int i = 0; i < 60; i++) {
            instance.update(0, null);
            assertEquals(anchorY + 32, instance.getY(), "the fully extended spring waits 60 ticks");
        }
        instance.update(0, null);
        assertEquals(anchorY + 24, instance.getY(), "retraction begins on the tick after the wait expires");
        instance.update(0, null);
        instance.update(0, null);
        instance.update(0, null);
        assertEquals(anchorY, instance.getY());
        assertTrue(((Sonic3kRetractingSpringObjectInstance) instance).retracting(),
                "subtracting $800 down to exactly zero does not borrow yet");
        instance.update(0, null);
        assertFalse(((Sonic3kRetractingSpringObjectInstance) instance).retracting());
        assertEquals(60, ((Sonic3kRetractingSpringObjectInstance) instance).holdTimer(),
                "the following subtract borrows, clamps to zero, and starts the retracted hold");
    }

    @Test
    void retractingSpringPlaysSpikeMoveAtTheOnScreenHoldExpiryAndLaunchesLikeRedUpSpring() {
        Sonic3kRetractingSpringObjectInstance spring = (Sonic3kRetractingSpringObjectInstance)
                new FbzTestRegistry().create(new ObjectSpawn(0x80, 0x80, 0x3D, 0x04, 0, false, 0));
        RecordingServices services = new RecordingServices();
        spring.setServices(services.withGameState(new GameStateManager()));
        AbstractObjectInstance.resetCameraBoundsForTests();
        spring.refreshPostCameraRenderState();

        for (int i = 0; i < 63; i++) {
            spring.update(i, null);
        }
        assertEquals(1, spring.holdTimer());
        assertEquals(0, services.rawSfxCount);
        spring.update(63, null);
        assertEquals(0, spring.holdTimer());
        assertEquals(1, services.rawSfxCount);
        assertEquals(Sonic3kSfx.SPIKE_MOVE.id, services.lastRawSfx);

        TestSprite player = new TestSprite("sidekick_3");
        player.setCentreY((short) spring.getY());
        spring.onSolidContact(player,
                new SolidContact(true, false, false, false, false, 0, false), 64);
        assertEquals(0xF000, player.getYSpeed() & 0xFFFF,
                "subtype $04 is an ordinary red up spring with y_vel=-$1000");
        assertTrue(player.getAir());
    }

    @Test
    void offscreenRetractingSpringSuppressesTheHoldExpirySound() {
        Sonic3kRetractingSpringObjectInstance spring = (Sonic3kRetractingSpringObjectInstance)
                new FbzTestRegistry().create(new ObjectSpawn(0x1200, 0x0800, 0x3D, 0x04, 0, false, 0));
        RecordingServices services = new RecordingServices();
        spring.setServices(services.withGameState(new GameStateManager()));
        for (int i = 0; i < 64; i++) {
            spring.update(i, null);
        }
        assertEquals(0, services.rawSfxCount,
                "sub_23AB8 tests render_flags before sfx_SpikeMove at hold expiry");
    }

    @Test
    void spikeMoveSoundUsesPriorRenderPassVisibilityNotLiveCameraGeometry() {
        Sonic3kRetractingSpringObjectInstance spring = new Sonic3kRetractingSpringObjectInstance(
                new ObjectSpawn(0x80, 0x80, 0x3D, 0x04, 0, false, 0));
        RecordingServices services = new RecordingServices();
        spring.setServices(services.withGameState(new GameStateManager()));
        AbstractObjectInstance.resetCameraBoundsForTests();
        spring.refreshPostCameraRenderState(); // prior Render_Sprites sets bit 7
        AbstractObjectInstance.updateCameraBounds(0x1000, 0, 0x1140, 224, 0);
        try {
            for (int i = 0; i < 64; i++) spring.update(i, null);
            assertEquals(1, services.rawSfxCount,
                    "sub_23AB8 observes retained render_flags from the prior render pass");
        } finally {
            AbstractObjectInstance.resetCameraBoundsForTests();
        }
    }

    @Test
    void anyEligibleEnginePlayerCanCollectThePlacedRingAndCollisionStopsImmediately() {
        for (String playerCode : List.of("sonic", "tails", "sidekick_3")) {
            Sonic3kPlacedRingObjectInstance ring = (Sonic3kPlacedRingObjectInstance)
                    new FbzTestRegistry().create(spawn(0x00, 0x00, false));
            RecordingServices services = new RecordingServices();
            ring.setServices(services);
            TestSprite player = new TestSprite(playerCode);

            ((TouchResponseListener) ring).onTouchResponse(player,
                    new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL), 100);

            assertEquals(1, player.localRings, playerCode);
            assertEquals(0, ring.getCollisionFlags(), "Obj_RingCollect clears collision_flags immediately");
            assertTrue(ring.isPersistent(), "Obj_RingSparkle survives camera unload until animation completion");
            assertEquals(1, services.rawSfxCount);
            assertEquals(Sonic3kSfx.RING_RIGHT.id, services.lastRawSfx);
        }
    }

    @Test
    void mainPlayerInvulnerabilityTimerAt90BlocksAnExtraSidekickRingContact() {
        TestSprite main = new TestSprite("sonic");
        main.setInvulnerableFrames(90);
        TestSprite extra = new TestSprite("sidekick_3");
        Sonic3kPlacedRingObjectInstance ring = (Sonic3kPlacedRingObjectInstance)
                new FbzTestRegistry().create(spawn(0x00, 0x00, false));
        RecordingServices services = new RecordingServices(main, List.of(extra));
        ring.setServices(services);

        ring.onTouchResponse(extra, new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL), 0);

        assertEquals(0, extra.localRings);
        assertEquals(0x47, ring.getCollisionFlags());
        main.setInvulnerableFrames(89);
        ring.onTouchResponse(extra, new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL), 1);
        assertEquals(1, extra.localRings);
    }

    @Test
    void sparkleDrawsFor24TicksAndDeletesOnTheFollowingTick() {
        Sonic3kPlacedRingObjectInstance ring = (Sonic3kPlacedRingObjectInstance)
                new FbzTestRegistry().create(spawn(0x00, 0x00, false));
        RecordingServices services = new RecordingServices();
        ring.setServices(services);
        ring.onTouchResponse(new TestSprite("sonic"),
                new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL), 0);
        for (int i = 0; i < 24; i++) {
            ring.update(i, null);
            assertFalse(ring.isDestroyed(), "Ani_RingSparkle $FC draws through tick 24");
        }
        ring.update(24, null);
        assertTrue(ring.isDestroyed(), "routine 8 deletes on the tick after $FC advances routine");
    }

    @Test
    void placedRingRetainsRomArtPriorityBucketsAndGlobalSpinPhase() throws Exception {
        Sonic3kPlacedRingObjectInstance ring = (Sonic3kPlacedRingObjectInstance)
                new FbzTestRegistry().create(spawn(0x00, 0x00, false));
        com.openggf.level.rings.RingManager ringManager = mock(com.openggf.level.rings.RingManager.class);
        RecordingServices services = new RecordingServices() {
            @Override public com.openggf.level.rings.RingManager ringManager() { return ringManager; }
        };
        ring.setServices(services);
        assertTrue(ring.isHighPriority(), "make_art_tile(ArtTile_Ring,1,1) sets priority");
        assertEquals(4, ring.getPriorityBucket(), "priority $100");
        ring.update(17, null);
        ring.appendRenderCommands(new java.util.ArrayList<>());
        verify(ringManager).drawRingAt(ring.getX(), ring.getY(), 17);

        var paletteField = com.openggf.game.sonic3k.Sonic3kRingArt.class
                .getDeclaredField("RING_PALETTE_INDEX");
        paletteField.setAccessible(true);
        assertEquals(1, paletteField.getInt(null));

        ring.onTouchResponse(new TestSprite("sonic"),
                new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL), 18);
        assertTrue(ring.isHighPriority());
        assertEquals(2, ring.getPriorityBucket(), "Obj_RingCollect priority $80");
    }

    @Test
    void giveRingCapsAt999AndUsesExtraLifeMusicOnlyOnFirst100And200Thresholds() {
        assertGiveRing(99, 100, 1, 0);
        assertGiveRing(199, 200, 1, 0);
        assertGiveRing(998, 999, 0, 1);
        assertGiveRing(999, 999, 0, 1);
    }

    @Test
    void lostRingOwnerResetClearsThresholdFlagsSo100CanAwardAgain() {
        LevelGamestate levelState = new LevelGamestate();
        levelState.setRings(99);
        GameStateManager gameState = new GameStateManager();
        RecordingServices services = new RecordingServices(levelState, gameState);
        TestSprite player = new TestSprite("sonic", levelState);

        Sonic3kRingAwardService.giveOne(services, player);
        assertEquals(0x02, levelState.getRingExtraLifeFlags());
        assertEquals(4, gameState.getLives());

        levelState.resetRingsForLoss();
        assertEquals(0, levelState.getRings());
        assertEquals(0, levelState.getRingExtraLifeFlags());
        levelState.setRings(99);
        Sonic3kRingAwardService.giveOne(services, player);
        assertEquals(5, gameState.getLives());
        assertEquals(2, services.musicCount);
    }

    private static void assertGiveRing(int before, int after, int musicCount, int ringSfxCount) {
        LevelGamestate levelState = new LevelGamestate();
        levelState.setRings(before);
        GameStateManager gameState = new GameStateManager();
        int livesBefore = gameState.getLives();
        RecordingServices services = new RecordingServices(levelState, gameState);
        Sonic3kPlacedRingObjectInstance ring = (Sonic3kPlacedRingObjectInstance)
                new FbzTestRegistry().create(spawn(0x00, 0x00, false));
        ring.setServices(services);
        TestSprite player = new TestSprite("sonic", levelState);
        ring.onTouchResponse(player, new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL), 0);
        assertEquals(after, levelState.getRings());
        assertEquals(musicCount, services.musicCount);
        assertEquals(ringSfxCount, services.rawSfxCount);
        assertEquals(livesBefore + musicCount, gameState.getLives());
    }

    @Test
    void subtype04UsesVerticalSpringArtAndWritesNativeSolidBitsOnLaunch() {
        var entry = Sonic3kPlcArtRegistry.getPlan(Sonic3kZoneIds.ZONE_FBZ, 1).levelArt().stream()
                .filter(candidate -> candidate.key().equals(Sonic3kObjectArtKeys.SPRING_VERTICAL))
                .findFirst().orElseThrow();
        assertEquals(Sonic3kConstants.MAP_SPRING_ADDR, entry.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x10, entry.artTileBase());
        assertEquals(0, entry.palette());

        Sonic3kRetractingSpringObjectInstance spring = new Sonic3kRetractingSpringObjectInstance(
                new ObjectSpawn(0x80, 0x80, 0x3D, 0x04, 0, false, 0));
        spring.setServices(new RecordingServices().withGameState(new GameStateManager()));
        assertEquals(16, spring.getOnScreenHalfWidth());
        assertEquals(16, spring.getOnScreenHalfHeight());
        assertEquals(4, spring.getPriorityBucket());
        TestSprite player = new TestSprite("sonic");
        spring.onSolidContact(player,
                new SolidContact(true, false, false, false, false, 0, false), 0);
        assertEquals(0x0C, player.getTopSolidBit() & 0xFF);
        assertEquals(0x0D, player.getLrbSolidBit() & 0xFF);
    }

    @Test
    void newSharedObjectsRoundTripTheirPhaseStateThroughGenericRewind() {
        for (Class<?> cls : List.of(Sonic3kPlacedRingObjectInstance.class,
                Sonic3kRetractingSpringObjectInstance.class)) {
            assertInstanceOf(RewindRoundTripHarness.RoundTripSweepResult.Passed.class,
                    RewindRoundTripHarness.probeClass(cls.getName()), cls.getName());
        }
    }

    @Test
    void sharedFactoryConfigurationRetainsFamilyCollisionRolesForEveryCountedSubtype() {
        FbzTestRegistry registry = new FbzTestRegistry();
        for (int subtype : COUNTED_SUBTYPES.get(0x07)) {
            assertInstanceOf(Sonic3kSpringObjectInstance.class, registry.create(spawn(0x07, subtype, false)));
        }
        for (int subtype : COUNTED_SUBTYPES.get(0x08)) {
            assertInstanceOf(Sonic3kSpikeObjectInstance.class, registry.create(spawn(0x08, subtype, false)));
        }
        for (int id : Set.of(0x02, 0x26)) {
            for (int subtype : COUNTED_SUBTYPES.get(id)) {
                assertFalse(registry.create(spawn(id, subtype, false)) instanceof SolidObjectProvider,
                        "controller family $" + hex(id) + " remains non-solid");
            }
        }
        assertTrue(registry.create(spawn(0x3D, 0x04, false)) instanceof Sonic3kSpringObjectInstance,
                "retracting spring must reuse canonical Obj_Spring physics/art/collision");
    }

    private static ObjectSpawn spawn(int id, int subtype, boolean remembered) {
        return new ObjectSpawn(0x1200, 0x0800, id, subtype, 0, remembered, remembered ? 7 : 0);
    }

    private static Map<Integer, Set<Integer>> matrix(String spec) {
        Map<Integer, Set<Integer>> result = new LinkedHashMap<>();
        for (String row : spec.split(";")) {
            String[] sides = row.split(":");
            result.put(Integer.parseInt(sides[0], 16), java.util.Arrays.stream(sides[1].split(","))
                    .map(value -> Integer.parseInt(value, 16))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        return Map.copyOf(result);
    }

    private static String hex(int value) {
        return String.format("%02X", value & 0xFF);
    }

    private static final class FbzTestRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_FBZ;
        }
    }

    private static class RecordingServices extends TestObjectServices {
        private int rawSfxCount;
        private int lastRawSfx = -1;
        private int musicCount;
        private final PlayableEntity main;
        private final List<PlayableEntity> sidekicks;
        private final LevelState levelState;
        private final GameStateManager ownedGameState;

        private RecordingServices() {
            this(null, List.of());
        }

        private RecordingServices(PlayableEntity main, List<PlayableEntity> sidekicks) {
            this.main = main;
            this.sidekicks = sidekicks;
            this.levelState = null;
            this.ownedGameState = null;
        }

        private RecordingServices(LevelState levelState, GameStateManager gameState) {
            this.main = null;
            this.sidekicks = List.of();
            this.levelState = levelState;
            this.ownedGameState = gameState;
            super.withGameState(gameState);
        }

        @Override
        public void playSfx(int soundId) {
            rawSfxCount++;
            lastRawSfx = soundId;
        }

        @Override
        public void playSfx(GameSound sound) {
        }

        @Override public void playMusic(int musicId) { musicCount++; }
        @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
            return new com.openggf.level.objects.ObjectPlayerQuery(() -> main, () -> sidekicks);
        }
        @Override public LevelState levelGamestate() { return levelState; }
        @Override public GameStateManager gameState() {
            return ownedGameState != null ? ownedGameState : super.gameState();
        }

        @Override
        public RecordingServices withGameState(GameStateManager gameState) {
            super.withGameState(gameState);
            return this;
        }
    }

    private static final class TestSprite extends AbstractPlayableSprite {
        private int localRings;
        private final LevelState levelState;

        private TestSprite(String code) {
            this(code, null);
        }

        private TestSprite(String code, LevelState levelState) {
            super(code, (short) 0, (short) 0);
            this.levelState = levelState;
        }

        @Override
        public void addRings(int delta) {
            if (levelState != null) levelState.setRings(levelState.getRings() + delta);
            else localRings += delta;
        }

        @Override public int getRingCount() { return levelState != null ? levelState.getRings() : localRings; }

        @Override public void draw() { }
        @Override public void defineSpeeds() { }
        @Override protected void createSensorLines() { }
    }
}
