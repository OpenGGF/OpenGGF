package com.openggf.game;

import com.openggf.game.rules.GameRules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the {@code stageRingsUseObjectTouchCollection} rule on
 * {@link GameRules}. Sonic 1 routes stage-ring collection through the
 * object-touch-response pipeline (ROM: Obj25 Ring / Touch_Rings). Sonic 2 and
 * Sonic 3&amp;K collect stage rings via the bounding-box sweep in RingManager
 * (ROM: Touch_Rings_Test). This lets RingManager branch on the rule
 * instead of checking {@code GameId} directly, matching the project's rule
 * that game differences must be gated by rules and not by game-name
 * if/else chains.
 */
public class TestStageRingsTouchCollectionRule {

    @Test
    public void sonic1EnablesStageRingObjectTouchCollection() {
        assertTrue(GameRules.SONIC_1.ring().stageRingsUseObjectTouchCollection(),
                "S1 stage rings must use the object-touch pipeline (ROM: Touch_Rings via Obj25)");
    }

    @Test
    public void sonic2DisablesStageRingObjectTouchCollection() {
        assertFalse(GameRules.SONIC_2.ring().stageRingsUseObjectTouchCollection(),
                "S2 stage rings must use the bounding-box sweep (ROM: Touch_Rings_Test)");
    }

    @Test
    public void sonic3kDisablesStageRingObjectTouchCollection() {
        assertFalse(GameRules.SONIC_3K.ring().stageRingsUseObjectTouchCollection(),
                "S3K stage rings must use the bounding-box sweep (ROM: Touch_Rings_Test)");
    }

    @Test
    public void shippedDuckTouchBoxesAreSelectedByGameMappingFrame() {
        assertTrue(GameRules.SONIC_1.objectInteraction().isDuckTouchBoxMappingFrame(0x39),
                "S1 FixBugs=0 ReactToItem tests fr_Duck=$39");
        assertFalse(GameRules.SONIC_1.objectInteraction().isDuckTouchBoxMappingFrame(0x4D));

        assertTrue(GameRules.SONIC_2.objectInteraction().isDuckTouchBoxMappingFrame(0x4D),
                "S2 fixBugs=0 Touch_Rings tests mapping_frame=$4D");
        assertFalse(GameRules.SONIC_2.objectInteraction().isDuckTouchBoxMappingFrame(0x39));

        assertFalse(GameRules.SONIC_3K.objectInteraction().isDuckTouchBoxMappingFrame(0x39));
        assertFalse(GameRules.SONIC_3K.objectInteraction().isDuckTouchBoxMappingFrame(0x4D),
                "S3K removed the duck touch-box adjustment");
    }

    @Test
    public void shippedDuckTouchBoxUsesNamedRomDimensions() {
        assertEquals(12, com.openggf.game.rules.ObjectInteractionRules.DUCK_TOUCH_BOX_TOP_SHIFT);
        assertEquals(20, com.openggf.game.rules.ObjectInteractionRules.DUCK_TOUCH_BOX_HEIGHT);
    }
}
