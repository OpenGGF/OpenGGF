package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Runtime-shared Doomsday Zone ({@code $C00}) RAM that the flight controller, the end boss,
 * the asteroids and missiles, and the screen/background events all read or write.
 *
 * <p>These are the disassembly's {@code _unkFAxx} words (constants.asm) plus the
 * {@code Events_bg} and {@code _unkEE98..EEA2} words the DDZ screen events use. None has an
 * engine-wide owner, so they live here where rewind captures them:
 * <ul>
 *   <li>{@code _unkFA82} autoscroll speed and {@code _unkFA8A} acceleration (16.16);</li>
 *   <li>{@code _unkFA86} the locked camera X accumulator ({@code sub_829A0});</li>
 *   <li>{@code _unkFA90} this frame's integer camera X delta, added by every DDZ object;</li>
 *   <li>{@code _unkFAAE} this frame's wrap offset ({@code $2000} on a wrap frame);</li>
 *   <li>{@code _unkFAB0..B6} the camera-relative flight box;</li>
 *   <li>{@code _unkFAB8} phase bits: bit 0 wrap mode (phase 2), bit 1 boss camera lock;</li>
 *   <li>{@code _unkFABC} the last {@code Debug_placement_mode} the controller saw;</li>
 *   <li>{@code Events_bg+$00} plane-body window base, {@code +$02/+$04} boss position,
 *       {@code +$06} accumulated background scroll;</li>
 *   <li>{@code _unkEE98/_unkEE9C} foreground plane scroll and {@code _unkEEA0/_unkEEA2}
 *       their draw-rounded copies; {@code Events_routine_fg} the {@code DDZ_ScreenEvent} stage.</li>
 * </ul>
 * The camera X fraction is the low word of the ROM's longword {@code Camera_X_pos}; the engine
 * camera holds only the integer word.
 */
public final class DdzZoneRuntimeState implements S3kZoneRuntimeState, S3kCameraStoredBounds {
    private static final int CAPTURE_BYTES = 4 * Integer.BYTES + 22 * Short.BYTES + 2;

    private final int actIndex;
    private final PlayerCharacter playerCharacter;

    private int scrollSpeed;
    private int scrollAcceleration;
    private int lockedCameraX;
    private short cameraXFraction;
    private short cameraDelta;
    private short wrapOffset;
    private short cameraXCoarseBack;
    private short boxMinY = 0x20;
    private short boxMaxY = 0xC0;
    private short boxMinX = 0x20;
    private short boxMaxX = 0xC0;
    private int phaseFlags;
    private short lastDebugMode;
    private short eventsBg0;
    private short bossX;
    private short bossY;
    private short backgroundScroll;
    private short foregroundX;
    private short foregroundY;
    private short foregroundXRounded;
    private short foregroundYRounded;
    private short foregroundRoutine;
    private boolean controllerPresent;
    private short cameraStoredMinX;
    private short cameraStoredMaxX;
    private short cameraStoredMinY;
    private short cameraStoredMaxY;

    public DdzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter) {
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
    }

    @Override public int zoneIndex() { return 0x0C; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public boolean isActTransitionFlagActive() { return false; }

    /** {@code _unkFA82}. */
    public int scrollSpeed() { return scrollSpeed; }
    public void setScrollSpeed(int value) { scrollSpeed = value; }

    /** {@code _unkFA8A}. */
    public int scrollAcceleration() { return scrollAcceleration; }
    public void setScrollAcceleration(int value) { scrollAcceleration = value; }

    /** {@code _unkFA86}. */
    public int lockedCameraX() { return lockedCameraX; }
    public void setLockedCameraX(int value) { lockedCameraX = value; }

    /** Low word of the longword {@code Camera_X_pos}. */
    public int cameraXFraction() { return cameraXFraction & 0xFFFF; }
    public void setCameraXFraction(int value) { cameraXFraction = (short) value; }

    /** {@code _unkFA90}. */
    public int cameraDelta() { return cameraDelta; }
    public void setCameraDelta(int value) { cameraDelta = (short) value; }

    /** {@code _unkFAAE}. */
    public int wrapOffset() { return wrapOffset; }
    public void setWrapOffset(int value) { wrapOffset = (short) value; }

    /**
     * {@code Camera_X_pos_coarse_back}: {@code (Camera_X_pos - $80) & $FF80}, latched by
     * {@code Load_Sprites} before {@code Process_Sprites}. The flight controller moves the camera
     * during the object pass, so later DDZ objects still compare against the frame-start value.
     */
    public int cameraXCoarseBack() { return cameraXCoarseBack & 0xFFFF; }
    public void latchCameraXCoarseBack(int cameraX) { cameraXCoarseBack = (short) ((cameraX - 0x80) & 0xFF80); }

    /** {@code _unkFAB0}/{@code _unkFAB2}: vertical flight box, camera relative. */
    public int boxMinY() { return boxMinY; }
    public int boxMaxY() { return boxMaxY; }
    /** {@code _unkFAB4}/{@code _unkFAB6}: horizontal flight box, camera relative. */
    public int boxMinX() { return boxMinX; }
    public int boxMaxX() { return boxMaxX; }

    public void setFlightBox(int minY, int maxY, int minX, int maxX) {
        boxMinY = (short) minY;
        boxMaxY = (short) maxY;
        boxMinX = (short) minX;
        boxMaxX = (short) maxX;
    }

    /** {@code btst #bit,(_unkFAB8).w}. */
    public boolean phaseFlag(int bit) { return (phaseFlags & (1 << bit)) != 0; }
    public void setPhaseFlag(int bit) { phaseFlags |= 1 << bit; }
    public int phaseFlags() { return phaseFlags; }

    /** {@code _unkFABC}. */
    public int lastDebugMode() { return lastDebugMode; }
    public void setLastDebugMode(int value) { lastDebugMode = (short) value; }

    /** {@code Events_bg+$00}. */
    public int foregroundWindowBase() { return eventsBg0; }
    public void setForegroundWindowBase(int value) { eventsBg0 = (short) value; }

    /** {@code Events_bg+$02}/{@code +$04}, written by the end boss each frame. */
    public int bossX() { return bossX & 0xFFFF; }
    public int bossY() { return bossY & 0xFFFF; }
    public void setBossPosition(int x, int y) {
        bossX = (short) x;
        bossY = (short) y;
    }

    /** {@code Events_bg+$06}. */
    public int backgroundScroll() { return backgroundScroll; }
    public void addBackgroundScroll(int delta) { backgroundScroll = (short) (backgroundScroll + delta); }

    /** {@code _unkEE98}/{@code _unkEE9C}. */
    public int foregroundX() { return foregroundX; }
    public int foregroundY() { return foregroundY; }

    /**
     * The plane A position actually shown. Until {@code DDZ_ScreenEvent} leaves routine 0 the VDP
     * nametable holds only {@code DDZ_ScreenInit}'s {@code Refresh_PlaneFull} draw of layout (0,0),
     * and the 64x32-cell plane wraps every 512x256 pixels, so the scroll words address that region
     * rather than the boss chunks further along the layout. Later routines draw the window they
     * scroll to ({@code Draw_TileColumn/Row}, {@code Draw_PlaneVertBottomUp}).
     */
    public int displayedForegroundX() { return foregroundRoutine == 0 ? foregroundX & 0x1FF : foregroundX; }
    public int displayedForegroundY() { return foregroundRoutine == 0 ? foregroundY & 0xFF : foregroundY; }
    public void setForeground(int x, int y) {
        foregroundX = (short) x;
        foregroundY = (short) y;
    }

    /** {@code _unkEEA0}/{@code _unkEEA2}. */
    public int foregroundXRounded() { return foregroundXRounded; }
    public int foregroundYRounded() { return foregroundYRounded; }
    public void setForegroundRounded(int x, int y) {
        foregroundXRounded = (short) x;
        foregroundYRounded = (short) y;
    }

    /** {@code Events_routine_fg}. */
    public int foregroundRoutine() { return foregroundRoutine; }
    public void setForegroundRoutine(int value) { foregroundRoutine = (short) value; }

    /** {@code _unkFA8E != 0}: the flight controller exists. */
    public boolean controllerPresent() { return controllerPresent; }
    public void setControllerPresent(boolean present) { controllerPresent = present; }

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
        buffer.putInt(scrollSpeed);
        buffer.putInt(scrollAcceleration);
        buffer.putInt(lockedCameraX);
        buffer.putInt(phaseFlags);
        buffer.putShort(cameraXFraction);
        buffer.putShort(cameraDelta);
        buffer.putShort(wrapOffset);
        buffer.putShort(cameraXCoarseBack);
        buffer.putShort(boxMinY);
        buffer.putShort(boxMaxY);
        buffer.putShort(boxMinX);
        buffer.putShort(boxMaxX);
        buffer.putShort(lastDebugMode);
        buffer.putShort(eventsBg0);
        buffer.putShort(bossX);
        buffer.putShort(bossY);
        buffer.putShort(backgroundScroll);
        buffer.putShort(foregroundX);
        buffer.putShort(foregroundY);
        buffer.putShort(foregroundXRounded);
        buffer.putShort(foregroundYRounded);
        buffer.putShort(foregroundRoutine);
        buffer.putShort(cameraStoredMinX);
        buffer.putShort(cameraStoredMaxX);
        buffer.putShort(cameraStoredMinY);
        buffer.putShort(cameraStoredMaxY);
        buffer.put((byte) (controllerPresent ? 1 : 0));
        buffer.put((byte) 0);
        return buffer.array();
    }

    @Override
    public void restoreBytes(byte[] bytes) {
        if (bytes == null || bytes.length < CAPTURE_BYTES) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        scrollSpeed = buffer.getInt();
        scrollAcceleration = buffer.getInt();
        lockedCameraX = buffer.getInt();
        phaseFlags = buffer.getInt();
        cameraXFraction = buffer.getShort();
        cameraDelta = buffer.getShort();
        wrapOffset = buffer.getShort();
        cameraXCoarseBack = buffer.getShort();
        boxMinY = buffer.getShort();
        boxMaxY = buffer.getShort();
        boxMinX = buffer.getShort();
        boxMaxX = buffer.getShort();
        lastDebugMode = buffer.getShort();
        eventsBg0 = buffer.getShort();
        bossX = buffer.getShort();
        bossY = buffer.getShort();
        backgroundScroll = buffer.getShort();
        foregroundX = buffer.getShort();
        foregroundY = buffer.getShort();
        foregroundXRounded = buffer.getShort();
        foregroundYRounded = buffer.getShort();
        foregroundRoutine = buffer.getShort();
        cameraStoredMinX = buffer.getShort();
        cameraStoredMaxX = buffer.getShort();
        cameraStoredMinY = buffer.getShort();
        cameraStoredMaxY = buffer.getShort();
        controllerPresent = buffer.get() != 0;
        buffer.get();
    }
}
