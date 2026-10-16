package example.dynamicrewind;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

import java.util.List;

/** Mod-classloader fixture used to prove independent dynamic-entry rewind recreation. */
public final class DynamicProbe extends AbstractObjectInstance implements GgfMod, ModRewindRecreatable {
    private int value;

    public DynamicProbe() {
        this(new ObjectSpawn(0, 0, 0, 0, 0, false, 0, -1,
                "dynamic-rewind", "dynamic-rewind:probe"));
    }

    public DynamicProbe(ObjectSpawn spawn) {
        super(spawn, "DynamicProbe");
    }

    @Override
    public void register(ModContext context) {
        // Loading and invoking this no-op entrypoint proves the production registration path.
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    @Override
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new DynamicProbe(context.spawn());
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}
