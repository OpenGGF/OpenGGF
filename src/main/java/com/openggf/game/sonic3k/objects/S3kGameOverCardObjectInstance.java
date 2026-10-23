package com.openggf.game.sonic3k.objects;

import com.openggf.game.JoypadPressSnapshot;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic3k.Sonic3kGameOverPlcService;
import com.openggf.level.objects.AbstractGameOverCardObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;

/**
 * Sonic 3&amp;K {@code Obj_GameOver} (docs/skdisasm/sonic3k.asm:62060-62141).
 *
 * <ul>
 *   <li>{@code tst.l (Nem_decomp_queue).w} (:62061-62063): the ROM holds until
 *       the Nemesis queue that {@code Load_PLC_2 #3} filled has drained. The
 *       ordinary level loop services three patterns per VBlank before this
 *       object can initialize or request its save.</li>
 *   <li>{@code loc_2D5CE}: the GAME word (frame 0 only, {@code tst.b
 *       mapping_frame}) calls {@code SaveGame_LivesContinues} before setting
 *       up (:62067-62070; routine at :16011-16033), so the save slot records
 *       the zero life count and the continues in hand.</li>
 *   <li>{@code loc_2D62A}: {@code move.w #8*60,anim_frame_timer(a0)} (:62099).</li>
 *   <li>{@code loc_2D638}: {@code Collision_response_list} is zeroed every
 *       wait frame (:62105); bit 0 of the frame is tested first, then
 *       {@code Ctrl_1_pressed | Ctrl_2_pressed} against A/B/C/Start
 *       (:62106-62115).</li>
 * </ul>
 */
public final class S3kGameOverCardObjectInstance extends AbstractGameOverCardObjectInstance {

    /** {@code move.w #8*60,anim_frame_timer(a0)}. */
    public static final int WAIT_FRAMES = 8 * 60;

    public S3kGameOverCardObjectInstance(int mappingFrame) {
        super(mappingFrame);
    }

    /** Headless probe constructor: the GAME word. */
    public S3kGameOverCardObjectInstance() {
        this(FRAME_GAME);
    }

    @Override
    protected boolean isArtPending() {
        Sonic3kGameOverPlcService plc = services().gameService(Sonic3kGameOverPlcService.class);
        if (plc == null) {
            throw new IllegalStateException("S3K GAME OVER card requires its native PLC service");
        }
        return plc.isBusy();
    }

    @Override
    protected void onArtReady() {
        if (getMappingFrame() == FRAME_GAME && !ObjectConstructionContext.isRewindActiveRestore()) {
            // SaveGame_LivesContinues: no-op for S&K alone / no save pointer, which
            // the session save request reproduces by ignoring sessions without a slot.
            services().requestSessionSave(SaveReason.LIVES_CONTINUES_SAVE);
        }
    }

    @Override
    protected int waitFrames() {
        return WAIT_FRAMES;
    }

    /**
     * {@code andi.b #button_A_mask|button_B_mask|button_C_mask|button_start_mask,d0}
     * over both controllers' press bytes.
     */
    @Override
    protected boolean isDismissPressed(JoypadPressSnapshot presses) {
        return presses.eitherAnyActionPressed() || presses.eitherStartPressed();
    }

    @Override
    protected boolean overElementPollsDismissButton() {
        return false;
    }
}
