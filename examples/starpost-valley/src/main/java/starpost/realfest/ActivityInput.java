package starpost.realfest;

import com.openggf.control.*;
import com.openggf.game.*;

/** The native player receives all movement; B is casting/interaction and modal fights hold input. */
public final class ActivityInput implements LevelInputOverlay,GameplayInputFilter {
    private final ActivitySession session;
    public ActivityInput(ActivitySession session) { this.session=session; }
    public PlayerInputState filter(PlayerInputState raw) {
        if(!session.active()) return raw;
        if(session.heldPlayer()) return PlayerInputState.neutral();
        return PlayerInputState.of(raw.heldMask(),raw.pressedMask(),raw.actionHeldMask()&~InputActionMasks.ACTION_B,
            raw.actionPressedMask()&~InputActionMasks.ACTION_B,raw.startHeld(),raw.startPressed());
    }
    public boolean handleInput(InputHandler input) {
        if(!session.active()) return false;
        var pad=input.logical().player1(); int actions=pad.actionPressedMask(),hotbar=-1;
        for(int i=0;i<9;i++) if(input.isKeyPressed(49+i)) hotbar=i;
        if(input.isKeyPressed(48)) hotbar=9;
        session.input(input.isKeyPressed(88)||(actions&InputActionMasks.ACTION_B)!=0,
            input.isKeyDown(88)||(pad.actionHeldMask()&InputActionMasks.ACTION_B)!=0,
            input.isKeyPressed(258)||pad.startPressed(),
            input.isKeyPressed(90)||input.isKeyPressed(32)||(actions&(InputActionMasks.ACTION_A|InputActionMasks.ACTION_C))!=0,hotbar);
        return session.heldPlayer()||pad.startPressed();
    }
}
