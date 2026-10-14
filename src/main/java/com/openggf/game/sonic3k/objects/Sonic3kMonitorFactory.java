package com.openggf.game.sonic3k.objects;

import com.openggf.game.ModApi;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

/** Bounded native S3K monitor recipe; shell, contacts, contents slots and rewind stay host-owned. */
@ModApi
public final class Sonic3kMonitorFactory {
    private Sonic3kMonitorFactory() { }
    /** Retains the exact placement while choosing shipped Eggman contents (Ani_Monitor_Eggman). */
    public static AbstractObjectInstance eggman(ObjectSpawn spawn) {
        return Sonic3kMonitorObjectInstance.withEggmanContents(spawn);
    }
}
