package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Raw object poses survive Knuckles' native glide reset, independently of long routes. */
@RequiresRom(SonicGame.SONIC_3K)
class TestKnucklesObjectControlAnimation {
    private final EnumMap<SonicConfiguration,Object> saved=new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;
    @BeforeEach void setup() {
        config=SonicConfigurationService.getInstance();
        for(var k:SonicConfiguration.values())if(config.hasSessionOverride(k))saved.put(k,config.getConfigValue(k));
        config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() {
        CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void glidingKnucklesCaptureKeepsNativeWireMappingOwnership(int width) {
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width==320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        int x = 0x18F4;
        int y = 0x240;
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 1)
                .startPosition((short) (x + 128), (short) (y + 0x40)).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        for (int i = 0; i < 3; i++) fixture.stepFrame(false, false, false, false, false);
        var wire = findWire(x);
        var player = fixture.sprite();
        assertInstanceOf(com.openggf.sprites.playable.Knuckles.class, player);
        NativePositionOps.writeXPosPreserveSubpixel(player, wire.handleX() - 8);
        NativePositionOps.writeYPosPreserveSubpixel(player, wire.handleY() + 8);
        player.setAir(true);
        player.setDoubleJumpFlag(1);
        player.setDoubleJumpProperty((byte) 0x80);
        player.setAnimationId(0x20);
        player.setForcedAnimationId(0x20);
        player.setAbilityMappingFrameControl(true);
        player.setMappingFrame(0xC0);
        player.setXSpeed((short) 0x400);
        player.setYSpeed((short) 0x80);
        player.setGSpeed((short) 0x400);
        fixture.stepFrame(false, false, false, true, true);
        assertTrue(findWire(x).isPlayerHeld(player), "real handle captures incoming glide");
        var before = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepFrame(false, false, false, false, false);
        assertEquals(0, player.getDoubleJumpFlag(), "held Knuckles has no active glide");
        // loc_4B13E writes object_control=3: bit1 skips Animate_Knuckles while
        // loc_4B04A publishes the wire's raw held mapping, not the glide owner.
        assertTrue(player.isObjectMappingFrameControl(), "wire retains raw mapping ownership");
        assertEquals(0x92, player.getMappingFrame(), "first wire-held pose");
        replay(fixture, before, false, "glide-to-wire ownership");
        fixture.stepFrame(false, false, false, false, false);
        assertTrue(player.isObjectMappingFrameControl(), "ownership survives the next player pass");
        assertTrue(findWire(x).isPlayerHeld(player));
    }

    @ParameterizedTest @ValueSource(ints={320,800})
    void glidingKnucklesPoleRetainsRawMappingOwnership(int width) {
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width==320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        int x=0xA08, y=0x1E8;
        var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(4,0)
                .startPosition((short)(x+128),(short)(y-40)).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        for(int i=0;i<3;i++) fixture.stepFrame(false,false,false,false,false);
        var player=fixture.sprite();
        assertInstanceOf(com.openggf.sprites.playable.Knuckles.class,player);
        NativePositionOps.writeXPosPreserveSubpixel(player,x-8);
        NativePositionOps.writeYPosPreserveSubpixel(player,y-40);
        player.setAir(true);player.setDoubleJumpFlag(1);player.setDoubleJumpProperty((byte)0x80);
        player.setAnimationId(0x20);player.setForcedAnimationId(0x20);
        player.setAbilityMappingFrameControl(true);player.setMappingFrame(0xC0);
        player.setXSpeed((short)0x400);player.setYSpeed((short)0x80);player.setGSpeed((short)0x400);
        var registry=fixture.gameplayMode().getRewindRegistry();
        var incomingGlide=registry.capture();
        fixture.stepFrame(false,false,false,true,true);
        var captured=registry.capture();
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(incomingGlide);same(incomingGlide,registry.capture(),"incoming ability owner restore");
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,""));
        fixture.stepFrame(false,false,false,true,true);
        same(captured,registry.capture(),"glide-to-pole capture replay");
        assertTrue(player.isObjectControlSuppressesMovement(), "placed pole captures glide");
        assertEquals(1,player.getDoubleJumpFlag(), "capture leaves native glide byte until player pass");
        assertTrue(player.isObjectMappingFrameControl());
        var before=fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepFrame(false,false,false,false,false);
        assertEquals(0,player.getDoubleJumpFlag());
        assertTrue(player.isObjectMappingFrameControl(),"pole retains raw mapping control after glide reset");
        assertEquals(0x61,player.getMappingFrame(),"sub_3C010 raw frame at angle E8");
        assertEquals(0,player.getAnimationId(),"pole owns the native animation byte");
        assertEquals(-1,player.getForcedAnimationId(),"old glide override relinquishes ownership");
        replay(fixture,before,false,"glide-to-pole");
    }
    private static SozRapelWireObjectInstance findWire(int x) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(SozRapelWireObjectInstance.class::isInstance).map(SozRapelWireObjectInstance.class::cast)
                .filter(o->o.getSpawn().x()==x).findFirst().orElseThrow();
    }
    private static void replay(HeadlessTestFixture fixture,CompositeSnapshot before,boolean jump,String label) {
        var registry=fixture.gameplayMode().getRewindRegistry();var after=registry.capture();
        // Exercise identity relinking, not only the in-place object fast path.
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(before);same(before,registry.capture(),label+" restore");
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,""));
        fixture.stepFrame(false,false,false,false,jump);same(after,registry.capture(),label+" forward replay");
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(String key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),
                ()->label+" "+key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
}
