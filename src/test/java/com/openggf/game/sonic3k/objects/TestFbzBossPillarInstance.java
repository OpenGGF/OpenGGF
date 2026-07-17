package com.openggf.game.sonic3k.objects;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.events.FbzObjectEventBridge;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.animation.SpriteAnimationSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestFbzBossPillarInstance {
    @AfterEach
    void clearCameraBounds() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 0, 0, 0);
    }

    @Test
    void endpointKeepsSignedAnchorNativeExtentsAndTwoChildSpriteOrder() {
        RecordingRenderer renderer = new RecordingRenderer();
        RecordingBridge bridge = new RecordingBridge(0x45C, 0x5D0);
        FbzBossPillarInstance pillar = new FbzBossPillarInstance();
        pillar.setServices(services(bridge, renderer, null, List.of()));

        pillar.update(0, null);
        AbstractObjectInstance.updateCameraBounds(0x3200, 0x3C, 0x3340, 0x11C, 0);
        pillar.appendRenderCommands(new ArrayList<>());

        assertEquals(0x323C, pillar.getX());
        assertEquals(-0x50, pillar.getY(), "native y_pos word must remain sign-extended at the endpoint");
        assertEquals(0xFFB0, pillar.getSpawn().y(), "ObjectSpawn stores the same signed word in canonical unsigned form");
        assertEquals(0x20, pillar.getOnScreenHalfWidth());
        assertEquals(0xFF, pillar.getOnScreenHalfHeight());
        assertTrue(pillar.isWithinSolidContactBounds());
        assertEquals(List.of(
                new DrawCall(0, 0x323C, 0x30, false, false),
                new DrawCall(0, 0x323C, -0xD0, false, false)), renderer.calls);
    }

    @Test
    void positiveObjectControlParticipatesButSignedBitSevenControlIsRejected() {
        PlayableEntity positive = player(0x2DDF, 0x600, false);
        RecordingBridge bridge = new RecordingBridge(0, 0);
        FbzBossPillarInstance acceptsPositive = new FbzBossPillarInstance();
        acceptsPositive.setServices(services(bridge, null, positive, List.of()));
        acceptsPositive.update(0, positive);
        assertEquals(0x578, acceptsPositive.getY());

        PlayableEntity signed = player(0x2DDF, 0x600, true);
        FbzBossPillarInstance rejectsSigned = new FbzBossPillarInstance();
        rejectsSigned.setServices(services(bridge, null, signed, List.of()));
        rejectsSigned.update(0, signed);
        assertEquals(0x580, rejectsSigned.getY());

        assertTrue(acceptsPositive.allowsObjectControlledSolidContacts());
        assertTrue(acceptsPositive.rejectsBit7ObjectControlSideContact(positive));
        assertTrue(acceptsPositive.rejectsBit7ObjectControlNewSolidContact(positive));
    }

    @Test
    void sidekickOnlyOccupancyUsesTheExtendedAllPlayerPolicy() {
        PlayableEntity sidekick = player(0x2DDF, 0x600, false);
        FbzBossPillarInstance pillar = new FbzBossPillarInstance();
        pillar.setServices(services(new RecordingBridge(0, 0), null, null, List.of(sidekick)));

        pillar.update(0, null);

        assertEquals(0x578, pillar.getY());
    }

    @Test
    void liveSolidContactIncludesExactRightEdgeForPositiveControlButRejectsBitSeven() {
        RecordingBridge bridge = new RecordingBridge(0, 0);
        FbzBossPillarInstance pillar = new FbzBossPillarInstance();
        TestPlayableSprite positiveControl = solidPlayerAt(
                0x2DE0 + pillar.getSolidParams().halfWidth(), 0x580);
        positiveControl.setObjectControlled(true);
        positiveControl.setObjectControlAllowsCpu(true);
        ObjectManager manager = managerWithPillar(pillar, bridge, positiveControl);
        pillar.snapshotPreUpdatePosition();
        setPillarCameraBounds();

        manager.updateSolidContacts(positiveControl);

        assertTrue(positiveControl.getPushing(),
                "SolidObjectFull's bhi gate includes relX == d1*2 for positive object_control");

        TestPlayableSprite bitSevenControl = solidPlayerAt(
                0x2DE0 + pillar.getSolidParams().halfWidth(), 0x580);
        bitSevenControl.setObjectControlled(true);
        manager.updateSolidContacts(bitSevenControl);

        assertFalse(bitSevenControl.getPushing(),
                "SolidObject_cont rejects signed bit-7 object_control before side classification");
    }

    @Test
    void liveSolidContactConsumesAirborneStaleStandingBitWithoutRelanding() {
        RecordingBridge bridge = new RecordingBridge(0, 0);
        FbzBossPillarInstance pillar = new FbzBossPillarInstance();
        TestPlayableSprite player = solidPlayerAt(0x2DE0,
                0x580 - 4 - pillar.getSolidParams().airHalfHeight() - 19 + 8);
        ObjectManager manager = managerWithPillar(pillar, bridge, player);
        setPillarCameraBounds();
        manager.forceRidingObjectForBootstrap(player, pillar);
        manager.updateSolidContacts(player);
        manager.clearRidingObject(player);
        player.setAir(true);
        player.setOnObject(false);
        player.setYSpeed((short) 0x38);

        manager.processImmediateInlineSolidCheckpoint(pillar, player, List.of());

        assertTrue(player.getAir(),
                "SolidObjectFull_1P must return after consuming an airborne stale standing bit");
        assertFalse(player.isOnObject());
        assertFalse(manager.isRidingObject(player));
        assertEquals(0x38, player.getYSpeed() & 0xFFFF);
        assertTrue(pillar.airborneStaleStandingBitReturnsNoContact(player),
                "Obj_FBZBossPillar calls SolidObjectFull and must select its stale-standing early return");
    }

    @Test
    void liveContinuedRideUsesCurrentPillarXAsD4AndAppliesZeroHorizontalCarry() {
        RecordingBridge bridge = new RecordingBridge(0, 0);
        FbzBossPillarInstance pillar = new FbzBossPillarInstance();
        TestPlayableSprite player = solidPlayerAt(0x2DE0,
                0x580 - pillar.getSolidParams().groundHalfHeight() - 19);
        ObjectManager manager = managerWithPillar(pillar, bridge, player);
        setPillarCameraBounds();
        manager.forceRidingObjectForBootstrap(player, pillar);
        player.setAir(false);
        player.setOnObject(true);
        int initialPlayerX = player.getCentreX();
        bridge.offsetX = 8;

        pillar.update(1, player);
        manager.processImmediateInlineSolidCheckpoint(pillar, player, List.of());

        assertEquals(0x2DE8, pillar.getX());
        assertEquals(initialPlayerX, player.getCentreX(),
                "pillar passes current x_pos in d4, so MvSonicOnPtfm horizontal delta is zero");
        assertTrue(manager.isRidingObject(player));
    }

    private static PlayableEntity player(int x, int y, boolean signedControl) {
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) x);
        when(player.getCentreY()).thenReturn((short) y);
        when(player.isObjectControlled()).thenReturn(true);
        when(player.isTouchResponseSuppressedByObjectControl()).thenReturn(signedControl);
        return player;
    }

    private static TestPlayableSprite solidPlayerAt(int x, int y) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setWidth(20);
        player.setHeight(38);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setAir(false);
        player.setXSpeed((short) -0x100);
        player.setGSpeed((short) -0x100);
        return player;
    }

    private static ObjectManager managerWithPillar(FbzBossPillarInstance pillar,
                                                   RecordingBridge bridge,
                                                   PlayableEntity mainPlayer) {
        ObjectRegistry registry = new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int objectId) { return "FBZBossPillar"; }
            @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
        };
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public LevelEventProvider levelEventProvider() { return bridge; }
        };
        services.withPlayerQuery(new ObjectPlayerQuery(() -> mainPlayer, List::of));
        holder[0] = new ObjectManager(List.of(), registry, 0, null, null,
                GraphicsManager.getInstance(), null, services);
        holder[0].addDynamicObject(pillar);
        return holder[0];
    }

    private static void setPillarCameraBounds() {
        AbstractObjectInstance.updateCameraBounds(0x2D00, 0x400,
                0x2D00 + 320, 0x400 + 224, 0);
    }

    private static StubObjectServices services(RecordingBridge bridge, RecordingRenderer renderer,
                                               PlayableEntity main, List<PlayableEntity> sidekicks) {
        ObjectRenderManager renderManager = renderer == null ? null
                : new ObjectRenderManager(new StubArtProvider(renderer));
        ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);
        StubObjectServices services = new StubObjectServices() {
            @Override public LevelEventProvider levelEventProvider() { return bridge; }
            @Override public ObjectRenderManager renderManager() { return renderManager; }
        };
        services.withPlayerQuery(query);
        return services;
    }

    private static final class RecordingBridge implements FbzObjectEventBridge, LevelEventProvider {
        private int offsetX;
        private final int offsetY;
        private RecordingBridge(int offsetX, int offsetY) { this.offsetX = offsetX; this.offsetY = offsetY; }
        @Override public int getBossBackgroundOffsetX() { return offsetX; }
        @Override public int getBossBackgroundOffsetY() { return offsetY; }
        @Override public void initLevel(int zone, int act) { }
        @Override public void update() { }
        @Override public void setMagneticState(Sonic3kFBZEvents.MagneticPolarity polarity, int timerPhase) { }
        @Override public void setCloudRewindId(int index, ObjectRefId id) { }
        @Override public void setCloudCleanupTerminal(boolean value) { }
        @Override public void setBossLoadPositionAdjustmentPending(boolean value) { }
        @Override public void setBossBackgroundOffsets(int x, int y) { }
        @Override public void setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode plane) { }
        @Override public void setCollisionMode(Sonic3kFBZEvents.CollisionMode collision, int x, int y) { }
        @Override public void setScreenShakeState(boolean active, int offset, int phase) { }
    }

    private record DrawCall(int frame, int x, int y, boolean hFlip, boolean vFlip) { }

    private static final class RecordingRenderer extends PatternSpriteRenderer {
        private final List<DrawCall> calls = new ArrayList<>();
        private RecordingRenderer() { super(dummySheet()); }
        @Override public boolean isReady() { return true; }
        @Override public void drawFrameIndex(int frame, int x, int y, boolean hFlip, boolean vFlip) {
            calls.add(new DrawCall(frame, x, y, hFlip, vFlip));
        }
    }

    private static final class StubArtProvider implements ObjectArtProvider {
        private final PatternSpriteRenderer renderer;
        private StubArtProvider(PatternSpriteRenderer renderer) { this.renderer = renderer; }
        @Override public void loadArtForZone(int zoneIndex) { }
        @Override public PatternSpriteRenderer getRenderer(String key) {
            return Sonic3kObjectArtKeys.FBZ_BOSS_PILLAR.equals(key) ? renderer : null;
        }
        @Override public ObjectSpriteSheet getSheet(String key) { return null; }
        @Override public SpriteAnimationSet getAnimations(String key) { return null; }
        @Override public int getZoneData(String key, int zoneIndex) { return -1; }
        @Override public Pattern[] getHudDigitPatterns() { return new Pattern[0]; }
        @Override public Pattern[] getHudTextPatterns() { return new Pattern[0]; }
        @Override public Pattern[] getHudLivesPatterns() { return new Pattern[0]; }
        @Override public Pattern[] getHudLivesNumbers() { return new Pattern[0]; }
        @Override public List<String> getRendererKeys() { return List.of(Sonic3kObjectArtKeys.FBZ_BOSS_PILLAR); }
        @Override public int ensurePatternsCached(GraphicsManager graphics, int base) { return base; }
        @Override public boolean isReady() { return true; }
    }

    private static ObjectSpriteSheet dummySheet() {
        Pattern[] patterns = {new Pattern()};
        SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false);
        return new ObjectSpriteSheet(patterns, List.of(new SpriteMappingFrame(List.of(piece))), 0, 1);
    }
}
