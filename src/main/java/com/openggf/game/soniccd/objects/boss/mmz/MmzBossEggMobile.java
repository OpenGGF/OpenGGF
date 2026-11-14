package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The Egg Mobile, object $41 ({@code EggMobileObject}, objects/r8/boss.asm:1920-2275): the machine around Eggman,
 * allocated by him ({@code SpawnObjectAfter}, sub_20CF00) and following his position. It owns the four panels ($40,
 * allocated after it, sub_20CF28) and the state they read: the panel radius ({@code var_38}, 0-$100), the panel table
 * set ({@code var_34}), the first panel's frame ({@code var_35}), the frame to settle at ({@code var_36}), the
 * rotation period ({@code var_37}), the rotation mode ({@code var_3a}), the panel the next hit drops
 * ({@code var_3b}) and the hit immunity ({@code var_3c}). Every routine draws unless the immunity count is odd.
 */
final class MmzBossEggMobile extends MmzBossPiece implements TouchResponseAttackable {
    static final int ID = 0x41;
    /** var_2c bits: 3 the panels come back, 4 reverse the rotation, 5 and 6 rotate the panels one frame up/down. */
    static final int REATTACH = 0x08, TOGGLE = 0x10, ROTATE_UP = 0x20, ROTATE_DOWN = 0x40;
    /** var_3a bits: 7 settled at {@link #targetFrame}, 5 the rotation runs, 6 the direction. */
    static final int MODE_SETTLED = 0x80, MODE_SPUN = 0x20, MODE_DIRECTION = 0x40;

    MmzBossEggman eggman;
    int radius;
    int setIndex;
    int panelFrame;
    int targetFrame;
    int period;
    int mode;
    int panelSelect;
    int immunity;
    int panelsUp;
    int panelsDown;
    int dropPattern;

    MmzBossEggMobile(int x, int y, MmzBossEggman eggman) {
        super(ID, x, y, "MmzBossEggMobile");
        this.eggman = eggman;
    }

    private MmzBossEggMobile(ObjectSpawn spawn) {
        super(spawn, "MmzBossEggMobile");
    }

    @Override String sheet() { return MmzBossArt.EGG_MOBILE; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = player instanceof AbstractPlayableSprite s ? s : null;
        switch (routine) {
            case 0 -> init();
            case 2 -> { follow(); idle(); }
            case 4 -> expand();
            case 6 -> launch();
            case 8 -> { follow(); retract(); }
            case 0xA -> { follow(); waitForDrops(sonic); }
            case 0xC -> { follow(); reattach(); }
            case 0xE -> { follow(); flicker(); }
            case 0x10 -> follow();
            case 0x12 -> { follow(); radiusFromHeight(); }
            default -> { }
        }
    }

    /** btst #0,obj.var_3c(a0); bne: no DrawObject (EggMobileObject, :1927-1929). */
    @Override protected boolean drawn() { return routine != 0 && (immunity & 1) == 0; }

    private void follow() {
        if (eggman == null) return;
        x = eggman.ox();
        y = eggman.oy();
    }

    /** EggMobileObject_0_Routine0 (:1952-1966): four panels at frames 0, 4, 8 and $C. */
    private void init() {
        routine = 2;
        priority = 5;
        width = 0x24;
        yRadius = 0x24;
        tileWord = MmzBossArt.EGG_MOBILE_TILE;
        radius = 0;
        panelSelect = 0xFF;
        var objects = services().objectManager();
        if (objects != null) {
            for (int i = 0; i < 4; i++) {
                MmzBossPanel panel = new MmzBossPanel(x, y, this, i, i * 4);
                objects.addDynamicObjectAfterCurrent(panel);
                if (panel.isDestroyed()) break;
            }
        }
        mode = (mode | MODE_SETTLED | MODE_SPUN) & ~MODE_DIRECTION;
    }

    /** sub_20C562 (:2177-2250): reverse on request, spin up for $A frames, settle at {@link #targetFrame}. */
    private void idle() {
        if (clear(TOGGLE)) {
            setTimerLow(0);
            mode &= ~(MODE_SETTLED | MODE_SPUN);
            boolean wasReversed = (mode & MODE_DIRECTION) != 0;
            mode ^= MODE_DIRECTION;                               // bchg #6; beq loc_20C5E0
            if (!wasReversed) reversed(); else forward();
            return;
        }
        if ((mode & MODE_DIRECTION) == 0) forward(); else reversed();
    }

    /** loc_20C596 (:2196-2224). */
    private void forward() {
        if ((mode & MODE_SETTLED) != 0) return;
        if ((mode & MODE_SPUN) != 0) {
            if (panelFrame == targetFrame) {
                mode |= MODE_SETTLED;
                mapFrame = 0;
                return;
            }
        } else {
            mapFrame = 1;
            setTimerLow(timerLow() + 1);
            if (timerLow() == 0xA) {
                mode |= MODE_SPUN;
                setTimerLow(0);
            }
        }
        rotationTick();
    }

    /** loc_20C5E0 (:2228-2250). */
    private void reversed() {
        if ((mode & MODE_SPUN) == 0) {
            mapFrame = 1;
            setTimerLow(timerLow() + 1);
            if (timerLow() != 0xA) return;
            setTimerLow(0);
            mode |= MODE_SPUN;
        }
        flicker();
        rotationTick();
    }

    /** loc_20C608 (:2241-2250): every {@link #period} frames the panels turn one frame down. */
    private void rotationTick() {
        setTimerHigh(timerHigh() + 1);
        if ((byte) timerHigh() < (byte) period) return;
        setTimerHigh(0);
        set(ROTATE_DOWN);
    }

    /** sub_20C624 (:2254-2275): frames 2 and 1 every 6 counts. */
    private void flicker() {
        setTimerLow(timerLow() + 1);
        if (timerLow() == 6) mapFrame = 2;
        else if (timerLow() == 0xC) {
            setTimerLow(0);
            mapFrame = 1;
        }
    }

    /** EggMobileObject_0_Routine4 (:1986-2036): the panels move out, 8 a frame, through table sets 1, 3 and 4. */
    private void expand() {
        boolean grow = true;
        if (step == 0) {
            setIndex = 1;
            radius = 0;
            step = 1;
        }
        if (step == 1 && radius <= 0xAA) grow = false;
        if (grow) {
            step = (step + 1) & 0xFF;
            if (step == 2) setIndex = 3;
            else if (step == 6) setIndex = 4;
        }
        radius = (radius + 8) & 0xFFFF;
        if ((short) radius < 0x100) return;
        radius = 0x100;
        step = 0;
        routine = 2;
        setIndex = 4;
    }

    /** EggMobileObject_0_Routine6 (:2040-2063): out at $10 a frame, set 1 then 2. */
    private void launch() {
        if (step == 0) {
            setIndex = 1;
            step = 1;
        }
        radius = (radius + 0x10) & 0xFFFF;
        if ((short) radius >= 0xEC) setIndex = 2;
        if ((short) radius < 0x100) return;
        radius = 0x100;
        step = 0;
        routine = 2;
    }

    /** EggMobileObject_0_Routine8 (:2067-2093): in at 6 a frame, set 2 then 1, then 0. */
    private void retract() {
        if (step == 0) {
            setIndex = 2;
            step = 1;
        }
        radius = (radius - 6) & 0xFFFF;
        if ((short) radius < 0xEC) setIndex = 1;
        if ((short) radius > 0) return;
        radius = 0;
        step = 0;
        routine = 2;
        setIndex = 0;
    }

    /**
     * EggMobileObject_0_RoutineA (:2097-2118): wait until every panel that went up is back ({@code var_3d} equal to
     * the hits left), then pick the drop pattern from the player's X plus its animation timer, and reverse.
     */
    private void waitForDrops(AbstractPlayableSprite player) {
        if (eggman == null || eggman.hitsLeft != panelsUp) {
            reversed();
            return;
        }
        int d0 = (player == null ? 0 : player.getCentreX()) + (player == null ? 0 : player.getAnimationFrameCount());
        dropPattern = ((d0 & 3) << 3) & 0xFF;                     // andi.b #3; add.b x3
        routine = 2;
        set(TOGGLE);
    }

    /** EggMobileObject_0_RoutineC (:2122-2148): the panels come back from radius -$100 at 6 a frame. */
    private void reattach() {
        if (step != 1) {
            set(REATTACH);
            step = (step + 1) & 0xFF;
            radius = 0xFF00;
            mode = (mode | MODE_SETTLED | MODE_SPUN) & ~MODE_DIRECTION;
        }
        radius = (radius + 6) & 0xFFFF;
        if ((short) radius < 0) return;
        clear(REATTACH);
        radius = 0;
        setTimerHigh(0);
        step = 0;
        routine = 2;
    }

    /** EggMobileObject_0_Routine12 (:2159-2173): radius $100 * min($1A0 - Eggman's Y, $24) / $24. */
    private void radiusFromHeight() {
        if (eggman == null) return;
        int d0 = (0x1A0 - eggman.oy()) & 0xFFFF;
        if ((short) d0 > 0x24) d0 = 0x24;
        radius = Integer.divideUnsigned((d0 << 8) & 0xFFFF, 0x24) & 0xFFFF;   // lsl.w #8; divu.w #$24
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        MmzBossEggman.attacked(this);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossEggMobile(spawn);
    }
}
