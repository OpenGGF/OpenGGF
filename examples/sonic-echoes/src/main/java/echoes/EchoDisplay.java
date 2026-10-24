package echoes;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.mods.ui.*;
import java.util.List;

/** World slot labels and ROM sparkles, followed by the room overlay. */
public final class EchoDisplay extends AbstractObjectInstance implements ModRewindRecreatable {
    @com.openggf.game.rewind.RewindTransient(reason="Immutable derived font, rebuilt by constructor")
    private final BitmapFont digits = BitmapFont.binary("12345",
            "010110010010111" + "111001111100111" + "111001111001111" + "101101111001001" + "111100111001111",3,5,4);
    @com.openggf.game.rewind.RewindTransient(reason="Derived immutable HUD, rebuilt by constructor")
    private final EchoHud hud = new EchoHud();
    public EchoDisplay(ObjectSpawn spawn) { super(spawn,"Echo feedback"); }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new EchoDisplay(context.spawn()); }
    // The room's frame controller hides the stock HUD once play starts, but the act's entry
    // (title-card phase) draws before that, so the stock SCORE/TIME/RINGS overlapped the room
    // HUD for about a second. This object exists from the level's first frame; during the entry
    // it is drawn but not updated, so the presentation-only flag is also set as it draws, before
    // the HUD pass reads it.
    @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) {
        services().levelManager().setForceHudSuppressed(true);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        services().levelManager().setForceHudSuppressed(true);
        var session = services().gameService(RoomSession.class);
        var camera = services().camera();
        var canvas = new LevelOverlayCanvas(services().graphicsManager(), camera.getWidth(), camera.getHeight());
        var bodies = services().spriteManager().getSidekicks();
        for (int i=0;i<session.recordings().size();i++) {
            var body = bodies.get(i);
            if (!session.fractured(i)) digits.draw(canvas,Integer.toString(i+1),
                    body.getCentreX()-camera.getX()-1,body.getCentreY()-camera.getY()-32,1,0xFFFFFFFF);
            int flash=session.fractureFlash(i);
            if (flash <= 0) continue;
            if (flash > 14) canvas.fill(0,0,canvas.width(),canvas.height(),0x30FFFFFF);
            var sparkle = getRenderer(EchoesMod.ID + ":sparkle");
            if (sparkle != null) for (int n=0;n<4;n++) {
                int spread=(18-flash)*2;
                sparkle.drawFrameIndex(1 + (18-flash)/6 % 4,session.fractureX(i)+(n%2==0?-spread:spread),
                        session.fractureY(i)+(n<2?-spread:spread),false,false);
            }
        }
        hud.draw(canvas, session);
    }
}
