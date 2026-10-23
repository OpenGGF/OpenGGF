package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FixBugs=0, locked-on SK table $95522 -> Obj_EggRobo $91518.
 * Fresh Level initialization / Delete_Referenced_Sprite clear SST byte $2B;
 * CreateChild10_NormalAdjusted ($84328), SetUp_ObjAttributes3 ($84040), and
 * loc_91756..loc_917BE never write it. AllocateObjectAfterCurrent only scans slots.
 * Thus literal zero is the native descriptor oracle, independently of ordinary hurt.
 * Pairing, fixture pose and manual routine entries are declared component inlets,
 * as in TestSszEggRoboVerticalFlip; this does not identify a cold-route shot/contact.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboShotShieldReaction {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void nativeZeroShieldDescriptorSurvivesBirthChargeArmingAndMotion(int flags) {
        var fixture = scene();
        var shot = fire(fixture, flags);
        assertNativeDescriptor(shot);
        assertEquals(0, shot.getCollisionFlags());
        int birthX = shot.getX();
        for (int entry = 0; entry < 32; entry++) {
            shot.update(entry, null);
            assertNativeDescriptor(shot);
            assertEquals(entry == 31 ? 0x9C : 0, shot.getCollisionFlags());
            assertEquals(birthX, shot.getX(), "arming entry has no MoveSprite2");
        }
        shot.update(32, null);
        assertNativeDescriptor(shot);
        assertEquals(birthX + (flags == 0 ? -8 : 8), shot.getX());
        assertEquals(0x9C, shot.getCollisionFlags());
    }

    @ParameterizedTest
    @CsvSource({"0,BASIC", "0,FIRE", "0,LIGHTNING", "0,BUBBLE",
            "1,BASIC", "1,FIRE", "1,LIGHTNING", "1,BUBBLE"})
    void everyRealShieldLeavesShieldOnlyContactAloneThenAbsorbsOrdinaryHurt(
            int flags, ShieldType shield) throws Exception {
        var fixture = scene();
        var shot = movingPublishedShot(fixture, flags);
        var player = fixture.sprite();
        player.setRingCount(10);
        player.giveShield(shield);
        int x = shot.getX(), y = shot.getY();
        int velocity = flags == 0 ? -0x800 : 0x800;

        // Touch_Sizes[$1C] at $FF9A is 24x4, not render half-width32.
        assertArrayEquals(new byte[] {24, 4},
                TestEnvironment.objectServices().rom().readBytes(0xFF9A, 2));
        pose(fixture, x + (flags == 0 ? -40 : 40), y);
        assertNoCompetingShieldOverlap(fixture, shot);
        manager().runTouchResponsesForPlayer(player, 100, true);
        assertFalse(player.isHurt(), "gap40 is inside shield48+shot48 but outside body16+shot48");
        assertTrue(player.hasShield());
        assertEquals(shield, player.getShieldType());
        assertEquals(10, player.getRingCount());
        assertShotUnchanged(shot, x, y, velocity);

        pose(fixture, x + (flags == 0 ? -16 : 16), y);
        assertNoCompetingShieldOverlap(fixture, shot);
        manager().runTouchResponsesForPlayer(player, 101, true);
        assertTrue(player.isHurt(), "Touch_ChkHurt / HurtCharacter: no elemental immunity");
        assertFalse(player.getDead());
        assertFalse(player.hasShield());
        assertEquals(10, player.getRingCount(), "ordinary shield loss retains rings");
        assertShotUnchanged(shot, x, y, velocity);
        shot.update(102, null);
        assertEquals(x + (flags == 0 ? -8 : 8), shot.getX(), "no radial reflection");
        assertEquals(y, shot.getY());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void hyperTouchLeavesTheActualPublishedHarmfulShotUnchanged(int flags) {
        var fixture = scene();
        var shot = movingPublishedShot(fixture, flags);
        int x = shot.getX(), y = shot.getY();
        manager().poweredAttacks().apply(fixture.sprite());
        assertShotUnchanged(shot, x, y, flags == 0 ? -0x800 : 0x800);
        shot.update(103, null);
        assertEquals(x + (flags == 0 ? -8 : 8), shot.getX());
        assertEquals(y, shot.getY());
    }

    private static void assertNativeDescriptor(EggRoboShotInstance shot) {
        assertAll("native shield_reaction=$00, not an inferred hurt outcome",
                () -> assertEquals(0, shot.getShieldReactionFlags()),
                () -> assertEquals(0, shot.getTouchResponseProfile().shieldReactionFlags()),
                () -> assertEquals(TouchShieldDeflectCapability.NONE,
                        shot.getTouchResponseProfile().shieldDeflectCapability()));
    }

    private static void assertShotUnchanged(EggRoboShotInstance shot, int x, int y, int velocity) {
        assertEquals(x, shot.getX());
        assertEquals(y, shot.getY());
        assertEquals(velocity, shot.xVelocityForTest());
        assertEquals(0x9C, shot.getCollisionFlags());
        assertFalse(shot.isDestroyed());
    }

    private static EggRoboShotInstance movingPublishedShot(HeadlessTestFixture fixture, int flags) {
        var shot = fire(fixture, flags);
        for (int entry = 0; entry < 32 + 4; entry++) shot.update(entry, null);
        int x = shot.getX();
        // Real ordinary dispatch builds the completed list; no private list injection.
        manager().update(0x100, null, List.of(), 90, false);
        assertEquals(x + (flags == 0 ? -8 : 8), shot.getX());
        manager().refreshPostCameraRenderState();
        manager().snapshotTouchResponseState(true);
        assertTrue(shot.isOnScreenForTouch());
        assertTrue(manager().poweredAttacks().targetReadView().contains(shot));
        return shot;
    }

    private static void assertNoCompetingShieldOverlap(HeadlessTestFixture fixture,
                                                      EggRoboShotInstance shot) throws Exception {
        var table = new TouchResponseTable(TestEnvironment.objectServices().romReader(), 0xFF62, 58);
        for (var object : manager().poweredAttacks().targetReadView()) {
            if (object == shot || !(object instanceof TouchResponseProvider provider)) continue;
            int flags = provider.getCollisionFlags();
            if (flags == 0) continue;
            int index = flags & 0x3F;
            assertTrue(Math.abs(object.getCollisionX() - fixture.sprite().getCentreX())
                            > 24 + table.getWidthRadius(index)
                    || Math.abs(object.getCollisionY() - fixture.sprite().getCentreY())
                            > 24 + table.getHeightRadius(index),
                    () -> "competing responder near controlled Shot contact: " + object);
        }
    }

    private static HeadlessTestFixture scene() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        assertInstanceOf(Sonic.class, fixture.sprite());
        assertEquals(320, fixture.camera().getWidth());
        assertFalse(CrossGameFeatureProvider.isActive());
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0xB80);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        ((SszZoneRuntimeState) TestEnvironment.objectServices().zoneRuntimeState())
                .markEggRoboFlyByPassed(0);
        return fixture;
    }

    private static EggRoboShotInstance fire(HeadlessTestFixture fixture, int flags) {
        var body = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, flags, false, 0));
        body.setServices(TestEnvironment.objectServices());
        manager().addDynamicObject(body);
        body.update(1, null);
        body.refreshPostCameraRenderState();
        body.update(2, null);
        var guns = List.copyOf(manager().activeObjectsOfType(EggRoboGunArmChildInstance.class));
        var jets = List.copyOf(manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class));
        body.update(3, null);
        var bornGuns = manager().activeObjectsOfType(EggRoboGunArmChildInstance.class).stream()
                .filter(child -> !guns.contains(child)).toList();
        var bornJets = manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class).stream()
                .filter(child -> !jets.contains(child)).toList();
        assertEquals(1, bornGuns.size());
        assertEquals(1, bornJets.size());
        var gun = bornGuns.getFirst();
        var jet = bornJets.getFirst();
        gun.update(0, null);
        jet.update(0, null);
        pose(fixture, flags == 0 ? 0x100 : 0x300, 0xC00);
        var shots = List.copyOf(manager().activeObjectsOfType(EggRoboShotInstance.class));
        body.update(8, fixture.sprite());
        assertTrue(body.gunArmed(), "public P1 alignment supplies the production request");
        gun.update(8, null);
        jet.update(8, null);
        assertEquals(95, gun.cooldownForTest());
        var born = manager().activeObjectsOfType(EggRoboShotInstance.class).stream()
                .filter(child -> !shots.contains(child)).toList();
        assertEquals(1, born.size());
        assertTrue(gun.getSlotIndex() < born.getFirst().getSlotIndex());
        return born.getFirst();
    }

    private static void pose(HeadlessTestFixture fixture, int x, int y) {
        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(), x);
        NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(), y);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
    }

    private static ObjectManager manager() { return TestEnvironment.objectServices().objectManager(); }
}
