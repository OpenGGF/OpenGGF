package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import java.nio.ByteBuffer;

/** SOZ object coupling; other Sandopolis event owners are still unimplemented. */
public final class SozZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    // Native _unkF7C4 points to an SST slot, not a durable rock identity.
    private int pushableRockSlot = -1;

    public SozZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter) {
        this.actIndex = actIndex;
        this.playerCharacter = playerCharacter;
    }
    @Override public int zoneIndex() { return 8; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public boolean isActTransitionFlagActive() { return false; }
    public int pushableRockSlot() { return pushableRockSlot; }
    public void publishPushableRockSlot(int slot) { pushableRockSlot = slot; }
    @Override public byte[] captureBytes() { return ByteBuffer.allocate(4).putInt(pushableRockSlot).array(); }
    @Override public void restoreBytes(byte[] bytes) { pushableRockSlot = ByteBuffer.wrap(bytes).getInt(); }

    /** Same Level_trigger_array bytes as ordinary buttons; no shadow signal store. */
    public static int trigger(int index) {
        int value = 0;
        for (int bit = 0; bit < 8; bit++) if (Sonic3kLevelTriggerManager.testBit(index, bit)) value |= 1 << bit;
        return value;
    }
    public static void writeTrigger(int index, int value) {
        // Object dispatch is sequential: the complete byte is visible to the next SST.
        for (int bit = 0; bit < 8; bit++) {
            if ((value & (1 << bit)) != 0) Sonic3kLevelTriggerManager.setBit(index, bit);
            else Sonic3kLevelTriggerManager.clearBit(index, bit);
        }
    }
}
