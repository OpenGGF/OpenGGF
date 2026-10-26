package com.openggf.game.sonic2.bumpers;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.competition.NativeTouchResponseOrder;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionRingStreaming;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.objects.MonitorObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingManagerInternalAccess;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Independent ROM-free boundary cases using real ring, map-bumper, monitor and
 * object-touch owners. The small source-shaped tables are semantic test inputs;
 * these checks do not certify an actual-ROM competition route or presentation.
 */
@ExtendWith(SingletonResetExtension.class)
class TestNativeCompetitionTouchResponseOrder {
    @BeforeEach void setup() { TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2); }
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void nativeHumanCollectsRingThenBouncesBeforeTheRealMonitorTouch(int slot) throws Exception {
        var f = new Fixture(true);
        var monitor = f.monitor();
        var player = f.player(slot);

        f.touch(player);

        assertEquals(List.of("ring", "bumper:" + slot, "monitor"), f.events);
        assertEquals(List.of(player), f.callbacks);
        assertEquals(1, f.banks[slot].getRings());
        assertEquals(0, f.banks[1 - slot].getRings(), "the other human's bank is independent");
        assertTrue(f.rings.isRingCollected(100, 256));
        assertEquals(1, f.ringsAtBounce);
        assertEquals(1, monitor.ringsAtTouch);
        assertEquals(-0xA00, monitor.ySpeedAtTouch,
                "the map bumper must publish its native velocity before the object listener reads it");
        assertEquals(0x46, monitor.getCollisionFlags(),
                "Touch_Monitor's upward/below path must leave the monitor intact");
        assertEquals(0xA00, player.getYSpeed(),
                "the real monitor below-hit path negates the bumper's upward velocity");
    }

    @Test void ordinaryWorldKeepsObjectTouchBeforeRingCollection() throws Exception {
        var f = new Fixture(false);
        var monitor = f.monitor();

        f.touch(f.player(0));

        assertEquals(List.of("monitor", "ring"), f.events);
        assertTrue(f.callbacks.isEmpty(), "an ordinary world has no native touch owner");
        assertEquals(0, monitor.ringsAtTouch);
        assertEquals(0x120, monitor.ySpeedAtTouch);
        assertEquals(0, monitor.getCollisionFlags(),
                "the same real monitor breaks when it sees the original downward Roll touch");
        assertEquals(-0x120, f.player(0).getYSpeed());
        assertEquals(1, f.banks[0].getRings());
        assertTrue(f.rings.isRingCollected(100, 256));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void ringInvulnerabilityRejectionDoesNotSuppressTheNativeBumperBoundary(int slot) throws Exception {
        var f = new Fixture(true);
        var player = f.player(slot);
        player.setInvulnerableFrames(90);

        f.touch(player);

        assertEquals(List.of(player), f.callbacks);
        assertEquals(List.of("bumper:" + slot), f.events);
        assertEquals(0, f.banks[slot].getRings());
        assertFalse(f.rings.isRingCollected(100, 256));
        assertEquals(-0xA00, player.getYSpeed(), "native map bumpers do not test invulnerability timers");
    }

    @Test void missingRingManagerStillDeliversTheNativeBoundary() throws Exception {
        var f = new Fixture(true);
        install(f.level, "ringManager", null);

        f.touch(f.player(0));

        assertEquals(List.of(f.player(0)), f.callbacks);
        assertEquals(List.of("bumper:0"), f.events);
        assertEquals(-0xA00, f.player(0).getYSpeed());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void deadActorStillReachesTheBoundaryWhoseActualBumperOwnsItsRejection(int slot) throws Exception {
        var f = new Fixture(true);
        var monitor = f.monitor();
        var player = f.player(slot);
        player.setDead(true);

        f.touch(player);

        assertEquals(List.of(player), f.callbacks,
                "LevelManager must not hide callback delivery inside its ring/dead eligibility branch");
        assertTrue(f.events.isEmpty(), "real ring, map-bumper and object touch owners reject the dead actor");
        assertEquals(0, f.banks[slot].getRings());
        assertFalse(f.rings.isRingCollected(100, 256));
        assertEquals(0x120, player.getYSpeed());
        assertEquals(0x46, monitor.getCollisionFlags());
    }

    @Test void sameCodeForeignHumanCannotAcquireTheNativeCallback() throws Exception {
        var f = new Fixture(true);
        var foreign = new Sonic(f.player(0).getCode(), (short) 0, (short) 0);
        prepare(foreign);
        assertFalse(f.owns(foreign));

        f.touch(foreign);

        assertTrue(f.callbacks.isEmpty());
        assertTrue(f.events.isEmpty());
        assertEquals(0x120, foreign.getYSpeed());
        assertFalse(f.rings.isRingCollected(100, 256));
    }

    private static void prepare(AbstractPlayableSprite player) {
        player.setRolling(true);
        player.setAnimationId(Sonic2AnimationIds.ROLL.id());
        player.setAir(true);
        NativePositionOps.writeXPosResetSubpixel(player, 100);
        NativePositionOps.writeYPosResetSubpixel(player, 256);
        player.setXSpeed((short) 0x100);
        player.setYSpeed((short) 0x120);
    }

    private static CNZNativeBumperTable bumperTable() {
        ByteBuffer bytes = ByteBuffer.allocate(18);
        bytes.putShort((short) 0x4EF9).putShort((short) 0).putShort((short) 0x1370);
        bytes.putShort((short) 6).putShort((short) 100).putShort((short) 264);
        bytes.putShort((short) 0).putShort((short) 0xFFFF).putShort((short) 0);
        return new CNZNativeBumperTable(0x1781A, bytes.array());
    }

    private static TouchResponseTable touchTable() {
        // Touch_Sizes (s2.asm:85271-85279): monitor entry 6 has radii $10/$10.
        return new TouchResponseTable(RomByteReader.fromBytes(new byte[]{
                4, 4, 0x14, 0x14, 0x0C, 0x14, 0x14, 0x0C,
                4, 0x10, 0x0C, 0x12, 0x10, 0x10}), 0, 7);
    }

    private static void install(LevelManager level, String name, Object value) throws Exception {
        Field field = LevelManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static final class ObservedMonitor extends MonitorObjectInstance {
        private final List<String> events;
        private int ringsAtTouch = -1;
        private int ySpeedAtTouch;
        ObservedMonitor(List<String> events) {
            super(new ObjectSpawn(100, 240, 0x26, 0, 0, false, 0), "touch-order-monitor");
            this.events = events;
        }
        @Override public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frame) {
            var actor = (AbstractPlayableSprite) player;
            events.add("monitor");
            ringsAtTouch = actor.currentLevelState().getRings();
            ySpeedAtTouch = actor.getYSpeed();
            super.onTouchResponse(player, result, frame);
        }
    }

    private static final class Fixture
            implements NativeCompetitionRoster, NativeTouchResponseOrder, WorldSessionPolicyState {
        private final boolean nativeMode;
        private final AbstractPlayableSprite[] players = {
                new Sonic("sonic", (short) 0, (short) 0), new Tails("tails", (short) 0, (short) 0)};
        private final Camera[] cameras = {new Camera(), new Camera()};
        private final LevelGamestate[] banks = {new LevelGamestate(), new LevelGamestate()};
        private final GameStateManager[] games = {new GameStateManager(), new GameStateManager()};
        private final List<String> events = new ArrayList<>();
        private final List<PlayableEntity> callbacks = new ArrayList<>();
        private final LevelManager level;
        private final ObjectManager objects;
        private final RingManager rings;
        private final Sonic2NativeCNZBumpers bumpers;
        private int ringsAtBounce = -1;

        Fixture(boolean nativeMode) throws Exception {
            this.nativeMode = nativeMode;
            var module = new Sonic2GameModule() {
                @Override public <T> T getGameService(Class<T> type) {
                    return type == WorldSessionPolicyProvider.class
                            ? type.cast((WorldSessionPolicyProvider) ignored -> Fixture.this)
                            : super.getGameService(type);
                }
            };
            var gameplay = SessionManager.openGameplaySession(module);
            GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
            level = gameplay.getLevelManager();
            level.resetLevelGamestate(banks[0]);
            for (var player : players) {
                gameplay.getSpriteManager().addSprite(player);
                prepare(player);
            }
            cameras[0].setFocusedSprite(players[0]);
            cameras[1].setFocusedSprite(players[1]);
            var audio = mock(AudioManager.class);
            doAnswer(call -> { events.add("ring"); return null; }).when(audio).playSecondarySfx(GameSound.RING);
            var debug = mock(DebugOverlayManager.class);
            var table = touchTable();
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return objects; }
                @Override public LevelManager levelManager() { return level; }
                @Override public com.openggf.game.session.WorldSession worldSession() { return gameplay.getWorldSession(); }
                @Override public Camera camera() { return cameras[0]; }
                @Override public GameStateManager gameState() { return games[0]; }
                @Override public com.openggf.game.GameModule gameModule() { return module; }
                @Override public AudioManager audioManager() { return audio; }
                @Override public DebugOverlayManager debugOverlay() { return debug; }
                @Override public SpriteManager spriteManager() { return gameplay.getSpriteManager(); }
                @Override public List<PlayableEntity> sidekicks() { return List.of(players[1]); }
            };
            services.withPlayerQuery(new ObjectPlayerQuery(() -> players[0], () -> List.of(players[1])));
            ObjectRegistry objectRegistry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int id) { return "touch-order"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
            };
            objects = new ObjectManager(List.of(), objectRegistry, -1, null, table, null, cameras[0], services);
            objects.enableExecThenLoadPlacement();
            objects.reset(0);
            install(level, "objectManager", objects);
            rings = new RingManager(List.of(new RingSpawn(100, 256)), null, level, table, audio);
            if (nativeMode) RingManagerInternalAccess.enableNativeCompetitionStreaming(
                    rings, new Sonic2CompetitionRingStreaming());
            rings.reset(0);
            install(level, "ringManager", rings);
            bumpers = new Sonic2NativeCNZBumpers(bumperTable(), this, slot -> {
                events.add("bumper:" + slot);
                ringsAtBounce = banks[slot].getRings();
            });
            bumpers.updateCursors();
        }

        ObservedMonitor monitor() {
            return objects.createDynamicObject(() -> new ObservedMonitor(events));
        }
        void touch(AbstractPlayableSprite player) {
            objects.snapshotTouchResponseState(false);
            level.applyTouchResponses(player);
        }
        @Override public boolean owns(PlayableEntity player) { return player == players[0] || player == players[1]; }
        @Override public void afterRings(PlayableEntity player) {
            callbacks.add(player);
            bumpers.checkPlayerCollision(slotOf((AbstractPlayableSprite) player));
        }
        @Override public AbstractPlayableSprite player(int slot) { return players[slot]; }
        @Override public LevelState levelState(int slot) { return banks[slot]; }
        @Override public GameStateManager gameState(int slot) { return games[slot]; }
        @Override public Camera camera(int slot) { return cameras[slot]; }
        @Override public <T> T getService(Class<T> type) {
            return nativeMode && (type == NativeCompetitionRoster.class || type == NativeTouchResponseOrder.class)
                    ? type.cast(this) : null;
        }
        @Override public RewindSnapshottable<?> rewindAdapter() {
            return new RewindSnapshottable<Integer>() {
                @Override public String key() { return "test-native-touch-order"; }
                @Override public Integer capture() { return 0; }
                @Override public void restore(Integer state) { }
            };
        }
        @Override public void bindRoster(SpriteManager sprites) { }
        @Override public void beforeAssembly(LevelLoadCause cause) { }
        @Override public void failedAssembly(LevelLoadCause cause) { }
        @Override public void closeScreens() { }
        @Override public void retire() { }
    }
}
