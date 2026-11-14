package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.NativePositionOps;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Spring, object $0A ({@code ObjSpring}, Level/_Objects/Spring/Main.asm:130-482): upward,
 * sideways (subtype bit 2), downward (Y-flipped placement) and diagonal (subtype bit 3).
 * Subtype bit 1 selects the yellow spring ($F600 instead of $F000, palette line +1). The
 * moving spring ($0F) and the player-attached form ({@code oRoutine2 = 5}) are other lanes'.
 *
 * <p>Differences from Sonic 1's Obj41: the sideways spring sets the player's facing from the
 * spring's flip and clears {@code oAngle} (Spring/Main.asm:323-338) where Sonic 1 toggles facing; the upward
 * spring does not force the player's routine; the diagonal spring exists; each routine runs only
 * while the retained on-screen bit is set (Spring/Main.asm:136-137); the despawn check uses the initial X with
 * no on-screen exception (Spring/Main.asm:158-166). The routines with a $FC animation then reset (Spring/Main.asm:281-293).
 */
public final class CdSpring extends CdObject implements ReevaluatingSolidObject,
        ObjectVisualFacing.NonDirectional {
    private enum Kind { UP, SIDE, DOWN, DIAGONAL }

    /** ObjSpring_Speeds: $F000 red, $F600 yellow (Spring/Main.asm:243-244). */
    private static final int[] SPEEDS = {-0x1000, -0x0A00};

    private final Kind kind;
    private final int speed;
    private final int initialX;
    private boolean animating;
    private boolean resetPending;
    private CdAnimator animator;
    private int mapFrame;
    private com.openggf.level.objects.AbstractObjectInstance parent;
    private int parentXOffset;
    private int parentYOffset;

    public CdSpring(ObjectSpawn spawn) {
        super(spawn, "Spring");
        int subtype = spawn.subtype();
        // ObjSpring_Init (Spring/Main.asm:186-239).
        this.priority = 4;
        this.initialX = spawn.x();
        this.tileWord = CdObjectArtProvider.SPRING_TILE;
        if ((subtype & 4) != 0) {
            kind = Kind.SIDE;
            width = 8;
            yRadius = 0x10;
        } else if ((subtype & 8) != 0) {
            kind = Kind.DIAGONAL;
            width = 0x18;          // USA build (Spring/Main.asm:210-212)
            yRadius = 0xC;
        } else {
            kind = vFlip() ? Kind.DOWN : Kind.UP;
            width = 0x10;
            yRadius = 8;
        }
        if ((subtype & 2) != 0) tileWord |= 0x2000;    // bset #5,oTile: next palette line (Spring/Main.asm:232-234)
        this.speed = SPEEDS[(subtype >> 1) & 1];
    }

    /**
     * The spring a block carries ({@code oVar34} the parent, {@code oVar38}/{@code oVar39} the offsets: Collision Chaos'
     * hidden block, Level/Collision Chaos/Objects/Block/Main.asm:277-293): after its routine and DrawObject the spring takes its
     * parent's position plus the offsets (Object Spring follow, Spring/Main.asm:158-166 and the routine's tail).
     */
    public CdSpring attach(com.openggf.level.objects.AbstractObjectInstance parent, int xOffset, int yOffset) {
        this.parent = parent;
        this.parentXOffset = (byte) xOffset;
        this.parentYOffset = (byte) yOffset;
        return this;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        run(vIntRunCount, player);
        if (parent != null) {
            x = (parent.getX() + parentXOffset) & 0xFFFF;
            y = (parent.getY() + parentYOffset) & 0xFFFF;
        }
    }

    private void run(int vIntRunCount, PlayableEntity player) {
        if (kind == Kind.DIAGONAL && tileWord == CdObjectArtProvider.SPRING_TILE) {
            CdObjectArtProvider provider = artProvider();
            int tile = provider == null ? -1 : provider.objectTileId(CdObjectArtProvider.TILE_ID_DIAGONAL_SPRING,
                    subtype2());
            if (tile >= 0) tileWord = tile | (tileWord & 0x2000);
        }
        // ObjSpring: a routine runs only while the retained on-screen bit is set (Spring/Main.asm:130-141).
        if (!renderFlagOnScreen()) return;
        if (resetPending) {
            // ObjSpring_Reset_*: move.b #1,oPrevAnim; subq.b #4,oRoutine; frame 0 (Spring/Main.asm:288-293).
            resetPending = false;
            animating = false;
            mapFrame = 0;
            if (animator != null) animator.restart();
            return;
        }
        if (animating) {
            // ObjSpring_Anim_*: AnimateObject; its $FC advances to the reset routine (Spring/Main.asm:281-285).
            if (animator == null) animator = animator(e -> e.springAnimations(), 0);
            if (animator == null) return;
            if (animator.step() == 2) resetPending = true;
            else mapFrame = animator.mapFrame();
            return;
        }
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        switch (kind) {
            // ObjSpring_Main_Up: bsr ObjSpring_SolidObject; bne .Action (Spring/Main.asm:257-262).
            case UP -> { if (solidObject(ReevaluatingSolidObject.Entry.SOLID)) launchUp(sprite); }
            // ObjSpring_Main_Side: SolidObject, then btst #5,oFlags(a0) (Spring/Main.asm:304-315).
            case SIDE -> {
                solidObject(ReevaluatingSolidObject.Entry.SOLID);
                if (pushedBy(sprite)) launchSide(sprite);
            }
            // ObjSpring_Main_Down: BtmSolidObject; bne .Action (Spring/Main.asm:373-380).
            case DOWN -> { if (solidObject(ReevaluatingSolidObject.Entry.BOTTOM)) launchDown(sprite); }
            // ObjSpring_Main_Diag: SolidObject; bne, else btst #5,oFlags(a0) (Spring/Main.asm:414-425).
            case DIAGONAL -> {
                if (solidObject(ReevaluatingSolidObject.Entry.SOLID) || pushedBy(sprite)) launchDiagonal(sprite);
            }
        }
    }

    /** ObjSpring_Main_Up .Action (Spring/Main.asm:267-276). */
    private void launchUp(AbstractPlayableSprite player) {
        NativePositionOps.addYPosPreserveSubpixel(player, 8);
        player.setYSpeed((short) speed);
        player.setAir(true);
        player.setOnObject(false);
        player.setAnimationId(CdSonicAnimation.SPRING);   // move.b #$10,oAnim(a1)
        clearStoodOn();                                     // bclr #3,oFlags(a0)
        trigger();
    }

    /** ObjSpring_Main_Side .Action (Spring/Main.asm:319-342). */
    private void launchSide(AbstractPlayableSprite player) {
        int xVel = speed;
        int newX = player.getX() + 8;
        boolean facingLeft = true;                          // bset #0,oFlags(a1)
        if (!hFlip()) {
            newX -= 0x10;
            xVel = -xVel;
            facingLeft = false;                             // bclr #0,oFlags(a1)
        }
        player.setX((short) newX);
        player.setXSpeed((short) xVel);
        player.setDirection(facingLeft ? Direction.LEFT : Direction.RIGHT);
        player.setMoveLockTimer(0xF);                        // move.w #$F,oPlayerMoveLock(a1)
        player.setGSpeed((short) xVel);
        if (!player.getRolling()) player.setAnimationId(CdSonicAnimation.WALK);
        player.setAngle((byte) 0);                          // clr.b oAngle(a1)
        clearPushedBy(player);                              // bclr #5,oFlags(a0)
        player.setPushing(false);
        trigger();
    }

    /** ObjSpring_Main_Down .Action (Spring/Main.asm:386-395). */
    private void launchDown(AbstractPlayableSprite player) {
        NativePositionOps.addYPosPreserveSubpixel(player, -8);
        player.setYSpeed((short) -speed);
        player.setAir(true);
        player.setOnObject(false);
        clearStoodOn();                                     // bclr #3,oFlags(a0)
        trigger();
    }

    /**
     * ObjSpring_Main_Diag .Action (Spring/Main.asm:430-465): CalcSine of $E0 scaled by -speed with
     * {@code mulu; lsr.l #8} (the low words are stored), then the flips.
     */
    private void launchDiagonal(AbstractPlayableSprite player) {
        int d2 = -speed & 0xFFFF;
        int yVel = (int) (((TrigLookupTable.sinHex(0xE0) & 0xFFFFL) * d2) >>> 8) & 0xFFFF;
        int xVel = (int) (((TrigLookupTable.cosHex(0xE0) & 0xFFFFL) * d2) >>> 8) & 0xFFFF;
        player.setYSpeed((short) yVel);
        player.setXSpeed((short) xVel);
        int newY = player.getY() + 8;
        if (vFlip()) {
            newY -= 0x10;
            player.setYSpeed((short) -player.getYSpeed());
        }
        player.setY((short) newY);
        int newX = player.getX() - 8;
        boolean facingLeft = false;
        if (hFlip()) {
            newX += 0x10;
            facingLeft = true;
            player.setXSpeed((short) -player.getXSpeed());
        }
        player.setX((short) newX);
        player.setDirection(facingLeft ? Direction.LEFT : Direction.RIGHT);
        player.setAir(true);
        player.setOnObject(false);
        player.setPushing(false);
        clearStoodOn();                                     // bclr #3 / #5,oFlags(a0) (Spring/Main.asm:458-462)
        clearPushedBy(player);
        trigger();
    }

    private void trigger() {
        animating = true;
        if (animator != null) animator.restart();
        services().playSfx(com.openggf.audio.GameSound.SPRING); // FM_SPRING, every launch (Spring/Main.asm:275, :341, :394, :464)
    }

    @Override public Role solidRole() { return Role.SPRING; }
    /** StandOnObject: cmpi.b #2,oRoutine(a0), the upward spring's main routine (Object Solid.asm:134-137). */
    @Override public boolean solidSpringFacesUp() { return kind == Kind.UP; }
    @Override public boolean solidVerticallyFlipped() { return vFlip(); }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialX; }

    /** .ChkDel compares oVar36's chunk with no on-screen exception (Spring/Main.asm:158-166). */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideDespawnRange(initialX, cameraX, false);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        String key = switch (kind) {
            case SIDE -> CdObjectArtProvider.SPRING_SIDE;
            case DIAGONAL -> CdObjectArtProvider.diagonalSpringKey(tileWord & ~0x2000);
            default -> CdObjectArtProvider.SPRING_UP;
        };
        drawFrame(key, mapFrame, hFlip(), vFlip());
    }
}
