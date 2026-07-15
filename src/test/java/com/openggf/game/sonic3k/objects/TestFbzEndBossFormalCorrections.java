package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.PlayableEntity;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteFrame;
import com.openggf.level.render.SpriteFramePiece;
import com.openggf.level.render.SpriteSheet;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Formal locked-on corrections for {@code Obj_FBZEndBoss}. */
class TestFbzEndBossFormalCorrections {
    @AfterEach void resetGraphics() { GraphicsManager.getInstance().resetState(); }

    @Test
    void finalHitRecognitionStopsTheCanonicalLevelTimerImmediately() throws Exception {
        LevelGamestate levelState = new LevelGamestate();
        GameStateManager gameState = new GameStateManager();
        TestPlayableSprite player = new TestPlayableSprite();
        FbzServices services = new FbzServices(player, List.of(), new Camera(), gameState, levelState);
        FbzEndBossInstance boss = new FbzEndBossInstance(new ObjectSpawn(
                0x3000, 0x600, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(services);
        set(boss, "phaseOrdinal", FbzEndBossInstance.Phase.DESCEND.ordinal());
        bossState(boss).hitCount = 1;

        boss.onPlayerAttack(player, null);
        assertFalse(levelState.isTimerPaused(), "sub_70E10 recognizes the hit on the root update");
        invoke(boss, "updateNativeHitHandler");

        assertTrue(levelState.isTimerPaused(),
                "BossDefeated_StopTimer clears Update_HUD_timer on the recognition update");
        assertEquals(FbzEndBossInstance.Phase.DEFEAT_RECENTER, boss.phase());
    }

    @Test
    void capsuleAndExitReadyPublishLiveCameraControlHelpersAndDoorHallQueueInOrder() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        TestPlayableSprite p1 = playerAt(0x3000, 0x660);
        TestPlayableSprite p2 = playerAt(0x2FF0, 0x660);
        TestPlayableSprite extra = playerAt(0x2FE0, 0x660);
        ObjectControlState.nativeBit7FullControl().applyTo(p1);
        ObjectControlState.nativeBit7FullControl().applyTo(p2);
        ObjectControlState.nativeBit7FullControl().applyTo(extra);
        p1.setLogicalInputState(false, false, false, true, false);

        Camera camera = new Camera();
        camera.setX((short) 0x2F80);
        camera.setMinX((short) 0x2F00);
        camera.setMaxX((short) 0x2F40);
        GameStateManager gameState = new GameStateManager();
        LevelGamestate levelState = new LevelGamestate();
        FbzServices services = new FbzServices(p1, List.of(p2, extra), camera, gameState, levelState);
        ObjectManager manager = manager(services);
        services.manager = manager;
        FbzEndBossInstance boss = manager.createDynamicObject(() -> new FbzEndBossInstance(
                new ObjectSpawn(0x31C0, 0x690, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0)));
        assertNotNull(boss);

        set(boss, "phaseOrdinal", FbzEndBossInstance.Phase.DEFEAT_CAPSULE_DELAY.ordinal());
        set(boss, "timer", -1);
        invoke(boss, "updateDefeatCapsuleDelay");

        assertTrue(gameState.isEndOfLevelActive());
        assertEquals(0x2FDC, camera.getMaxXTarget() & 0xFFFF,
                "loc_70898 publishes Camera_stored_max_X_pos");
        assertEquals(1, manager.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).size(),
                "Child6_IncLevX owns the gradual live max-X movement");

        invoke(boss, "updateCapsuleWait");
        assertEquals(camera.getX(), camera.getMinX(),
                "loc_708AA pins Camera_min_X_pos while _unkFAA8 is active");

        set(boss, "exitArtQueued", true);
        gameState.setEndOfLevelActive(false);
        invoke(boss, "updateCapsuleWait");

        assertEquals(FbzEndBossInstance.Phase.EXIT_READY, boss.phase());
        assertFalse(p1.isObjectControlled(), "Restore_PlayerControl runs before relock");
        assertTrue(p1.isControlLocked(), "Ctrl_1_locked is set in the same handoff");
        assertEquals(0, p1.getLogicalInputState(), "Ctrl_1_logical is cleared before Task17 auto-run");
        assertFalse(p2.isObjectControlled(), "Restore_PlayerControl2 runs before helper allocation");
        assertFalse(extra.isObjectControlled(), "extra sidekicks receive safe control restoration only");
        assertEquals(0x1000, camera.getMaxYTarget() & 0xFFFF);
        assertEquals(0x3738, camera.getMaxXTarget() & 0xFFFF);
        assertEquals(2, manager.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).size());

        AbstractObjectInstance p2Lock = manager.getActiveObjects().stream()
                .filter(AbstractObjectInstance.class::isInstance)
                .map(AbstractObjectInstance.class::cast)
                .filter(object -> object.getName().equals("S3kNativeP2Lock"))
                .findFirst().orElseThrow(() -> new AssertionError("loc_863C0 helper not installed"));
        p2Lock.update(0, p1);
        assertTrue(p2.isControlLocked());
        assertTrue(extra.isControlLocked(), "extra sidekicks are safety-locked without native authority");

        List<Sonic3kPlcLoader.KosmQueueEntry> entries = Sonic3kPlcLoader.fbzEndBossExitKosmEntries();
        assertEquals(2, entries.size());
        assertEquals(com.openggf.game.sonic3k.constants.Sonic3kConstants.ART_KOSM_FBZ_EXIT_DOOR_ADDR,
                entries.get(0).sourceAddress());
        assertEquals(com.openggf.game.sonic3k.constants.Sonic3kConstants.ART_KOSM_FBZ_EXIT_HALL_ADDR,
                entries.get(1).sourceAddress());
    }

    @Test
    void startupPreservesNativeCreationWaitCallbackAndInitUpdateBoundaries() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        Camera camera = new Camera();
        camera.setX((short) 0x3000);
        camera.setY((short) 0x700);
        FbzServices services = new FbzServices(null, List.of(), camera,
                new GameStateManager(), new LevelGamestate());
        ObjectManager manager = manager(services);
        services.manager = manager;
        FbzEndBossInstance boss = manager.createDynamicObject(() -> new FbzEndBossInstance(
                new ObjectSpawn(0x31C0, 0x690, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0)));

        boss.update(0, null); // creation execution installs Obj_Wait and returns
        assertEquals(0x77, getIntUnchecked(boss, "timer"));
        assertEquals(FbzEndBossInstance.Phase.PRE_MUSIC, boss.phase());
        assertEquals(1, manager.getActiveObjects().stream().filter(FbzEndBossGraphMember.class::isInstance).count());

        for (int update = 2; update <= 120; update++) boss.update(update, null);
        assertEquals(0, getIntUnchecked(boss, "timer"));
        assertEquals(FbzEndBossInstance.Phase.PRE_MUSIC, boss.phase());

        boss.update(121, null); // Obj_Wait underflow invokes callback, which only changes code
        assertEquals(FbzEndBossInstance.Phase.PRE_MUSIC_INIT, boss.phase());
        assertEquals(1, manager.getActiveObjects().stream().filter(FbzEndBossGraphMember.class::isInstance).count());

        boss.update(122, null);
        assertEquals(FbzEndBossInstance.Phase.DESCEND, boss.phase());
        assertEquals(5, manager.getActiveObjects().stream().filter(FbzEndBossGraphMember.class::isInstance).count());
        FbzEndBossInstance.Position expected = FbzEndBossInstance.initialPosition(
                camera.getXCopy(), camera.getYCopy());
        assertEquals(expected.x(), boss.getX());
        assertEquals(expected.y(), boss.getY());
    }

    @Test
    void targetRightRootDebrisKeepsUnflippedOffsetsAndVelocities() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        FbzServices services = new FbzServices(null, List.of(), new Camera(),
                new GameStateManager(), new LevelGamestate());
        ObjectManager manager = manager(services);
        services.manager = manager;
        FbzEndBossInstance boss = manager.createDynamicObject(() -> new FbzEndBossInstance(
                new ObjectSpawn(0x3000, 0x600, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0)));
        set(boss, "facingRight", true);
        invoke(boss, "spawnRootDebris");

        List<FbzEndBossDebrisChild> debris = manager.activeObjectsOfType(FbzEndBossDebrisChild.class);
        assertEquals(List.of(0x2FEC, 0x3014, 0x2FF0, 0x3010),
                debris.stream().map(FbzEndBossDebrisChild::getX).toList());
        assertEquals(List.of(-0x200, 0x200, 0, -0x40),
                debris.stream().map(value -> getIntUnchecked(value, "velocityX")).toList());
    }

    @Test
    void weaponReparentsToShipAtMinus2cAndDiesOnlyWithShipChildDelete() throws Exception {
        FbzEndBossInstance boss = new FbzEndBossInstance(new ObjectSpawn(
                0x3000, 0x600, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0));
        FbzEndBossShipChild ship = new FbzEndBossShipChild(boss);
        FbzEndBossWeaponChild weapon = new FbzEndBossWeaponChild(boss, 0, -0x28);
        boss.attach(ship);
        set(weapon, "initialized", true);
        bossState(boss).defeated = true;
        setInherited(ship, "x", 0x3100);
        setInherited(ship, "y", 0x640);

        weapon.update(0, null);
        assertFalse(weapon.isDestroyed());
        assertEquals(0x3100, weapon.getX());
        assertEquals(0x614, weapon.getY(), "loc_70B58 changes child_dy -$28 to -$2C");

        ship.setDestroyed(true);
        weapon.update(1, null);
        assertTrue(weapon.isDestroyed(), "Child_Draw_Sprite2 propagates the ship child-delete bit4");
    }

    @Test
    void shipRiseChecksCameraThresholdBeforeDecrementingY() throws Exception {
        Camera camera = new Camera();
        camera.setY((short) 0x600);
        FbzEndBossInstance boss = new FbzEndBossInstance(new ObjectSpawn(
                0x3000, 0x600, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0));
        FbzEndBossShipChild ship = new FbzEndBossShipChild(boss);
        ship.setServices(new FbzServices(null, List.of(), camera,
                new GameStateManager(), new LevelGamestate()));
        set(ship, "headSpawnAttempted", true);
        set(ship, "escapePhase", 2);
        setInherited(ship, "y", 0x6C1);
        set(boss, "dismantling", true);

        ship.update(0, null);

        assertEquals(0x6C0, ship.getY());
        assertFalse(ship.hasHorizontalEscapeVelocity(),
                "a ship one pixel below the threshold rises but cannot transition early");
    }

    @Test
    void rootPreZeroTransitionSuppressesDrawAndShipFlameInheritsFlip() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        RecordingRenderer renderer = new RecordingRenderer();
        RecordingRenderManager renderManager = new RecordingRenderManager(renderer);
        FbzServices services = new FbzServices(null, List.of(), new Camera(),
                new GameStateManager(), new LevelGamestate());
        services.renderManager = renderManager;
        FbzEndBossInstance boss = new FbzEndBossInstance(new ObjectSpawn(
                0x3000, 0x600, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(services);
        set(boss, "phaseOrdinal", FbzEndBossInstance.Phase.DEFEAT_RECENTER.ordinal());
        set(boss, "angle", 0xFE);

        invoke(boss, "updateDefeatRecenter");
        boss.appendRenderCommands(new ArrayList<>());
        assertEquals(1, renderer.draws.size(), "the nonzero-to-zero rotation update still draws");

        invoke(boss, "updateDefeatRecenter");
        boss.appendRenderCommands(new ArrayList<>());
        assertEquals(1, renderer.draws.size(), "the following pre-zero transition update does not draw");

        invoke(boss, "updateDefeatExplosions");
        boss.appendRenderCommands(new ArrayList<>());
        assertEquals(2, renderer.draws.size(),
                "the next Wait_FadeToLevelMusic countdown update resumes root drawing");
        for (int i = 1; i < 63; i++) {
            invoke(boss, "updateDefeatExplosions");
            boss.appendRenderCommands(new ArrayList<>());
        }
        assertEquals(64, renderer.draws.size(),
                "timer $003F draws on exactly 63 Wait_FadeToLevelMusic executions");
        invoke(boss, "updateDefeatExplosions");
        boss.appendRenderCommands(new ArrayList<>());
        assertEquals(64, renderer.draws.size(),
                "the 64th execution decrements zero negative, callbacks, and does not draw");

        FbzEndBossShipChild ship = new FbzEndBossShipChild(boss);
        ship.setServices(services);
        set(ship, "escapePhase", 3);
        set(ship, "velocityX", 0x300);
        FbzEndBossShipFlameChild flame = new FbzEndBossShipFlameChild(boss, ship);
        flame.setServices(services);
        flame.update(0, null);
        flame.appendRenderCommands(new ArrayList<>());
        Draw flameDraw = renderer.draws.getLast();
        assertEquals(6, flameDraw.frame());
        assertTrue(flameDraw.hFlip(), "Refresh_ChildPositionAdjusted copies the escaping ship flip");
        assertEquals(ship.getX() - 0x1E, flame.getX());
    }

    @Test
    void freshFirstArmWaveAndRootRenderAreRightFacingAndWeaponDrawsOnSecondExecution()
            throws Exception {
        GraphicsManager.getInstance().initHeadless();
        RecordingRenderer renderer = new RecordingRenderer();
        FbzServices services = new FbzServices(null, List.of(), new Camera(),
                new GameStateManager(), new LevelGamestate());
        services.renderManager = new RecordingRenderManager(renderer);
        FbzEndBossInstance boss = new FbzEndBossInstance(new ObjectSpawn(
                0x3000, 0x600, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(services);
        invoke(boss, "beginAttack");
        FbzEndBossArmChild arm = new FbzEndBossArmChild(boss, 0, -0x30, 0x48);
        arm.setServices(services);
        set(arm, "jointSpawnAttempted", true);
        set(arm, "motionPhase", 1);

        arm.update(0, null);
        boss.appendRenderCommands(new ArrayList<>());
        arm.appendRenderCommands(new ArrayList<>());

        assertEquals(0x100, getIntUnchecked(arm, "velocityX"));
        assertEquals(List.of(new Draw(0, false), new Draw(1, false)), renderer.draws,
                "fresh root and first-wave arm render with native bit0 clear");

        renderer.draws.clear();
        FbzEndBossWeaponChild weapon = new FbzEndBossWeaponChild(boss, 0, -0x28);
        weapon.setServices(services);
        weapon.update(0, null);
        weapon.appendRenderCommands(new ArrayList<>());
        assertTrue(renderer.draws.isEmpty(),
                "loc_70AFC initialization clears the parent bit and returns without drawing");
        weapon.update(1, null);
        weapon.appendRenderCommands(new ArrayList<>());
        assertEquals(List.of(new Draw(3, false)), renderer.draws,
                "loc_70B18 first draws the central weapon on execution two");
    }

    private static TestPlayableSprite playerAt(int x, int y) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        return player;
    }

    private static ObjectManager manager(FbzServices services) {
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), null, services);
        manager.reset(0);
        return manager;
    }

    private static com.openggf.level.objects.boss.BossStateContext bossState(FbzEndBossInstance boss)
            throws Exception {
        Field field = com.openggf.level.objects.boss.AbstractBossInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        return (com.openggf.level.objects.boss.BossStateContext) field.get(boss);
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setInherited(Object target, String fieldName, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static int getIntUnchecked(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class FbzServices extends StubObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> sidekicks;
        private final Camera camera;
        private final GameStateManager gameState;
        private final LevelGamestate levelState;
        private final KosinskiModuleQueue queue = new KosinskiModuleQueue();
        private ObjectManager manager;
        private Rom rom;
        private ObjectRenderManager renderManager;

        private FbzServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks,
                            Camera camera, GameStateManager gameState, LevelGamestate levelState) {
            this.main = main;
            this.sidekicks = sidekicks;
            this.camera = camera;
            this.gameState = gameState;
            this.levelState = levelState;
        }

        @Override public ObjectManager objectManager() { return manager; }
        @Override public Camera camera() { return camera; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public LevelGamestate levelGamestate() { return levelState; }
        @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> main, () -> sidekicks); }
        @Override public List<PlayableEntity> sidekicks() { return List.copyOf(sidekicks); }
        @Override public KosinskiModuleQueue kosinskiModuleQueue() { return queue; }
        @Override public Rom rom() { return rom; }
        @Override public ObjectRenderManager renderManager() { return renderManager; }
        @Override public int getCurrentLevelMusicId() { return -1; }
    }

    private record Draw(int frame, boolean hFlip) { }

    private static final class RecordingRenderer extends PatternSpriteRenderer {
        private final List<Draw> draws = new ArrayList<>();
        private RecordingRenderer() { super(emptySheet(), GraphicsManager.getInstance()); }
        @Override public boolean isReady() { return true; }
        @Override public void drawFrameIndex(int frameIndex, int x, int y, boolean hFlip, boolean vFlip) {
            draws.add(new Draw(frameIndex, hFlip));
        }
    }

    private static final class RecordingRenderManager extends ObjectRenderManager {
        private final PatternSpriteRenderer renderer;
        private RecordingRenderManager(PatternSpriteRenderer renderer) { super((ObjectArtProvider) null); this.renderer = renderer; }
        @Override public PatternSpriteRenderer getRenderer(String key) { return renderer; }
    }

    private static SpriteSheet<SpriteFrame<SpriteFramePiece>> emptySheet() {
        return new SpriteSheet<>() {
            @Override public Pattern[] getPatterns() { return new Pattern[0]; }
            @Override public int getFrameCount() { return 0; }
            @Override public SpriteFrame<SpriteFramePiece> getFrame(int index) { return null; }
            @Override public int getPaletteIndex() { return 0; }
            @Override public int getFrameDelay() { return 0; }
        };
    }
}
