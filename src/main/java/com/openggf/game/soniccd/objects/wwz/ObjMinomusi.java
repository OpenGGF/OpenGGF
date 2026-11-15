package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Minomusi, object $33 ({@code ObjMinomusi}, Level/Wacky Workbench/Objects/Minomusi/Main.asm:15-272): a
 * bagworm that hangs on a silk thread, waits 121 frames at a time until the player is 40 to 160 pixels
 * below and within 168 pixels either side, drops 95 pixels, and attacks for 230 frames (61 when damaged)
 * before climbing back. During the attack its animation shows the spikes; at script index $1E it
 * allocates a spikes object (secondary subtype 1, collision $B5) that lives until the animation loops to
 * index 1. Init allocates the silk (secondary subtype -1) and deletes the badnik if there is no slot.
 * Act 3's index leaves $33 to {@code NullObject}.
 */
public final class ObjMinomusi extends WwzBadnik {
    private static final int SET_WAIT = 2, WAIT_PLAYER = 4, START_DROP = 6, DROP = 8, START_RETRACT = 0xA,
            RETRACT = 0xC, START_ATTACK = 0xE, ATTACK = 0x10;
    /** {@code move.w #121,oMinoTime(a0)} (:93, :100). */
    private static final int WAIT = 121;
    private static final int SPIKES_COLLISION = 0xB5;

    private final boolean damaged;
    private int time;
    private int yVel;
    private int startY;
    private int dropY;
    private CdAnimator animator;
    /** {@code oAnimFrame}: the script index after the last {@code AnimateObject}. */
    private int animFrame;
    private boolean initialised;

    public ObjMinomusi(ObjectSpawn spawn) {
        super(spawn, "Minomusi");
        this.damaged = spawn.subtype() != 0;
    }

    @Override
    protected void run(PlayableEntity player) {
        AbstractPlayableSprite sprite = sprite(player);
        switch (routine) {
            case 0 -> {
                init();
                if (isDestroyed()) return;
                setWait();
            }
            case SET_WAIT -> setWait();
            case WAIT_PLAYER -> waitPlayer(sprite);
            case START_DROP -> { routine += 2; yVel = 0x80000; }
            case DROP -> drop();
            case START_RETRACT -> { routine += 2; yVel = damaged ? 0x20000 : 0x70000; }
            case RETRACT -> retract();
            case START_ATTACK -> startAttack();
            case ATTACK -> attack();
            default -> { }
        }
    }

    /** ObjMinomusi_Init (:47-86); it falls through into ObjMinomusi_SetWait. */
    private void init() {
        routine = SET_WAIT;
        priority = 3;
        yRadius = 16;
        xRadius = 16;
        width = 16;
        tileWord = WwzObjectArt.MINOMUSI_TILE;
        colType = 0x34;
        y = (y + 8) & 0xFFFF;
        startY = y;
        dropY = (y + 95) & 0xFFFF;
        initialised = true;
        var objects = services().objectManager();
        if (objects == null) return;
        // jsr FindNextObjSlot: beq.s .SpawnSilk, else jmp DeleteObject (the badnik is gone without drawing).
        objects.addDynamicObjectAfterCurrent(new ObjMinomusiPart(this, ObjMinomusiPart.Role.SILK));
    }

    /** ObjMinomusi_SetWait (:90-93). */
    private void setWait() {
        routine = WAIT_PLAYER;
        mapFrame = 9;
        time = WAIT;
    }

    /** ObjMinomusi_WaitPlayer (:97-112). */
    private void waitPlayer(AbstractPlayableSprite player) {
        time = (time - 1) & 0xFFFF;
        if (time != 0) return;
        time = WAIT;
        boolean near = player != null && checkPlayer(player);
        routine += near ? 2 : -2;                                  // move.b #2,d6 / neg.b d6 unless carry
    }

    /** ObjMinomusi_CheckPlayer (:116-129): carry set when the player is below and near. */
    private boolean checkPlayer(AbstractPlayableSprite player) {
        int dy = (player.getCentreY() - y - 40) & 0xFFFF;          // subi.w #40,d0
        if (dy >= 120) return false;                                // subi.w #120,d0 / bcc.s .End
        int dx = (player.getCentreX() - x + 168) & 0xFFFF;          // subi.w #-168,d0
        return dx < 336;                                            // subi.w #336,d0
    }

    /** ObjMinomusi_Drop (:139-148). */
    private void drop() {
        addToY(yVel);
        int remaining = (short) (dropY - y);
        if (remaining > 0) return;                                  // bgt.s .End
        y = (y + remaining) & 0xFFFF;
        routine = START_ATTACK;
    }

    /** ObjMinomusi_Retract (:165-174). */
    private void retract() {
        setYPosition(yPosition() - yVel);
        int remaining = (short) (startY - y);
        if (remaining < 0) return;                                  // blt.s .End
        y = (y + remaining) & 0xFFFF;
        routine = SET_WAIT;
    }

    /** ObjMinomusi_StartAttack (:179-191): {@code move.w d1,oAnim(a0)} sets the animation and forces a restart. */
    private void startAttack() {
        routine = ATTACK;
        time = damaged ? 61 : 230;
        animator = WwzObjectArt.animator(artProvider(), "minoAnimations", damaged ? 1 : 0);
        animFrame = 0;
    }

    /** ObjMinomusi_Attack (:195-232). */
    private void attack() {
        time = (time - 1) & 0xFFFF;
        if (time == 0) routine = START_RETRACT;
        if (animator == null) return;
        stepAnimation();
        if (damaged || animFrame != 0x1E) return;
        var objects = services().objectManager();
        if (objects != null) objects.addDynamicObjectAfterCurrent(new ObjMinomusiPart(this, ObjMinomusiPart.Role.SPIKES));
        if (renderFlagOnScreen()) services().playSfx(WwzSounds.B7);   // tst.b oSprFlags(a0) / FM_B7
    }

    /**
     * {@code AnimateObject} over Ani_Minomusi, also keeping {@code oAnimFrame}: the script index
     * advances once a step, shows script value {@code oAnimFrame}, and a $FF loops to index 1.
     */
    private void stepAnimation() {
        int before = animator.anim;
        animator.step();
        mapFrame = animator.mapFrame();
        var provider = artProvider();
        int table = WwzObjectCatalogue.offset(provider.payload(), "minoAnimations");
        int script = table + (short) provider.image().u16(table + before * 2);
        if (provider.image().u8(script + 1 + animFrame) == 0xFF) animFrame = 0;
        animFrame++;
    }

    int startY() { return startY; }

    int animFrame() { return animFrame; }

    boolean damaged() { return damaged; }

    @Override
    protected String sheetKey() {
        return initialised ? WwzObjectArt.key(damaged ? WwzObjectArt.MINOMUSI_DAMAGED : WwzObjectArt.MINOMUSI) : null;
    }

    /** The silk and the spikes: copies of the badnik's slot that follow it (:236-271). */
    static final class ObjMinomusiPart extends WwzObject
            implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
        enum Role { SILK, SPIKES }

        /** obj.var_38: the badnik's slot, which each part tests ({@code movea.w obj.var_38(a0),a1}, :211, :226). */
        private final int parentSlot;
        private final boolean damagedBody;
        private final Role role;

        ObjMinomusiPart(ObjMinomusi parent, Role role) {
            super(new ObjectSpawn(parent.x, role == Role.SILK ? parent.y : parent.y + 4, 0x33, 0, 0, false, 0),
                    role == Role.SILK ? "MinomusiSilk" : "MinomusiSpikes");
            this.parentSlot = parent.getSlotIndex();
            this.damagedBody = parent.damaged();
            this.role = role;
            this.spriteFlip = parent.spriteFlip;
            this.tileWord = parent.tileWord;
            this.priority = role == Role.SILK ? parent.priority + 1 : parent.priority;
            if (role == Role.SILK) {
                yRadius = 32;
                width = 1;
            } else {
                yRadius = parent.yRadius;
                width = parent.width;
            }
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!slotHoldsId(parentSlot, 0x33)) {                     // cmpi.b #$33,obj.id(a1); bne Delete (:212-213, :227-229)
                setDestroyed(true);
                return;
            }
            // Another $33 in the slot (a part that took the freed slot) passes the test; its fields are not a badnik's.
            if (!(slotOccupant(parentSlot) instanceof ObjMinomusi body)) return;
            if (role == Role.SPIKES) {
                if (body.animFrame() == 1) setDestroyed(true);          // cmpi.b #1,oAnimFrame(a1) / beq.s .Delete
                return;
            }
            int frame = (short) (body.y - body.startY() - 24) >> 3;    // asr.w #3,d0
            if (frame < 0) frame = 0;
            mapFrame = frame;
            y = ((frame << 2) + body.startY() + 16) & 0xFFFF;
        }

        @Override public int getCollisionFlags() { return role == Role.SPIKES ? SPIKES_COLLISION : 0; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
        @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

        @Override
        protected String sheetKey() {
            return WwzObjectArt.key(damagedBody ? WwzObjectArt.MINOMUSI_DAMAGED : WwzObjectArt.MINOMUSI);
        }
    }
}
