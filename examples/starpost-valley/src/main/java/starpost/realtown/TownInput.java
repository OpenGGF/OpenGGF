package starpost.realtown;

import com.openggf.control.InputHandler;
import com.openggf.control.InputActionMasks;
import com.openggf.game.LevelInputOverlay;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Uses the engine's input edges (including pad B), so held keys never reopen a menu. */
public final class TownInput implements LevelInputOverlay, com.openggf.game.GameplayInputFilter {
    private final TownSession session;
    public TownInput(TownSession session) { this.session = session; }
    /** B is town action, not the native jump union. Modal input cannot drive the real player. */
    public com.openggf.control.PlayerInputState filter(com.openggf.control.PlayerInputState raw) {
        if (!session.active()) return raw;
        if (session.modal()) return com.openggf.control.PlayerInputState.neutral();
        return com.openggf.control.PlayerInputState.of(raw.heldMask(),raw.pressedMask(),
            raw.actionHeldMask() & ~InputActionMasks.ACTION_B,
            raw.actionPressedMask() & ~InputActionMasks.ACTION_B,raw.startHeld(),raw.startPressed());
    }
    public boolean handleInput(InputHandler input) {
        if (!session.active()) return false;
        var pad = input.logical().player1();
        int directions = pad.pressedMask();
        int actions = pad.actionPressedMask();
        int hotbar = -1;
        for (int i=0; i<9; i++) if (input.isKeyPressed(49+i)) hotbar=i;
        if (input.isKeyPressed(48)) hotbar=9;
        if (input.isKeyPressed(81)) hotbar=Math.floorMod(session.game().inventory.selected()-1,12);
        if (input.isKeyPressed(69)) hotbar=(session.game().inventory.selected()+1)%12;
        boolean modal = session.modal();
        session.input(input.isKeyPressed(88) || (actions & InputActionMasks.ACTION_B)!=0,
            input.isKeyPressed(265) || (directions & AbstractPlayableSprite.INPUT_UP)!=0,
            input.isKeyPressed(257) || input.isKeyPressed(335) || input.isKeyPressed(32)
                || input.isKeyPressed(90) || (actions & (InputActionMasks.ACTION_A|InputActionMasks.ACTION_C))!=0,
            input.isKeyPressed(263) || input.isKeyPressed(262) || input.isKeyPressed(265) || input.isKeyPressed(264)
                || (directions & 15)!=0,
            input.isKeyPressed(258) || input.isKeyPressed(73) || pad.startPressed() || !modal && input.isKeyPressed(257),hotbar);
        return modal || pad.startPressed() || input.isKeyPressed(257);
    }
}
