package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;

/**
 * Object {@code $6E} under the SK Set 2 pointer table - {@code Obj_InvisibleLavaBlock}
 * (sonic3k.asm:43270-43272).
 *
 * <p>The routine is two instructions long: {@code bset #4,shield_reaction(a0)} and then a fall
 * through into {@code Obj_InvisibleHurtBlockHorizontal}. Everything else - the subtype-derived
 * width and height, the three face variants selected by the placement flip bits, and the
 * {@code sub_1F58C} hurt - is the shared hurt block. Bit 4 is {@code Status_FireShield}, so a
 * player carrying a fire shield walks through the lava floor unharmed.
 *
 * <p>44 of these are placed across Lava Reef (34 in act 1, 4 in act 2, 6 in the boss act); all the
 * lava-floor damage in the zone hangs on them.
 */
public class Sonic3kInvisibleLavaBlockObjectInstance extends Sonic3kInvisibleHurtBlockHObjectInstance {

    public Sonic3kInvisibleLavaBlockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "InvisibleLavaBlock", REACTION_FIRE_SHIELD);
    }
}
