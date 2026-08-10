package com.openggf.level;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomWorldPositionedObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestFbzRomWorldPositionContract {
    private static final int OFFSET_X = -0x2E00;

    @Test
    void halfOpenSlotsAndBit2GateNativeWordMutationAndPreserveFractions() {
        NativeObject slot3 = objectAt(3, 0x3100, 0x1234, true);
        NativeObject slot4 = objectAt(4, 0x3200, 0x5678, true);
        NativeObject slot93 = objectAt(93, 0x3300, 0x9ABC, true);
        NativeObject slot94 = objectAt(94, 0x3400, 0xDEF0, true);
        NativeObject bit2Clear = objectAt(20, 0x3500, 0x1357, false);

        LevelActTransitionExecutor.offsetCarriedObjectsForTransition(
                List.of(new TransitionSstOccupant(slot3, 3),
                        new TransitionSstOccupant(slot4, 4),
                        new TransitionSstOccupant(slot93, 93),
                        new TransitionSstOccupant(slot94, 94),
                        new TransitionSstOccupant(bit2Clear, 20)), request());

        assertState(slot3, 0x3100, 0x1234, 0);
        assertState(slot4, 0x0400, 0x5678, 1);
        assertState(slot93, 0x0500, 0x9ABC, 1);
        assertState(slot94, 0x3400, 0xDEF0, 0);
        assertState(bit2Clear, 0x3500, 0x1357, 0);
    }

    @Test
    void eligibleObjectWithoutNativePositionContractFailsLoudly() {
        UnsupportedWorldObject unsupported = new UnsupportedWorldObject();
        unsupported.setSlotIndex(4);

        assertThrows(IllegalStateException.class,
                () -> LevelActTransitionExecutor.offsetCarriedObjectsForTransition(
                        List.of(new TransitionSstOccupant(unsupported, 4)), request()));
    }

    @Test
    void offsetEligibilityUsesCapturedOriginalSlotRatherThanMutableObjectSlot() {
        NativeObject identity = objectAt(4, 0x3200, 0x5678, true);
        TransitionSstOccupant carried = new TransitionSstOccupant(identity, 4);

        // Rebuilding the replacement ObjectManager may rewrite the object's mutable
        // slot field.  Offset_ObjectsDuringTransition scans the original SST address,
        // so eligibility must remain attached to the capture tuple.
        identity.setSlotIndex(94);
        LevelActTransitionExecutor.offsetCarriedObjectsForTransition(List.of(carried), request());

        assertState(identity, 0x0400, 0x5678, 1);
    }

    private static SeamlessLevelTransitionRequest request() {
        return SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .playerOffset(OFFSET_X, 0)
                .romWorldObjectOffsetRange(4, 94)
                .build();
    }

    private static NativeObject objectAt(int slot, int xWord, int xFraction, boolean bit2) {
        NativeObject object = new NativeObject(xWord, 0x540, xFraction, bit2);
        object.setSlotIndex(slot);
        return object;
    }

    private static void assertState(NativeObject object, int xWord, int xFraction,
                                    int anchorCalls) {
        assertEquals(xWord, object.getX() & 0xFFFF);
        assertEquals(xFraction, object.xFraction());
        assertEquals(anchorCalls, object.anchorCalls);
    }

    private static final class NativeObject extends AbstractObjectInstance
            implements RomWorldPositionedObject {
        private int xFixed;
        private int yFixed;
        private final boolean bit2;
        private int anchorCalls;

        private NativeObject(int x, int y, int xFraction, boolean bit2) {
            super(new ObjectSpawn(x, y, 1, 0, 0, false, 0), "native-test-object");
            this.xFixed = (x << 16) | (xFraction & 0xFFFF);
            this.yFixed = y << 16;
            this.bit2 = bit2;
        }

        @Override public int getX() { return xFixed >> 16; }
        @Override public int getY() { return yFixed >> 16; }
        @Override public boolean participatesInRomWorldTransitionOffset() { return bit2; }
        int xFraction() { return xFixed & 0xFFFF; }

        @Override
        public void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY) {
            int x = (getX() + offsetX) & 0xFFFF;
            int y = (getY() + offsetY) & 0xFFFF;
            xFixed = (x << 16) | (xFixed & 0xFFFF);
            yFixed = (y << 16) | (yFixed & 0xFFFF);
        }

        @Override
        public void afterRomWorldTransitionOffset(int offsetX, int offsetY) {
            anchorCalls++;
        }

        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isHighPriority() { return false; }
    }

    private static final class UnsupportedWorldObject extends AbstractObjectInstance {
        private UnsupportedWorldObject() {
            super(new ObjectSpawn(0x3200, 0x540, 1, 0, 0, false, 0), "unsupported");
        }

        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isHighPriority() { return false; }
    }
}
