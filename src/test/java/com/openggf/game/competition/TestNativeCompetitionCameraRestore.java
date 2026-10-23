package com.openggf.game.competition;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameDataSource;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.snapshot.SolidExecutionSnapshot;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.AbstractLevel;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Camera ownership must be repaired before the real object and solid identity codecs run. */
@Isolated
class TestNativeCompetitionCameraRestore {
    @BeforeEach
    void setup() {
        TestEnvironment.resetAll();
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tails", "knuckles"})
    void nativeCameraRestoresCurrentP1BeforeDestructiveObjectAndSolidRestore(String savedCharacter)
            throws Exception {
        var configuration = EngineServices.current().configuration();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, savedCharacter);
        var roster = new Sonic2CompetitionSession();
        roster.bindPlayers(new Sonic("sonic", (short) 160, (short) 256),
                new Tails("tails", (short) 320, (short) 256));
        roster.bindCameras(new Camera(configuration), new Camera(configuration));
        var policy = new RosterPolicy(roster);
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:native-camera") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) world -> policy)
                        : super.getGameService(type);
            }
            @Override public TouchResponseTable createTouchResponseTable(GameDataSource source) { return null; }
            @Override public ObjectRegistry createObjectRegistry() { return TEST_OBJECTS; }
        };
        GameplayModeContext context = SessionManager.openGameplaySession(module);
        GameplaySessionFactory.attachManagers(context, EngineServices.current());
        for (int slot = 0; slot < 2; slot++) roster.camera(slot).setFocusedSprite(roster.player(slot));
        var level = context.getLevelManager();
        level.initializeCollisionOnly(new EmptyTerrain(), 0, 0);
        level.initObjectManager();
        var objects = level.getObjectManager();
        objects.reset(0);
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        // Match the post-level assembly dependency: object identities precede solid history.
        var registry = context.getRewindRegistry();
        registry.deregister("solid-execution");
        registry.register(objects.rewindSnapshottable());
        var solids = (DefaultSolidExecutionRegistry) context.getSolidExecutionRegistry();
        registry.register(solids);
        var source = objects.createDynamicObject(() -> new StandingObject(TEST_SPAWN));
        source.rider = roster.player(1);
        source.ticks = 7;
        publishStanding(solids, source, roster.player(0), roster.player(1));
        roster.camera(0).setX((short) 0x123);
        roster.camera(0).setY((short) 0x45);
        roster.camera(1).setX((short) 0x678);
        roster.camera(1).setY((short) 0x90);
        var captured = registry.capture();
        var keys = new ArrayList<>(captured.entries().keySet());
        assertTrue(keys.indexOf("sprites") < keys.indexOf("camera"));
        assertTrue(keys.indexOf("camera") < keys.indexOf("object-manager"));
        assertTrue(keys.indexOf("object-manager") < keys.indexOf("solid-execution"));
        var standing = (SolidExecutionSnapshot) captured.get("solid-execution");
        assertEquals(List.of(1, 2), standing.previousStanding().stream()
                .map(entry -> entry.playerId().encoded()).sorted().toList());

        var oldP1 = roster.player(0);
        var oldP2 = roster.player(1);
        replaceHumans(context.getSpriteManager());
        // Both cameras still hold retired actors; saved Tails would also alias P1/P2.
        assertSame(oldP1, roster.camera(0).getFocusedSprite());
        assertSame(oldP2, roster.camera(1).getFocusedSprite());
        registry.restore(captured);
        assertNotSame(oldP1, roster.player(0));
        assertNotSame(oldP2, roster.player(1));
        assertSame(roster.player(0), roster.camera(0).getFocusedSprite());
        assertSame(roster.player(1), roster.camera(1).getFocusedSprite());
        assertEquals(captured.get("camera"), roster.camera(0).capture());
        assertEquals(captured.get("native_competition_camera_2"), roster.camera(1).capture());
        var restored = onlyStandingObject(objects.getActiveObjects());
        assertNotSame(source, restored, "force the actual object recreation path");
        assertSame(roster.player(1), restored.rider, "object player reference resolves as native ID 2");
        var identities = objects.captureIdentityContext().requireIdentityTable();
        assertSame(roster.player(0), identities.resolvePlayer(PlayerRefId.mainPlayer(), true));
        assertSame(roster.player(1), identities.resolvePlayer(PlayerRefId.sidekick(0), true));
        assertTrue(solids.previousStanding(restored, roster.player(1)).standing());
        assertFalse(solids.previousStanding(restored, roster.player(0)).standing());
        assertEquals(standing, solids.capture());
        for (int i = 0; i < 4; i++)
            objects.update(0, roster.player(0), List.of(roster.player(1)), i, false);
        assertEquals(11, restored.ticks, "forward object passes consume the restored P2 solid history");
        var forwardStanding = solids.capture();
        registry.restore(captured);
        var replayed = onlyStandingObject(objects.getActiveObjects());
        for (int i = 0; i < 4; i++)
            objects.update(0, roster.player(0), List.of(roster.player(1)), i, false);
        assertEquals(restored.ticks, replayed.ticks);
        assertEquals(forwardStanding, solids.capture());
        assertEquals(savedCharacter, configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertFalse(roster.player(0).isCpuControlled());
        assertFalse(roster.player(1).isCpuControlled());
    }

    @Test
    void ordinaryCameraRestoreStillSelectsTheSavedCampaignCharacter() {
        var context = TestEnvironment.activeGameplayMode();
        var sonic = new Sonic("sonic", (short) 100, (short) 200);
        var tails = new Tails("tails", (short) 300, (short) 200);
        var knuckles = new Knuckles("knuckles", (short) 500, (short) 200);
        context.getSpriteManager().addSprite(sonic);
        context.getSpriteManager().addSprite(tails);
        context.getSpriteManager().addSprite(knuckles);
        assertNull(NativeCompetitionRoster.from(context.getWorldSession()));
        NativeCompetitionCameras.register(context);
        for (var actor : List.of(tails, knuckles)) {
            EngineServices.current().configuration().setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE, actor.getCode());
            context.getCamera().setFocusedSprite(sonic);
            var captured = context.getRewindRegistry().capture();
            context.getRewindRegistry().restore(captured);
            assertSame(actor, context.getCamera().getFocusedSprite());
        }
    }

    private static void replaceHumans(SpriteManager sprites) {
        sprites.removeSprite("sonic");
        sprites.removeSprite("tails");
        sprites.addSprite(new Sonic("sonic", (short) 999, (short) 999));
        sprites.addSprite(new Tails("tails", (short) 888, (short) 888));
    }

    private static StandingObject onlyStandingObject(Iterable<? extends com.openggf.level.objects.ObjectInstance> objects) {
        var matches = new ArrayList<StandingObject>();
        for (var object : objects) if (object instanceof StandingObject standing) matches.add(standing);
        assertEquals(1, matches.size());
        return matches.getFirst();
    }

    private static void publishStanding(DefaultSolidExecutionRegistry solids, StandingObject object,
                                        PlayableEntity p1, PlayableEntity p2) {
        Map<PlayableEntity, PlayerSolidContactResult> contacts = new IdentityHashMap<>();
        contacts.put(p1, new PlayerSolidContactResult(ContactKind.SIDE, false, false, true, false, null, null, 0));
        contacts.put(p2, new PlayerSolidContactResult(ContactKind.TOP, true, true, false, false, null, null, 0));
        solids.beginFrame(0, List.of(p1, p2));
        solids.publishCheckpoint(new SolidCheckpointBatch(object, contacts));
        solids.finishFrame();
    }

    private static final ObjectSpawn TEST_SPAWN = new ObjectSpawn(160, 256, 1, 0, 0, false, 0);
    private static final ObjectRegistry TEST_OBJECTS = new ObjectRegistry() {
        @Override public com.openggf.level.objects.ObjectInstance create(ObjectSpawn spawn) { return new StandingObject(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "camera-restore-standing"; }
    };

    private static final class StandingObject extends AbstractObjectInstance implements RewindRecreatable {
        private AbstractPlayableSprite rider;
        private int ticks;
        StandingObject(ObjectSpawn spawn) { super(spawn, "camera-restore-standing"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            var solids = services().solidExecutionRegistry();
            if (solids.previousStanding(this, rider).standing()) ticks++;
            var contact = new PlayerSolidContactResult(ContactKind.TOP, true, true, false, false, null, null, 0);
            solids.publishCheckpoint(new SolidCheckpointBatch(this, Map.of(rider, contact)));
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isPersistent() { return true; }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new StandingObject(context.spawn());
        }
    }

    /** Geometry is unused: this regression exercises manager identity and solid history, not terrain. */
    private static final class EmptyTerrain extends AbstractLevel {
        EmptyTerrain() {
            super(0);
            palettes = new com.openggf.level.Palette[PALETTE_COUNT];
            java.util.Arrays.setAll(palettes, ignored -> new com.openggf.level.Palette());
            patterns = new com.openggf.level.Pattern[]{new com.openggf.level.Pattern()};
            chunks = new com.openggf.level.Chunk[]{new com.openggf.level.Chunk()};
            blocks = new com.openggf.level.Block[]{new com.openggf.level.Block()};
            solidTiles = new com.openggf.level.SolidTile[]{
                    new com.openggf.level.SolidTile(0, new byte[16], new byte[16], (byte) 0)};
            patternCount = chunkCount = blockCount = solidTileCount = 1;
            map = new com.openggf.level.Map(2, 8, 8);
            maxX = maxY = 8 * getBlockPixelSize();
            objects = List.of();
            rings = List.of();
        }
    }

    private record RosterPolicy(Sonic2CompetitionSession roster) implements WorldSessionPolicyState {
        @Override public <T> T getService(Class<T> type) {
            return type == NativeCompetitionRoster.class ? type.cast(roster) : null;
        }
        @Override public RewindSnapshottable<?> rewindAdapter() { return roster; }
        @Override public void bindRoster(SpriteManager sprites) {
            roster.bindPlayers((AbstractPlayableSprite) sprites.getSprite("sonic"),
                    (AbstractPlayableSprite) sprites.getSprite("tails"));
        }
        @Override public void beforeAssembly(LevelLoadCause cause) { }
        @Override public void failedAssembly(LevelLoadCause cause) { }
        @Override public void closeScreens() { }
        @Override public void retire() { }
    }
}
