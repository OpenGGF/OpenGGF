package starpost.realruins;

import com.openggf.control.*;
import com.openggf.game.LevelInputOverlay;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Direction edges select shafts; B burns a rock and never enters the native jump union. */
public final class RuinsInput implements LevelInputOverlay,com.openggf.game.GameplayInputFilter {
    private final RuinsSession session;
    public RuinsInput(RuinsSession session) { this.session=session; }
    public PlayerInputState filter(PlayerInputState raw) {
        if (!session.active()) return raw;
        return PlayerInputState.of(raw.heldMask(),raw.pressedMask(),raw.actionHeldMask()&~InputActionMasks.ACTION_B,
                raw.actionPressedMask()&~InputActionMasks.ACTION_B,raw.startHeld(),raw.startPressed());
    }
    public boolean handleInput(InputHandler input) {
        if (!session.active()) return false;
        var pad=input.logical().player1();
        session.input((pad.pressedMask()&AbstractPlayableSprite.INPUT_UP)!=0 || input.isKeyPressed(265),
                (pad.pressedMask()&AbstractPlayableSprite.INPUT_DOWN)!=0 || input.isKeyPressed(264),
                (pad.actionPressedMask()&InputActionMasks.ACTION_B)!=0 || input.isKeyPressed(88),
                pad.startPressed() || input.isKeyPressed(258));
        for(int i=0;i<9;i++) if(input.isKeyPressed(49+i)) session.game().inventory.select(i);
        if(input.isKeyPressed(48)) session.game().inventory.select(9);
        return pad.startPressed() || input.isKeyPressed(258);
    }
}
