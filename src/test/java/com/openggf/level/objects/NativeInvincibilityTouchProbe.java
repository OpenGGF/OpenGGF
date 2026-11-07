package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;
import static org.mockito.Mockito.*;

/** An overlapping enemy through the real shared touch dispatcher, without executing the native world body. */
public final class NativeInvincibilityTouchProbe {
    private NativeInvincibilityTouchProbe() { }
    public static boolean touch(AbstractPlayableSprite player) {
        var table = mock(TouchResponseTable.class);
        when(table.getWidthRadius(2)).thenReturn(12);
        when(table.getHeightRadius(2)).thenReturn(12);
        var camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        var services = new TestObjectServices().withCamera(camera);
        var manager = new ObjectManager(List.of(), new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int id) { return "probe"; }
        }, 0, null, table, null, camera, services);
        var enemy = new Enemy(player.getCentreX(), player.getCentreY());
        manager.addDynamicObject(enemy);
        manager.update(0, player, List.of(), 1, false);
        new ObjectTouchResponseController(manager, table).update(player, 2, false);
        return enemy.attacked;
    }
    private static final class Enemy extends AbstractObjectInstance implements TouchResponseProvider, TouchResponseAttackable {
        boolean attacked;
        Enemy(int x,int y) { super(new ObjectSpawn(x,y,0x7F,0,0,false,0), "invincibility-touch-probe"); }
        @Override public int getCollisionFlags() { return 2; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public boolean requiresRenderFlagForTouch() { return false; }
        @Override public void update(int vIntRunCount,PlayableEntity player) { }
        @Override public void onPlayerAttack(PlayableEntity player,TouchResponseResult result) { attacked=true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
