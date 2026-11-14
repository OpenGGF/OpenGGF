package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.ObjectServices;

/** Facts about the loaded Wacky Workbench stage that the zone's objects branch on. */
final class WwzStage {
    private WwzStage() { }

    /**
     * Whether the stage's object index leaves {@code objectId} to {@code NullObject}
     * ({@code move.b #0,(a0)}). Act 2's index blanks the Goddess statue ($08)
     * (r6/object_index_2.asm:11); act 3's blanks the launcher, freezer, Goddess statue and the
     * badniks $30-$33 (r6/object_index_3.asm:7-8, 11, 51-54), whose IDs the boss parts reuse.
     */
    static boolean nullObject(ObjectServices services, int objectId) {
        int act = actNumber(services);
        return switch (objectId) {
            case 0x08 -> act >= 2;
            case 0x04, 0x05, 0x30, 0x32, 0x33 -> act == 3;
            default -> false;
        };
    }

    /** 1 to 3, or 0 outside a Sonic CD level. */
    static int actNumber(ObjectServices services) {
        var levels = services == null ? null : services.levelManager();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel cd ? cd.payload().stage().actNumber() : 0;
    }

    /** The stage's act and period for the objects whose timers differ by time zone. */
    static CdLevel level(ObjectServices services) {
        var levels = services == null ? null : services.levelManager();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel cd ? cd : null;
    }
}
