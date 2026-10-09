package starpost.realtown;

import com.openggf.control.InputHandler;
import com.openggf.control.InputActionMasks;
import com.openggf.game.LevelInputOverlay;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Uses the engine's input edges (including pad B), so held keys never reopen a menu. */
public final class TownInput implements LevelInputOverlay {
    private final TownSession session;
    public TownInput(TownSession session) { this.session = session; }
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
            input.isKeyPressed(258) || input.isKeyPressed(73),hotbar);
        return modal;
    }
}
