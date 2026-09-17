package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Runtime-shared Sonic 3 &amp; Knuckles Death Egg ({@code $B00}, {@code $B01}) RAM: the event
 * words that {@code DEZ1_ScreenEvent}, {@code DEZ1_BackgroundEvent}, {@code DEZ2_ScreenEvent} and
 * {@code DEZ2_BackgroundEvent} read and write (sonic3k.asm:118623-118810), plus the stored camera
 * bounds the miniboss and end boss restore.
 *
 * <p>This is <b>not</b> Sonic 2's Death Egg.
 *
 * <ul>
 *   <li>{@code Events_fg_4} — raised by the act 1 miniboss at 8 hits ({@code loc_7EE42} installs
 *       {@code loc_7DFB8}), by the surviving miniboss chain after the act change
 *       ({@code loc_7E342}) and by the act 2 end boss. Each screen-event handler consumes it with
 *       {@code clr.w}.</li>
 *   <li>{@code Events_fg_5} — raised by {@code Obj_LevelResultsCreate} for every act 1 except AIZ
 *       and ICZ (:62615-62621) and consumed by {@code DEZ1_BackgroundEvent} routine 0, which
 *       queues the act 2 art and starts the seamless change.</li>
 *   <li>{@code Events_routine_fg} / {@code Events_routine_bg} — the screen and background stage
 *       indices, in the ROM's units of 4 because they index a table of {@code bra.w}. A direct
 *       {@code $B01} load starts them at 4 and 8 ({@code DEZ2_ScreenInit},
 *       {@code DEZ2_BackgroundInit}); the seamless change leaves them at 0, which is why
 *       {@code DEZ2_ScreenEvent} stage 0 is reachable only that way.</li>
 * </ul>
 *
 * <p>The reverse-gravity flag is deliberately <b>not</b> here: it is a global
 * ({@code Reverse_gravity_flag}, {@code $F7C6}) that survives the seamless act change, and it
 * stays in {@code GameStateManager} where rewind already captures it.
 */
public final class S3kDezZoneRuntimeState implements S3kZoneRuntimeState, S3kCameraStoredBounds {
    private static final int CAPTURE_BYTES = 8 * Short.BYTES;

    private final int actIndex;
    private final PlayerCharacter playerCharacter;

    private short eventsFg4;
    private short eventsFg5;
    private short foregroundRoutine;
    private short backgroundRoutine;
    private short cameraStoredMinX;
    private short cameraStoredMaxX;
    private short cameraStoredMinY;
    private short cameraStoredMaxY;

    public S3kDezZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter) {
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
        if (actIndex == 1) {
            // DEZ2_ScreenInit: move.w #4,(Events_routine_fg).w
            // DEZ2_BackgroundInit: move.w #8,(Events_routine_bg).w
            // The seamless change overwrites both with 0 in loc_593EC.
            foregroundRoutine = 4;
            backgroundRoutine = 8;
        }
    }

    @Override public int zoneIndex() { return 0x0B; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }

    /** DEZ keeps {@code Dynamic_resize_routine} separate from the event routines; loc_593EC clears it. */
    @Override public int getDynamicResizeRoutine() { return 0; }

    @Override public boolean isActTransitionFlagActive() { return false; }

    /** {@code Events_fg_4}. */
    public int eventsFg4() { return eventsFg4 & 0xFFFF; }
    public void raiseEventsFg4() { eventsFg4 = 1; }
    public void setEventsFg4(int value) { eventsFg4 = (short) value; }

    /** {@code clr.w (Events_fg_4).w}: returns true when the handler had work to do. */
    public boolean consumeEventsFg4() {
        if (eventsFg4 == 0) {
            return false;
        }
        eventsFg4 = 0;
        return true;
    }

    /** {@code Events_fg_5}, raised by the act 1 results object. */
    public int eventsFg5() { return eventsFg5 & 0xFFFF; }
    public void raiseEventsFg5() { eventsFg5 = 1; }

    public boolean consumeEventsFg5() {
        if (eventsFg5 == 0) {
            return false;
        }
        eventsFg5 = 0;
        return true;
    }

    /** {@code Events_routine_fg}, in the ROM's stride of 4. */
    public int foregroundRoutine() { return foregroundRoutine & 0xFFFF; }
    public void setForegroundRoutine(int value) { foregroundRoutine = (short) value; }
    /** {@code addq.w #4,(Events_routine_fg).w}. */
    public void advanceForegroundRoutine() { foregroundRoutine += 4; }

    /** {@code Events_routine_bg}, in the ROM's stride of 4. */
    public int backgroundRoutine() { return backgroundRoutine & 0xFFFF; }
    public void setBackgroundRoutine(int value) { backgroundRoutine = (short) value; }
    public void advanceBackgroundRoutine() { backgroundRoutine += 4; }

    @Override public int cameraStoredMinX() { return cameraStoredMinX & 0xFFFF; }
    @Override public int cameraStoredMaxX() { return cameraStoredMaxX & 0xFFFF; }
    @Override public int cameraStoredMinY() { return cameraStoredMinY; }
    @Override public int cameraStoredMaxY() { return cameraStoredMaxY; }
    public void setCameraStoredMinX(int value) { cameraStoredMinX = (short) value; }
    public void setCameraStoredMaxX(int value) { cameraStoredMaxX = (short) value; }
    public void setCameraStoredMinY(int value) { cameraStoredMinY = (short) value; }
    public void setCameraStoredMaxY(int value) { cameraStoredMaxY = (short) value; }

    @Override
    public byte[] captureBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(CAPTURE_BYTES);
        buffer.putShort(eventsFg4);
        buffer.putShort(eventsFg5);
        buffer.putShort(foregroundRoutine);
        buffer.putShort(backgroundRoutine);
        buffer.putShort(cameraStoredMinX);
        buffer.putShort(cameraStoredMaxX);
        buffer.putShort(cameraStoredMinY);
        buffer.putShort(cameraStoredMaxY);
        return buffer.array();
    }

    @Override
    public void restoreBytes(byte[] bytes) {
        if (bytes == null || bytes.length < CAPTURE_BYTES) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        eventsFg4 = buffer.getShort();
        eventsFg5 = buffer.getShort();
        foregroundRoutine = buffer.getShort();
        backgroundRoutine = buffer.getShort();
        cameraStoredMinX = buffer.getShort();
        cameraStoredMaxX = buffer.getShort();
        cameraStoredMinY = buffer.getShort();
        cameraStoredMaxY = buffer.getShort();
    }
}
