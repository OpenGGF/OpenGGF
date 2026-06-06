package com.openggf.game.sonic1.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Sonic 1 Purple Rock (GHZ) - Object ID 0x3B.
 * <p>
 * A static solid object found in Green Hill Zone. No subtypes, no movement,
 * no animation - just a solid rock that the player can stand on and collide with.
 * <p>
 * From disassembly: d1 = $1B (halfWidth), d2 = $10 (airHalfHeight),
 * d3 = $10 (groundHalfHeight), calls SolidObject.
 * <p>
 * Reference: docs/s1disasm/_incObj/3B Purple Rock.asm
 */
public class Sonic1RockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.w #$10+sonic_solid_width,d1 = $10 + $B = $1B
    private static final int HALF_WIDTH = 0x1B;

    // From disassembly: move.w #$10,d2 / move.w #$10,d3
    private static final int HALF_HEIGHT = 0x10;

    // From disassembly (Rock_Main): move.b #$13,obActWid(a0). This is the
    // standable top-surface half-width that Solid_Landed re-reads for NEW
    // landings (docs/s1disasm/_incObj/sub SolidObject.asm:270 move.b
    // obActWid(a0),d1). It is authored INDEPENDENTLY of the collision width
    // d1 = $10 + sonic_solid_width ($1B): here obActWid ($13) does NOT equal
    // d1 - sonic_solid_width ($1B - $B = $10), so the generic
    // "obActWid = collisionHalfWidth - $B" derivation would wrongly narrow the
    // landing surface to $10 and reject the GHZ2 top-landing one frame late.
    // Reference: docs/s1disasm/_incObj/3B Purple Rock.asm:20,24-28.
    private static final int ACT_WIDTH = 0x13;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    public Sonic1RockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PurpleRock");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.ROCK);
        if (renderer == null) return;
        renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    /**
     * ROM: Solid_Landed re-reads {@code obActWid(a0)} (= $13) as the standable
     * top-surface half-width for NEW landings, which for this object is narrower
     * than the collision half-width ($1B) yet wider than the generic
     * {@code collisionHalfWidth - sonic_solid_width} ($10) fallback. Supplying the
     * real obActWid keeps the GHZ2 air-roll top-landing on the ROM-accurate frame.
     * Reference: docs/s1disasm/_incObj/sub SolidObject.asm:267-277;
     * docs/s1disasm/_incObj/3B Purple Rock.asm:20.
     */
    @Override
    public int getTopLandingHalfWidth(PlayableEntity player, int collisionHalfWidth) {
        return ACT_WIDTH;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // No special behavior - standard solid collision handled by ObjectManager
    }
}
