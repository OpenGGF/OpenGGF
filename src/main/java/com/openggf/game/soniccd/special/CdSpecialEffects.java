package com.openggf.game.soniccd.special;

/**
 * The special stage's presentation objects whose only state is their sprite: the item icon and
 * lost rings ({@code ObjItem}, {@code ObjLostRing}, Item/Main.asm), the eight explosion slots
 * ({@code ObjExplosion}, Explosion/Main.asm) and the eight dust slots ({@code ObjDust},
 * Dust/Main.asm). Their {@code Random} draws matter: they share the stage's generator with the
 * UFOs' explosion directions.
 */
final class CdSpecialEffects {
    /** One effect slot: present, routine, timer and its sprite with a 16.16 velocity. */
    static final class Slot {
        boolean present;
        boolean delete;
        int routine;
        int timer;
        int type;
        int xVel;
        int yVel;
        int yFrac;
        int xFrac;
        CdSpecialSprites.Sprite sprite;
    }

    private final CdSpecialStage stage;
    final Slot item = new Slot();
    final Slot[] rings = slots(7);
    final Slot[] explosions = slots(8);
    final Slot[] dust = slots(8);
    private final CdSpecialAnimationScripts itemScripts;
    private final CdSpecialAnimationScripts explosionScripts;
    private final CdSpecialAnimationScripts dustScripts;
    private int lostRingDirection;

    CdSpecialEffects(CdSpecialStage stage, CdSpecialStageAssets assets) {
        this.stage = stage;
        this.itemScripts = assets.animationScripts("MapSpr_Item");
        this.explosionScripts = assets.animationScripts("MapSpr_Explosion");
        this.dustScripts = assets.animationScripts("MapSpr_Dust");
    }

    private static Slot[] slots(int n) {
        Slot[] out = new Slot[n];
        for (int i = 0; i < n; i++) out[i] = new Slot();
        return out;
    }

    /** {@code FindExplosionObjSlot} (scddisasm/src/Special Stage/Objects/Explosion/Main.asm:15-25): the first empty slot. */
    Slot freeExplosion() {
        for (Slot s : explosions) if (!s.present) return s;
        return null;
    }

    /** {@code FindDustObjSlot} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1003-1014). */
    Slot freeDust() {
        for (Slot s : dust) if (!s.present) return s;
        return null;
    }

    /** {@code move.b #4,itemObject}; the UFO sets the position and spawn type (UFO/Main.asm:234-238). */
    void spawnItem(int x, int y, int type) {
        if (!item.present) { item.present = true; item.routine = 0; }
        if (item.sprite == null) item.sprite = new CdSpecialSprites.Sprite(0, itemScripts);
        item.sprite.x = x;
        item.sprite.y = y;
        item.type = type;
    }

    /** {@code .Lose7Rings}..{@code .Lose1Ring} (Sonic/Main.asm:723-742): slots 1-7, the last {@code count}. */
    void dropRings(int count) {
        for (int i = 7 - count; i < 7; i++) {
            if (!rings[i].present) { rings[i].present = true; rings[i].routine = 0; }
        }
    }

    /** Priority-2 order before the UFOs: item, lost rings 1-7, explosions 1-8 (Sub.asm:112-131). */
    void runPriority2(CdSpecialSprites sprites) {
        if (item.present) runItem(sprites);
        for (Slot r : rings) if (r.present) runRing(r, sprites);
        for (Slot e : explosions) if (e.present) runExplosion(e, sprites);
    }

    /** Dust slots run last in priority 1 (Sub.asm:100-108). */
    void runDust(CdSpecialSprites sprites) {
        for (Slot d : dust) if (d.present) runDustSlot(d, sprites);
    }

    private static void finish(Slot s) {
        if (s.delete) { s.present = false; s.delete = false; s.sprite = null; }
    }

    /** {@code ObjItem} (Item/Main.asm:91-133). */
    private void runItem(CdSpecialSprites sprites) {
        Slot s = item;
        if (s.routine == 0) {
            s.sprite.tile = 0x878F;
            s.sprite.set(s.type);
            s.routine = 1;
            s.timer = 0x10;
            s.yVel = -0x10 << 16;
            stage.playSound(CdSpecialSound.RING);
        }
        s.timer = (s.timer - 1) & 0xFF;
        if (s.timer == 0) s.delete = true;
        int y = (s.sprite.y << 16) | s.yFrac;
        y += s.yVel;
        s.sprite.y = (short) (y >> 16);
        s.yFrac = y & 0xFFFF;
        s.yVel += 0x20000;
        sprites.draw(s.sprite);
        finish(s);
    }

    /** {@code ObjLostRing} (Item/Main.asm:16-89). */
    private void runRing(Slot s, CdSpecialSprites sprites) {
        if (s.routine == 0) {
            s.sprite = new CdSpecialSprites.Sprite(0xE78F, itemScripts);
            s.sprite.set(4);
            s.sprite.x = stage.sonicSprite().x;
            s.sprite.y = stage.sonicSprite().y;
            s.xFrac = 0;
            s.yFrac = 0;
            s.routine = 1;
            s.timer = 45;
            int r = stage.random();
            int vx = r & 0x3F000;
            lostRingDirection ^= 1;
            if (lostRingDirection == 0) vx = -vx; // bchg #0; beq: flies left when the bit was set
            s.xVel = vx;
            s.yVel = (-0xA - (r & 0xF)) << 16;
            stage.playSound(CdSpecialSound.RING_LOSS);
        }
        s.timer = (s.timer - 1) & 0xFF;
        if (s.timer == 0) s.delete = true;
        int x = (s.sprite.x << 16) | s.xFrac;
        x += s.xVel;
        s.sprite.x = (short) (x >> 16);
        s.xFrac = x & 0xFFFF;
        int y = (s.sprite.y << 16) | s.yFrac;
        y += s.yVel;
        s.sprite.y = (short) (y >> 16);
        s.yFrac = y & 0xFFFF;
        if ((s.sprite.y & 0xFFFF) > 216 + 128) {
            s.sprite.y = 216 + 128;
            s.yVel = -s.yVel;
        } else {
            s.yVel += 0x20000;
        }
        sprites.draw(s.sprite);
        finish(s);
    }

    /** {@code ObjExplosion} (Explosion/Main.asm:27-71). */
    private void runExplosion(Slot s, CdSpecialSprites sprites) {
        if (s.routine == 0) {
            s.sprite.set(0);
            s.timer = 12;
            s.routine = 1;
            stage.playSound(CdSpecialSound.EXPLOSION);
        }
        s.timer = (s.timer - 1) & 0xFFFF;
        if (s.timer == 0) s.delete = true;
        sprites.draw(s.sprite);
        finish(s);
    }

    /** An explosion spawned by a breaking UFO at its sprite (UFO/Main.asm:128-139, 295-306). */
    void spawnExplosion(int ufoX, int ufoY) {
        Slot s = freeExplosion();
        if (s == null) return;
        s.present = true;
        s.routine = 0;
        s.sprite = new CdSpecialSprites.Sprite(0x87AE, explosionScripts);
        int r = stage.random();
        s.sprite.x = (short) (ufoX - 16 + (r & 0x1F));
        s.sprite.y = (short) (ufoY - (r & 0x1F));
    }

    /** {@code ObjDust} (Dust/Main.asm:7-60). */
    private void runDustSlot(Slot s, CdSpecialSprites sprites) {
        if (s.routine == 0) {
            s.sprite = new CdSpecialSprites.Sprite(0x87AE, dustScripts);
            s.sprite.x = 112 + 128;
            s.sprite.y = 212 + 128;
            s.xFrac = 0;
            s.sprite.set(0);
            s.timer = 6;
            s.routine = 1;
            int r = stage.random();
            s.sprite.x += r & 0x1F;
            s.sprite.y += r & 0x7;
            int hold = stage.playerHold;
            s.xVel = (hold & 4) != 0 ? 3 << 16 : (hold & 8) != 0 ? -3 << 16 : 0;
        }
        s.timer = (s.timer - 1) & 0xFFFF;
        if (s.timer == 0) s.delete = true;
        int x = (s.sprite.x << 16) | s.xFrac;
        x += s.xVel;
        s.sprite.x = (short) (x >> 16);
        s.xFrac = x & 0xFFFF;
        s.sprite.y--;
        sprites.draw(s.sprite);
        finish(s);
    }
}
