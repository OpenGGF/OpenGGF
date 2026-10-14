package secondstrike.monitors;

import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

/**
 * Native Robotnik icon, hurt, shell persistence and contents-slot graph.
 * ROM off_1D87C and Ani_Monitor_Eggman select subtype0, not an empty box.
 * The original spawn subtype stays intact as part of its placement identity.
 */
public final class RobotnikMonitor extends Sonic3kMonitorObjectInstance {
    public RobotnikMonitor(ObjectSpawn spawn) { super(spawn, 0); }

    @Override public RobotnikMonitor recreateForRewind(RewindRecreateContext context) {
        return new RobotnikMonitor(context.spawn());
    }
}
