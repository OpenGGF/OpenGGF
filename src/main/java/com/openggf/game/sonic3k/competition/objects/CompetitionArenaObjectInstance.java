package com.openggf.game.sonic3k.competition.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.competition.S3kCompetitionPlayer;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.List;
import java.util.function.Supplier;

/**
 * Competition SST routines, operating on the native miniature players rather than campaign sprites.
 *
 * <p>Owning routines in the locked-on {@code sonic3k.asm}: {@code Obj_CGZTriangleBumpers}
 * (68694-68824), {@code Obj_CGZBladePlatform} (72849-72937),
 * {@code Obj_2PRetractingSpring} (48557-48915), {@code Obj_BPZElephantBlock}
 * (72941-73055), {@code Obj_BPZBalloon} (73061-73139),
 * {@code Obj_DPZDisolvingSandBar} (73146-73220), {@code Obj_DPZButton}
 * (73226-73276), {@code Obj_EMZDripper} (75026-75142),
 * {@code Obj_InvisibleBlock} (42661-42734), and {@code AIZLRZEMZRock_CompetitionBreak}
 * / {@code AIZLRZEMZRock_SpawnDebrisPieces} (44081-44125,44521-44576).
 *
 * <p>The context owns native allocation, ROM assets, sound and player damage. It never supplies
 * trace/comparison state. Session transitions belong to the session, not these SST routines.
 */
public final class CompetitionArenaObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider {
    public enum Kind {
        TRIANGLE(null, 0), BLADE("competition_blade", 5),
        RETRACTING_SPRING("competition_retracting_spring", 4),
        ELEPHANT("competition_elephant", 5), BALLOON("competition_balloon", 4),
        SAND_BAR("competition_sand_bar", 4), BUTTON("competition_button", 5),
        DRIPPER("competition_dripper", 5), FAN("competition_fan", 4),
        BUMPER("competition_bumper", 1), MUD(null, 0), PATH_SWAP(null, 5),
        SPRING("competition_spring", 4), FLOOR_BUTTON("competition_floor_button", 4),
        ROCK("competition_rock", 4), INVISIBLE_PLATFORM(null, 4),
        ROCK_SHARD("competition_rock", 4), ROCK_DEBRIS("competition_rock", 1),
        BLADE_LEFT(null, 0), BLADE_RIGHT(null, 0),
        SPRING_BASE("competition_retracting_spring_base", 4),
        DROPLET("competition_dripper_drop", 5), SPLASH("competition_dripper_drop", 5);

        private final String artKey;
        private final int bucket;

        Kind(String artKey, int bucket) {
            this.artKey = artKey;
            this.bucket = bucket;
        }

        public String artKey() { return artKey; }
    }

    /** ROM-owned AIZLRZEMZRock debris table row; offsets are signed bytes, velocities signed words. */
    public record RockDebrisPiece(int dx, int dy, int xVelocity, int yVelocity) { }

    /** Session-owned native execution dependencies; the host must retain allocations in SST order. */
    public interface NativeContext {
        List<S3kCompetitionPlayer> players();
        int zoneId();
        int levelFrameCounter();
        int trigger(int index);
        void setTrigger(int index, int value);
        AbstractObjectInstance allocateAfter(AbstractObjectInstance parent,
                                             Supplier<? extends AbstractObjectInstance> factory);
        SpriteAnimationSet animations(String artKey);
        int oscillationByte(int nativeOffset);
        /** Floor distance at a supplied bottom-sensor Y; the caller has already added y_radius. */
        int floorDistance(int x, int y);
        int randomWord();
        int bottomY();
        /** -1 disables wrapping; otherwise the native Screen_Y_wrap_value word. */
        int wrapYMask();
        void playSfx(int id);
        void hurt(S3kCompetitionPlayer player, int sourceX);
        int paletteColor(int line, int index);
        void setPaletteColor(int line, int index, int nativeColor);
        List<RockDebrisPiece> rockDebris();
    }

    private enum Contact { NONE, SIDE, TOP, BOTTOM }

    private Kind kind;
    // Exact native SST pointer: unlike structural parent hints, this identity must survive rewind.
    private CompetitionArenaObjectInstance nativeParent;
    private int originX;
    private int originY;
    private int x;
    private int y;
    private int subtype;
    private int frame;
    private int animation;
    private ObjectAnimationState animationState;
    private boolean initialized;
    private int standingMask;
    private int extension;
    private int delay;
    private boolean retracting;
    private boolean reverseTrigger;
    private int width;
    private int baseOffsetX;
    private int baseOffsetY;
    private int xFraction;
    private int yFraction;
    private int xVelocity;
    private int yVelocity;
    private int lifetime;
    private int mudP1 = 0x30;
    private int mudP2 = 0x30;
    private int previousSides;
    private int knownSides;
    private int nativeZoneId;
    private int pendingTouches;
    private boolean listedForTouch;
    private boolean nativeVisible;
    private boolean renderPublished;
    private int nativeHeight;
    private boolean dissolveArmed;
    private int dissolveDelay;

    /** Harmless supported generic rewind probe; compact state restores the real routine. */
    public CompetitionArenaObjectInstance(ObjectSpawn spawn) { this(spawn, Kind.DROPLET); }

    public CompetitionArenaObjectInstance(ObjectSpawn spawn, Kind kind) {
        super(spawn, "Competition" + kind);
        this.kind = kind;
        this.originX = this.x = spawn.x();
        this.originY = this.y = spawn.y();
        this.subtype = spawn.subtype();
        if (kind == Kind.SPRING && ((subtype >>> 4) & 7) >= 3)
            throw new IllegalArgumentException("Competition arena layouts use up/horizontal/down springs; diagonal spring consumers are not installed");
        this.width = 0x10;
        this.nativeHeight = 0x10;
        switch (kind) {
            case BLADE -> { width = 0x20; nativeHeight = 8; }
            case SAND_BAR -> { width = subtype == 0 ? 0x20 : 0x30; }
            case BUTTON -> { width = 0xC; nativeHeight = 8; }
            case FLOOR_BUTTON -> { width = 0xC; nativeHeight = 0; }
            case DRIPPER, DROPLET, SPLASH -> { width = 8; nativeHeight = 8; }
            case FAN -> nativeHeight = 4;
            case SPRING, RETRACTING_SPRING -> {
                int orientation = (subtype >>> 4) & 7;
                if (orientation == 1 || orientation == 3) width = 8;
            }
            default -> { }
        }
        if (kind == Kind.ELEPHANT && xFlip()) {
            extension = subtype << 8;
            retracting = true;
        }
        if (kind == Kind.FLOOR_BUTTON) y = (y + 4) & 0xFFFF;
        if (kind == Kind.SAND_BAR) dissolveDelay = subtype == 0 ? 0 : 15;
        if (kind == Kind.DRIPPER && xFlip()) frame = 3;
        if (kind == Kind.ROCK) {
            if (subtype != 0x31) throw new IllegalArgumentException("EMZ's native rock consumer requires placement subtype $31");
            width = 0xE;
            nativeHeight = 0xF;
        }
        if (kind == Kind.INVISIBLE_PLATFORM) {
            width = ((subtype & 0xF0) + 0x10) >>> 1;
            nativeHeight = ((subtype & 0xF) + 1) << 3;
        }
    }

    @Override
    public CompetitionArenaObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new CompetitionArenaObjectInstance(ctx.spawn(), kind);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity ignoredCampaignPlayer) {
        S3kCompetitionSession session = WorldSessionPolicyAccess.getService(
                services().worldSession(), S3kCompetitionSession.class);
        if (session != null) tickNative(session.nativeObjectContext());
    }

    /** Execute one native object pass after the miniature players' movement/touch pass. */
    public void tickNative(NativeContext context) {
        if (isDestroyed()) return;
        renderPublished = false;
        nativeZoneId = context.zoneId();
        boolean firstPass = !initialized;
        if (firstPass) {
            initialized = true;
            initialize(context);
        }
        // Obj_Spring ends in Spring_Common's RTS; retracting springs explicitly continue into their routine.
        if (firstPass && kind == Kind.SPRING) { updateDynamicSpawn(x, y); return; }
        // RideObject_SetRide / the player jump owner clear the previous object's native status bit.
        // The miniature body stores that relationship by SST identity rather than an SST pointer.
        for (var player : context.players()) {
            if (player.standingObject() != nativeIdentity() || (player.status() & 8) == 0 || player.airborne())
                standingMask &= ~(1 << player.slot());
        }
        switch (kind) {
            case TRIANGLE -> triangle(context);
            case BLADE -> blade(context);
            case BLADE_LEFT, BLADE_RIGHT -> bladeCollision(context);
            case ELEPHANT -> elephant(context);
            case BALLOON -> balloon(context);
            case SAND_BAR -> sandBar(context);
            case BUTTON, FLOOR_BUTTON -> button(context);
            case RETRACTING_SPRING -> retractingSpring(context);
            case SPRING -> spring(context);
            case SPRING_BASE -> springBase(context);
            case FAN -> fan(context);
            case BUMPER -> bumper(context);
            case MUD -> mud(context);
            case PATH_SWAP -> pathSwap(context);
            case DRIPPER -> dripper(context);
            case DROPLET -> droplet(context);
            case SPLASH -> splash(context);
            case ROCK -> rock(context);
            case ROCK_SHARD -> rockShard();
            case ROCK_DEBRIS -> rockDebris();
            case INVISIBLE_PLATFORM -> invisiblePlatform(context);
        }
        renderPublished = !isDestroyed() && kind != Kind.TRIANGLE && kind != Kind.MUD
                && kind != Kind.PATH_SWAP && kind != Kind.INVISIBLE_PLATFORM
                && kind != Kind.BLADE_LEFT && kind != Kind.BLADE_RIGHT
                && (kind != Kind.SPRING_BASE || frame != 0);
        updateDynamicSpawn(x, y);
    }

    private void initialize(NativeContext context) {
        if (kind == Kind.BLADE) {
            var left = context.allocateAfter(this, () -> child(Kind.BLADE_LEFT, -0x10, 0xC));
            if (left != null) context.allocateAfter(this, () -> child(Kind.BLADE_RIGHT, 0x10, 0x14));
        } else if (kind == Kind.RETRACTING_SPRING) {
            int orientation = (subtype >>> 4) & 7;
            int mapping = switch (orientation) { case 3 -> 1; case 4, 6 -> 2; case 5, 7 -> 3; default -> 0; };
            int dx = mapping == 1 ? (xFlip() ? 0xC : -0xC) : 0;
            int dy = mapping == 2 ? 0xB : mapping == 3 ? -0xB : 0;
            var base = context.allocateAfter(this, () -> child(Kind.SPRING_BASE, dx, dy));
            if (base instanceof CompetitionArenaObjectInstance nativeBase) {
                nativeBase.frame = mapping;
                nativeBase.width = mapping == 1 ? 8 : 0xC;
                nativeBase.nativeHeight = mapping == 1 ? 0xC : 8;
            }
            // Obj_2PRetractingSpring rewrites only the parent's low nibble using byte_23B18.
            subtype = (subtype & 0xF0) | (orientation == 1 || orientation == 3 ? 0 : 1);
            reverseTrigger = (spawn.renderFlags() & 2) != 0;
        }
        if (kind == Kind.SPRING || kind == Kind.RETRACTING_SPRING) {
            int orientation = (subtype >>> 4) & 7;
            if (orientation == 1 || orientation == 3) { animation = 2; frame = 3; }
            else if (orientation == 2 || orientation == 5 || orientation == 7) frame = 6;
        }
    }

    private CompetitionArenaObjectInstance child(Kind childKind, int dx, int dy) {
        var child = new CompetitionArenaObjectInstance(
                new ObjectSpawn((x + dx) & 0xFFFF, (y + dy) & 0xFFFF,
                        spawn.objectId(), spawn.subtype(), spawn.renderFlags(), false, 0), childKind);
        if (childKind == Kind.BLADE_LEFT || childKind == Kind.BLADE_RIGHT || childKind == Kind.SPRING_BASE)
            child.nativeParent = this;
        child.baseOffsetX = dx;
        child.baseOffsetY = dy;
        return child;
    }

    private void triangle(NativeContext context) {
        int height = ((subtype >>> 4) & 7) == 0 ? 0x40 : 0x80;
        for (var player : context.players()) {
            boolean wasAirborne = player.airborne();
            if (solid(player, 0x10, 8, height, height + 1, false) != Contact.SIDE
                    || !wasAirborne || player.routine() >= 4) continue;
            boolean launchSide = xFlip() ? (short) (player.centreX() - x) < 0
                    : (short) (player.centreX() - x) >= 0;
            if (!launchSide) continue;
            int vx = xFlip() ? -0x800 : 0x800;
            player.setXVelocity(vx);
            player.setYVelocity((subtype & 0x80) == 0 ? 0x400 : 0);
            player.setFacingLeft(xFlip());
            player.setMoveLock(15);
            player.setGroundVelocity(vx);
            if (!player.rolling()) player.setAnimation(0);
            flipAndLayer(player, true);
            player.setPushing(false);
            player.setRollingJump(false);
            context.playSfx(Sonic3kSfx.SMALL_BUMPERS.id);
        }
    }

    private void blade(NativeContext context) {
        // loc_35B16 uses last pass's p1/p2 standing bits, then SolidObjectFull updates them.
        int riders = Integer.bitCount(standingMask & 3);
        extension = riders != 0 ? Math.min(0x8000, extension + riders * 0x80) : Math.max(0, extension - 0x100);
        // add.b $36(a0),d0 changes Y's low byte and does not carry into its high byte.
        y = (originY & 0xFF00) | ((originY + (extension >>> 8)) & 0xFF);
        for (var player : context.players()) solid(player, 0x27, 0x20, 8, 9, false);
        frame = (frame + 1) & 3;
    }

    private void bladeCollision(NativeContext context) {
        if (nativeParent == null || nativeParent.isDestroyed()) { setDestroyed(true); return; }
        y = (nativeParent.y + (kind == Kind.BLADE_LEFT ? 8 : 0x10)) & 0xFFFF;
        listedForTouch = true;
    }

    private void elephant(NativeContext context) {
        int limit = (subtype << 8) & 0xFFFF;
        if (!retracting) {
            extension = (extension + 0x80) & 0xFFFF;
            if (Integer.compareUnsigned(extension, limit) >= 0) { extension = limit; retracting = true; }
        } else {
            int next = extension - 0x80;
            if (next <= 0) { extension = 0; retracting = false; } else extension = next;
        }
        int offset = (byte) (extension >>> 8);
        int halfOffset = offset >> 1;
        x = (originX + (xFlip() ? halfOffset : -halfOffset)) & 0xFFFF;
        width = (halfOffset + 8) & 0xFF;
        frame = width >= 0x20 ? 1 : 0;
        for (var player : context.players()) solid(player, width + 7, width, 0x10, 0x11, false);
    }

    private void balloon(NativeContext context) {
        // loc_35E4C lists the balloon only during mapping frames 5 and 6.
        if (pendingTouches != 0) {
            for (var player : context.players()) {
                if ((pendingTouches & (1 << player.slot())) == 0) continue;
                int angle = TrigLookupTable.calcAngle((short) (x - player.centreX()), (short) (y - player.centreY()));
                if (((angle - 0x20) & 0xFF) < 0x40) {
                    player.setYVelocity(-0x900);
                    animation = 1;
                } else {
                    player.setXVelocity((-0x480 * TrigLookupTable.cosHex(angle)) >> 8);
                    player.setYVelocity((-0x480 * TrigLookupTable.sinHex(angle)) >> 8);
                    animation = 2;
                }
                releaseRide(player);
                player.setAirborne(true);
                player.setRollingJump(false);
                player.setPushing(false);
                player.setJumping(false);
                context.playSfx(Sonic3kSfx.SMALL_BUMPERS.id);
            }
        }
        pendingTouches = 0;
        animate(context);
        listedForTouch = frame >= 5 && frame < 7;
    }

    private void sandBar(NativeContext context) {
        if (animation == 0) {
            if (standingMask != 0) dissolveArmed = true;
            if (dissolveArmed && --dissolveDelay < 0) {
                dissolveDelay = subtype == 0 ? 0 : 15;
                dissolveArmed = false;
                animation = 1;
            }
        }
        animate(context);
        int topHeight = frame < 4 ? 0x11 : Math.max(0, 0x14 - frame);
        for (var player : context.players()) {
            if (topHeight == 0) releaseRide(player);
            else solid(player, subtype == 0 ? 0x20 : 0x30, subtype == 0 ? 0x20 : 0x30,
                    topHeight - 1, topHeight, true);
        }
    }

    private void button(NativeContext context) {
        for (var player : context.players()) {
            if (kind == Kind.BUTTON) solid(player, 0xC, 0xC, 1, 2, true);
            else solid(player, 0x13, 0xC, 4, 5, false);
        }
        int index = subtype & 0xF;
        int previous = context.trigger(index);
        if (standingMask != 0) {
            if (previous == 0) { context.playSfx(Sonic3kSfx.SWITCH.id); delay = 0; }
            context.setTrigger(index, previous | 1);
            frame = 1;
            if (--delay < 0) {
                delay = 3;
                if (kind == Kind.BUTTON) {
                    boolean alternate = context.paletteColor(2, 14) == 0x2E;
                    context.setPaletteColor(2, 14, alternate ? 0xC2E : 0x2E);
                    context.setPaletteColor(2, 15, alternate ? 0xA0E : 0xA);
                }
            }
        } else {
            frame = 0;
            if (kind == Kind.BUTTON || (subtype & 0x10) == 0) context.setTrigger(index, previous & ~1);
            if (kind == Kind.BUTTON) {
                context.setPaletteColor(2, 14, 0x2E);
                context.setPaletteColor(2, 15, 0xA);
            }
        }
    }

    private void retractingSpring(NativeContext context) {
        if ((subtype & 0x80) == 0) {
            if (delay != 0) {
                // sub_23CDC reads the carried BuildSprites bit7 after its timer reaches zero.
                if (--delay == 0 && nativeVisible) context.playSfx(Sonic3kSfx.SPIKE_MOVE.id);
            } else if (retracting) {
                int next = extension - 0x800;
                if (next < 0) { extension = 0; retracting = false; delay = 60; }
                else extension = next;
            } else {
                extension += 0x800;
                if (extension >= 0x1800) { extension = 0x1800; retracting = true; delay = 60; }
            }
        } else if (delay != 0) {
            if (--delay == 0) retracting = false;
        } else if (reverseTrigger) {
            if (retracting) {
                extension = Math.min(0x1800, extension + 0x800);
                if (extension == 0x1800) delay = 180;
            } else if (extension != 0) {
                extension = Math.max(0, extension - 0x800);
                if (extension == 0) switchSpringDirection();
            }
        } else if (retracting) {
            int next = extension - 0x800;
            if (next < 0) { extension = 0; delay = 180; }
            else extension = next;
        } else if (extension != 0x1800) {
            extension = Math.min(0x1800, extension + 0x800);
            if (extension == 0x1800) switchSpringDirection();
        }
        int orientation = (subtype >>> 4) & 7;
        int offset = extension >>> 8;
        if (orientation == 1 || orientation >= 4) x = (originX + offset) & 0xFFFF;
        else y = (originY + offset) & 0xFFFF;
        spring(context);
    }

    private void switchSpringDirection() {
        int orientation = subtype & 0xF0;
        if (orientation != 0xE0 && orientation != 0xF0) return;
        int delta = orientation == 0xE0 ? 0x16 : -0x16;
        originY = (originY + delta) & 0xFFFF;
        y = (y + delta) & 0xFFFF;
        subtype ^= 0x10;
        // The allocated base negates its own $32 when its parent's direction changes.
        baseOffsetY ^= 1;
    }

    private void springBase(NativeContext context) {
        if (nativeParent == null || nativeParent.isDestroyed()) { setDestroyed(true); return; }
        x = (nativeParent.x + baseOffsetX) & 0xFFFF;
        int dy = nativeParent.baseOffsetY == 0 ? baseOffsetY : -baseOffsetY;
        y = (nativeParent.y + dy) & 0xFFFF;
        if ((subtype & 0x80) != 0 && context.trigger(subtype & 0xF) != 0) nativeParent.retracting = true;
        if (frame == 0) return;
        int halfWidth = frame == 1 ? 8 : 0xC;
        int halfHeight = frame == 1 ? 0xC : 8;
        for (var player : context.players()) solid(player, halfWidth + 7, halfWidth, halfHeight, halfHeight + 1, false);
    }

    private void spring(NativeContext context) {
        int orientation = (subtype >>> 4) & 7;
        boolean horizontal = orientation == 1 || orientation == 3;
        boolean down = orientation == 2 || orientation == 5 || orientation == 7;
        int speed = kind == Kind.RETRACTING_SPRING ? 0x800 : ((subtype & 2) == 0 ? 0x1000 : 0xA00);
        for (var player : context.players()) {
            Contact hit = solid(player, horizontal ? 0xF : 0x13, horizontal ? 8 : 0x10,
                    horizontal ? 0xC : 8, horizontal ? 0xD : down ? 9 : 0x10, false);
            if (horizontal) {
                boolean launchSide = xFlip() ? (short) (player.centreX() - x) < 0
                        : (short) (player.centreX() - x) >= 0;
                if (hit != Contact.SIDE || !launchSide) continue;
                int vx = xFlip() ? -speed : speed;
                player.setCentreNative((player.centreX() + (xFlip() ? 4 : -4)) & 0xFFFF, player.centreY());
                player.setXVelocity(vx);
                player.setGroundVelocity(vx);
                player.setFacingLeft(xFlip());
                player.setMoveLock(15);
                if (!player.rolling()) player.setAnimation(0);
                if ((subtype & 0x80) != 0) player.setYVelocity(0);
                flipAndLayer(player, true);
                player.setPushing(false);
                player.setDoubleJumpFlag(0);
                animation = 3;
            } else {
                if (hit != (down ? Contact.BOTTOM : Contact.TOP)) continue;
                int vy = down ? (speed == 0x1000 ? 0xD00 : speed) : -speed;
                player.setCentreNative(player.centreX(), (player.centreY() + (down ? -4 : 8)) & 0xFFFF);
                player.setYVelocity(vy);
                releaseRide(player);
                player.setAirborne(true);
                player.setJumping(false);
                if (down) player.setDoubleJumpFlag(0);
                else { player.setSpinDashFlag(0); player.setAnimation(0x10); }
                player.setRoutine(2);
                if ((subtype & 0x80) != 0) player.setXVelocity(0);
                flipAndLayer(player, false);
                animation = 1;
            }
            context.playSfx(Sonic3kSfx.SPRING.id);
        }
        animate(context);
    }

    private void flipAndLayer(S3kCompetitionPlayer player, boolean horizontal) {
        if ((subtype & 1) != 0) {
            player.setGroundVelocity(player.facingLeft() ? -1 : 1);
            player.setFlipAngle(player.facingLeft() ? -1 : 1);
            player.setAnimation(0);
            player.setFlipsRemaining(horizontal ? ((subtype & 2) == 0 ? 3 : 1) : ((subtype & 2) == 0 ? 1 : 0));
            player.setFlipSpeed(horizontal ? 8 : 4);
        }
        if ((subtype & 0xC) == 4) player.setSolidBits(0xC, 0xD);
        if ((subtype & 0xC) == 8) player.setSolidBits(0xE, 0xF);
    }

    private void fan(NativeContext context) {
        int inner = ((subtype & 0xF) + 8) << 4;
        for (var player : context.players()) {
            if (player.routine() >= 4 || player.objectControl() != 0) continue;
            int dx = (player.centreX() - x + 0x14) & 0xFFFF;
            int dy = (context.oscillationByte(0x16) + player.centreY() + inner - y) & 0xFFFF;
            if (dx >= 0x28 || dy >= inner + 0x30) continue;
            int distance = (dy - inner) & 0xFFFF;
            if (dy >= inner) distance = ((~distance) << 1) & 0xFFFF;
            int lift = ((short) (-(distance + inner))) >> 6;
            player.setCentreNative(player.centreX(), (player.centreY() + lift) & 0xFFFF);
            player.setAirborne(true);
            player.setRollingJump(false);
            player.setYVelocity(0);
            player.setDoubleJumpFlag(0);
            player.setJumping(false);
            player.setGroundVelocity(1);
            if (player.flipAngle() == 0) {
                player.setFlipAngle(1);
                player.setAnimation(0);
                player.setFlipsRemaining(0x7F);
                player.setFlipSpeed(8);
            }
        }
        frame = (frame + 1) % 3;
    }

    private void bumper(NativeContext context) {
        for (var player : context.players()) {
            if ((pendingTouches & (1 << player.slot())) == 0) continue;
            int angle = (TrigLookupTable.calcAngle((short) (x - player.centreX()), (short) (y - player.centreY()))
                    + ((context.levelFrameCounter() >>> 8) & 3)) & 0xFF;
            player.setXVelocity((-0x700 * TrigLookupTable.cosHex(angle)) >> 8);
            player.setYVelocity((-0x700 * TrigLookupTable.sinHex(angle)) >> 8);
            releaseRide(player);
            player.setAirborne(true);
            player.setRollingJump(false);
            player.setPushing(false);
            player.setJumping(false);
            animation = 1;
            context.playSfx(Sonic3kSfx.SMALL_BUMPERS.id);
        }
        pendingTouches = 0;
        animate(context);
        listedForTouch = true;
    }

    private void mud(NativeContext context) {
        int halfWidth = (subtype << 3) & 0xFF;
        for (var player : context.players()) {
            int mask = 1 << player.slot();
            int depth = player.slot() == 0 ? mudP1 : mudP2;
            if ((standingMask & mask) != 0) {
                if (depth == 0) {
                    depth = 0x30;
                    releaseRide(player);
                    player.setDead(true);
                } else depth--;
            } else if (depth < 0x30) depth = (depth + 2) & 0xFF;
            if (player.slot() == 0) mudP1 = depth; else mudP2 = depth;
            solid(player, halfWidth, halfWidth, (depth >>> 1) - 1, depth >>> 1, true);
        }
    }

    private void pathSwap(NativeContext context) {
        boolean horizontalLine = (subtype & 4) != 0;
        int extent = 0x20 << (subtype & 3);
        for (var player : context.players()) {
            int mask = 1 << player.slot();
            int axis = horizontalLine ? player.centreY() : player.centreX();
            int marker = horizontalLine ? y : x;
            boolean side = Integer.compareUnsigned(axis & 0xFFFF, marker) >= 0;
            if ((knownSides & mask) == 0) {
                knownSides |= mask;
                // Init records strictly beyond the marker; the same init pass then executes its crossing routine.
                if (Integer.compareUnsigned(axis & 0xFFFF, marker) > 0) previousSides |= mask;
            }
            boolean previous = (previousSides & mask) != 0;
            if (side == previous) continue;
            if (side) previousSides |= mask; else previousSides &= ~mask;
            int perpendicular = (short) ((horizontalLine ? player.centreX() - x : player.centreY() - y));
            if (perpendicular < -extent || perpendicular >= extent || Math.abs((short) (axis - marker)) >= 0x40
                    || ((subtype & 0x80) != 0 && player.airborne())) continue;
            if (!xFlip()) {
                boolean secondary = (subtype & (side ? 8 : 0x10)) != 0;
                player.setSolidBits(secondary ? 0xE : 0xC, secondary ? 0xF : 0xD);
            }
            player.setHighPriority((subtype & (side ? 0x20 : 0x40)) != 0);
        }
    }

    private void dripper(NativeContext context) {
        if (xFlip() || ((context.levelFrameCounter() + subtype) & 0x7F) != 0) return;
        context.allocateAfter(this, () -> {
            var child = child(Kind.DROPLET, 0, 0);
            child.frame = 1;
            return child;
        });
    }

    private void droplet(NativeContext context) {
        move();
        yVelocity = (short) (yVelocity + 8);
        if (context.floorDistance(x, (y + 4) & 0xFFFF) < 0) {
            kind = Kind.SPLASH;
            xVelocity = 0x100;
            yVelocity = ((short) -yVelocity) >> 2;
            frame = 2;
            // Parent has $3C=0: its first splash update wraps the byte to FF.
            lifetime = 0;
            for (int i = 0; i < 3; i++) {
                var allocated = context.allocateAfter(this, () -> child(Kind.SPLASH, 0, 0));
                if (!(allocated instanceof CompetitionArenaObjectInstance piece)) continue;
                piece.xFraction = xFraction;
                piece.yFraction = yFraction;
                piece.yVelocity = yVelocity;
                piece.frame = 2;
                int random = context.randomWord() & 0xFFFF;
                piece.xVelocity = (random & 0x1FF) - 0x100;
                piece.lifetime = ((Integer.rotateLeft(random | (random << 16), 4)) & 0xF) + 0x1A;
                // Native sub_381F2 reduces the parent's velocity after copying, only after allocation succeeds.
                yVelocity = (short) (yVelocity - 0x10);
            }
        }
        int wrap = context.wrapYMask();
        if (wrap >= 0) y &= wrap;
        if ((short) y > context.bottomY() + 0x60) setDestroyed(true);
    }

    private void splash(NativeContext context) {
        move();
        yVelocity = (short) (yVelocity + 8);
        lifetime = (lifetime - 1) & 0xFF;
        if (lifetime == 0) setDestroyed(true);
    }

    private void invisiblePlatform(NativeContext context) {
        // loc_1ECAA (sonic3k.asm:42750-42760) retains the campaign +$B side allowance in competition.
        for (var player : context.players()) solid(player, width + 0xB, width,
                nativeHeight, nativeHeight + 1, false);
    }

    private void rock(NativeContext context) {
        int p1Animation = context.players().getFirst().animation();
        int p2Animation = context.players().size() > 1 ? context.players().get(1).animation() : -1;
        for (var player : context.players()) solid(player, width + 0xB, width,
                nativeHeight, nativeHeight + 1, false);
        int standing = standingMask;
        if (standing == 0 || ((standing & 1) == 0 || p1Animation != 2)
                && ((standing & 2) == 0 || p2Animation != 2)) return;
        for (var player : context.players()) {
            if ((standing & (1 << player.slot())) == 0) continue;
            int oldAnimation = player.slot() == 0 ? p1Animation : p2Animation;
            if (oldAnimation == 2) {
                player.setRolling(true);
                player.setRadii(7, 0xE);
                player.setAnimation(2);
                player.setYVelocity(-0x300);
            }
            player.setAirborne(true);
            player.setStatus(player.status() & ~8);
            player.setRoutine(2);
        }
        standingMask = 0;
        context.allocateAfter(this, () -> {
            var shard = child(Kind.ROCK_SHARD, 0, 0);
            shard.width = width;
            shard.nativeHeight = nativeHeight;
            shard.nativeVisible = nativeVisible;
            shard.frame = 7;
            return shard;
        });
        int breakX = x;
        int breakY = y;
        List<RockDebrisPiece> pieces = context.rockDebris();
        if (pieces.size() != 6) throw new IllegalStateException("Native EMZ rock requires six ROM debris rows");
        kind = Kind.ROCK_DEBRIS;
        frame = 3;
        for (int i = 0; i < pieces.size(); i++) {
            CompetitionArenaObjectInstance fragment;
            if (i == 0) fragment = this;
            else {
                var allocated = context.allocateAfter(this, () -> child(Kind.ROCK_DEBRIS, 0, 0));
                if (!(allocated instanceof CompetitionArenaObjectInstance newFragment)) break;
                fragment = newFragment;
                // FixBugs=0 repeats width_pixels=$18 and leaves the fresh child's height byte zero.
                // The fixed branch writes height_pixels=$18; the parent retains its old height $F.
                fragment.nativeHeight = 0;
                fragment.nativeVisible = nativeVisible;
            }
            RockDebrisPiece row = pieces.get(i);
            fragment.x = (breakX + row.dx()) & 0xFFFF;
            fragment.y = (breakY + row.dy()) & 0xFFFF;
            fragment.width = 0x18;
            fragment.xVelocity = (short) row.xVelocity();
            fragment.yVelocity = (short) row.yVelocity();
            fragment.frame = 3 + (i & 3);
        }
        context.playSfx(Sonic3kSfx.COLLAPSE.id);
        // AIZLRZEMZRock_CompetitionBreak falls through into its first debris movement in the parent slot.
        rockDebris();
    }

    private void rockShard() {
        if (!nativeVisible) {
            kind = Kind.ROCK;
            frame = 0;
            // Original intact geometry was copied before debris changed the parent's dimensions.
            subtype = 0x31;
        }
    }

    private void rockDebris() {
        move();
        yVelocity = (short) (yVelocity + 0x18);
        if (!nativeVisible) setDestroyed(true);
    }

    private void move() {
        int fixedX = (x << 8) | xFraction;
        int fixedY = (y << 8) | yFraction;
        fixedX += (short) xVelocity;
        fixedY += (short) yVelocity;
        x = (fixedX >>> 8) & 0xFFFF;
        y = (fixedY >>> 8) & 0xFFFF;
        xFraction = fixedX & 0xFF;
        yFraction = fixedY & 0xFF;
    }

    private void animate(NativeContext context) {
        if (animationState == null) {
            SpriteAnimationSet set = context.animations(kind.artKey);
            if (set == null) return;
            animationState = new ObjectAnimationState(set, animation, frame);
        }
        if (animationState.getAnimId() != animation) animationState.setAnimId(animation);
        animationState.update();
        animation = animationState.getAnimId();
        frame = animationState.getMappingFrame();
    }

    /** SolidObjectFull2_1P / SolidObjectTop_1P in miniature coordinates; all widths are native d1. */
    private Contact solid(S3kCompetitionPlayer p, int rangeX, int objectHalfWidth,
                          int objectHalfHeight, int standingHeight, boolean topOnly) {
        int mask = 1 << p.slot();
        int dx = (short) (p.centreX() - x);
        if ((standingMask & mask) != 0) {
            if (p.standingObject() == nativeIdentity() && (p.status() & 8) != 0
                    && !p.airborne() && dx >= -rangeX && dx < rangeX) {
                p.setCentreNative(p.centreX(), (y - standingHeight - p.yRadius()) & 0xFFFF);
                return Contact.TOP;
            }
            standingMask &= ~mask;
            if (p.standingObject() == nativeIdentity()) p.setStatus((p.status() & ~8) | 2);
            return Contact.NONE;
        }
        if (p.routine() >= 6 || (p.objectControl() & 0x80) != 0) return Contact.NONE;
        if (!topOnly && usesVisibleFullRoutine() && !nativeVisible) return Contact.NONE;
        if (dx < -rangeX || dx > rangeX || (topOnly && dx == rangeX)) return Contact.NONE;
        if (topOnly) {
            if (p.yVelocity() < 0) return Contact.NONE;
            int distance = (short) (y - standingHeight - p.centreY() - p.yRadius() - 4);
            if (distance > 0 || distance < -0x10) return Contact.NONE;
            p.setCentreNative(p.centreX(), (p.centreY() + distance + 3) & 0xFFFF);
            setRide(p);
            return Contact.TOP;
        }
        int combinedHeight = objectHalfHeight + p.yRadius();
        int defaultHeight = objectHalfHeight + 0xB;
        int dy = (p.centreY() - y + 4 + combinedHeight) & 0xFFF;
        int totalHeight = combinedHeight + defaultHeight;
        if (dy >= totalHeight) { p.setPushing(false); return Contact.NONE; }
        int penetrationX = dx <= 0 ? dx + rangeX : dx - rangeX;
        int penetrationY = dy <= combinedHeight ? dy : dy - 4 - totalHeight;
        if (Math.abs(penetrationX) <= Math.abs(penetrationY) && Math.abs(penetrationY) > 4) {
            if ((penetrationX > 0 && p.xVelocity() >= 0) || (penetrationX < 0 && p.xVelocity() < 0)) {
                p.setXVelocity(0);
                p.setGroundVelocity(0);
            }
            p.setCentreNative((p.centreX() - penetrationX) & 0xFFFF, p.centreY());
            p.setPushing(!p.airborne());
            return Contact.SIDE;
        }
        if (penetrationY < 0) {
            if (p.airborne() || p.yVelocity() < 0) {
                if (p.airborne()) p.setGroundVelocity(0);
                p.setCentreNative(p.centreX(), (p.centreY() - penetrationY) & 0xFFFF);
                p.setYVelocity(0);
            } else if (p.yVelocity() == 0 && Math.abs(penetrationX) >= 0x10) p.setDead(true);
            return Contact.BOTTOM;
        }
        if (penetrationY >= 0x10 || dx < -objectHalfWidth || dx >= objectHalfWidth) return Contact.NONE;
        p.setCentreNative(p.centreX(), (p.centreY() - penetrationY + 3) & 0xFFFF);
        if (p.yVelocity() < 0) return Contact.NONE;
        setRide(p);
        return Contact.TOP;
    }

    private void setRide(S3kCompetitionPlayer player) {
        boolean wasAirborne = player.airborne();
        standingMask |= 1 << player.slot();
        player.setStandingObject(nativeIdentity());
        player.setAngle(0);
        player.setYVelocity(0);
        player.setGroundVelocity(player.xVelocity());
        player.setAirborne(false);
        if (wasAirborne) player.land(0);
    }

    private void releaseRide(S3kCompetitionPlayer player) {
        boolean ownsRide = (standingMask & (1 << player.slot())) != 0
                || player.standingObject() == nativeIdentity() && (player.status() & 8) != 0;
        standingMask &= ~(1 << player.slot());
        if (ownsRide && player.standingObject() == nativeIdentity())
            player.setStatus((player.status() & ~8) | 2);
    }

    private boolean touches(S3kCompetitionPlayer player, int halfWidth, int halfHeight) {
        if (player.routine() >= 4) return false;
        // TouchResponse_CompetitionMode (sonic3k.asm:20632-20643): X half-width4, y_radius-3.
        int dx = Math.abs((short) (player.centreX() - x));
        int dy = Math.abs((short) (player.centreY() - y));
        return dx <= halfWidth + 4 && dy <= halfHeight + Math.max(0, player.yRadius() - 3);
    }

    /**
     * Player-slot TouchResponse_CompetitionMode pass, before arena object execution.
     * The host calls this in collision-list order for each player after their own movement.
     * Special contacts accumulate the ROM collision_property bits; harmful children hurt immediately.
     */
    public boolean touchNativePlayer(NativeContext context, S3kCompetitionPlayer player) {
        if (!listedForTouch || isDestroyed() || !player.touchEnabled()) return false;
        if (kind == Kind.BUMPER || kind == Kind.BALLOON) {
            if (touches(player, 8, 8)) {
                pendingTouches |= 1 << player.slot();
                return true;
            }
        } else if ((kind == Kind.BLADE_LEFT || kind == Kind.BLADE_RIGHT) && touches(player, 0x10, 4)) {
            context.hurt(player, x);
            return true;
        }
        return false;
    }

    public int nativeIdentity() {
        if (getSlotIndex() < 0) throw new IllegalStateException("Competition objects require an allocated native SST slot");
        return getSlotIndex();
    }
    public Kind kind() { return kind; }
    public int mappingFrame() { return frame; }
    public int extensionWord() { return extension; }
    public int delayTicks() { return delay; }
    public int nativeStandingMask() { return standingMask; }
    public int nativeSubtype() { return subtype; }
    public int nativeXVelocity() { return (short) xVelocity; }
    public int nativeYVelocity() { return (short) yVelocity; }
    public int lifetimeByte() { return lifetime; }
    public int mudDepth(int slot) { return slot == 0 ? mudP1 : mudP2; }

    /** Live native collision byte; list publication is a separate SST-routine decision. */
    @Override public int getCollisionFlags() {
        return switch (kind) {
            case BUMPER, BALLOON -> 0xD7;
            case BLADE_LEFT, BLADE_RIGHT -> 0xA6;
            default -> 0;
        };
    }
    @Override public int getCollisionProperty() { return pendingTouches & 0xFF; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return listedForTouch; }
    // Touch_Loop reads collision_flags directly, without testing render_flags bit7.
    @Override public boolean requiresRenderFlagForTouch() { return false; }

    private boolean xFlip() { return (spawn.renderFlags() & 1) != 0; }

    private boolean usesVisibleFullRoutine() {
        return kind == Kind.BLADE || kind == Kind.ELEPHANT || kind == Kind.FLOOR_BUTTON
                || kind == Kind.SPRING_BASE || kind == Kind.ROCK;
    }

    /** Publish the carried native BuildSprites bit7 after both views have been built, never during updates. */
    public void refreshNativeVisibility(boolean visible) { if (renderPublished) nativeVisible = visible; }
    public boolean nativeVisible() { return nativeVisible; }
    public boolean nativeDrawPublished() { return renderPublished; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOnScreenHalfWidth() { return width; }
    @Override public int getOnScreenHalfHeight() { return nativeHeight; }
    @Override public int getPriorityBucket() { return RenderPriority.clamp(kind.bucket); }
    @Override public boolean isHighPriority() {
        if (kind == Kind.DRIPPER && xFlip() || kind == Kind.ROCK || kind == Kind.ROCK_SHARD || kind == Kind.ROCK_DEBRIS) return true;
        int orientation = (subtype >>> 4) & 7;
        return kind == Kind.SPRING && nativeZoneId == 0x12 && orientation == 0;
    }
    @Override public boolean isPersistent() { return true; }

    /** World position of the submitted mapping, distinct from Elephant's solid/visibility centre. */
    public int nativeRenderX() {
        return kind == Kind.ELEPHANT
                ? (originX + (xFlip() ? (byte) (extension >>> 8) : -(byte) (extension >>> 8))) & 0xFFFF : x;
    }
    public int nativeRenderY() { return y; }
    public boolean nativeRenderXFlip() { return xFlip(); }
    public boolean nativeRenderYFlip() {
        if (kind == Kind.ELEPHANT) return false;
        if (kind == Kind.SPRING || kind == Kind.RETRACTING_SPRING) {
            int orientation = (subtype >>> 4) & 7;
            return orientation == 2 || orientation == 5 || orientation == 7;
        }
        return (spawn.renderFlags() & 2) != 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!renderPublished) return;
        String key = artKey();
        if (key == null || kind == Kind.SPRING_BASE && frame == 0) return;
        var renderer = getRenderer(key);
        if (renderer == null) return;
        // Elephant's native multi-draw branch submits only sub2; its main frame is empty.
        renderer.drawFrameIndex(frame, nativeRenderX(), nativeRenderY(),
                nativeRenderXFlip(), nativeRenderYFlip());
    }

    /** Palette/mapping variants are distinct ROM-backed sheets, not renderer-time tile guesses. */
    public String artKey() {
        if (kind == Kind.DRIPPER && xFlip()) return "competition_dripper_static";
        if (kind == Kind.SAND_BAR && subtype != 0) return "competition_sand_bar_wide";
        if (kind == Kind.SPRING_BASE && nativeZoneId == 0x12) return "competition_retracting_spring_base_emz";
        if (kind == Kind.SPRING || kind == Kind.RETRACTING_SPRING) {
            int orientation = (subtype >>> 4) & 7;
            if (kind == Kind.SPRING && (subtype & 2) != 0)
                return orientation == 1 || orientation == 3 ? "competition_spring_horizontal_yellow" : "competition_spring_yellow";
            return orientation == 1 || orientation == 3 ? "competition_spring_horizontal" : "competition_spring";
        }
        return kind.artKey;
    }
}
