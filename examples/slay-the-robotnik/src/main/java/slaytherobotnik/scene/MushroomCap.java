package slaytherobotnik.scene;

/**
 * A Mushroom Hill bouncy cap ({@code Obj_MHZMushroomCap}). It sways with the zone's tile
 * animation: Anim_Counters+$F steps by 2 a frame up to $58 (AnimateTiles_MHZ loc_28180) and,
 * plus the cap's own offset ($14 for light-spotted caps), picks an (x, y) nudge from
 * MHZMushroomCap_Positions. A player standing on it starts its spring animation
 * (Ani_MHZMushroomCap 1: squashed for five frames, then wobbling) and is launched on its first
 * spring-up frame (3) at $660, $760 or $860 plus $20 depending on how fast they landed
 * (MHZMushroomCap_BounceCharacter), with sfx_MushroomBounce. Its top is solid
 * {@code byte_3E0DA[frame]} pixels above its centre.
 */
final class MushroomCap {
    /** Light-spotted caps are offset so they do not sway in step ($36 = $14). */
    static final int LIGHT_OFFSET = 0x14;
    private static final int COUNTER_WRAP = 0x58;

    private final SpriteAnim anim;
    private final byte[] positions;
    private final int offset;

    MushroomCap(byte[] aniTable, byte[] positions, int offset) {
        this.anim = new SpriteAnim(aniTable, 0);
        this.positions = positions;
        this.offset = offset;
    }

    /** Anim_Counters+$F after {@code frames} frames of the zone running. */
    static int counter(long frames) {
        return (int) ((frames * 2) % COUNTER_WRAP);
    }

    /** One frame: Animate_Sprite, returning to rest when the spring animation ends ($FC). */
    void tick() {
        anim.tick();
        if (anim.advanced()) {
            anim.clearAdvanced();
            anim.set(0);
        }
    }

    /** The sway's x nudge for {@code counter}. */
    int dx(int counter) {
        return positions[counter + offset];
    }

    /** The sway's y nudge for {@code counter}. */
    int dy(int counter) {
        return positions[counter + offset + 1];
    }

    int frame() {
        return anim.frame();
    }

    /** Someone is standing on the cap: start the spring animation. */
    void spring() {
        anim.set(1);
    }

    /** True on the spring animation's launch frame. */
    boolean launching() {
        return anim.anim() == 1 && anim.frame() == 3;
    }

    /** How far above the cap's centre its solid top is (byte_3E0DA). */
    int surface() {
        return anim.frame() == 1 ? 8 : 0x12;
    }

    /** The launch y_vel for a player who landed at {@code landingYVel}. */
    static int launchSpeed(int landingYVel) {
        int speed = 0x660;
        if (landingYVel >= 0x660) {
            speed = landingYVel < 0x760 ? 0x760 : 0x860;
        }
        return -(speed + 0x20);
    }
}
