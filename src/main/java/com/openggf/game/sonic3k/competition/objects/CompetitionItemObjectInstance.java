package com.openggf.game.sonic3k.competition.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.competition.S3kCompetitionPlayer;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession;
import com.openggf.game.sonic3k.competition.S3kCompetitionDamage;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * S3KL $64: native wheel, dropped items and rolling attack. ROM Obj_2PItem/
 * sub_3643C/sub_36926 (sonic3k.asm:73281-73938). Miniature touch is deliberately
 * separate from ordinary character touch: TouchResponse_CompetitionMode uses
 * an eight-pixel-wide player box and y_radius-3 (20595-20609).
 * All kinds draw unflipped. The wheel's angleStep controls its orbit, while
 * updateProjectile selects an angle-derived mapping pose; neither supplies a
 * horizontal body mirror for the item or its independently allocated effects.
 */
public final class CompetitionItemObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, TouchResponseProvider, NativeSpriteSubmissionSource, ObjectVisualFacing.PoseOwned {
    public static final String ART_KEY = "competition_item";
    public enum Kind { WHEEL, EFFECT, DROPPING_SLIDE, SLIDE, DROPPING_SPRING, SPRING,
        DROPPING_PROJECTILE, PROJECTILE }

    private Kind kind = Kind.WHEEL;
    private int x;
    private int y;
    private int xFraction;
    private int yFraction;
    private int xVelocity;
    private int yVelocity;
    private int groundVelocity;
    private int angle;
    private int angleStep;
    private int topSolidBit = 0xC;
    private int lrbSolidBit = 0xD;
    private int animation = 7;
    private int previousAnimation = -1;
    private int animationFrame;
    private int animationTimer;
    private int mappingFrame;
    private int collisionFlags = 0xC7;
    private boolean publishedTouch;
    private boolean drawPublished;
    private boolean animationFinished;
    private boolean airborne;
    private boolean highPriority;
    private int pendingTouchMask;
    private int slidePlayer = -1;
    private int slideTicks;

    public CompetitionItemObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CompetitionItem");
        x = spawn.x(); y = spawn.y(); angle = spawn.subtype();
        angleStep = (spawn.renderFlags() & 1) == 0 ? 1 : -1;
    }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        S3kCompetitionSession session = WorldSessionPolicyAccess.getService(
                services().worldSession(), S3kCompetitionSession.class);
        if (session != null) {
            updateNative(session);
            if (drawPublished) NativeSpriteSubmissionAccess.submitSpilling(
                    services().objectManager(), getSlotIndex(), getPriorityBucket());
        }
    }

    public void updateNative(S3kCompetitionSession session) {
        if (isDestroyed()) return;
        drawPublished = false;
        int touches = pendingTouchMask;
        pendingTouchMask = 0;
        publishedTouch = false;
        switch (kind) {
            case WHEEL -> {
                for (S3kCompetitionPlayer player : session.players())
                    if ((touches & (1 << player.slot())) != 0) collectNative(session, player.slot());
                // loc_363D0 uses signed WORD intermediate arithmetic: 56*sin/cos.
                int dx = (short) (TrigLookupTable.cosHex(angle) * 56) >> 8;
                int dy = (short) (TrigLookupTable.sinHex(angle) * 56) >> 8;
                x = (((spawn.x() + dx) & session.xWrapMask()) + 0x400) & 0xFFFF;
                y = (spawn.y() + dy) & 0xFFFF;
                if (session.yWrapMask() >= 0) y &= session.yWrapMask();
                angle = (angle + angleStep) & 0xFF;
                animate(session);
                publishedTouch = true;
            }
            case EFFECT -> {
                animate(session);
                if (animationFinished) ObjectLifetimeOps.expireDynamic(this);
            }
            case DROPPING_SLIDE, DROPPING_SPRING, DROPPING_PROJECTILE -> drop(session);
            case SPRING -> {
                for (S3kCompetitionPlayer player : session.players()) {
                    if ((touches & (1 << player.slot())) != 0 && player.topSolidBit() == topSolidBit) {
                        animation = 10;
                        player.setYVelocity(-0xA00); player.setAirborne(true);
                        player.setJumping(false); player.setGroundVelocity(player.facingLeft() ? -1 : 1);
                        player.setFlipAngle(player.facingLeft() ? -1 : 1);
                        player.setFlipsRemaining(0); player.setFlipSpeed(4); player.setAnimation(0);
                        player.setRollJump(false); player.setPushing(false);
                        session.playSfx(Sonic3kSfx.SMALL_BUMPERS);
                    }
                }
                animate(session);
                if (animationFinished) ObjectLifetimeOps.expireDynamic(this);
                publishedTouch = animation != 9;
            }
            case SLIDE -> { updateSlide(session, touches); publishedTouch = true; }
            case PROJECTILE -> { updateProjectile(session, touches); publishedTouch = true; }
        }
        // Native Draw_Sprite queues only an executed, surviving routine. In particular the
        // reserved projectile cannot draw until its first SST dispatch (slot $5E
        // follows the wheel in the native pass); allocation alone never publishes it to Render_Sprites.
        drawPublished = !isDestroyed();
        if (isDestroyed()) publishedTouch = false;
    }

    /** Player-slot prepass, before the object SST pass; collision uses last frame's position. */
    public boolean touchNativePlayer(S3kCompetitionPlayer player) {
        if (!publishedTouch || collisionFlags == 0 || isDestroyed() || !overlaps(player,x,y,6,6)) return false;
        pendingTouchMask |= 1 << player.slot();
        return true; // Touch_Process returns after the first overlapping native list entry.
    }

    /** Collision bits are consumed P1 then P2, so the first collector empties the wheel. */
    public void collectNative(S3kCompetitionSession session, int slot) {
        if (kind != Kind.WHEEL || animation == 7 || collisionFlags == 0) return;
        int effect = animation;
        S3kCompetitionPlayer player = session.player(slot);
        animation = 7;
        collisionFlags = 0;
        if (effect <= 3) {
            session.applyImmediateItem(slot, effect);
            int childAnim = effect == 3 ? 8 : 9;
            session.allocateAfter(this, () -> child(Kind.EFFECT, childAnim, player));
        } else if (effect == 4) {
            // loc_36612 overwrites the reserved slot; dynamic allocation is not consulted.
            session.overwriteProjectile(() -> {
                CompetitionItemObjectInstance child = child(Kind.DROPPING_PROJECTILE, 4, player);
                child.highPriority = session.playerHighPriority(slot);
                return child;
            });
        } else {
            Kind next = effect == 5 ? Kind.DROPPING_SLIDE : Kind.DROPPING_SPRING;
            session.allocateAfter(this, () -> child(next, effect, player));
        }
    }

    private CompetitionItemObjectInstance child(Kind childKind, int childAnimation, S3kCompetitionPlayer player) {
        CompetitionItemObjectInstance child = new CompetitionItemObjectInstance(
                new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), spawn.renderFlags(), false, y & 0xFFF));
        child.kind = childKind; child.animation = childAnimation;
        child.mappingFrame = mappingFrame; child.animationFrame = animationFrame;
        child.animationTimer = animationTimer; child.previousAnimation = previousAnimation;
        child.topSolidBit = player.topSolidBit(); child.lrbSolidBit = player.lrbSolidBit();
        child.collisionFlags = 0;
        if (childKind == Kind.DROPPING_PROJECTILE) child.groundVelocity = -0x400;
        return child;
    }

    private void drop(S3kCompetitionSession session) {
        move(true);
        int distance = session.itemTerrain().floorDistance(x, y, 8, topSolidBit);
        if (distance < 0) {
            y = (y + distance) & 0xFFFF;
            kind = switch (kind) {
                case DROPPING_SLIDE -> Kind.SLIDE;
                case DROPPING_SPRING -> Kind.SPRING;
                case DROPPING_PROJECTILE -> Kind.PROJECTILE;
                default -> throw new IllegalStateException();
            };
            collisionFlags = 0xC7;
        }
        if (session.yWrapMask() >= 0) y &= session.yWrapMask();
        if ((short) y > session.bottomY() + 0x60) ObjectLifetimeOps.expireDynamic(this);
        animate(session);
    }

    private void updateSlide(S3kCompetitionSession session, int touches) {
        if (collisionFlags != 0) for (S3kCompetitionPlayer player : session.players()) {
            int velocity = player.groundVelocity();
            if (player.topSolidBit() != topSolidBit || (player.statusSecondary() & 0x80) != 0
                    || player.airborne() || velocity == 0 || (touches & (1 << player.slot())) == 0) continue;
            animation = 9; collisionFlags = 0;
            if (Math.abs(velocity) < 0x200) velocity = (short) (velocity + (velocity < 0 ? -0x200 : 0x200));
            player.setGroundVelocity((short) (velocity << 1));
            player.setAnimation(0x1B); player.setStatusSecondary(player.statusSecondary() | 0x80);
            slidePlayer = player.slot(); slideTicks = 60;
        }
        animate(session);
        if (slideTicks != 0) {
            S3kCompetitionPlayer player = session.player(slidePlayer);
            if (--slideTicks != 0) {
                int velocity = player.groundVelocity();
                if (velocity < 0) player.setGroundVelocity(Math.min(0, velocity + 0x20));
                else player.setGroundVelocity(Math.max(0, velocity - 0x20));
                if (player.groundVelocity() != 0) return;
            }
            player.setMoveLock(5); player.setStatusSecondary(player.statusSecondary() & 0x7F);
            // The native timer remains live after a zero-velocity release; it still
            // decrements until expiry unless animation completion deletes the item.
        }
        if (animationFinished) ObjectLifetimeOps.expireDynamic(this);
    }

    private void updateProjectile(S3kCompetitionSession session, int touches) {
        for (S3kCompetitionPlayer player : session.players()) {
            if (player.topSolidBit() != topSolidBit || (touches & (1 << player.slot())) == 0) continue;
            kind = Kind.EFFECT; animation = 11;
            S3kCompetitionDamage.projectile(session,session.damageContext(),player,x);
        }
        // Changing the next routine does not skip the current loc_368D0 tail:
        // both touch bits, movement and angle-derived mapping still run this pass.
        boolean landed = false;
        if (!airborne) {
            xVelocity = (short) ((TrigLookupTable.cosHex(angle) * groundVelocity) >> 8);
            yVelocity = (short) ((TrigLookupTable.sinHex(angle) * groundVelocity) >> 8);
            move(false); wrapProjectile(session);
            S3kCompetitionSession.ItemGround ground = session.itemTerrain().followGround(
                    x, y, 4, 8, angle, groundVelocity, topSolidBit, lrbSolidBit);
            x = ground.centreX() & 0xFFFF; y = ground.centreY() & 0xFFFF;
            angle = ground.angle() & 0xFF; airborne = ground.airborne();
        } else {
            xVelocity = Math.max(-0x400, (short) (xVelocity - 0x10));
            move(true); wrapProjectile(session);
            int distance = session.itemTerrain().floorDistance(x, y, 8, topSolidBit);
            if (distance < 0) {
                y = (y + distance) & 0xFFFF; airborne = false; angle = 0;
                landed = true; // sub_36926 returns to its caller before the wall probe.
            }
            if (!landed && angle != 0) {
                if ((byte) angle < 0) angle = angle + 2 > 0xFF ? 0 : angle + 2;
                else angle = Math.max(0, angle - 2);
            }
        }
        if (!landed && angle == 0) {
            int distance = session.itemTerrain().leftWallDistance(x, y, 8, lrbSolidBit);
            if (distance < 0) {
                x = (x - distance) & 0xFFFF; kind = Kind.EFFECT; animation = 11; collisionFlags = 0;
            }
        }
        mappingFrame = (((angle + 0x10) & 0xFF) >> 5) + 0x11;
        if ((short) y > session.bottomY() + 0x60) ObjectLifetimeOps.expireDynamic(this);
    }

    private void wrapProjectile(S3kCompetitionSession session) {
        // Both wrap words are applied unconditionally by sub_36926.
        y &= session.projectileYWrapMask();
        x = ((x & session.xWrapMask()) + 0x400) & 0xFFFF;
    }

    private void move(boolean gravity) {
        int fixedX = (x << 16) | xFraction;
        int fixedY = (y << 16) | yFraction;
        fixedX += (short) xVelocity << 8;
        fixedY += (short) yVelocity << 8;
        x = fixedX >>> 16; xFraction = fixedX & 0xFFFF;
        y = fixedY >>> 16; yFraction = fixedY & 0xFFFF;
        if (gravity) yVelocity = (short) (yVelocity + 0x38);
    }

    private void animate(S3kCompetitionSession session) {
        if (animation != previousAnimation) {
            previousAnimation = animation; animationFrame = 0; animationTimer = 0;
        }
        animationTimer = (animationTimer - 1) & 0xFF;
        if ((byte) animationTimer >= 0) return;
        int table = animation * 2;
        int script = (session.itemAnimationByte(table) << 8) | session.itemAnimationByte(table + 1);
        animationTimer = session.itemAnimationByte(script);
        int opcode = session.itemAnimationByte(script + 1 + animationFrame);
        if (opcode == 0xFF) {
            animationFrame = 0; opcode = session.itemAnimationByte(script + 1);
        } else if (opcode == 0xFE) {
            animationFrame = (animationFrame - session.itemAnimationByte(script + 2 + animationFrame)) & 0xFF;
            opcode = session.itemAnimationByte(script + 1 + animationFrame);
        } else if (opcode == 0xFD) {
            animation = session.itemAnimationByte(script + 2 + animationFrame); return;
        } else if (opcode == 0xFC) {
            animationFinished = true; return;
        }
        mappingFrame = opcode;
        animationFrame = (animationFrame + 1) & 0xFF;
    }

    /** Inclusive rectangle comparisons match Touch_Width/Touch_Height's bhi gates. */
    private static boolean overlaps(S3kCompetitionPlayer player, int x, int y, int halfWidth, int halfHeight) {
        int dx = (short) (player.centreX() - x);
        int dy = (short) (player.centreY() - y);
        return dx >= -halfWidth - 4 && dx <= halfWidth + 4
                && dy >= -halfHeight - (player.yRadius() - 3)
                && dy <= halfHeight + player.yRadius() - 3;
    }

    public void replenish(int effect) {
        if (effect < 0 || effect > 6) throw new IllegalArgumentException("Native wheel effect out of range");
        if (kind == Kind.WHEEL) { animation = effect; collisionFlags = 0xC7; }
    }
    public boolean isWheel() { return kind == Kind.WHEEL; }
    public Kind kind() { return kind; }
    public int effect() { return animation; }
    public int angle() { return angle; }
    public int mappingFrame() { return mappingFrame; }
    public int collisionFlags() { return collisionFlags; }
    @Override public int getCollisionFlags() { return collisionFlags & 0xFF; }
    @Override public int getCollisionProperty() { return pendingTouchMask & 0xFF; }
    // The competition touch pass consumes its previous list even when no view drew the item.
    @Override public boolean requiresRenderFlagForTouch() { return false; }
    public boolean nativeDrawPublished() { return drawPublished; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return publishedTouch; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; } // Obj_2PItem has no camera-cull tail.
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x280); // Obj_2PItem's native priority word.
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawPublished || isDestroyed()) return;
        var renderer = getRenderer(ART_KEY);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, x, y, false, false);
    }
}
