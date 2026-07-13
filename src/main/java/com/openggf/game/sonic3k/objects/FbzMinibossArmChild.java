package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** One of the two exact {@code loc_6F178} arm-controller state machines. */
final class FbzMinibossArmChild extends AbstractObjectInstance implements RewindRecreatable {
    static final int LINK_COUNT = 5;
    static final int ARM_NORMAL_ATTACK = 1;
    static final int ARM_PATROL_READY = 2;
    static final int ARM_TERMINAL_EDGE = 3;
    static final int ARM_OUTWARD_ATTACK = 6;

    private enum State {
        INIT, WAIT_ACTIVATION, ACTIVATION_WAIT, FAN_OUT, FAN_OUT_PAUSE,
        PATROL, NORMAL_SWING, NORMAL_HOLD, WAIT_CHAIN_RETURN,
        OUTWARD_ARMED, OUTWARD_DELAY
    }
    private static final State[] STATES = State.values();

    @RewindTransient(reason = "structural root link restored from stable family slot")
    private FbzMinibossInstance boss;
    @RewindTransient(reason = "cycle first link restored from stable side/index metadata")
    private FbzMinibossChainLink next;
    @RewindTransient(reason = "cycle terminal restored from stable side/index metadata")
    private FbzMinibossChainLink terminal;
    private int familySlot;
    private int side;
    private int x;
    private int y;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int timer;
    private int stateOrdinal;
    private int controlBits;
    private int angle;
    private int angleStep;
    private boolean linksAttempted;
    private boolean defeatInitialized;
    private boolean flickerVisible = true;

    FbzMinibossArmChild(FbzMinibossInstance boss, int side) {
        super(new ObjectSpawn(boss.getX(), boss.getY(), 0xAA, 0xA + side * 2, 0, false, 0),
                side == 0 ? "FBZMinibossArmLeft" : "FBZMinibossArmRight");
        this.boss = boss;
        this.side = side;
        familySlot = boss.getSlotIndex();
        x = boss.getX();
        y = boss.getY();
        xFixed = x << 8;
        yFixed = y << 8;
    }

    /** Parent-free rewind probe/recreate shell; phase 2 restores scalar identity. */
    private FbzMinibossArmChild(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossArm");
        x = spawn.x();
        y = spawn.y();
        xFixed = x << 8;
        yFixed = y << 8;
    }

    static FbzMinibossArmChild forTest(FbzMinibossInstance boss, int side) {
        return new FbzMinibossArmChild(boss, side);
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (boss == null) return;
        if (boss.isDefeated()) {
            updateDefeatFlicker();
            return;
        }
        switch (state()) {
            case INIT -> initialize();
            case WAIT_ACTIVATION -> awaitActivation();
            case ACTIVATION_WAIT -> {
                if (waitExpired()) beginFanOut();
            }
            case FAN_OUT -> {
                move24_8();
                if (waitExpired()) beginFanOutPause();
            }
            case FAN_OUT_PAUSE -> {
                if (waitExpired()) beginPatrol();
            }
            case PATROL -> updatePatrol(player);
            case NORMAL_SWING -> updateNormalSwing();
            case NORMAL_HOLD -> {
                updatePatrolAngle();
                if (waitExpired()) {
                    stateOrdinal = State.WAIT_CHAIN_RETURN.ordinal();
                    boss.clearRootBit(FbzMinibossInstance.ROOT_ARM_RETURNED);
                }
            }
            case WAIT_CHAIN_RETURN -> {
                if (controlBit(ARM_PATROL_READY)) beginPatrol();
            }
            case OUTWARD_ARMED -> updateOutwardArmed();
            case OUTWARD_DELAY -> {
                if (waitExpired()) {
                    stateOrdinal = State.WAIT_CHAIN_RETURN.ordinal();
                    setControlBit(ARM_TERMINAL_EDGE);
                }
            }
        }
    }

    private void initialize() {
        xVelocity = side == 0 ? -0x140 : 0x140;
        yVelocity = -0xD0;
        timer = side == 0 ? 0 : 0x3F;
        stateOrdinal = State.WAIT_ACTIVATION.ordinal();
    }

    private void awaitActivation() {
        if (!boss.rootBit(FbzMinibossInstance.ROOT_FIGHT_STARTED)) return;
        // loc_6F1D6 changes only the routine/callback. Movement begins on the next update.
        stateOrdinal = State.ACTIVATION_WAIT.ordinal();
    }

    private void beginFanOut() {
        stateOrdinal = State.FAN_OUT.ordinal();
        timer = 0x1F;
        createLinkPrefix();
    }

    private void beginFanOutPause() {
        stateOrdinal = State.FAN_OUT_PAUSE.ordinal();
        setControlBit(ARM_TERMINAL_EDGE);
        timer = 0x1F;
    }

    private void beginPatrol() {
        stateOrdinal = State.PATROL.ordinal();
        setControlBit(ARM_PATROL_READY);
        clearControlBit(ARM_TERMINAL_EDGE);
        clearControlBit(ARM_NORMAL_ATTACK);
        clearControlBit(ARM_OUTWARD_ATTACK);
        boss.releaseNormalAttack();
        boss.releaseOutwardAttack();
        boss.clearRootBit(FbzMinibossInstance.ROOT_ARM_RETURNED);
        timer = 2 * 60;
        angle = side == 0 ? -0x60 : 0x60;
        angleStep = side == 0 ? 2 : -2;
    }

    private void updatePatrol(PlayableEntity player) {
        updatePatrolAngle();
        if (!waitExpired()) return;
        timer = 0xF0;
        if (boss.rootBit(FbzMinibossInstance.ROOT_FIGHT_STARTED)) {
            if (boss.claimNormalAttack(this)) {
                stateOrdinal = State.NORMAL_SWING.ordinal();
                clearControlBit(ARM_PATROL_READY);
            }
            return;
        }
        if (boss.rootBit(FbzMinibossInstance.ROOT_OUTWARD_BUSY)) return;
        PlayableEntity p1 = player;
        if (tryServices() != null && services().playerQuery() != null) {
            PlayableEntity queried = services().playerQuery().mainPlayerOrNull();
            if (queried != null) p1 = queried;
        }
        if (p1 == null) return;
        int playerX = p1.getCentreX();
        boolean facesPlayer = side == 0 ? playerX < boss.getX() : playerX >= boss.getX();
        if (facesPlayer && boss.claimOutwardAttack(this)) {
            stateOrdinal = State.OUTWARD_ARMED.ordinal();
            clearControlBit(ARM_PATROL_READY);
        }
    }

    private void updateNormalSwing() {
        if (controlBit(ARM_TERMINAL_EDGE)) {
            stateOrdinal = State.NORMAL_HOLD.ordinal();
            angleStep = 2;
            timer = 0x60;
            return;
        }
        int target = side == 0 ? -0x40 : 0x40;
        int step = side == 0 ? 2 : -2;
        angle = signedByte(angle + step);
        if (angle == target) angle = target;
    }

    private void updateOutwardArmed() {
        if (!clearControlBit(ARM_TERMINAL_EDGE)) return;
        stateOrdinal = State.OUTWARD_DELAY.ordinal();
        timer = 0x10;
    }

    private void updatePatrolAngle() {
        int current = angle & 0xFF;
        int low = side == 0 ? 0x80 : 0x50;
        int high = side == 0 ? 0xB0 : 0x80;
        if ((angleStep > 0 && current >= high) || (angleStep < 0 && current <= low)) {
            angleStep = -angleStep;
        }
        angle = signedByte(angle + angleStep);
    }

    private boolean waitExpired() { return --timer < 0; }

    private void move24_8() {
        xFixed += xVelocity;
        yFixed += yVelocity;
        x = xFixed >> 8;
        y = yFixed >> 8;
    }

    private void updateDefeatFlicker() {
        if (!boss.rootBit(FbzMinibossInstance.ROOT_DEFEAT_RELEASE)) return;
        if (!defeatInitialized) {
            defeatInitialized = true;
            xVelocity = -0x100;
            yVelocity = -0x100;
        }
        xFixed += xVelocity;
        yFixed += yVelocity;
        yVelocity += 0x38;
        x = xFixed >> 8;
        y = yFixed >> 8;
        flickerVisible = !flickerVisible;
        if (!isInRange()) ObjectLifetimeOps.expireDynamic(this);
    }

    private void createLinkPrefix() {
        if (linksAttempted) return;
        linksAttempted = true;
        boss.noteArmTableInvocation();
        FbzMinibossChainLink previous = null;
        for (int index = 0; index < LINK_COUNT; index++) {
            final int stableIndex = index;
            final FbzMinibossChainLink stablePrevious = previous;
            FbzMinibossChainLink made = spawnChild(() ->
                    new FbzMinibossChainLink(boss, this, stablePrevious, side, stableIndex));
            if (made.isDestroyed()) return;
            if (previous == null) next = made;
            else previous.setNext(made);
            previous = made;
            terminal = made;
        }
        terminal.setNext(this);
    }

    FbzMinibossChainLink[] createLinksForTest() {
        FbzMinibossChainLink[] links = new FbzMinibossChainLink[LINK_COUNT];
        for (int i = 0; i < LINK_COUNT; i++) {
            links[i] = new FbzMinibossChainLink(boss, this, i == 0 ? null : links[i - 1], side, i);
            if (i > 0) links[i - 1].setNext(links[i]);
        }
        next = links[0];
        terminal = links[LINK_COUNT - 1];
        terminal.setNext(this);
        return links;
    }

    boolean controlBit(int bit) { return (controlBits & (1 << bit)) != 0; }
    void setControlBit(int bit) { controlBits |= 1 << bit; }
    boolean clearControlBit(int bit) {
        int mask = 1 << bit;
        boolean wasSet = (controlBits & mask) != 0;
        controlBits &= ~mask;
        return wasSet;
    }
    private State state() { return STATES[stateOrdinal]; }
    int side() { return side; }
    int familySlot() { return familySlot; }
    int angle() { return angle; }
    int angleStep() { return angleStep; }
    FbzMinibossChainLink next() { return next; }
    FbzMinibossChainLink terminal() { return terminal; }
    void setBoss(FbzMinibossInstance boss) { this.boss = boss; }
    void setNext(FbzMinibossChainLink next) { this.next = next; }
    void setTerminal(FbzMinibossChainLink terminal) { this.terminal = terminal; }
    FbzMinibossInstance boss() { return boss; }

    static int signedByte(int value) { return (byte) value; }

    @Override
    public FbzMinibossArmChild recreateForRewind(RewindRecreateContext ctx) {
        // RewindRecreatable forbids wiring object references during phase 1.
        // familySlot/side are restored in phase 2; settle() resolves the root.
        return new FbzMinibossArmChild(ctx.spawn());
    }

    @Override protected void afterRewindRestoreSettled() {
        FbzMinibossRewindLinks.settle(services().objectManager(), familySlot);
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return 6; }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ_MINIBOSS);
        if (r != null && r.isReady() && (!defeatInitialized || flickerVisible)) {
            r.drawFrameIndex(5, x, y, side != 0, false);
        }
    }
}
