package echoes;

import com.openggf.control.PlayerInputState;
import com.openggf.sprites.playable.*;

/** A controller-2 manual body with no follow/timeout/flight state machine. Input is row-local. */
final class EchoController extends SidekickCpuController {
    private PlayerInputState input = PlayerInputState.neutral();
    private boolean active;
    EchoController(AbstractPlayableSprite body, AbstractPlayableSprite leader) {
        super(body, leader);
        setInitialState(State.NORMAL);
    }
    void feed(PlayerInputState input, boolean active) {
        this.input = input; this.active = active;
        super.setController2Input(input.heldMask(), input.pressedMask());
    }
    // The shared first-sidekick dispatch must not substitute the user's physical controller 2.
    @Override public void setController2Input(int held, int logical) { }
    @Override public void update(int frame) { }
    @Override public boolean isUnderManualControl() { return active; }
    @Override public boolean consumeSkipPhysicsThisFrame() { return !active; }
    private boolean held(int mask) { return (input.heldMask() & mask) != 0; }
    @Override public boolean getInputUp() { return held(AbstractPlayableSprite.INPUT_UP); }
    @Override public boolean getInputDown() { return held(AbstractPlayableSprite.INPUT_DOWN); }
    @Override public boolean getInputLeft() { return held(AbstractPlayableSprite.INPUT_LEFT); }
    @Override public boolean getInputRight() { return held(AbstractPlayableSprite.INPUT_RIGHT); }
    @Override public boolean getInputJump() { return held(AbstractPlayableSprite.INPUT_JUMP); }
    @Override public boolean getInputJumpPress() { return (input.pressedMask() & AbstractPlayableSprite.INPUT_JUMP) != 0; }
}
