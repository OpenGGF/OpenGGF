package echoes;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** The four keyed room objects; mutable contact/latch/open state is captured by generic object rewind. */
public final class EchoObject extends AbstractObjectInstance implements SolidObjectProvider, ModRewindRecreatable {
    private int latchRemaining;
    private boolean pressed, open, collected;
    public EchoObject(ObjectSpawn spawn) { super(spawn, "Echo room object"); }
    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new EchoObject(context.spawn()); }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    private RoomSession session() { return services().gameService(RoomSession.class); }
    private RoomDefinition.Placement definition() { return session().room().objects().get(spawn.subtype()); }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    private List<AbstractPlayableSprite> bodies() {
        return services().spriteManager().getAllSprites().stream().filter(AbstractPlayableSprite.class::isInstance)
                .map(AbstractPlayableSprite.class::cast).filter(p -> session().active(p, services())).toList();
    }
    private boolean overlaps(AbstractPlayableSprite body, int padding) {
        var d = definition();
        return Math.abs(body.getCentreX() - getX()) < d.width() / 2 + body.getXRadius() + padding
                && Math.abs(body.getCentreY() - getY()) < d.height() / 2 + body.getYRadius() + padding;
    }
    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        var d = definition();
        switch (d.kind()) {
            case "plate" -> { } // Evaluated together before physics, independent of placement order.
            case "gate" -> {
                boolean requested = session().allChannels(d.channels());
                if (requested || open && bodies().stream().anyMatch(p -> overlaps(p, 3))) open = true;
                else open = false;
            }
            case "rift" -> {
                var live = services().camera().getFocusedSprite();
                if (!live.getDead() && session().allChannels(d.channels()) && overlaps(live, 0)) session().clear();
            }
            case "shard" -> {
                if (!collected && bodies().stream().anyMatch(p -> overlaps(p, 0))) { collected = true; session().shard(spawn.subtype()); }
            }
        }
    }
    void samplePlate() {
        var d = definition();
        if (!d.kind().equals("plate")) return;
        pressed = bodies().stream().anyMatch(p -> !p.getAir() && Math.abs(p.getCentreX() - getX()) <= d.width()/2
                && Math.abs(p.getCentreY() + p.getYRadius() - getY()) <= 12);
        if (pressed) latchRemaining = d.latch();
        boolean active = pressed || latchRemaining > 0;
        if (!pressed && latchRemaining > 0) latchRemaining--;
        session().channel(d.channels(), active);
    }
    public boolean open() { return open; }
    public boolean pressed() { return pressed; }
    @Override public boolean isSolidFor(PlayableEntity player) {
        return definition().kind().equals("gate") && !open;
    }
    @Override public SolidObjectParams getSolidParams() {
        var d = definition();
        return SolidObjectParams.of(d.width()/2, d.height()/2, d.height()/2);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var d = definition();
        if (collected) return;
        String key = switch (d.kind()) {
            case "plate" -> EchoesMod.ID + ":button";
            case "gate" -> EchoesMod.ID + ":barrier";
            case "rift" -> EchoesMod.ID + ":sparkle";
            default -> EchoesMod.ID + ":sparkle";
        };
        var renderer = getRenderer(key);
        if (renderer == null) return;
        if (d.kind().equals("gate")) {
            if (!open) for (int y = getY() - d.height()/2 + 16; y < getY() + d.height()/2; y += 32)
                renderer.drawFrameIndex(0, getX(), y, false, false);
        } else {
            int frame = d.kind().equals("plate") ? (pressed ? 1 : 0) : 1 + session().frame() / 6 % 4;
            renderer.drawFrameIndex(frame, getX(), getY(), false, false);
            if (d.kind().equals("rift")) {
                // The larger paired sparkle silhouette distinguishes an exit from a single shard.
                renderer.drawFrameIndex(1+(session().frame()/6+2)%4,getX(),getY()-14,false,false);
                renderer.drawFrameIndex(1+(session().frame()/6)%4,getX(),getY()+14,false,false);
            }
        }
        if (!d.channels().isEmpty()) drawChannelMarkers(d);
    }
    private void drawChannelMarkers(RoomDefinition.Placement d) {
        var camera=services().camera();
        var canvas=new LevelOverlayCanvas(services().graphicsManager(),camera.getWidth(),camera.getHeight());
        String[] keys=d.channels().split("\\+");
        int x=getX()-camera.getX()-keys.length*5;
        int y=getY()-camera.getY()+(d.kind().equals("gate")?d.height()/2-28:d.kind().equals("rift")?23:-10);
        for(String key:keys) {
            int color=switch(key) {
                case "cyan" -> 0xFF55DFFF;
                case "magenta" -> 0xFFFF66CC;
                case "gold" -> 0xFFFFD454;
                default -> 0xFFB6EF65;
            };
            canvas.fill(x-1,y-1,9,5,0xFF10172D);
            canvas.fill(x,y,7,3,session().channel(key)?color:(color&0x00FFFFFF)|0x65000000);
            x+=10;
        }
    }
}
