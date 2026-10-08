package slaytherobotnik.scene;

import com.openggf.mods.scene.art.RomAnimationPlayer;

/** Mod vocabulary over the shared ROM object-animation player. */
final class SpriteAnim {
    private final RomAnimationPlayer player;
    SpriteAnim(byte[] table, int anim) { player = new RomAnimationPlayer(table, anim); }
    void set(int anim) { player.set(anim); }
    int anim() { return player.animation(); }
    int frame() { return player.frame(); }
    boolean advanced() { return player.advanced(); }
    void clearAdvanced() { player.clearAdvanced(); }
    void tick() { player.tick(); }
}
