package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent short native boundaries, rather than waiting behind a DEZ route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingRescue {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(){
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        GameServices.camera().refreshViewportDimensions(config);
        assertEquals(320, GameServices.camera().getWidth());
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        return HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
    }
    private EndingRescueDirectorObjectInstance owner(){return GameServices.level().getObjectManager()
            .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst();}
    @Test void scriptStatusClearPreservesRadiiVelocityAndWaterPhysics(){
        var f=boot();var p=f.sprite();p.setDirection(com.openggf.physics.Direction.LEFT);
        p.setAir(true);p.setRolling(true);p.setOnObject(true);p.setRollingJump(true);p.setPushing(true);
        p.setInWater(true);p.setPreventTailsRespawn(true);p.setXSpeed((short)0x345);p.setYSpeed((short)-0x234);
        int[] before={p.getXRadius(),p.getYRadius(),p.getWidth(),p.getHeight(),p.getEffectiveRunAccel()};
        com.openggf.sprites.playable.PlayableSpriteInternalAccess.clearScriptedNativeStatus(p);
        assertEquals(com.openggf.physics.Direction.RIGHT,p.getDirection());assertFalse(p.getAir());
        assertFalse(p.getRolling());assertFalse(p.isOnObject());assertFalse(p.getRollingJump());
        assertFalse(p.getPushing());assertFalse(p.isInWater());assertFalse(p.isPreventTailsRespawn());
        assertArrayEquals(before,new int[]{p.getXRadius(),p.getYRadius(),p.getWidth(),p.getHeight(),p.getEffectiveRunAccel()});
        assertEquals(0x345,p.getXSpeed());assertEquals(-0x234,p.getYSpeed());
    }
    @Test void endingInitCreatesNativeFixedRolesAndHalfPixelScrollThresholds(){
        var f=boot();var o=owner();o.update(0,f.sprite());
        assertEquals(2,o.routine());assertEquals(0x100,o.getX());assertEquals(0xE0,o.getY());
        var actors=GameServices.level().getObjectManager().activeObjectsOfType(EndingRescueDirectorObjectInstance.Actor.class);
        // Dynamic_object_RAM is Object_RAM+3*object_size; loc_5D96A uses offsets5/45.
        assertEquals(2,actors.size());assertTrue(actors.stream().anyMatch(a->a.getSlotIndex()==8));
        assertTrue(actors.stream().anyMatch(a->a.getSlotIndex()==48));
        for(int i=0;i<255;i++)o.update(i,f.sprite());assertEquals(2,o.routine());
        o.update(255,f.sprite());assertEquals(4,o.routine());
        for(int i=0;i<255;i++)o.update(i,f.sprite());assertEquals(4,o.routine());
        o.update(255,f.sprite());assertEquals(6,o.routine());assertTrue(o.planeReleased());
        assertEquals(0x1000000,((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).screen().scrollYFixed);
    }
    @Test void nativeScreenAndManagedRescueRolesRestoreAndReplayAcrossPlaneCatch(){
        var f=boot();for(int i=0;i<560;i++)f.stepFrame(false,false,false,false,false);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int i=0;i<180;i++)f.stepFrame(false,false,false,false,false);
        int expectedRoles=GameServices.level().getObjectManager().getActiveObjects().size();int[] positions={owner().routine(),owner().getX(),owner().getY()};
        byte[] ram=((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes();
        registry.restore(saved);
        for(int i=0;i<180;i++)f.stepFrame(false,false,false,false,false);
        assertArrayEquals(positions,new int[]{owner().routine(),owner().getX(),owner().getY()});
        assertArrayEquals(ram,((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes());
        assertEquals(expectedRoles,GameServices.level().getObjectManager().getActiveObjects().size());
    }
    @Test void tornadoSubspritesReadNativeNoseCycleAndSignedOffsetsFromRom(){
        var f=boot();owner().update(0,f.sprite());
        var plane=GameServices.level().getObjectManager()
                .activeObjectsOfType(EndingRescueDirectorObjectInstance.Actor.class).stream()
                .filter(a->a.getSlotIndex()==8).findFirst().orElseThrow();
        plane.update(0,f.sprite());var parts=plane.subsprites();
        assertEquals(3,parts.get(0).frame());
        assertEquals(plane.getX()+0x34,parts.get(0).x());assertEquals(plane.getY()+4,parts.get(0).y());
        assertEquals(6,parts.get(1).frame());
        assertEquals(plane.getX()+0x18,parts.get(1).x());assertEquals(plane.getY()+0x18,parts.get(1).y());
        plane.update(1,f.sprite());parts=plane.subsprites();
        assertEquals(4,parts.get(0).frame());assertEquals(7,parts.get(1).frame());
    }
    @Test void nativePlaneRegistersItsResidentVramRangeForLaterModuleDma(){
        var f=boot();owner().update(0,f.sprite());
        var plane=GameServices.level().getObjectManager()
                .activeObjectsOfType(EndingRescueDirectorObjectInstance.Actor.class).stream()
                .filter(a->a.getSlotIndex()==8).findFirst().orElseThrow();
        plane.update(0,f.sprite());plane.appendRenderCommands(new java.util.ArrayList<>());
        var provider=(com.openggf.game.sonic3k.Sonic3kObjectArtProvider)
                GameServices.module().getObjectArtProvider();
        var affected=provider.getAffectedRendererKeys(java.util.List.of(
                new com.openggf.game.sonic3k.Sonic3kPlcLoader.TileRange(0x1E3,0x8B)));
        assertTrue(affected.stream().anyMatch(key->key.startsWith("ending-tornado:")),
                "module DMA must refresh the virtual atlas, even when resident Pattern identities stay equal");
    }
    @Test void restoringBeforeFirstObjectPassPreservesUnclaimedEntryPlayerState(){
        var f=boot();assertEquals(0,owner().routine());
        assertEquals(96,f.sprite().getCentreX());assertEquals(300,f.sprite().getCentreY());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int i=0;i<600;i++)f.stepFrame(false,false,false,false,false);
        registry.restore(saved);var p=GameServices.sprites().getMainPlayable();
        assertEquals(0,owner().routine());assertEquals(96,p.getCentreX());assertEquals(300,p.getCentreY());
        assertEquals(p.getCentreX(),p.getRenderCentreX());assertEquals(p.getCentreY(),p.getRenderCentreY());
        var differences=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                "sprites",saved.get("sprites"),registry.capture().get("sprites"));
        assertTrue(differences.isEmpty(),()->"pre-entry player restoration: "+differences);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> configuredRosters() {
        var rows = new java.util.ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for (int width : new int[]{320, 352, 400, 528, 800}) {
            for (String main : new String[]{"sonic", "tails"}) {
                for (String followers : new String[]{main, "sonic,tails,knuckles",
                        "sonic,tails,knuckles,sonic,tails,knuckles,sonic"}) {
                    rows.add(org.junit.jupiter.params.provider.Arguments.of(width, main, followers));
                }
            }
        }
        return rows.stream();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("configuredRosters")
    void nativeEntryRetiresConfiguredFollowersAndReplaysTheRosterBoundary(
            int width, String main, String followers) throws java.io.IOException {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        GameServices.camera().refreshViewportDimensions(config);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, followers);
        var f = HeadlessTestFixture.builder().withZoneAndAct(13, 1)
                .withFreshLevelStartLifecycle().build();
        assertEquals(width, GameServices.camera().getWidth());
        var keys = java.util.Arrays.stream(followers.split(","))
                .map(com.openggf.game.CharacterKey::parsePersisted).toList();
        assertEquals(keys, GameServices.sprites().getSidekicks().stream()
                .map(com.openggf.sprites.playable.AbstractPlayableSprite::characterKey).toList());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        f.stepFrame(false, false, false, false, false);
        assertEquals(2, owner().routine());
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        var p1 = GameServices.sprites().getMainPlayable();
        assertEquals(com.openggf.game.CharacterKey.parsePersisted(main), p1.characterKey());
        assertSame(p1, GameServices.camera().getFocusedSprite());
        assertTrue(p1.isObjectControlled());
        assertEquals(0x100, p1.getCentreX());
        assertEquals(0xE0, p1.getCentreY());
        var entered = registry.capture();
        for (int n = 0; n < 30; n++) f.stepFrame(false, false, false, false, false);
        var advanced = registry.capture();
        registry.restore(before);
        sameWorld(before, registry.capture(), "configured roster before entry");
        assertEquals(keys, GameServices.sprites().getSidekicks().stream()
                .map(com.openggf.sprites.playable.AbstractPlayableSprite::characterKey).toList());
        f.stepFrame(false, false, false, false, false);
        sameWorld(entered, registry.capture(), "configured roster retirement replay");
        for (int n = 0; n < 30; n++) f.stepFrame(false, false, false, false, false);
        sameWorld(advanced, registry.capture(), "configured roster active replay");
        // A level-to-level load preserves the surviving native roster. A fresh
        // game startup constructs the configured team before loading its level.
        var fresh = HeadlessTestFixture.builder().withZoneAndAct(0, 0)
                .withFreshLevelStartLifecycle().build();
        assertNotSame(p1, fresh.sprite(), "fresh startup creates a new main sprite");
        assertEquals(width, GameServices.camera().getWidth());
        assertEquals(keys, GameServices.sprites().getSidekicks().stream()
                .map(com.openggf.sprites.playable.AbstractPlayableSprite::characterKey).toList());
        p1 = GameServices.sprites().getMainPlayable();
        assertEquals(p1.getCentreX(), p1.getRenderCentreX());
        assertEquals(p1.getCentreY(), p1.getRenderCentreY());
    }

    private static void sameWorld(com.openggf.game.rewind.CompositeSnapshot expected,
                                  com.openggf.game.rewind.CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
