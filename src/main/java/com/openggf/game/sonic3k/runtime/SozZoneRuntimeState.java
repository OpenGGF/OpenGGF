package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import java.nio.ByteBuffer;

/** Shared Sandopolis object, lighting and event state. */
public final class SozZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    // Native _unkF7C4 points to an SST slot, not a durable rock identity.
    private int pushableRockSlot = -1;
    private final SozEventState events = new SozEventState();
    private final SozLightingState lighting = new SozLightingState();
    private int sandCorkForegroundFlag;
    private int sandCorkBackgroundFlag;

    public SozZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter) {
        this.actIndex = actIndex;
        this.playerCharacter = playerCharacter;
    }
    @Override public int zoneIndex() { return 8; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public boolean isActTransitionFlagActive() { return false; }
    @Override public com.openggf.physics.BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
        return events.backgroundCollision()
                ? new com.openggf.physics.BackgroundPlaneCollisionProvider.State(true, 0x1930,
                        (short) -(0x2E0 + events.sandHeight()))
                : com.openggf.physics.BackgroundPlaneCollisionProvider.State.INACTIVE;
    }
    public SozEventState events() { return events; }
    public void requestEndBossDefeat() { sandCorkBackgroundFlag = 0x55; }
    public void requestMinibossDoorClose() { events.doorSignal(-1); }
    public void requestMinibossShake(int duration) { events.screenShakeFlag(duration); }
    public void requestMinibossPostResultsAlignmentComplete() { sandCorkBackgroundFlag = 0x55; }
    public SozLightingState lighting() { return lighting; }
    public int pushableRockSlot() { return pushableRockSlot; }
    public void publishPushableRockSlot(int slot) { pushableRockSlot = slot; }
    /** Obj_SOZSandCork/sub_41D5C: high subtype bit chooses Events_fg_4, else fg_5. */
    public void requestSandCorkRelease(boolean alternate) {
        if (alternate) sandCorkForegroundFlag = 0xFFFF;
        else sandCorkBackgroundFlag = 0xFFFF;
    }
    public int sandCorkForegroundFlag() { return sandCorkForegroundFlag; }
    public int sandCorkBackgroundFlag() { return sandCorkBackgroundFlag; }
    public int consumeSandCorkForegroundFlag() {
        int result = sandCorkForegroundFlag;
        sandCorkForegroundFlag = 0;
        return result;
    }
    public int consumeSandCorkBackgroundFlag() {
        int result = sandCorkBackgroundFlag;
        sandCorkBackgroundFlag = 0;
        return result;
    }
    @Override public byte[] captureBytes() {
        var buffer = ByteBuffer.allocate(12 + SozLightingState.SNAPSHOT_BYTES + SozEventState.SNAPSHOT_BYTES);
        buffer.putInt(pushableRockSlot).putInt(sandCorkForegroundFlag).putInt(sandCorkBackgroundFlag);
        lighting.capture(buffer);
        events.capture(buffer);
        return buffer.array();
    }
    @Override public void restoreBytes(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes);
        pushableRockSlot = buffer.getInt();
        sandCorkForegroundFlag = buffer.remaining() >= 4 ? buffer.getInt() : 0;
        sandCorkBackgroundFlag = buffer.remaining() >= 4 ? buffer.getInt() : 0;
        lighting.restore(buffer.remaining() >= SozLightingState.SNAPSHOT_BYTES ? buffer
                : ByteBuffer.allocate(SozLightingState.SNAPSHOT_BYTES));
        events.restore(buffer.remaining() >= SozEventState.SNAPSHOT_BYTES ? buffer
                : ByteBuffer.allocate(SozEventState.SNAPSHOT_BYTES));
    }

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
