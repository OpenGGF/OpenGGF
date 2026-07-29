package com.openggf.sprites.playable;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SuperStateController#isSuper()} classification.
 * SuperStateController is abstract, so we drive the real predicate through a
 * minimal concrete subclass and set its private {@code state} field directly,
 * then assert what the controller actually classifies as "super".
 */
public class TestSuperStateController {

    /** Minimal concrete controller. The base constructor only stores the player
     * and calls reset(); it never dereferences the player, so null is fine. */
    private static final class TestableController extends SuperStateController {
        TestableController() {
            super(null);
        }

        @Override protected int getRingDrainInterval() { return 0; }
        @Override protected int getMinRingsToTransform() { return 0; }
        @Override protected com.openggf.game.PhysicsProfile getSuperProfile() { return null; }
        @Override protected com.openggf.game.PhysicsProfile getNormalProfile() { return null; }
        @Override protected void onTransformationStarted() {}
        @Override protected boolean updateTransformationAnimation() { return false; }
        @Override protected void onSuperActivated() {}
        @Override protected void updateSuperPalette() {}
        @Override protected void onRevertStarted() {}

        RewindState genericSnapshot() {
            return createRewindState(1, 2, 3, 4);
        }
    }

    private static SuperStateController controllerInState(SuperState state) throws Exception {
        TestableController controller = new TestableController();
        Field stateField = SuperStateController.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(controller, state);
        return controller;
    }

    @Test
    public void testSuperStateEnumHasFourValues() {
        assertEquals(4, SuperState.values().length, "SuperState should have 4 values");
    }

    @Test
    public void testTransformingAndSuperAreConsideredSuper() throws Exception {
        // Drive the real isSuper() predicate, not enum distinctness.
        assertTrue(controllerInState(SuperState.TRANSFORMING).isSuper(),
                "TRANSFORMING must be classified as super");
        assertTrue(controllerInState(SuperState.SUPER).isSuper(),
                "SUPER must be classified as super");
    }

    @Test
    public void testNormalIsNotSuperOrTransforming() throws Exception {
        assertFalse(controllerInState(SuperState.NORMAL).isSuper(),
                "NORMAL must NOT be classified as super");
    }

    @Test
    public void testRevertingIsNotClassifiedAsSuper() throws Exception {
        // REVERTING is a distinct lifecycle state and isSuper() must exclude it.
        assertFalse(controllerInState(SuperState.REVERTING).isSuper(),
                "REVERTING must NOT be classified as super");
    }

    @Test
    public void testResetReturnsToNormalNonSuperState() {
        TestableController controller = new TestableController();
        controller.reset();
        assertEquals(SuperState.NORMAL, controller.getState(), "reset() returns to NORMAL");
        assertFalse(controller.isSuper(), "A freshly reset controller is not super");
    }

    @Test
    public void genericAndCrossGameControllersKeepOpaquePresentationTierUnset() {
        assertEquals(-1, new TestableController().genericSnapshot().presentationTier(),
                "the compatible factory must not assign an S3K presentation tier");
    }

    @Test
    public void genericControllersDoNotExposeHyperCapability() {
        assertFalse(new TestableController().isHyperFormActive());
    }
}
