package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Runtime-shared Sky Sanctuary ({@code $A00}/{@code $A01}) RAM that the screen and background
 * events, the arrival controller, the cutscene objects, the teleporter pads and the four bosses
 * all read or write. None of these words has an engine-wide owner, so they live here where
 * rewind captures them.
 *
 * <ul>
 *   <li>{@code Events_bg+$00..$0F}: {@code LevelSetup} clears all sixteen bytes on every load
 *       (sonic3k.asm:102201-102204), so a respawn forgets a beaten boss. {@code sub_575EA} reads
 *       {@code +$00}/{@code +$02} as zero / positive / negative for idle / fighting / beaten,
 *       tests {@code +$01}/{@code +$03} as the arena-lock latches, {@code +$04} as the arrival
 *       control lock, {@code +$05} as "an event owns the bounds" and {@code +$06} as the final
 *       arena latch. {@code +$08} is the cutscene button flag that {@code Obj_SSZCutsceneBridge}
 *       waits on ({@code loc_44FA2}).</li>
 *   <li>{@code Events_routine_fg}/{@code Events_routine_bg}: the {@code SSZ1_ScreenEvent} and
 *       {@code SSZ1_BackgroundEvent} stage indices.</li>
 *   <li>{@code Events_fg_4}: the Death Egg launch handshake ({@code sub_5750C} sets
 *       {@code +1} when all ten columns clamp) and, in act 2, the ending trigger.</li>
 *   <li>{@code _unkEE98}/{@code _unkEE9C}: cleared by {@code SSZ1_ScreenInit}; {@code _unkEE9C}
 *       is the cloud oscillator {@code loc_57B6A} writes and the background maths adds.</li>
 *   <li>{@code _unkFAB8}: cutscene Knuckles phase bits. Bit 0 is set by {@code Obj_57E34} when
 *       the beam swing completes ({@code loc_57E86}); bit 1 by the Death Egg child.</li>
 * </ul>
 *
 * <p>{@code screenInitApplied} is the engine's record that {@code SSZ1_ScreenInit} /
 * {@code SSZ2_ScreenInit} has run for this load. It lives here rather than on the event
 * instance so a rewind restore cannot replay the init.
 */
public final class SszZoneRuntimeState implements S3kZoneRuntimeState {
    /** {@code Events_bg} is sixteen bytes; {@code LevelSetup} clears all of them. */
    public static final int EVENTS_BG_BYTES = 0x10;

    private static final int CAPTURE_BYTES =
            EVENTS_BG_BYTES + 11 * Short.BYTES + 3 * Integer.BYTES + 2;

    private final int actIndex;
    private final PlayerCharacter playerCharacter;

    private final byte[] eventsBg = new byte[EVENTS_BG_BYTES];
    private short eventsFg4;
    private short eventsRoutineFg;
    private short eventsRoutineBg;
    private short unkEE98;
    private short unkEE9C;
    /** {@code _unkFAA4}: the SST slot of the object the launch carries; written by the bosses. */
    private short unkFAA4;
    /**
     * {@code _unkFA82}: one bit per EggRobo pairing group, indexed by {@code subtype >> 4}. A
     * nibble-0 fly-by sets its bit as it leaves the screen ({@code loc_91570}) and the matching
     * nibble-2 fighters test it in {@code sub_91914}. No ROM code clears it, so it is cleared
     * here by the state itself being rebuilt on every level load.
     *
     * <p>{@code loc_7A7C4} (the MTZ boss) writes six bytes starting at this address for an
     * unrelated purpose; {@link #setEggRoboFlyByBits(int)} is that overwrite.
     */
    private int unkFA82;

    /** {@code _unkFA84}: this frame's halved camera X delta, added by {@code MoveSprite_SSZBGAdjust}. */
    private short unkFA84;
    private int unkFAB8;
    private boolean screenInitApplied;
    private boolean bossFlag;
    /**
     * {@code Events_bg+$10}: the background-framing toggle {@code sub_579F0} flips at
     * {@code Camera_X_pos $1800}. It sits outside the sixteen bytes {@code LevelSetup} clears,
     * so {@code SSZ1_BackgroundInit}'s own {@code clr.w (Events_bg+$10).w} is the only reset.
     */
    private short unkEventsBg10;
    /** {@code Camera_X_pos_BG_copy} / {@code Camera_Y_pos_BG_copy} as the SSZ handler owns them. */
    private short backgroundCameraX;
    private short backgroundCameraY;
    /** {@code Camera_X_pos_BG_rounded}, re-rounded by the {@code $1800} toggle only. */
    private short backgroundCameraXRounded;
    /** {@code HScroll_table+$000} as a longword: {@code sub_57A60}'s {@code +$500}/frame drift. */
    private int cloudDriftAccumulator;
    /** Whether {@code SSZ1_BackgroundInit} has chosen this load's starting background mode. */
    private boolean backgroundInitApplied;
    /**
     * The parallax frame counter {@code SSZ1_BackgroundEvent} last ran for. The engine can
     * compose the same frame more than once — a rewind restore re-renders the restored frame —
     * and the event has persistent state, so it is captured with the rest of it.
     */
    private int backgroundScrollFrame = Integer.MIN_VALUE;

    public SszZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter) {
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
    }

    @Override public int zoneIndex() { return 0x0A; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return eventsRoutineFg; }
    @Override public boolean isActTransitionFlagActive() { return false; }

    /** {@code tst.b (Events_bg+offset).w} as a signed byte: negative means a beaten boss. */
    public int eventsBgByte(int offset) {
        return eventsBg[offset & (EVENTS_BG_BYTES - 1)];
    }

    /** {@code st}/{@code clr.b}/{@code move.b} on one {@code Events_bg} byte. */
    public void setEventsBgByte(int offset, int value) {
        eventsBg[offset & (EVENTS_BG_BYTES - 1)] = (byte) value;
    }

    /** {@code tst.w (Events_bg+offset).w}: the byte pair, unsigned. */
    public int eventsBgWord(int offset) {
        int index = offset & (EVENTS_BG_BYTES - 1);
        return ((eventsBg[index] & 0xFF) << 8)
                | (eventsBg[(index + 1) & (EVENTS_BG_BYTES - 1)] & 0xFF);
    }

    /** {@code move.w #$7F00,(Events_bg+offset).w}: writes both bytes. */
    public void setEventsBgWord(int offset, int value) {
        int index = offset & (EVENTS_BG_BYTES - 1);
        eventsBg[index] = (byte) (value >> 8);
        eventsBg[(index + 1) & (EVENTS_BG_BYTES - 1)] = (byte) value;
    }

    /** {@code LevelSetup}: {@code Events_bg+$00..$0F} are cleared on every level load. */
    public void clearEventsBg() {
        Arrays.fill(eventsBg, (byte) 0);
    }

    /** {@code Events_fg_4} as a word; {@code Events_fg_4+1} is its low byte. */
    public int eventsFg4() { return eventsFg4 & 0xFFFF; }
    public void setEventsFg4(int value) { eventsFg4 = (short) value; }
    public int eventsFg4Low() { return eventsFg4 & 0xFF; }
    public void setEventsFg4Low(int value) {
        eventsFg4 = (short) ((eventsFg4 & 0xFF00) | (value & 0xFF));
    }

    /** {@code Events_routine_fg}. */
    public int foregroundRoutine() { return eventsRoutineFg & 0xFFFF; }
    public void setForegroundRoutine(int value) { eventsRoutineFg = (short) value; }

    /** {@code Events_routine_bg}. */
    public int backgroundRoutine() { return eventsRoutineBg & 0xFFFF; }
    public void setBackgroundRoutine(int value) { eventsRoutineBg = (short) value; }

    /** {@code _unkEE98}. */
    public int unkEE98() { return unkEE98; }
    public void setUnkEE98(int value) { unkEE98 = (short) value; }

    /** {@code _unkEE9C}: the solid-cloud oscillator offset added to the background Y. */
    public int cloudOscillator() { return unkEE9C; }
    public void setCloudOscillator(int value) { unkEE9C = (short) value; }

    /** {@code _unkFAA4}. */
    public int carriedObjectSlot() { return unkFAA4 & 0xFFFF; }
    public void setCarriedObjectSlot(int value) { unkFAA4 = (short) value; }

    /** {@code btst d0,(_unkFA82).w}. */
    public boolean eggRoboFlyByPassed(int group) {
        return (unkFA82 & (1 << (group & 0x0F))) != 0;
    }

    /** {@code bset d0,d1} / {@code move.w d1,(_unkFA82).w}. */
    public void markEggRoboFlyByPassed(int group) {
        unkFA82 |= 1 << (group & 0x0F);
    }

    /** The whole word, for tests. */
    public int eggRoboFlyByBits() { return unkFA82 & 0xFFFF; }

    /**
     * {@code loc_7A7C4}: the Metropolis recreation's setup writes {@code $10,0,3,0,1,0} over
     * {@code _unkFA82.._unkFA87}, so the first of those bytes lands on the pairing word's high
     * half and the second clears its low half. Every EggRobo group but bit 12 therefore reads as
     * not yet passed for the rest of the act, and group 12 reads as passed whether or not its
     * fly-by ever left the screen. The other four bytes have no consumer in this engine.
     */
    public void setEggRoboFlyByBits(int value) { unkFA82 = value & 0xFFFF; }

    /** {@code _unkFA84}: {@code loc_6607E} writes {@code (Camera_X - previous) >> 1} each frame. */
    public int backgroundCameraDelta() { return unkFA84; }
    public void setBackgroundCameraDelta(int value) { unkFA84 = (short) value; }

    /** {@code btst #bit,(_unkFAB8).w}. */
    public boolean cutsceneFlag(int bit) { return (unkFAB8 & (1 << bit)) != 0; }
    public void setCutsceneFlag(int bit) { unkFAB8 |= 1 << bit; }
    public void clearCutsceneFlags() { unkFAB8 = 0; }
    public int cutsceneFlags() { return unkFAB8; }

    /**
     * {@code Boss_flag}. {@code Obj_SSZGHZBoss}'s init writes {@code move.b #1,(Boss_flag).w} and
     * {@code loc_7A3F8} is the only thing that clears it. It is kept here because the write is
     * real and the rewind capture has to carry it; <b>no SSZ consumer reads it yet</b>, so it
     * records the boss's own state rather than gating anything.
     */
    public boolean bossFlag() { return bossFlag; }

    public void setBossFlag(boolean value) { bossFlag = value; }

    public boolean screenInitApplied() { return screenInitApplied; }
    public void markScreenInitApplied() { screenInitApplied = true; }

    /** {@code Events_bg+$10}: zero below {@code Camera_X_pos $1800}, {@code $FFFF} at or above. */
    public int backgroundFarFraming() { return unkEventsBg10 & 0xFFFF; }
    public void setBackgroundFarFraming(int value) { unkEventsBg10 = (short) value; }

    /** {@code Camera_X_pos_BG_copy}. */
    public int backgroundCameraX() { return backgroundCameraX; }
    public void setBackgroundCameraX(int value) { backgroundCameraX = (short) value; }

    /** {@code Camera_Y_pos_BG_copy}. */
    public int backgroundCameraY() { return backgroundCameraY; }
    public void setBackgroundCameraY(int value) { backgroundCameraY = (short) value; }

    /** {@code Camera_X_pos_BG_rounded}. */
    public int backgroundCameraXRounded() { return backgroundCameraXRounded; }
    public void setBackgroundCameraXRounded(int value) { backgroundCameraXRounded = (short) value; }

    /** {@code HScroll_table+$000}: the 16.16 cloud drift {@code sub_57A60} advances by {@code $500}. */
    public int cloudDrift() { return cloudDriftAccumulator; }
    public void setCloudDrift(int value) { cloudDriftAccumulator = value; }

    public boolean backgroundInitApplied() { return backgroundInitApplied; }
    public void markBackgroundInitApplied() { backgroundInitApplied = true; }

    /** The frame {@code SSZ1_BackgroundEvent} last advanced on. */
    public int backgroundScrollFrame() { return backgroundScrollFrame; }
    public void setBackgroundScrollFrame(int value) { backgroundScrollFrame = value; }

    @Override
    public byte[] captureBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(CAPTURE_BYTES);
        buffer.put(eventsBg);
        buffer.putShort(eventsFg4);
        buffer.putShort(eventsRoutineFg);
        buffer.putShort(eventsRoutineBg);
        buffer.putShort(unkEE98);
        buffer.putShort(unkEE9C);
        buffer.putShort(unkFAA4);
        buffer.putShort(unkFA84);
        buffer.putInt(unkFAB8);
        buffer.put((byte) (screenInitApplied ? 1 : 0));
        buffer.putShort(unkEventsBg10);
        buffer.putShort(backgroundCameraX);
        buffer.putShort(backgroundCameraY);
        buffer.putShort(backgroundCameraXRounded);
        buffer.putInt(cloudDriftAccumulator);
        buffer.put((byte) (backgroundInitApplied ? 1 : 0));
        buffer.putInt(backgroundScrollFrame);
        return buffer.array();
    }

    @Override
    public void restoreBytes(byte[] bytes) {
        if (bytes == null || bytes.length < CAPTURE_BYTES) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.get(eventsBg);
        eventsFg4 = buffer.getShort();
        eventsRoutineFg = buffer.getShort();
        eventsRoutineBg = buffer.getShort();
        unkEE98 = buffer.getShort();
        unkEE9C = buffer.getShort();
        unkFAA4 = buffer.getShort();
        unkFA84 = buffer.getShort();
        unkFAB8 = buffer.getInt();
        screenInitApplied = buffer.get() != 0;
        unkEventsBg10 = buffer.getShort();
        backgroundCameraX = buffer.getShort();
        backgroundCameraY = buffer.getShort();
        backgroundCameraXRounded = buffer.getShort();
        cloudDriftAccumulator = buffer.getInt();
        backgroundInitApplied = buffer.get() != 0;
        backgroundScrollFrame = buffer.getInt();
    }
}
