package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.level.objects.ObjectInstance;

/** Native parent3 $38 bit6 and render_flags bit0 read by Obj_MechaSonic_Sparks. */
public interface SszMechaSparkHost extends ObjectInstance {
    boolean stopsSparks();
    boolean sparkFlipX();
}
