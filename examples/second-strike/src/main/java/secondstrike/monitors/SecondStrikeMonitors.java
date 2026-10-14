package secondstrike.monitors;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorFactory;

/** Native Robotnik contents for ordinary first-act boxes, preserving every speed-shoe box. */
public final class SecondStrikeMonitors {
    public static final String ROBOTNIK = "second-strike:robotnik-monitor";

    private SecondStrikeMonitors() { }

    public static void register(ModContext context) {
        context.registerObject("robotnik-monitor", (spawn, registry) -> Sonic3kMonitorFactory.eggman(spawn));
    }

    public static boolean replaces(ObjectSpawn spawn) {
        return spawn.ownerModId() == null && spawn.objectId() == 0x01 && (spawn.subtype() & 0x0F) != 4;
    }
}
