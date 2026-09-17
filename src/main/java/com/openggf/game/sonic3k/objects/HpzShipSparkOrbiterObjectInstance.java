package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.io.IOException;
import java.util.List;

/**
 * ROM {@code loc_65376} / {@code loc_65360} (sonic3k.asm:133153-133290): one link of a spark
 * chain hanging from a {@link HpzShipSparkEmitterObjectInstance}. Each link orbits the previous
 * link ({@code parent3}); {@code $44} is the emitter.
 *
 * <p>After {@code $48 - subtype*4} frames of {@code MoveSprite_Circular} without drawing
 * ({@code loc_653F2}) the radius grows by {@code $20} to {@code $800} ({@code $C00} for the tip,
 * subtype {@code $12}, which also turns into a spark with {@code sub_662A6}). The chain spins with
 * {@code MoveSprite_CircularSimple} (the tip uses {@code MoveSprite_AtAngleLookup} over
 * {@code AngleLookup_1}) until the emitter's {@code $38} bit 7, then each link swings to its
 * {@code byte_653DE} target angle. The first link of each chain sets {@code _unkFAB8} bit 2 or 3
 * when it arrives; after the ship's zap (bit 4) the tip flickers {@code RawAni_65550} for 60
 * frames, the links hold, swing back to their start angle and spin again.
 */
public final class HpzShipSparkOrbiterObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int LAST_SUBTYPE = 0x12;
    /** {@code byte_653DE}. */
    private static final int[] TARGET_ANGLES = {
            -0x30, -0x2C, -0x28, -0x20, -0x18, -0xC, -8, -4, 0, 0x40,
            0x30, 0x28, 0x20, 0x18, 0x10, 8, -0x10, -0x18, -0x20, -0x40
    };
    /** {@code RawAni_65550}. */
    private static final int[] ZAP_FRAMES = {0x15, 0x1C, 0x19, 0x1B};

    private static final int PHASE_INIT = 0;
    private static final int PHASE_WAIT = 1;
    private static final int PHASE_GROW = 2;
    private static final int PHASE_SPIN = 3;
    private static final int PHASE_AIM = 4;
    private static final int PHASE_WAIT_ZAP = 5;
    private static final int PHASE_ZAP = 6;
    private static final int PHASE_ZAP_HOLD = 7;
    private static final int PHASE_RETURN = 8;
    private static final int PHASE_RESUME = 9;

    @RewindTransient(reason = "object link restored by ObjectRefId in restoreRewindState")
    private AbstractObjectInstance link;
    @RewindTransient(reason = "object link restored by ObjectRefId in restoreRewindState")
    private HpzShipSparkEmitterObjectInstance emitter;
    private int phase;
    /** 16.16 {@code x_pos}/{@code y_pos} longwords. */
    private int xLong;
    private int yLong;
    /** {@code $38} bits 2 (chain tip) and 3 ({@code loc_65360}). */
    private int flags;
    private int startAngle;
    private int targetAngle;
    private int angle;
    private int angleStep;
    private int swingCounter;
    private int swingPeriod;
    private int radius;
    private int timer;
    private int timerReload;
    private int mappingFrame = 0x12;
    private boolean visible;


    /** Rewind probe; the links are restored by object id. */
    private HpzShipSparkOrbiterObjectInstance(ObjectSpawn spawn) {
        this(spawn, null, null);
    }

    public HpzShipSparkOrbiterObjectInstance(ObjectSpawn spawn, AbstractObjectInstance link,
                                             HpzShipSparkEmitterObjectInstance emitter) {
        super(spawn, "HpzShipSparkOrbiter");
        this.link = link;
        this.emitter = emitter;
        xLong = spawn.x() << 16;
        yLong = spawn.y() << 16;
    }

    @Override
    public HpzShipSparkOrbiterObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzShipSparkOrbiterObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (phase == PHASE_INIT) {
            init();
            return;
        }
        if (link == null || emitter == null) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        switch (phase) {
            case PHASE_WAIT -> {
                timer = (short) (timer - 1);
                if (timer >= 0) {
                    sub6628C();
                    moveSpriteCircular();
                    return;
                }
                phase = PHASE_GROW;
                grow();
            }
            case PHASE_GROW -> grow();
            case PHASE_SPIN -> {
                sub6628C();
                if (emitter.released()) {
                    timer = (short) (timer - 1);
                    if (timer < 0) {
                        phase = PHASE_AIM;
                    }
                }
                orbitAndDraw();
            }
            case PHASE_AIM -> aim();
            case PHASE_WAIT_ZAP -> {
                HpzZoneRuntimeState hpz = HpzKnucklesCutsceneSupport.hpz(services());
                if (hpz != null && hpz.knucklesCutsceneFlag(HpzKnucklesCutsceneSupport.FLAG_ZAP)) {
                    phase = PHASE_ZAP;
                    timer = 60 - 1;
                }
                orbitAndDraw();
            }
            case PHASE_ZAP -> zap(vIntRunCount);
            case PHASE_ZAP_HOLD -> {
                timer = (short) (timer - 1);
                if (timer < 0) {
                    phase = PHASE_RETURN;
                }
                orbitAndDraw();
            }
            case PHASE_RETURN -> swingBack();
            default -> {
                sub6628C();
                orbitAndDraw();
            }
        }
        updateDynamicSpawn(xLong >> 16 & 0xFFFF, yLong >> 16 & 0xFFFF);
    }

    /** {@code loc_65376} / {@code loc_65360} / {@code loc_65382}. */
    private void init() {
        boolean second = (spawn.renderFlags() & 0xFF) != 0;
        if (second) {
            flags |= 8;
            angle = 0x40;
            angleStep = 1;
        } else {
            angle = 0xC0;
            angleStep = 0xFF;
        }
        startAngle = angle;
        int d0 = spawn.subtype() & 0xFF;
        int d1 = (d0 >> 1) + (second ? 0xA : 0);
        targetAngle = TARGET_ANGLES[d1] & 0xFF;
        if (d0 == LAST_SUBTYPE) {
            flags |= 4;
        }
        swingPeriod = ((d0 + 0x10) * 2) & 0xFF;
        timer = 0x48 - (d0 << 2);
        timerReload = timer;
        phase = PHASE_WAIT;
    }

    /** {@code loc_6540A}. */
    private void grow() {
        int d0 = (radius + 0x20) & 0xFFFF;
        if (d0 >= 0x800 && ((flags & 4) == 0 || d0 >= 0xC00)) {
            phase = PHASE_SPIN;
            timer = timerReload;
        }
        radius = d0;
        sub6628C();
        moveSpriteCircular();
        if ((flags & 4) != 0) {
            sub662A6();
        }
        childDraw();
    }

    /** {@code loc_6549A}. */
    private void aim() {
        int d2 = (byte) (targetAngle - angle);
        if (d2 == 0) {
            arrived();
            return;
        }
        // sgt d3 follows the signed-byte compare, overflow included; bpl/neg use the result byte.
        boolean greater = (byte) targetAngle > (byte) angle;
        int magnitude = (d2 < 0 ? -d2 : d2) & 0xFF;
        if (magnitude < 4) {
            arrived();
            return;
        }
        angle = (angle + (greater ? 4 : -4)) & 0xFF;
        orbitAndDraw();
    }

    /** {@code loc_654C2}. */
    private void arrived() {
        phase = PHASE_WAIT_ZAP;
        if ((spawn.subtype() & 0xFF) == 0) {
            HpzZoneRuntimeState hpz = HpzKnucklesCutsceneSupport.hpz(services());
            if (hpz != null) {
                hpz.setKnucklesCutsceneFlag(emitter.emitterSubtype() == 0
                        ? HpzKnucklesCutsceneSupport.FLAG_SPARKS_LEFT
                        : HpzKnucklesCutsceneSupport.FLAG_SPARKS_RIGHT);
            }
        }
        orbitAndDraw();
    }

    /** {@code loc_654FA}. */
    private void zap(int vInt) {
        if ((flags & 4) == 0) {
            moveSpriteCircularSimple();
        } else {
            int d0 = (emitter.emitterSubtype() != 0 ? 2 : 0) + ((vInt & 1) != 0 ? 1 : 0);
            mappingFrame = ZAP_FRAMES[d0];
            moveSpriteAtAngleLookup();
        }
        timer = (short) (timer - 1);
        if (timer < 0) {
            phase = PHASE_ZAP_HOLD;
            timer = timerReload;
        }
        childDraw();
    }

    /** {@code loc_65564}. */
    private void swingBack() {
        int d2 = (byte) (startAngle - angle);
        if (d2 != 0) {
            boolean greater = (byte) startAngle > (byte) angle;
            int magnitude = ((d2 < 0 ? -d2 : d2) - 1) & 0xFF;
            if (magnitude != 0) {
                angle = (angle + (greater ? 2 : -2)) & 0xFF;
                orbitAndDraw();
                return;
            }
        }
        // loc_6558C
        phase = PHASE_RESUME;
        swingCounter = 0;
        angleStep = (flags & 8) != 0 ? 1 : 0xFF;
        orbitAndDraw();
    }

    /** {@code loc_6546E}. */
    private void orbitAndDraw() {
        if ((flags & 4) == 0) {
            moveSpriteCircularSimple();
        } else {
            sub662A6();
            moveSpriteAtAngleLookup();
        }
        childDraw();
    }

    /** {@code Child_Draw_Sprite}. */
    private void childDraw() {
        if (link.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        visible = true;
    }

    /** {@code sub_6628C}. */
    private void sub6628C() {
        angle = (angle + angleStep) & 0xFF;
        swingCounter = (swingCounter - 1) & 0xFF;
        if ((byte) swingCounter < 0) {
            swingCounter = swingPeriod;
            angleStep = (-angleStep) & 0xFF;
        }
    }

    /** {@code sub_662A6}. */
    private void sub662A6() {
        int d0 = (angle + 0x10) & 0xFF;
        d0 = ((d0 << 3) | (d0 >>> 5)) & 0xFF;
        mappingFrame = (d0 & 7) + 0x13;
    }

    /** {@code MoveSprite_Circular}: word writes from the 16-bit radius. */
    private void moveSpriteCircular() {
        int sin = TrigLookupTable.sinHex(angle);
        int cos = TrigLookupTable.cosHex(angle);
        int dx = (short) ((radius * sin) >> 16);
        int dy = (short) ((radius * cos) >> 16);
        writeX((linkX() + dx) & 0xFFFF);
        writeY((linkY() + dy) & 0xFFFF);
    }

    /** {@code MoveSprite_CircularSimple} with {@code d2 = 5}: longword writes. */
    private void moveSpriteCircularSimple() {
        int sin = TrigLookupTable.sinHex(angle);
        int cos = TrigLookupTable.cosHex(angle);
        xLong = linkXLong() + ((sin << 16) >> 5);
        yLong = linkYLong() + ((cos << 16) >> 5);
    }

    /** {@code MoveSprite_AtAngleLookup} with {@code AngleLookup_1}. */
    private void moveSpriteAtAngleLookup() {
        byte[] table = angleLookup();
        int d0 = angle & 0x3F;
        int near = table[d0] & 0xFF;
        int far = table[0x3F - d0] & 0xFF;
        int dx;
        int dy;
        switch ((angle >> 5) & 6) {
            case 0 -> { dx = near; dy = far; }
            case 2 -> { dx = far; dy = -near; }
            case 4 -> { dx = -near; dy = -far; }
            default -> { dx = -far; dy = near; }
        }
        writeX((linkX() + dx) & 0xFFFF);
        writeY((linkY() + dy) & 0xFFFF);
    }

    private byte[] angleLookup() {
        try {
            return services().romReader().slice(Sonic3kConstants.ANGLE_LOOKUP_1_ADDR, 0x40);
        } catch (IOException ex) {
            throw new IllegalStateException("AngleLookup_1", ex);
        }
    }

    private void writeX(int x) {
        xLong = (x << 16) | (xLong & 0xFFFF);
    }

    private void writeY(int y) {
        yLong = (y << 16) | (yLong & 0xFFFF);
    }

    private int linkX() {
        return link.getX() & 0xFFFF;
    }

    private int linkY() {
        return link.getY() & 0xFFFF;
    }

    private int linkXLong() {
        return link instanceof HpzShipSparkOrbiterObjectInstance orbiter
                ? orbiter.xLong : linkX() << 16;
    }

    private int linkYLong() {
        return link instanceof HpzShipSparkOrbiterObjectInstance orbiter
                ? orbiter.yLong : linkY() << 16;
    }

    @Override public int getX() { return (xLong >> 16) & 0xFFFF; }
    @Override public int getY() { return (yLong >> 16) & 0xFFFF; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }
    @Override public boolean isPersistent() { return true; }
    public int phaseForTest() { return phase; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.KNUX_FINAL_BOSS_CRANE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
        }
    }

    private record Links(ObjectRefId linkId, ObjectRefId emitterId) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId linkId = context.identityTable().map(table -> table.encodeObject(link)).orElse(null);
        ObjectRefId emitterId = context.identityTable().map(table -> table.encodeObject(emitter)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new Links(linkId, emitterId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof Links links) {
            link = links.linkId() == null ? null
                    : (AbstractObjectInstance) context.requireIdentityTable().resolveObject(links.linkId(), true);
            emitter = links.emitterId() == null ? null
                    : (HpzShipSparkEmitterObjectInstance) context.requireIdentityTable().resolveObject(links.emitterId(), true);
        }
    }
}
