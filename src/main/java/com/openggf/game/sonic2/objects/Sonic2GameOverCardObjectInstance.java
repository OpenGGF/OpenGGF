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
public final class Sonic2GameOverCardObjectInstance extends AbstractGameOverCardObjectInstance {

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
