package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Exact {@code loc_6F3DE} five-link state machine. */
final class FbzMinibossChainLink extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {
    private static final int[] INITIAL_SPEEDS = {-0xC0, -0x180, -0x240, -0x300, -0x3C0};
    private static final int[] LEFT_FAN_TARGETS = {-0x60, -0x78, 0x60, 0x48, 0x38};
    private static final int[] RIGHT_FAN_TARGETS = {0x60, 0x78, -0x70, -0x48, -0x34};
    private static final int[][] DEFEAT_VELOCITIES = {
            {0x100, -0x100}, {-0x200, -0x200}, {0x200, -0x200},
            {-0x300, -0x200}, {0x300, -0x200}
    };

    private enum State {
        INIT, WAIT_PARENT_FAN, FAN_OUT, WAIT_PARENT_PATROL, STAGGER, FOLLOW,
        NORMAL_ALIGN, NORMAL_WAIT_PARENT, NORMAL_DELAY, NORMAL_FAN,
        RECYCLE_WAIT, RECYCLE_OR_RESET, OUTWARD_ALIGN, OUTWARD_WAIT_PARENT,
        LUNGE_OUT, LUNGE_PAUSE, LUNGE_RETURN, DEFEAT_FLICKER
    }
    private static final State[] STATES = State.values();

    @RewindTransient(reason = "structural root link restored from stable family slot")
    private FbzMinibossInstance boss;
    @RewindTransient(reason = "cycle owner restored from stable arm side")
    private FbzMinibossArmChild arm;
    @RewindTransient(reason = "cycle predecessor restored from stable link index")
    private Object previous;
    @RewindTransient(reason = "cycle successor restored from stable link index")
    private Object next;
    private int familySlot;
    private int side;
    private int linkIndex;
    private int x;
    private int y;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int angle;
    private int angleStep;
    private int timer;
    private int stagger;
    private int stateOrdinal;
    private int controlBits;
    private int targetX;
    private int targetY;
    private boolean defeatInitialized;
    private boolean flickerVisible = true;

    FbzMinibossChainLink(FbzMinibossInstance boss, FbzMinibossArmChild arm,
                         FbzMinibossChainLink previous, int side, int linkIndex) {
        super(new ObjectSpawn(arm.getX(), arm.getY(), 0xAA, linkIndex * 2, 0, false, 0),
                "FBZMinibossChainLink");
        this.boss = boss;
        this.arm = arm;
        this.previous = previous == null ? arm : previous;
        this.side = side;
        this.linkIndex = linkIndex;
        familySlot = boss.getSlotIndex();
        x = arm.getX();
        y = arm.getY();
        xFixed = x << 8;
        yFixed = y << 8;
    }

    /** Parent-free rewind probe/recreate shell; phase 2 restores scalar identity. */
    private FbzMinibossChainLink(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossChainLink");
        x = spawn.x();
        y = spawn.y();
        xFixed = x << 8;
        yFixed = y << 8;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (boss == null || arm == null) return;
        if (boss.isDefeated()) {
            updateDefeatFlicker();
            return;
        }
        switch (state()) {
            case INIT -> initialize();
            case WAIT_PARENT_FAN -> awaitParentFan();
            case FAN_OUT -> updateFanOut();
            case WAIT_PARENT_PATROL -> awaitParentPatrol();
            case STAGGER -> updateStagger();
            case FOLLOW -> updateFollow();
            case NORMAL_ALIGN -> updateNormalAlign();
            case NORMAL_WAIT_PARENT -> updateNormalWaitParent();
            case NORMAL_DELAY -> {
                if (waitExpired()) stateOrdinal = State.NORMAL_FAN.ordinal();
            }
            case NORMAL_FAN -> updateNormalFan();
            case RECYCLE_WAIT -> {
                if (!parentControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE)) {
                    stateOrdinal = State.RECYCLE_OR_RESET.ordinal();
                    clearControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
                }
            }
            case RECYCLE_OR_RESET -> updateRecycleOrReset();
            case OUTWARD_ALIGN -> updateOutwardAlign();
            case OUTWARD_WAIT_PARENT -> updateOutwardWaitParent();
            case LUNGE_OUT -> updateLunge(false);
            case LUNGE_PAUSE -> beginLungeReturn(); // ROM's jsr Obj_Wait falls through on this first call.
            case LUNGE_RETURN -> updateLunge(true);
            case DEFEAT_FLICKER -> updateDefeatFlicker();
        }
    }

    private void initialize() {
        stateOrdinal = State.WAIT_PARENT_FAN.ordinal();
    }

    private void awaitParentFan() {
        refreshFromParent();
        if (!parentControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE)) return;
        stateOrdinal = State.FAN_OUT.ordinal();
        setControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        xVelocity = INITIAL_SPEEDS[linkIndex];
        yVelocity = INITIAL_SPEEDS[linkIndex];
        if (side != 0) xVelocity = -xVelocity;
        timer = 0xF;
    }

    private void updateFanOut() {
        move24_8();
        if (!waitExpired()) return;
        stateOrdinal = State.WAIT_PARENT_PATROL.ordinal();
        clearControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        xVelocity = 0;
        yVelocity = 0;
    }

    private void awaitParentPatrol() {
        if (!parentControlBit(FbzMinibossArmChild.ARM_PATROL_READY)) return;
        enterStagger();
    }

    private void enterStagger() {
        stateOrdinal = State.STAGGER.ordinal();
        setControlBit(FbzMinibossArmChild.ARM_PATROL_READY);
        clearControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        clearControlBit(FbzMinibossArmChild.ARM_OUTWARD_ATTACK);
        clearControlBit(FbzMinibossArmChild.ARM_NORMAL_ATTACK);
        angle = parentAngle();
        angleStep = parentAngleStep();
        stagger = (linkIndex + 1) * 4;
    }

    private void updateStagger() {
        if (--stagger < 0) stateOrdinal = State.FOLLOW.ordinal();
        circularMove();
    }

    private void updateFollow() {
        updatePatrolAngle();
        circularMove();
        if (parentControlBit(FbzMinibossArmChild.ARM_NORMAL_ATTACK)) {
            stateOrdinal = State.NORMAL_ALIGN.ordinal();
            setControlBit(FbzMinibossArmChild.ARM_NORMAL_ATTACK);
            clearControlBit(FbzMinibossArmChild.ARM_PATROL_READY);
        } else if (parentControlBit(FbzMinibossArmChild.ARM_OUTWARD_ATTACK)) {
            stateOrdinal = State.OUTWARD_ALIGN.ordinal();
            setControlBit(FbzMinibossArmChild.ARM_OUTWARD_ATTACK);
            clearControlBit(FbzMinibossArmChild.ARM_PATROL_READY);
            if (isTerminal()) captureP1Target();
        }
    }

    private void updateNormalAlign() {
        int target = side == 0 ? -0x40 : 0x40;
        int step = side == 0 ? 2 : -2;
        angle = FbzMinibossArmChild.signedByte(angle + step);
        if (angle == target) {
            stateOrdinal = State.NORMAL_WAIT_PARENT.ordinal();
            if (isTerminal()) arm.setControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        }
        circularMove();
    }

    private void updateNormalWaitParent() {
        circularMove();
        if (!parentControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE)) return;
        stateOrdinal = State.NORMAL_DELAY.ordinal();
        setControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        timer = 0xF;
    }

    private void updateNormalFan() {
        angleStep = parentAngleStep();
        int target = side == 0 ? LEFT_FAN_TARGETS[linkIndex] : RIGHT_FAN_TARGETS[linkIndex];
        int candidate = FbzMinibossArmChild.signedByte(angle + (side == 0 ? -angleStep : angleStep));
        boolean reached = side == 0
                ? Integer.compareUnsigned(candidate & 0xFF, target & 0xFF) <= 0
                : Integer.compareUnsigned(candidate & 0xFF, target & 0xFF) >= 0;
        if (reached) {
            angle = target;
            stateOrdinal = State.RECYCLE_WAIT.ordinal();
            angleStep = FbzMinibossArmChild.signedByte(angleStep + 2);
            if (isTerminal()) {
                arm.setControlBit(FbzMinibossArmChild.ARM_PATROL_READY);
                boss.setRootBit(FbzMinibossInstance.ROOT_ARM_RETURNED);
                boss.publishScriptedTerminalImpact();
            }
        } else {
            angle = candidate;
        }
        circularMove();
    }

    private void updateRecycleOrReset() {
        if (parentControlBit(FbzMinibossArmChild.ARM_PATROL_READY)) {
            enterStagger();
            return;
        }
        int target = side == 0 ? -0x60 : 0x60;
        int step = side == 0 ? 2 : -2;
        angle = FbzMinibossArmChild.signedByte(angle + step);
        if (angle == target && isTerminal()) arm.setControlBit(FbzMinibossArmChild.ARM_PATROL_READY);
        circularMove();
    }

    private void updateOutwardAlign() {
        int step = side == 0 ? -2 : 2;
        angle = FbzMinibossArmChild.signedByte(angle + step);
        if ((angle & 0xFF) == 0x80) {
            stateOrdinal = State.OUTWARD_WAIT_PARENT.ordinal();
            if (isTerminal()) arm.setControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        }
        circularMove();
    }

    private void updateOutwardWaitParent() {
        circularMove();
        if (!parentControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE)) return;
        stateOrdinal = State.LUNGE_OUT.ordinal();
        setControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        timer = 0x1F;
        if (isTerminal()) {
            xVelocity = (short) ((targetX - x) << 3);
            yVelocity = (short) ((targetY - y) << 3);
        }
    }

    private void updateLunge(boolean returning) {
        if (isTerminal()) move24_8();
        else interpolateBetweenArmAndTerminal();
        if (!waitExpired()) return;
        if (returning) {
            stateOrdinal = State.RECYCLE_OR_RESET.ordinal();
            return;
        }
        stateOrdinal = State.LUNGE_PAUSE.ordinal();
        timer = 0x1F;
        if (tryServices() != null) services().playSfx(Sonic3kSfx.MECHA_LAND.id);
    }

    private void beginLungeReturn() {
        stateOrdinal = State.LUNGE_RETURN.ordinal();
        xVelocity = -xVelocity;
        yVelocity = -yVelocity;
        timer = 0x1F;
    }

    private void captureP1Target() {
        PlayableEntity p1 = tryServices() == null ? null : services().playerQuery().mainPlayerOrNull();
        if (p1 != null) {
            targetX = p1.getCentreX();
            targetY = p1.getCentreY();
        }
    }

    private void updatePatrolAngle() {
        int current = angle & 0xFF;
        int low = side == 0 ? 0x80 : 0x50;
        int high = side == 0 ? 0xB0 : 0x80;
        if ((angleStep > 0 && current >= high) || (angleStep < 0 && current <= low)) {
            angleStep = -angleStep;
        }
        angle = FbzMinibossArmChild.signedByte(angle + angleStep);
    }

    private void circularMove() {
        Object parent = previous;
        int parentX = parent instanceof FbzMinibossArmChild a ? a.getX() : ((FbzMinibossChainLink) parent).getX();
        int parentY = parent instanceof FbzMinibossArmChild a ? a.getY() : ((FbzMinibossChainLink) parent).getY();
        int amplitude = isTerminal() ? 32 : 16;
        // MoveSprite_CircularSimple maps the returned sine word to X and the
        // cosine word to Y (d0/d1), rather than the conventional screen axes.
        x = parentX + ((TrigLookupTable.sinHex(angle & 0xFF) * amplitude) >> 8);
        y = parentY + ((TrigLookupTable.cosHex(angle & 0xFF) * amplitude) >> 8);
        xFixed = x << 8;
        yFixed = y << 8;
    }

    private void interpolateBetweenArmAndTerminal() {
        FbzMinibossChainLink end = arm.terminal();
        if (end == null) return;
        int factor = linkIndex + 1;
        x = arm.getX() + ((end.getX() - arm.getX()) / 5) * factor;
        y = arm.getY() + ((end.getY() - arm.getY()) / 5) * factor;
        xFixed = x << 8;
        yFixed = y << 8;
    }

    private void refreshFromParent() {
        if (previous instanceof FbzMinibossArmChild a) {
            x = a.getX();
            y = a.getY();
        } else if (previous instanceof FbzMinibossChainLink link) {
            x = link.getX();
            y = link.getY();
        }
        xFixed = x << 8;
        yFixed = y << 8;
    }

    private void updateDefeatFlicker() {
        if (!boss.rootBit(FbzMinibossInstance.ROOT_DEFEAT_RELEASE)) return;
        if (!defeatInitialized) {
            defeatInitialized = true;
            stateOrdinal = State.DEFEAT_FLICKER.ordinal();
            controlBits = 0;
            xVelocity = DEFEAT_VELOCITIES[linkIndex][0];
            if (side != 0) xVelocity = -xVelocity;
            yVelocity = DEFEAT_VELOCITIES[linkIndex][1];
        }
        xFixed += xVelocity;
        yFixed += yVelocity;
        yVelocity += 0x38;
        x = xFixed >> 8;
        y = yFixed >> 8;
        flickerVisible = !flickerVisible;
        if (!isInRange()) ObjectLifetimeOps.expireDynamic(this);
    }

    private void move24_8() {
        xFixed += xVelocity;
        yFixed += yVelocity;
        x = xFixed >> 8;
        y = yFixed >> 8;
    }

    private boolean waitExpired() { return --timer < 0; }
    private boolean parentControlBit(int bit) {
        return previous instanceof FbzMinibossArmChild a ? a.controlBit(bit)
                : ((FbzMinibossChainLink) previous).controlBit(bit);
    }
    private int parentAngle() {
        return previous instanceof FbzMinibossArmChild a ? a.angle() : ((FbzMinibossChainLink) previous).angle;
    }
    private int parentAngleStep() {
        return previous instanceof FbzMinibossArmChild a ? a.angleStep() : ((FbzMinibossChainLink) previous).angleStep;
    }
    private boolean controlBit(int bit) { return (controlBits & (1 << bit)) != 0; }
    private void setControlBit(int bit) { controlBits |= 1 << bit; }
    private void clearControlBit(int bit) { controlBits &= ~(1 << bit); }
    private State state() { return STATES[stateOrdinal]; }
    private boolean isTerminal() { return linkIndex == 4; }

    static int phaseWaitUpdates() { return 0x20; }
    static int aimedPauseUpdates() { return 1; }
    static int[] interpolateFive(int from, int to) {
        int[] values = new int[5];
        int delta = (to - from) / 5;
        for (int i = 0; i < values.length; i++) values[i] = from + delta * (i + 1);
        return values;
    }
    static FbzMinibossChainLink terminalForTest(FbzMinibossInstance boss, int side) {
        FbzMinibossArmChild arm = FbzMinibossArmChild.forTest(boss, side);
        return arm.createLinksForTest()[4];
    }

    int side() { return side; }
    int linkIndex() { return linkIndex; }
    int familySlot() { return familySlot; }
    Object previous() { return previous; }
    Object next() { return next; }
    void setBoss(FbzMinibossInstance boss) { this.boss = boss; }
    void setArm(FbzMinibossArmChild arm) { this.arm = arm; }
    void setPrevious(Object previous) { this.previous = previous; }
    void setNext(Object next) { this.next = next; }
    FbzMinibossInstance boss() { return boss; }
    FbzMinibossArmChild arm() { return arm; }
    boolean acceptsPlayerAttack() { return false; }

    @Override public int getCollisionFlags() { return isTerminal() && !boss.isDefeated() ? 0x86 : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() {
        boolean deployed = state().ordinal() > State.FAN_OUT.ordinal();
        if (deployed) return isTerminal() ? 1 : 3;
        return isTerminal() ? 4 : 5;
    }

    @Override
    public FbzMinibossChainLink recreateForRewind(RewindRecreateContext ctx) {
        // Object references are resolved only after every captured entry exists.
        return new FbzMinibossChainLink(ctx.spawn());
    }

    @Override protected void afterRewindRestoreSettled() {
        FbzMinibossRewindLinks.settle(services().objectManager(), familySlot);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ_MINIBOSS);
        if (r != null && r.isReady() && (!defeatInitialized || flickerVisible)) {
            r.drawFrameIndex(isTerminal() ? 7 : 6, x, y, side != 0, false);
        }
    }
}
