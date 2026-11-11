package com.openggf.game.sonic2.objects;

import com.openggf.game.JoypadPressSnapshot;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.level.objects.AbstractGameOverCardObjectInstance;

/**
 * Sonic 2 {@code Obj39} (docs/s2disasm/s2.asm:27670-27774).
 *
 * <ul>
 *   <li>{@code Obj39_Init}: {@code tst.l (Plc_Buffer).w} holds until the
 *       {@code PLCID_GameOver} load has drained (:27682-27685).</li>
 *   <li>{@code Obj39_SetTimer}: {@code move.w #$2D0,anim_frame_duration(a0)}
 *       (:27713).</li>
 *   <li>{@code Obj39_Wait}: bit 0 of the frame is tested first, so OVER only
 *       displays; then {@code Ctrl_1_Press | Ctrl_2_Press} against A/B/C
 *       (:27724-27736). An explicitly native card retains its parent word and
 *       delegates dismissal to that world's competition lifecycle owner.</li>
 * </ul>
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.SCREEN_POSITION)
public final class Sonic2GameOverCardObjectInstance extends AbstractGameOverCardObjectInstance
        implements com.openggf.game.sonic2.competition.view.Sonic2NativeDisplaySource {

    // A scalar native parent word survives graph recreation without retaining an old actor.
    // Obj39 tests parent+1: $B000 selects P1, $B040 selects P2.
    private int nativeParentWord;
    private boolean nativeCard;

    /** {@code move.w #$2D0,anim_frame_duration(a0)}. */
    public static final int WAIT_FRAMES = 0x2D0;

    public Sonic2GameOverCardObjectInstance(int mappingFrame) {
        super(mappingFrame);
    }

    public Sonic2GameOverCardObjectInstance(int mappingFrame, int nativeOwnerSlot) {
        this(mappingFrame);
        if (nativeOwnerSlot < 0 || nativeOwnerSlot > 1)
            throw new IllegalArgumentException("Native card owner " + nativeOwnerSlot);
        nativeCard = true;
        nativeParentWord = 0xB000 + nativeOwnerSlot * 0x40;
    }

    /** CheckGameOver changes code, selected frame bytes and LEFT parent, not an entire SST. */
    public static Sonic2GameOverCardObjectInstance nativeWrite(Sonic2GameOverCardObjectInstance previous,
            Integer mappingFrameWrite, Integer parentWrite) {
        var next = new Sonic2GameOverCardObjectInstance();
        next.nativeCard = true;
        if (previous != null) {
            next.applyNativeCardRam(previous.captureNativeCardRam());
            next.nativeParentWord = previous.nativeParentWord;
        }
        if (mappingFrameWrite != null) {
            var ram = next.captureNativeCardRam();
            next.applyNativeCardRam(new NativeCardRam(ram.routine(), mappingFrameWrite, ram.xPos(), ram.waitTimer()));
        }
        if (parentWrite != null) next.nativeParentWord = parentWrite & 0xFFFF;
        return next;
    }

    public int getNativeParentWord() { return nativeParentWord; }

    @Override public int getLiveObjectId() {
        // CheckGameOver writes Obj39 to the fixed SST before its art-ready routine.
        return nativeCard ? 0x39 : super.getLiveObjectId();
    }

    /** Read-only native display words, including the uninitialized PLC-held state. */
    public com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot nativeCardState() {
        if (!nativeCard) return null;
        var entry = com.openggf.game.sonic2.competition.view.Sonic2CompetitionSpriteArtTable.entry(0x39);
        boolean initialized = getRoutine() != ROUTINE_CHECK_ART;
        // Obj39_Init (s2.asm:27682-27699): screen Y=$F0, flags/bucket0,
        // mapping14C6C, art84DE; Adjust2PArtPointer preserves attributes ->826F.
        return new com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot(
                0x39, initialized && isOnScreen() ? 0x80 : 0, getVdpX(), initialized ? 0xF0 : 0, 0, 0,
                new com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.Mapping(
                        initialized ? entry.mappingsAddress() : 0, getMappingFrame()),
                initialized ? com.openggf.game.sonic2.competition.view.Sonic2CompetitionSpriteArtTable
                        .adjust2PArtPointer(entry.artTile1P()) : 0, null);
    }

    @Override public com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot nativeDisplaySlot() {
        // Obj39_SlideIn/Wait/Display reach DisplaySprite; SetTimer's shipped
        // fixBugs=0 rts omits one display. Parent selects dismissal, not a view:
        // both BuildSprites screen-space passes consume the same card words.
        return isDisplayedThisFrame() ? nativeCardState() : null;
    }

    @Override
    protected boolean handleNativeDismiss() {
        if (!nativeCard) return false;
        var lifecycle = com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle.from(
                services().levelManager());
        if (lifecycle == null) throw new IllegalStateException("Native death card lost its world owner");
        lifecycle.deathCardDismissed((nativeParentWord & 0xFF) == 0 ? 0 : 1);
        return true;
    }

    /** Headless probe constructor: the GAME word. */
    public Sonic2GameOverCardObjectInstance() {
        this(FRAME_GAME);
    }

    @Override
    protected boolean isArtPending() {
        Sonic2PlcService plcService = services().gameService(Sonic2PlcService.class);
        return plcService != null && plcService.isBusy();
    }

    @Override
    protected int waitFrames() {
        return WAIT_FRAMES;
    }

    /** {@code move.b (Ctrl_1_Press).w,d0; or.b (Ctrl_2_Press).w,d0; andi.b #button_B_mask|button_C_mask|button_A_mask,d0}. */
    @Override
    protected boolean isDismissPressed(JoypadPressSnapshot presses) {
        return presses.eitherAnyActionPressed();
    }

    @Override
    protected boolean overElementPollsDismissButton() {
        return false;
    }
}
