package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Runtime-shared Lava Reef state for {@code $900}, {@code $901} and the boss act {@code $1600}.
 *
 * <p>The three acts share one {@code Events_*} block in ROM RAM, so they share one state here.
 * None of these words has an engine-wide owner, so rewind only captures them because they live
 * in a zone runtime state:
 *
 * <ul>
 *   <li>{@code Screen_shake_flag}/{@code Screen_shake_offset}: every Lava Reef background event
 *       tail-calls {@code ShakeScreen_Setup} (sonic3k.asm:115318, 115364, 115388, 115682) and
 *       {@code LRZ1_ScreenEvent}/{@code LRZ2_ScreenEvent} (115199-115201, 115670-115673) add the
 *       offset to {@code Camera_Y_pos_copy} before drawing. The engine advances the countdown at
 *       the head of its screen-event pass, so {@link #appliedScreenShakeOffset()} is the word this
 *       frame's events and scroll read and {@link #screenShake()} already holds the next one.</li>
 *   <li>{@code Events_routine_bg}: the {@code LRZ1_BackgroundEvent_Index} /
 *       {@code LRZ2_BackgroundEvent_Index} stage (0, 4, 8, {@code $C}).</li>
 *   <li>{@code Events_bg+$0C}: the {@code LRZ1_ScreenEvent} chunk-edit request. The rock crusher
 *       writes both signs of it - {@code st (Events_bg+$0C)} for subtype 0 (negative, the
 *       {@code $44/$00/$4A} + {@code $3E/$00/$4B} edits) and {@code st (Events_bg+$0D)} otherwise
 *       (positive {@code $00FF}, the {@code $9C} edit) - and the screen event clears it.</li>
 *   <li>{@code Events_bg+$10}/{@code +$12}: the two slower background X fractions
 *       {@code LRZ1_Deform} and {@code sub_57082} publish for the animated-tile channels.</li>
 *   <li>{@code Camera_X_pos_BG_copy}/{@code Camera_Y_pos_BG_copy}: this frame's background camera,
 *       which the animated-tile phase subtracts from the two fractions above.</li>
 *   <li>{@code LRZ_rocks_routine}: the rock-sprite renderer's state, cleared by the seamless
 *       act change at {@code loc_56CAA} (115349).</li>
 *   <li>{@code LRZ_rocks_addr_front}/{@code LRZ_rocks_addr_back}: the two placement-list pointers
 *       {@code Draw_LRZ_Special_Rock_Sprites} walks (sonic3k.asm:39574-39619), kept here as record
 *       indices into the act's list. The ROM carries them frame to frame and only nudges them, so
 *       a restore that did not bring them back would resume the walk from the wrong record.</li>
 * </ul>
 *
 * <p>Words owned by later slices (the dome-region index, the LRZ3 machine, boss state) join this
 * class as their slice lands; the capture layout is versioned only by its own length.
 */
public final class LrzZoneRuntimeState implements S3kZoneRuntimeState, S3kCameraStoredBounds {
    private static final int CAPTURE_BYTES =
            S3kScreenShake.captureBytes() + 2 * Integer.BYTES + 21 * Short.BYTES + LrzBossActState.CAPTURE_BYTES;

    /** No placement can sit at X 0, so it is free as "no big door has opened". */
    private static final int NO_BIG_DOOR = 0;

    private final int zoneIndex;
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final S3kScreenShake screenShake = new S3kScreenShake();
    private final LrzBossActState bossAct = new LrzBossActState();
    public LrzBossActState bossAct() { return bossAct; }

    private int appliedScreenShakeOffset;
    private short backgroundRoutine;
    /** ROM _unkFAB8 bits 0-2 shared by the LRZ2 boulder cutscene. */
    private short cutsceneFlags;
    public int cutsceneFlags() { return cutsceneFlags & 0xFF; }
    public void setCutsceneFlag(int bit) { cutsceneFlags |= (short) (1 << bit); }
    private short chunkEditRequest;
    private short backgroundCameraX;
    private short backgroundCameraY;
    private short animationPhaseX0;
    private short animationPhaseX1;
    private short rocksRoutine;
    private short rocksFrontIndex;
    private short rocksBackIndex;
    private short openedBigDoorX;
    /** ROM {@code Events_bg+$00}: non-zero while a dome region holds the background locked. */
    private short domeRegionLocked;
    private short savedBackgroundCameraX;
    private short savedBackgroundCameraY;
    private short delayedRowcount;
    /** ROM {@code _unkEE9C}: the dome lava platform's published phase, and the locked BG's Y term. */
    private short domePlatformPhase;
    /** ROM {@code Events_fg_5}: {@code Obj_Results} sets it, {@code loc_56BD2} consumes it. */
    private short eventsFg5;
    /** The {@code Queue_Kos_Module} job {@code loc_56BD2} starts; {@code -1} when none is live. */
    private int act2ArtJobOrdinal = -1;
    private short cameraStoredMinX;
    private short cameraStoredMaxX;
    private short cameraStoredMinY;
    private short cameraStoredMaxY;

    public LrzZoneRuntimeState(int zoneIndex, int actIndex, PlayerCharacter playerCharacter) {
        this.zoneIndex = zoneIndex;
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
    }

    @Override public int zoneIndex() { return zoneIndex; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public boolean isActTransitionFlagActive() { return false; }

    /** ROM {@code Screen_shake_flag} / {@code Screen_shake_offset} owner. */
    public S3kScreenShake screenShake() {
        return screenShake;
    }

    /**
     * Runs {@code ShakeScreen_Setup} for this frame, before the screen and background events.
     * The offset the previous setup produced becomes {@link #appliedScreenShakeOffset()}.
     */
    public void advanceScreenShake(int levelFrameCounter, boolean playerDeadOrRestarting) {
        appliedScreenShakeOffset = screenShake.offset();
        screenShake.setup(levelFrameCounter, playerDeadOrRestarting);
    }

    /** {@code Screen_shake_offset} as this frame's Lava Reef events and deformation read it. */
    public int appliedScreenShakeOffset() {
        return appliedScreenShakeOffset;
    }

    /** {@code Events_routine_bg}. */
    public int backgroundRoutine() {
        return backgroundRoutine;
    }

    public void setBackgroundRoutine(int value) {
        backgroundRoutine = (short) value;
    }

    /** {@code Events_bg+$0C}: negative and positive select different {@code LRZ1_ScreenEvent} edits. */
    public int chunkEditRequest() {
        return chunkEditRequest;
    }

    public void setChunkEditRequest(int value) {
        chunkEditRequest = (short) value;
    }

    /** {@code clr.w (Events_bg+$0C).w} at {@code loc_56B54}. */
    public int consumeChunkEditRequest() {
        int pending = chunkEditRequest;
        chunkEditRequest = 0;
        return pending;
    }

    /** {@code Camera_X_pos_BG_copy} / {@code Camera_Y_pos_BG_copy} as the deformation left them. */
    public int backgroundCameraX() { return backgroundCameraX; }
    public int backgroundCameraY() { return backgroundCameraY; }

    /**
     * {@code sub_56DAC}'s two writes. The locked background never runs {@code LRZ1_Deform}, so
     * {@code Events_bg+$10}/{@code +$12} keep the values the last unlocked frame left and the
     * animated-tile channels hold their phase while the dome is on screen.
     */
    public void publishLockedBackgroundCamera(int backgroundX, int backgroundY) {
        backgroundCameraX = (short) backgroundX;
        backgroundCameraY = (short) backgroundY;
    }

    /** {@code Events_bg+$10} / {@code Events_bg+$12}: the animated-tile phase sources. */
    public int animationPhaseX0() { return animationPhaseX0; }
    public int animationPhaseX1() { return animationPhaseX1; }

    /** Published once per frame by {@code LRZ1_Deform} / {@code sub_57082}. */
    public void publishDeformationWords(int backgroundX, int backgroundY, int phaseX0, int phaseX1) {
        backgroundCameraX = (short) backgroundX;
        backgroundCameraY = (short) backgroundY;
        animationPhaseX0 = (short) phaseX0;
        animationPhaseX1 = (short) phaseX1;
    }

    /** {@code LRZ_rocks_routine}; {@code loc_56CAA} clears it on the seamless act change. */
    public int rocksRoutine() { return rocksRoutine; }

    public void setRocksRoutine(int value) {
        rocksRoutine = (short) value;
    }

    /** {@code LRZ_rocks_addr_front} / {@code LRZ_rocks_addr_back} as placement-record indices. */
    public int rocksFrontIndex() { return rocksFrontIndex & 0xFFFF; }
    public int rocksBackIndex() { return rocksBackIndex & 0xFFFF; }

    public void setRocksWindow(int frontIndex, int backIndex) {
        rocksFrontIndex = (short) frontIndex;
        rocksBackIndex = (short) backIndex;
    }

    /**
     * {@code Obj_LRZBigDoor}'s "already opened" memory. The ROM keeps it as bit 0 of this
     * placement's own {@code Object_respawn_table} byte ({@code btst #0,(a2)} at sonic3k.asm:88079
     * and {@code bset #0,(a2)} at :88107), so a door the player has opened and then walked away
     * from comes back already open with its shake finished. The engine models only bit 7 of that
     * table (the respawn-remember flag), so the opened placement is kept here by its X word
     * instead; Lava Reef places exactly one {@code $1A}, and a second one would need a wider field.
     */
    public boolean isBigDoorOpened(int placementX) {
        return openedBigDoorX != NO_BIG_DOOR && (openedBigDoorX & 0xFFFF) == (placementX & 0xFFFF);
    }

    public void markBigDoorOpened(int placementX) {
        openedBigDoorX = (short) placementX;
    }

    /**
     * {@code Camera_stored_min_X_pos} and friends, saved and restored around the rock crusher's
     * camera resize and the boss arenas. They have no engine-wide owner.
     */
    /** ROM {@code Events_bg+$00} as {@code tst.w} reads it. */
    public boolean domeRegionLocked() {
        return domeRegionLocked != 0;
    }

    /**
     * {@code st (Events_bg+$00).w} writes {@code $FF} into the high byte of that word, so the
     * locked state reads back as {@code $FF00} and never as 1.
     */
    public void setDomeRegionLocked(boolean locked) {
        domeRegionLocked = (short) (locked ? 0xFF00 : 0);
    }

    /**
     * {@code Events_bg+$02} and {@code Events_bg+$04}: the background camera copies
     * {@code loc_56E66} saves on the way out of a dome region, before it runs
     * {@code LRZ1_Deform} again. They are the <em>locked</em> copies, which is why the plane keeps
     * showing the dome while the bottom-up refresh walks up it. {@code loc_56C28}'s tail reads
     * them back as one long: zero means "no refresh in progress" and any non-zero value pins
     * {@code Camera_X/Y_pos_BG_copy} and selects {@code PlainDeformation}.
     */
    public int savedBackgroundCameraX() { return savedBackgroundCameraX & 0xFFFF; }

    public int savedBackgroundCameraY() { return savedBackgroundCameraY & 0xFFFF; }

    /** {@code tst.l} on {@code Events_bg+$02}: both words zero is the only "off". */
    public boolean backgroundCameraPinned() {
        return (savedBackgroundCameraX | savedBackgroundCameraY) != 0;
    }

    public void saveBackgroundCamera(int x, int y) {
        savedBackgroundCameraX = (short) x;
        savedBackgroundCameraY = (short) y;
    }

    /** {@code clr.l (Events_bg+$02).w} at {@code loc_56C88}. */
    public void clearSavedBackgroundCamera() {
        savedBackgroundCameraX = 0;
        savedBackgroundCameraY = 0;
    }

    /** {@code Draw_delayed_rowcount}, signed: the pass ends on the decrement that makes it negative. */
    public int delayedRowcount() {
        return delayedRowcount;
    }

    public void setDelayedRowcount(int value) {
        delayedRowcount = (short) value;
    }

    /** ROM {@code _unkEE9C}. */
    public int domePlatformPhase() {
        return domePlatformPhase & 0xFFFF;
    }

    public void setDomePlatformPhase(int value) {
        domePlatformPhase = (short) value;
    }

    @Override public int cameraStoredMinX() { return cameraStoredMinX & 0xFFFF; }
    @Override public int cameraStoredMaxX() { return cameraStoredMaxX & 0xFFFF; }
    @Override public int cameraStoredMinY() { return cameraStoredMinY; }
    @Override public int cameraStoredMaxY() { return cameraStoredMaxY; }

    /** {@code Events_fg_5} (sonic3k.asm:115274). */
    public int eventsFg5() {
        return eventsFg5;
    }

    public void setEventsFg5(int value) {
        eventsFg5 = (short) value;
    }

    /** {@code Kos_modules_left}'s engine stand-in: the pending act-2 art job, or {@code -1}. */
    public long act2ArtJobOrdinal() {
        return act2ArtJobOrdinal;
    }

    public void setAct2ArtJobOrdinal(long ordinal) {
        act2ArtJobOrdinal = (int) ordinal;
    }

    public void storeCameraBounds(int minX, int maxX, int minY, int maxY) {
        cameraStoredMinX = (short) minX;
        cameraStoredMaxX = (short) maxX;
        cameraStoredMinY = (short) minY;
        cameraStoredMaxY = (short) maxY;
    }

    @Override
    public byte[] captureBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(CAPTURE_BYTES);
        screenShake.captureTo(buffer);
        buffer.putInt(appliedScreenShakeOffset);
        buffer.putShort(backgroundRoutine);
        buffer.putShort(cutsceneFlags);
        buffer.putShort(chunkEditRequest);
        buffer.putShort(backgroundCameraX);
        buffer.putShort(backgroundCameraY);
        buffer.putShort(animationPhaseX0);
        buffer.putShort(animationPhaseX1);
        buffer.putShort(rocksRoutine);
        buffer.putShort(rocksFrontIndex);
        buffer.putShort(rocksBackIndex);
        buffer.putShort(openedBigDoorX);
        buffer.putShort(domeRegionLocked);
        buffer.putShort(savedBackgroundCameraX);
        buffer.putShort(savedBackgroundCameraY);
        buffer.putShort(delayedRowcount);
        buffer.putShort(domePlatformPhase);
        buffer.putShort(eventsFg5);
        buffer.putInt(act2ArtJobOrdinal);
        buffer.putShort(cameraStoredMinX);
        buffer.putShort(cameraStoredMaxX);
        buffer.putShort(cameraStoredMinY);
        buffer.putShort(cameraStoredMaxY);
        bossAct.captureTo(buffer);
        return buffer.array();
    }

    @Override
    public void restoreBytes(byte[] bytes) {
        if (bytes == null || bytes.length < CAPTURE_BYTES) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        screenShake.restoreFrom(buffer);
        appliedScreenShakeOffset = buffer.getInt();
        backgroundRoutine = buffer.getShort();
        cutsceneFlags = buffer.getShort();
        chunkEditRequest = buffer.getShort();
        backgroundCameraX = buffer.getShort();
        backgroundCameraY = buffer.getShort();
        animationPhaseX0 = buffer.getShort();
        animationPhaseX1 = buffer.getShort();
        rocksRoutine = buffer.getShort();
        rocksFrontIndex = buffer.getShort();
        rocksBackIndex = buffer.getShort();
        openedBigDoorX = buffer.getShort();
        domeRegionLocked = buffer.getShort();
        savedBackgroundCameraX = buffer.getShort();
        savedBackgroundCameraY = buffer.getShort();
        delayedRowcount = buffer.getShort();
        domePlatformPhase = buffer.getShort();
        eventsFg5 = buffer.getShort();
        act2ArtJobOrdinal = buffer.getInt();
        cameraStoredMinX = buffer.getShort();
        cameraStoredMaxX = buffer.getShort();
        cameraStoredMinY = buffer.getShort();
        cameraStoredMaxY = buffer.getShort();
        bossAct.restoreFrom(buffer);
    }
}
