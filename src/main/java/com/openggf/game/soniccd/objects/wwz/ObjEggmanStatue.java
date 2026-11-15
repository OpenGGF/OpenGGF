package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Eggman statue, object $2B ({@code ObjEggmanStatue}, Level/Wacky Workbench/Objects/Eggman Statue/Main.asm:
 * 569-745): a solid $28-wide statue (not in a good future) with special collision $F8, which a rolling
 * player counts ({@code Player_TouchSpecial .FlagColRoll}, Wacky Workbench/Player Object Collision.asm:
 * 389-393). The first count clears the collision, releases the player and starts the table of
 * explosions (time, dx, dy records from {@code ObjEggmanStatue_ExplodeLocs}), then frame 1 for 60
 * frames, then drops spike bombs ({@code ObjEggmanStatue_BombLocs}, subtype -1, 160 above, floor 38 below)
 * and deletes itself. A spike bomb falls with gravity $400 and explodes at its floor.
 */
public final class ObjEggmanStatue extends WwzObject
        implements ReevaluatingSolidObject, TouchResponseProvider, TouchResponseListener,
        ObjectVisualFacing.NonDirectional {
    private static final int MAIN = 2, EXPLODE = 4, WAIT = 6, DROP_BOMBS = 8;
    /** {@code move.b #$F8,oColType(a0)} (:55). */
    private static final int COLLISION_FLAGS = 0xF8;
    /** {@code cmpi.b #2,oAnim(a0)} (Player Object Collision.asm:389-393): SonAni_Roll. */
    private static final int ROLL_ANIMATION = 2;

    private int colType;
    private int colStatus;
    private int pointer;
    private int time;
    private boolean initialised;

    public ObjEggmanStatue(ObjectSpawn spawn) {
        super(spawn, "EggmanStatue");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (getSpawn().subtype() != 0) {
            return;                                                  // tst.b oSubtype / bne.w ObjSpikeBomb: not placed
        }
        AbstractPlayableSprite sprite = sprite(player);
        switch (routine) {
            case 0 -> {
                if (goodFuture()) {                                 // tst.b goodFuture / addq.l #4,sp / DeleteObject
                    setDestroyed(true);
                    return;
                }
                init();
                main(sprite);
            }
            case MAIN -> main(sprite);
            case EXPLODE -> explode();
            case WAIT -> {
                time = (time - 1) & 0xFF;
                if (time == 0) routine += 2;
            }
            case DROP_BOMBS -> dropBombs();
            default -> { }
        }
    }

    /** ObjEggmanStatue_Init (:40-56). */
    private void init() {
        routine = MAIN;
        priority = 3;
        width = 20;
        yRadius = 28;
        tileWord = WwzObjectArt.EGGMAN_STATUE_TILE;
        colType = COLLISION_FLAGS;
        pointer = WwzObjectCatalogue.offset(artProvider().payload(), "eggmanStatueExplodeLocs");
        initialised = true;
    }

    /** ObjEggmanStatue_Main (:60-76). */
    private void main(AbstractPlayableSprite player) {
        if (colStatus != 0) {
            colType = 0;                                             // clr.w oColType(a0): type and status
            colStatus = 0;
            routine = EXPLODE;
            if (player != null && solidObject(Entry.SOLID)) {
                ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
            }
            return;
        }
        if (player != null) solidObject(Entry.SOLID);
    }

    /** ObjEggmanStatue_Explode (:80-112). */
    private void explode() {
        var image = artProvider().image();
        int next = image.u8(pointer);
        if ((next & 0x80) != 0) {                                    // move.b (a6)+,d0 / bmi.s .Done
            routine += 2;
            mapFrame = 1;
            time = 60;
            return;
        }
        time = (time + 1) & 0xFF;                                    // addq.b #1,oEggStatTime
        if (next != time) return;
        int dx = (byte) image.u8(pointer + 1);
        int dy = (byte) image.u8(pointer + 2);
        pointer += 3;
        var objects = services().objectManager();
        if (objects != null) {
            objects.addDynamicObject(new CdExplosion((x + dx) & 0xFFFF, (y + dy) & 0xFFFF, 0, false, 0, 0));
        }
        services().playSfx(WwzSounds.EXPLODE);
    }

    /** ObjEggmanStatue_DropBombs (:126-155): FindObjSlot per record until the table's $FF. */
    private void dropBombs() {
        var image = artProvider().image();
        int at = WwzObjectCatalogue.offset(artProvider().payload(), "eggmanStatueBombLocs");
        var objects = services().objectManager();
        while (objects != null) {
            int dx = image.u8(at);
            if (dx == 0xFF) break;
            int dy = (byte) image.u8(at + 1);
            at += 2;
            objects.addDynamicObject(new ObjSpikeBomb((x + (byte) dx) & 0xFFFF, (y - 160 + dy) & 0xFFFF,
                    (y + 38) & 0xFFFF));
        }
        setDestroyed(true);
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        // .FlagColRoll: cmpi.b #2,oAnim(a0); addq.b #1,oColStatus(a1).
        if (colType != 0 && player != null && player.getAnimationId() == ROLL_ANIMATION) colStatus++;
    }

    @Override public boolean requiresContinuousTouchCallbacks() { return true; }

    /**
     * {@code Player_TouchSpecial} handles the $C0 types by flag only: the roll-only $38 adds to {@code oColStatus} and
     * returns, with no boss bounce or hurt (Wacky Workbench/Player Object Collision.asm:309-393), so the contact is
     * listener-only.
     */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }
    @Override public int getCollisionFlags() { return initialised ? colType : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    @Override protected boolean despawnsByCheckObjDespawn() { return routine <= MAIN; }

    @Override
    protected String sheetKey() {
        return initialised ? WwzObjectArt.key(WwzObjectArt.EGGMAN_STATUE) : null;
    }

    /** The spike bomb (:199-251): hazard collision $B7 on a $6-radius ball. */
    static final class ObjSpikeBomb extends WwzObject
            implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
        private static final int MAIN = 2, EXPLODE = 4;
        private final int floorY;
        private int yVel;
        private int colType;
        private CdAnimator animator;
        private boolean initialised;

        ObjSpikeBomb(int x, int y, int floorY) {
            super(new ObjectSpawn(x, y, 0x2B, 0xFF, 0, false, 0), "SpikeBomb");
            this.floorY = floorY;
            this.spriteFlip = 0;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> init();
                case MAIN -> main();
                case EXPLODE -> {
                    // ObjSpikeBomb_Explode: oExplodeBadnik set, FM_EXPLODE; oSubtype stays -1 so the
                    // explosion plays animation 1 (Eggman Statue/Main.asm:245-250, Explosion/Main.asm:81-83).
                    services().playSfx(WwzSounds.EXPLODE);
                    becomeExplosion(0, 0, 0xFF, false);
                }
                default -> { }
            }
        }

        /** ObjSpikeBomb_Init (:215-225). */
        private void init() {
            routine = MAIN;
            priority = 3;
            yRadius = 6;
            width = 6;
            tileWord = WwzObjectArt.SPIKE_BOMB_TILE;
            colType = 0xB7;
            yVel = 0;
            initialised = true;
        }

        /** ObjSpikeBomb_Main (:229-241). */
        private void main() {
            addToY(yVel);
            yVel += 0x400;
            if ((short) y >= (short) floorY) routine += 2;           // cmp.w oSpikeFloorY(a0),d0 / blt.s .Animate
            if (animator == null) animator = WwzObjectArt.animator(artProvider(), "spikeBombAnimations", 0);
            if (animator != null) {
                animator.step();
                mapFrame = animator.mapFrame();
            }
        }

        @Override public int getCollisionFlags() { return initialised ? colType : 0; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
        @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

        @Override
        protected String sheetKey() {
            return initialised ? WwzObjectArt.key(WwzObjectArt.SPIKE_BOMB) : null;
        }
    }
}
