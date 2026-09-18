package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.boss.BossChildComponent;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZMiniboss} (sonic3k.asm:160001-160900), the Lava Reef act 1 miniboss.
 *
 * <p>The load-bearing reading here is the child census. {@code loc_78562} makes two rings of
 * twelve through {@code CreateChild8_TreeListRepeated}, whose subtype counter steps with
 * {@code addq.w #2,d2} (sonic3k.asm:177181-177203) -- by <b>two</b>, not one. {@code loc_7880A}
 * then sorts each child by subtype: {@code 0} is an arm segment, {@code $16} the firing hand, and
 * everything between an arm link. Stepping by two makes {@code $16} the twelfth child, so each
 * ring is one segment, ten links and one hand.
 *
 * <p>Read the counter as stepping by one and the same code produces two arm segments, twenty-two
 * links and <b>no hands at all</b> -- and since the hands are the only children that shoot and the
 * only ones with their own {@code collision_property}, the whole fight would be inert. That is
 * what these assertions are for.
 */
class TestLrzMinibossInstance {

    private static final int SPAWN_X = 0x2C00;
    private static final int SPAWN_Y = 0x0700;

    private LrzMinibossInstance boss;
    private Camera camera;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) SPAWN_X);
        camera.setY((short) SPAWN_Y);
        boss = new LrzMinibossInstance(new ObjectSpawn(SPAWN_X, SPAWN_Y, 0x9D, 0, 0, false, 0));
        boss.setServices(new TestObjectServices().withCamera(camera));
    }

    @Test
    void initCreatesTwoRingsOfOneSegmentTenLinksAndOneHand() {
        boss.update(0, null);

        List<BossChildComponent> children = boss.getChildComponents();
        assertEquals(24, children.size(),
                "two CreateChild8_TreeListRepeated rings of $C-1+1 = 12 (ChildObjDat_78D84 and _78D8A)");
        assertEquals(2L, countOf(children, LrzMinibossArmSegmentChild.class),
                "subtype 0 of each ring is loc_78838, the camera-anchored arm segment");
        assertEquals(2L, countOf(children, LrzMinibossHandChild.class),
                "subtype $16 is loc_78922, the firing hand -- reachable only because "
                        + "CreateChild8_TreeListRepeated steps the subtype by two");
        assertEquals(20L, countOf(children, LrzMinibossOrbiterChild.class),
                "subtypes 2..$14 are loc_788DE arm links");
    }

    @Test
    void theTwoRingsAreMirrorImages() {
        boss.update(0, null);
        List<LrzMinibossArmSegmentChild> segments = boss.getChildComponents().stream()
                .filter(LrzMinibossArmSegmentChild.class::isInstance)
                .map(LrzMinibossArmSegmentChild.class::cast)
                .toList();
        assertEquals(2, segments.size());
        assertNotEquals(segments.get(0).isMirrored(), segments.get(1).isMirrored(),
                "loc_787FE sets render_flags bit 0 on the second ring only");
    }

    /**
     * {@code loc_78592} -> {@code loc_785C2}: the boss waits {@code $2F} frames for its art, then
     * extends the arms and hovers for {@code $15F}.
     */
    @Test
    void theArtDelayRunsForFortySevenFramesBeforeTheArmsExtend() {
        boss.update(0, null);      // loc_78562, then routine steps to loc_78592
        boss.update(1, null);      // loc_78592 queues the art and loads $2F

        for (int frame = 0; frame < 0x2F; frame++) {
            boss.update(2 + frame, null);
            assertEquals(0, boss.getFlags38() & 0x08,
                    "bset #3,$38 happens only when $2E passes zero, at frame $2F");
        }
        boss.update(2 + 0x2F, null);
        assertTrue((boss.getFlags38() & 0x08) != 0,
                "loc_785C2: bset #3,$38(a0) once the $2F countdown goes negative");
    }

    /**
     * {@code loc_78D2C} -> {@code loc_787D8}: with both hands dead the hover collapses from
     * {@code $15F} to {@code $1F}, which is what ends the fight quickly.
     */
    @Test
    void killingBothHandsShortensTheHover() {
        boss.update(0, null);
        List<LrzMinibossHandChild> hands = boss.getChildComponents().stream()
                .filter(LrzMinibossHandChild.class::isInstance)
                .map(LrzMinibossHandChild.class::cast)
                .toList();
        assertEquals(2, hands.size());

        for (LrzMinibossHandChild hand : hands) {
            for (int hit = 0; hit < 4; hit++) {
                hand.takeHit();
            }
            assertEquals(0, hand.getHitsRemaining(),
                    "collision_property 4 at loc_78922");
        }
        assertEquals(0xC0, boss.getFlags38() & 0xC0,
                "sub_78CF4's loc_78D2C sets $38 bits 6 and 7, one per facing");
    }

    private static long countOf(List<BossChildComponent> children, Class<?> type) {
        return children.stream().filter(type::isInstance).count();
    }
}
