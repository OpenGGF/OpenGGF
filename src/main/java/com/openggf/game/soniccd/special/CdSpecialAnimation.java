package com.openggf.game.soniccd.special;

/**
 * Sonic's animation selection on his sprite: {@code SetObjAnim} restarts an animation and
 * {@code ChgObjAnim} switches it keeping the frame (scddisasm/src/Special Stage/Sub.asm:802-840); {@code DrawObject} steps it.
 */
final class CdSpecialAnimation {
    CdSpecialSprites.Sprite sprite;

    void bind(CdSpecialAnimationScripts scripts) { sprite = new CdSpecialSprites.Sprite(0x85E0, scripts); }

    void set(CdSpecialStageSonic sonic, int id) {
        sprite.set(id);
        sonic.anim = sprite.anim.anim;
    }

    void change(CdSpecialStageSonic sonic, int id) {
        sprite.change(id);
        sonic.anim = sprite.anim.anim;
    }

    int frame() { return sprite.anim.frame; }
}
