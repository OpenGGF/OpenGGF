package com.openggf.level.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.LrzFlameObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R123 removable SYNTHETIC component, not the hcz_3 row442 damage caller.
 * Real HCZ2 team/ROM table, a separately managed production flame, and public
 * P2 control/geometry inputs isolate touch eligibility without a mock result.
 * No parent arrival, flame timer/phase, native pointer, or recorded state is seeded.
 *
 * Native044 HurtCharacter (sonic3k.asm:21101..21136) selects loc_102E0 for
 * Player_2 in non-competition play before consulting rings. Human input does
 * not turn that same native slot into Player_1. Tails_Control masks $A0 before
 * TouchResponse (26302..26306); full bit-7 control is the rejecting control.
 * These source oracles do not identify a route hazard or prove route causality.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kNativeP2TouchHurtEligibility {
    private static final short X = 0x900;
    private static final short Y = 0x300;

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest(name = "registered native P2: cpuControlled={0}")
    @ValueSource(booleans = {true, false})
    void registeredP2UsesTheRinglessHurtTailRegardlessOfDeclaredCpuInput(boolean cpuControlled)
            throws IOException {
        var inlet = inlet(cpuControlled);
        var p2 = inlet.p2();
        assertFalse(p2.isTouchResponseSuppressedByObjectControl());
        assertFalse(p2.getInvulnerable());

        // The actual production manager performs overlap and response dispatch.
        // There is exactly one real candidate; no supplied collision result.
        inlet.objects().runTouchResponsesForPlayer(p2, inlet.fixture().frameCount(), false);

        assertAll("native P2 identity owns HurtCharacter's non-competition tail",
                () -> assertSame(p2, inlet.objects().getObjectServices().playerQuery().nativeP2OrNull()),
                () -> assertSame(p2, GameServices.sprites().getRegisteredSidekicks().getFirst()),
                () -> assertEquals(cpuControlled, p2.isCpuControlled()),
                () -> assertFalse(p2.getDead(), "ringless native P2 is not the P1 death branch"),
                () -> assertTrue(p2.isHurt(), "loc_102E0 writes routine 4"),
                () -> assertEquals(Sonic3kAnimationIds.HURT.id(), p2.getAnimationId()),
                () -> assertEquals(0x78, p2.getInvulnerableFrames()),
                () -> assertTrue(p2.getAir()),
                () -> assertFalse(inlet.fixture().sprite().getDead()),
                () -> assertFalse(inlet.fixture().sprite().isHurt()));
    }

    @Test
    void declaredBit7ControlRejectsTheSameRealP2Hazard() throws IOException {
        var inlet = inlet(false);
        var p2 = inlet.p2();
        ObjectControlState.nativeBit7FullControl().applyTo(p2);
        assertTrue(p2.isTouchResponseSuppressedByObjectControl());
        int priorAnimation = p2.getAnimationId();
        int priorMapping = p2.getMappingFrame();

        inlet.objects().runTouchResponsesForPlayer(p2, inlet.fixture().frameCount(), false);

        assertAll("native Tails_Control $A0 rejection precedes touch response",
                () -> assertSame(p2, inlet.objects().getObjectServices().playerQuery().nativeP2OrNull()),
                () -> assertFalse(p2.getDead()),
                () -> assertFalse(p2.isHurt()),
                () -> assertEquals(priorAnimation, p2.getAnimationId()),
                () -> assertEquals(priorMapping, p2.getMappingFrame()),
                () -> assertEquals(0, p2.getInvulnerableFrames()));
    }

    // R126_COMPONENT_BEGIN MAIN_PLAYER_NEGATIVE
    @Test
    void declaredRinglessMainPlayerKeepsTheDeathTail() throws IOException {
        var inlet = inlet(false);
        var p1 = inlet.fixture().sprite();
        assertSame(p1, inlet.objects().getObjectServices().playerQuery().mainPlayerOrNull());
        assertNotSame(p1, inlet.objects().getObjectServices().playerQuery().nativeP2OrNull());
        assertFalse(p1.isCpuControlled());
        assertEquals(0, p1.getRingCount());
        assertFalse(p1.hasShield());
        assertEquals(0, p1.getInvincibleFrames());
        assertFalse(p1.getInvulnerable());
        assertFalse(p1.isTouchResponseSuppressedByObjectControl());
        // Declared SYNTHETIC public P1 geometry at the same prepared real hazard.
        p1.setCentreX(inlet.p2().getCentreX());
        p1.setCentreY(inlet.p2().getCentreY());
        var flame = inlet.objects().getTouchResponseObjects().getFirst();
        assertTrue(Math.abs(flame.getCollisionX() - p1.getCentreX()) < 8);
        assertTrue(p1.getYRadius() > 3);
        assertTrue(Math.abs(flame.getCollisionY() - p1.getCentreY()) < p1.getYRadius() - 3);

        inlet.objects().runTouchResponsesForPlayer(p1, inlet.fixture().frameCount(), false);

        assertAll("HurtCharacter's ringless Player_1 path remains distinct",
                () -> assertTrue(p1.getDead()),
                () -> assertFalse(p1.isHurt()),
                () -> assertFalse(inlet.p2().getDead()),
                () -> assertFalse(inlet.p2().isHurt()));
    }
    // R126_COMPONENT_END MAIN_PLAYER_NEGATIVE

    private record Inlet(HeadlessTestFixture fixture, AbstractPlayableSprite p2,
                         ObjectManager objects) { }

    private static Inlet inlet(boolean cpuControlled) throws IOException {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 1)
                .withSkippedZoneIntro().build();
        fixture.stepIdleFrames(1); // actual ordinary first-P2 initialization
        var roster = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(1, roster.size(), "actual one-sidekick stock team prerequisite");
        var p2 = roster.getFirst();
        assertNotSame(fixture.sprite(), p2);
        assertTrue(p2.isCpuControlled(), "original registered P2 prerequisite");
        assertNotNull(p2.getCpuController());
        assertFalse(p2.getDead());
        assertFalse(p2.isHurt());
        assertFalse(p2.isObjectControlled());
        assertFalse(p2.hasShield());
        assertEquals(0, p2.getInvincibleFrames());
        assertEquals(0, p2.getInvulnerableFrames());

        // Declared public input ownership and geometry, not a native arrival.
        p2.setCpuControlled(cpuControlled);
        p2.setCentreX(X);
        p2.setCentreY(Y);
        p2.setRingCount(0);
        fixture.sprite().setRingCount(0);
        var services = GameServices.level().getObjectManager().getObjectServices();
        assertSame(p2, services.playerQuery().nativeP2OrNull(),
                "changing input ownership must retain the actual first-P2 binding");
        // Declared public camera geometry encloses both P2 and the synthetic hazard.
        fixture.camera().setFrozen(true);
        fixture.camera().setX((short) (X - 0x80));
        fixture.camera().setY((short) (Y - 0x70));
        var table = GameServices.module().createTouchResponseTable(
                GameServices.level().getWorldSession().getDataSource());
        var objects = new ObjectManager(List.of(), GameServices.module().createObjectRegistry(),
                0, null, table, null, fixture.camera(), services);
        // Public geometry-only recreation form; default native object state.
        // No explicit timer/phase argument or field mutation is used.
        var flame = new LrzFlameObjectInstance(new ObjectSpawn(X + 4, Y, 0, 0, 0, false, Y));
        objects.addDynamicObject(flame);
        assertTrue(flame.getSlotIndex() >= 0, "actual ordinary managed allocation");
        assertFalse(flame.isDestroyed());
        assertEquals(0x98, flame.getCollisionFlags(), "real ROM-owned HURT category");
        assertTrue(flame.getTouchResponseProfile().requiresRenderFlagForTouch());
        int unexecutedMapping = flame.mappingFrame();
        int beforeObjectPass = objects.getFrameCounter();
        // Real dispatch snapshots positions, executes the flame and clears its
        // first-execution wait. This pass disables touch, so it cannot pre-hurt P2.
        objects.update(fixture.camera().getX(), fixture.sprite(), List.of(p2),
                fixture.frameCount(), false);
        assertEquals(beforeObjectPass + 1, objects.getFrameCounter());
        assertNotEquals(unexecutedMapping, flame.mappingFrame(),
                "actual flame execution consumes its default flicker state");
        assertFalse(flame.isDestroyed());
        assertTrue(flame.isOnScreenForTouch(),
                "the real render/snapshot/first-execution gate must admit this hazard");
        assertTrue(flame.getCollisionX() > fixture.camera().getX()
                && flame.getCollisionX() < fixture.camera().getX() + fixture.camera().getWidth());
        assertTrue(flame.getCollisionY() > fixture.camera().getY()
                && flame.getCollisionY() < fixture.camera().getY() + fixture.camera().getHeight());
        assertTrue(table.getWidthRadius(flame.getCollisionFlags() & 0x3F) > 0);
        assertTrue(table.getHeightRadius(flame.getCollisionFlags() & 0x3F) > 0);
        // Its actual center lies strictly inside Touch_NoInstaShield's P2 box.
        // Positive real ROM extents therefore give a non-boundary overlap.
        assertTrue(Math.abs(flame.getCollisionX() - p2.getCentreX()) < 8);
        assertTrue(p2.getYRadius() > 3);
        assertTrue(Math.abs(flame.getCollisionY() - p2.getCentreY()) < p2.getYRadius() - 3);
        assertEquals(List.of(flame), objects.getTouchResponseObjects(),
                "the prepared real manager still has exactly this candidate");
        assertSame(p2, objects.getObjectServices().playerQuery().nativeP2OrNull());
        assertFalse(p2.getDead(), "preparation must not apply prior damage");
        assertFalse(p2.isHurt(), "preparation must not apply prior damage");
        assertFalse(p2.isTouchResponseSuppressedByObjectControl());
        assertFalse(p2.getInvulnerable());
        assertFalse(fixture.sprite().getDead());
        assertFalse(fixture.sprite().isHurt());
        return new Inlet(fixture, p2, objects);
    }
}
