package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GraphicsManager;
import com.openggf.game.sonic3k.objects.FbzExitDoorInstance;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.PoweredBadnikScoring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestPoweredScreenAttack {

    private ObjectManager objectManager;
    private PlayableEntity player;

    @BeforeEach
    void setUp() {
        Camera camera = mock(Camera.class);
        DebugOverlayManager debug = mock(DebugOverlayManager.class);
        ObjectServices services = new TestObjectServices()
                .withCamera(camera)
                .withDebugOverlay(debug);
        objectManager = new ObjectManager(List.of(), emptyRegistry(), 0, null,
                mock(TouchResponseTable.class), mock(GraphicsManager.class),
                camera, services);
        player = mock(PlayableEntity.class);
    }

    @Test
    void destroysOnlyEligibleNormalEnemiesWithoutBouncingPlayer() {
        ObjectInstance ordinary = responder(0x08, 0);
        ObjectInstance boss = responder(0x09, 4);
        objectManager.initialCollisionResponseList()
                .captureCompletedBuild(List.of(ordinary, boss));
        when(player.getXSpeed()).thenReturn((short) 0x320);
        when(player.getYSpeed()).thenReturn((short) -0x180);

        objectManager.poweredAttacks().apply(player);

        verify((PoweredScreenAttackable) ordinary).onPoweredScreenAttack(player);
        verify((PoweredScreenAttackable) boss, never()).onPoweredScreenAttack(any());
        verify(player, never()).setXSpeed(anyShort());
        verify(player, never()).setYSpeed(anyShort());
    }

    @Test
    void realBossCategorySurvivesWithoutSpecialSideEffects() {
        TestBoss boss = new TestBoss();
        AbstractPlayableSprite sidekick = mock(AbstractPlayableSprite.class);
        ((TestObjectServices) objectManager.services()).withSidekicks(List.of(sidekick));
        boss.setServices(objectManager.services());
        boss.snapshotPreUpdatePosition();
        objectManager.initialCollisionResponseList().captureCompletedBuild(List.of(boss));

        objectManager.poweredAttacks().apply(player);

        assertEquals(3, boss.getCollisionProperty());
        assertEquals(0, boss.attackCount);
        verify(sidekick, never()).setRolling(anyBoolean());
        verify(sidekick, never()).setAir(anyBoolean());
    }

    @Test
    void normalEnemyUsesPoweredChainScoringContract() {
        ObjectInstance ordinary = responder(0x08, 0, PoweredScreenAttackable.class);
        objectManager.initialCollisionResponseList().captureCompletedBuild(List.of(ordinary));

        objectManager.poweredAttacks().apply(player);

        verify((PoweredScreenAttackable) ordinary).onPoweredScreenAttack(player);
        verify((TouchResponseAttackable) ordinary, never()).onPlayerAttack(any(), any());
    }

    @Test
    void poweredChainAwardsTenThousandFromTheSixteenthKill() {
        AbstractPlayableSprite scoringPlayer =
                mock(AbstractPlayableSprite.class, CALLS_REAL_METHODS);

        assertEquals(100, PoweredBadnikScoring.incrementChain(scoringPlayer));
        assertEquals(200, PoweredBadnikScoring.incrementChain(scoringPlayer));
        assertEquals(500, PoweredBadnikScoring.incrementChain(scoringPlayer));
        for (int kill = 4; kill < 16; kill++) {
            assertEquals(1000, PoweredBadnikScoring.incrementChain(scoringPlayer));
        }
        assertEquals(10_000, PoweredBadnikScoring.incrementChain(scoringPlayer));
        assertEquals(10_000, PoweredBadnikScoring.incrementChain(scoringPlayer));
    }

    @Test
    void usesFrozenCollisionResponseListAndSkipsOffScreenResponders() {
        AbstractObjectInstance frozen = abstractResponder(0x08, 0, true);
        AbstractObjectInstance current = abstractResponder(0x08, 0, true);
        AbstractObjectInstance offScreen = abstractResponder(0x08, 0, false);
        objectManager.initialCollisionResponseList()
                .captureCompletedBuild(List.of(frozen, offScreen));
        objectManager.initialCollisionResponseList().resetCurrentBuild();
        objectManager.initialCollisionResponseList().addToCurrentBuild(current);

        objectManager.poweredAttacks().apply(player);

        verify((PoweredScreenAttackable) frozen).onPoweredScreenAttack(player);
        verify((PoweredScreenAttackable) current, never()).onPoweredScreenAttack(any());
        verify((PoweredScreenAttackable) offScreen, never()).onPoweredScreenAttack(any());
    }

    @Test
    void bouncesOnlyShieldReactiveHarmfulProjectiles() {
        ObjectInstance reactive = responder(0x88, 0);
        ObjectInstance inert = responder(0x89, 0);
        when(((TouchResponseProvider) reactive).getShieldReactionFlags()).thenReturn(8);
        objectManager.initialCollisionResponseList()
                .captureCompletedBuild(List.of(reactive, inert));

        objectManager.poweredAttacks().apply(player);

        verify((TouchResponseProvider) reactive).onShieldDeflect(player);
        verify((TouchResponseProvider) inert, never()).onShieldDeflect(any());
    }

    @Test
    void specialResponseOrsPropertyAndPutsNativeP2IntoAirborneAnimation() {
        ObjectInstance special = specialResponder(0xC8, 1);
        AbstractPlayableSprite sidekick = mock(AbstractPlayableSprite.class);
        ((TestObjectServices) objectManager.services()).withSidekicks(List.of(sidekick));
        objectManager.initialCollisionResponseList()
                .captureCompletedBuild(List.of(special));

        objectManager.poweredAttacks().apply(player);

        verify((PoweredScreenAttackSpecial) special).orCollisionProperty(3);
        verify(sidekick, never()).setRolling(anyBoolean());
        verify(sidekick).setAir(true);
        verify(sidekick).setAnimationId(2);
    }

    @Test
    void productionS3kSpecialTargetOwnsCollisionPropertyMutation() {
        FbzExitDoorInstance target = new FbzExitDoorInstance(
                new ObjectSpawn(160, 112, 0xCE, 0, 0, false, 0));

        ((PoweredScreenAttackSpecial) target).orCollisionProperty(3);

        assertEquals(3, target.getCollisionProperty());
    }

    @Test
    void knucklesAloneSpecialCopiesTargetPositionIntoNativeP2() {
        ObjectInstance special = specialResponder(0xD7, 0);
        AbstractPlayableSprite sidekick = mock(AbstractPlayableSprite.class);
        Knuckles knuckles = mock(Knuckles.class);
        when(special.getX()).thenReturn(0x456);
        when(special.getY()).thenReturn(0x789);
        ((TestObjectServices) objectManager.services()).withSidekicks(List.of(sidekick));
        objectManager.initialCollisionResponseList().captureCompletedBuild(List.of(special));

        objectManager.poweredAttacks().apply(knuckles);

        verify(sidekick).shiftX(0x456);
        verify(sidekick).setCentreYPreserveSubpixel((short) 0x789);
        verify(sidekick).setAnimationId(2);
        verify(sidekick, never()).setRolling(anyBoolean());
        verify(sidekick).setAir(true);
    }

    private static ObjectInstance responder(int flags, int property) {
        return responder(flags, property, PoweredScreenAttackable.class);
    }

    private static ObjectInstance responder(int flags, int property, Class<?>... extra) {
        Class<?>[] interfaces = new Class<?>[extra.length + 2];
        interfaces[0] = TouchResponseProvider.class;
        interfaces[1] = TouchResponseAttackable.class;
        System.arraycopy(extra, 0, interfaces, 2, extra.length);
        ObjectInstance instance = mock(ObjectInstance.class, withSettings()
                .extraInterfaces(interfaces));
        when(instance.publishesTouchResponseListEntryThisFrame()).thenReturn(true);
        when(((TouchResponseProvider) instance).getCollisionFlags()).thenReturn(flags);
        when(((TouchResponseProvider) instance).getCollisionProperty()).thenReturn(property);
        when(((TouchResponseProvider) instance).usesS3kTouchSpecialPropertyResponse())
                .thenReturn(true);
        return instance;
    }

    private static AbstractObjectInstance abstractResponder(
            int flags, int property, boolean onScreen) {
        AbstractObjectInstance instance = mock(AbstractObjectInstance.class, withSettings()
                .extraInterfaces(TouchResponseProvider.class, TouchResponseAttackable.class,
                        PoweredScreenAttackable.class));
        when(instance.publishesTouchResponseListEntryThisFrame()).thenReturn(true);
        when(instance.isOnScreenForTouch()).thenReturn(onScreen);
        when(((TouchResponseProvider) instance).getCollisionFlags()).thenReturn(flags);
        when(((TouchResponseProvider) instance).getCollisionProperty()).thenReturn(property);
        return instance;
    }

    private static ObjectInstance specialResponder(int flags, int property) {
        ObjectInstance instance = mock(ObjectInstance.class, withSettings()
                .extraInterfaces(TouchResponseProvider.class, PoweredScreenAttackSpecial.class));
        when(instance.publishesTouchResponseListEntryThisFrame()).thenReturn(true);
        when(((TouchResponseProvider) instance).getCollisionFlags()).thenReturn(flags);
        when(((TouchResponseProvider) instance).getCollisionProperty()).thenReturn(property);
        when(((TouchResponseProvider) instance).usesS3kTouchSpecialPropertyResponse())
                .thenReturn(true);
        TouchResponseProfile profile =
                TouchResponseProfile.fromProvider((TouchResponseProvider) instance);
        when(((TouchResponseProvider) instance).getTouchResponseProfile())
                .thenReturn(profile);
        return instance;
    }

    private static ObjectRegistry emptyRegistry() {
        ObjectRegistry registry = mock(ObjectRegistry.class);
        when(registry.getPrimaryName(anyInt())).thenReturn("Test");
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_1);
        when(registry.objectWindowingStrategy()).thenReturn(ObjectWindowingStrategy.LEGACY);
        return registry;
    }

    private static final class TestBoss extends AbstractBossInstance {
        private int attackCount;

        private TestBoss() {
            super(new ObjectSpawn(160, 112, 1, 0, 0, false, 0), "TestBoss");
        }

        @Override protected void initializeBossState() { }
        @Override protected void updateBossLogic(int frameCounter, PlayableEntity player) { }
        @Override protected int getInitialHitCount() { return 3; }
        @Override protected void onHitTaken(int remainingHits) { attackCount++; }
        @Override protected int getCollisionSizeIndex() { return 8; }
        @Override protected int getBossExplosionSfxId() { return 0; }
        @Override protected int getBossHitSfxId() { return 0; }
        @Override public boolean isOnScreenForTouch() { return true; }
        @Override public void appendRenderCommands(
                List<com.openggf.graphics.GLCommand> commands) { }
    }
}
