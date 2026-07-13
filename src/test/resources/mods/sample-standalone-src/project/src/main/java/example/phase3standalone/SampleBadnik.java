package example.phase3standalone;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.audio.StreamedMusicPort.SfxRef;

import java.util.List;

public final class SampleBadnik extends AbstractBadnikInstance implements RewindRecreatable {
    private int subpixel;
    private boolean announced;

    public SampleBadnik(ObjectSpawn spawn) { super(spawn, "phase3-standalone:walker"); }

    @Override protected void updateMovement(int frameCounter, PlayableEntity player) {
        subpixel += 0x80;
        if (subpixel >= 0x100) { currentX++; subpixel -= 0x100; }
        if (!announced) {
            announced = services().playSfx(new SfxRef("phase3-standalone", "hit"));
        }
    }

    @Override protected int getCollisionSizeIndex() { return 1; }
    @Override protected DestructionConfig getDestructionConfig() {
        return new DestructionConfig(0, null, false, null, null, false);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SampleBadnik(context.spawn());
    }
}
