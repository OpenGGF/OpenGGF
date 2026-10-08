package starfall;

import com.openggf.mods.testing.ModTestKit;
import com.openggf.mods.scene.host.SceneHostTestAccess;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.patch.GameplayLaunchRequest;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Production jar, owner boundary, physical input, rendering records and atomic scene storage. */
class SceneTest {
    @TempDir Path work;
    private ModTestKit open(Path mods,Path saves) throws Exception {
        Path classes=Path.of(StarfallMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ModTestKit kit=ModTestKit.packageAndOpen(classes,mods,saves);
        var module=kit.launch(new Sonic3kGameModule(),new GameplayLaunchRequest("s3k","sonic",List.of()));
        kit.openScene(module,528,224,new SceneServices(null,null,saves,
                (x,y)->new int[]{(int)x,(int)y,x>=0&&x<528&&y>=0&&y<224?1:0},null,null));
        kit.tick();return kit;
    }
    private Object scene(ModTestKit kit){return SceneHostTestAccess.scene(kit.sceneHost());}
    private Object call(ModTestKit kit,String method) throws Exception {return scene(kit).getClass().getMethod(method).invoke(scene(kit));}
    private long ticks(ModTestKit kit) throws Exception {Object world=call(kit,"world");return world.getClass().getField("ticks").getLong(world);}
    private void press(ModTestKit kit,int key){kit.input().key(key,true);kit.tick();kit.input().key(key,false);kit.tick();}
    @Test void keyboardPanelsPauseAndRepeatedDrawsNeverAdvanceWorld() throws Exception {
        try(var kit=open(work.resolve("mods"),work.resolve("saves"))) {
            assertFalse(kit.draw().isEmpty());press(kit,GLFW_KEY_ENTER);assertEquals("PLAY",call(kit,"screen"));
            kit.input().key(GLFW_KEY_RIGHT,true);for(int i=0;i<20;i++)kit.tick();kit.input().key(GLFW_KEY_RIGHT,false);kit.tick();
            for(int key:new int[]{GLFW_KEY_C,GLFW_KEY_TAB,GLFW_KEY_J,GLFW_KEY_M,GLFW_KEY_P}) {
                press(kit,key);assertNotEquals("PLAY",call(kit,"screen"));long before=ticks(kit);
                for(int i=0;i<10;i++){kit.tick();assertFalse(kit.draw().isEmpty());}
                assertEquals(before,ticks(kit));press(kit,GLFW_KEY_BACKSPACE);assertEquals("PLAY",call(kit,"screen"));
            }
            long before=ticks(kit);for(int i=0;i<20;i++)kit.draw();assertEquals(before,ticks(kit));
            assertTrue(kit.disabledOwners().isEmpty());assertTrue(kit.findings().isEmpty(),kit.findings()::toString);
        }
    }
    @Test void atlasPansAcrossTheExpandedWorldWithoutAdvancingGameplay() throws Exception {
        try(var kit=open(work.resolve("mods"),work.resolve("saves"))) {
            press(kit,GLFW_KEY_ENTER);press(kit,GLFW_KEY_M);
            var mapX=scene(kit).getClass().getDeclaredField("mapX");mapX.setAccessible(true);
            int original=mapX.getInt(scene(kit));long before=ticks(kit);
            press(kit,GLFW_KEY_RIGHT);assertTrue(mapX.getInt(scene(kit))>original);
            kit.input().handler().handleMouseMove(492,39);
            kit.input().handler().handleMouseButton(GLFW_MOUSE_BUTTON_LEFT,GLFW_PRESS);kit.tick();
            kit.input().handler().handleMouseButton(GLFW_MOUSE_BUTTON_LEFT,GLFW_RELEASE);kit.tick();
            assertTrue(mapX.getInt(scene(kit))>8000);assertFalse(kit.draw().isEmpty());
            assertEquals(before,ticks(kit));assertTrue(kit.findings().isEmpty(),kit.findings()::toString);
            press(kit,GLFW_KEY_BACKSPACE);press(kit,GLFW_KEY_M);
            assertTrue(mapX.getInt(scene(kit))<100,"reopening centers on the player");
        }
    }
    @Test void heldMouseChopsAndBuildsWhileLetterboxClicksCannotAct() throws Exception {
        try(var kit=open(work.resolve("mods"),work.resolve("saves"))) {
            press(kit,GLFW_KEY_ENTER);for(int i=0;i<20;i++)kit.tick();
            Object w=call(kit,"world");Class<?> type=w.getClass();
            int width=type.getField("width").getInt(w),surface=(int)type.getMethod("surface",int.class).invoke(w,40);
            byte[] tiles=(byte[])type.getField("tiles").get(w);tiles[(surface-2)*width+43]=World.LOG;tiles[(surface-1)*width+43]=World.LOG;
            press(kit,GLFW_KEY_2); // axe
            java.lang.reflect.Field cx=scene(kit).getClass().getDeclaredField("cameraX"),cy=scene(kit).getClass().getDeclaredField("cameraY");
            cx.setAccessible(true);cy.setAccessible(true);
            int px=(int)(43*World.T+6-cx.getDouble(scene(kit))),py=(int)((surface-1)*World.T+6-cy.getDouble(scene(kit)));
            var input=kit.input().handler();input.handleMouseMove(px,py);input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT,GLFW_PRESS);
            for(int i=0;i<25;i++)kit.tick();input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT,GLFW_RELEASE);kit.tick();
            int[] inventory=(int[])type.getField("inventory").get(w);assertEquals(6,inventory[Content.Item.WOOD.ordinal()]);
            assertEquals(World.AIR,tiles[(surface-2)*width+43]);assertEquals(World.AIR,tiles[(surface-1)*width+43]);
            press(kit,GLFW_KEY_4);input.handleMouseMove(-30,py);input.handleMouseButton(GLFW_MOUSE_BUTTON_RIGHT,GLFW_PRESS);
            kit.tick();input.handleMouseButton(GLFW_MOUSE_BUTTON_RIGHT,GLFW_RELEASE);kit.tick();assertEquals(6,inventory[Content.Item.WOOD.ordinal()]);
            input.handleMouseMove(px,py);input.handleMouseButton(GLFW_MOUSE_BUTTON_RIGHT,GLFW_PRESS);kit.tick();
            input.handleMouseButton(GLFW_MOUSE_BUTTON_RIGHT,GLFW_RELEASE);kit.tick();
            assertEquals(World.PLANK,tiles[(surface-1)*width+43]);assertEquals(5,inventory[Content.Item.WOOD.ordinal()]);
            assertTrue(kit.findings().isEmpty(),kit.findings()::toString);
        }
    }
    @Test void saveContinueAndBackupRecoveryRetainTheSameTerrainAndInventory() throws Exception {
        Path saves=work.resolve("saves");String encoded;
        try(var kit=open(work.resolve("mods-a"),saves)) {
            press(kit,GLFW_KEY_ENTER);for(int i=0;i<90;i++)kit.tick();
            press(kit,GLFW_KEY_P); // freeze exact state before closing
            Object world=call(kit,"world");Class<?> codec=kit.loader("starfall-frontier").loadClass("starfall.SaveCodec");
            encoded=(String)codec.getMethod("encode",world.getClass()).invoke(null,world);
        }
        Path primary=saves.resolve("starfall-frontier/world.sav");
        if(!Files.exists(primary))primary=saves.resolve("mods/starfall-frontier/world.sav");
        assertTrue(Files.exists(primary));assertEquals(encoded,saveText(primary));
        try(var kit=open(work.resolve("mods-b"),saves)) {
            press(kit,GLFW_KEY_ENTER);assertEquals("PLAY",call(kit,"screen"));assertTrue(ticks(kit)>=90);
            press(kit,GLFW_KEY_P);
        }
        Path backup=primary.resolveSibling("world-backup.sav");assertTrue(Files.exists(backup));
        String backupText=Files.readString(backup);Files.writeString(primary,"corrupt");
        try(var kit=open(work.resolve("mods-c"),saves)) {
            assertEquals("TITLE",call(kit,"screen"));press(kit,GLFW_KEY_ENTER);press(kit,GLFW_KEY_P);
            assertTrue(ticks(kit)>0);assertTrue(kit.findings().isEmpty());
        }
        assertNotEquals("corrupt",Files.readString(primary));assertEquals(backupText,Files.readString(backup));
    }
    private String saveText(Path primary) throws Exception {
        String text=Files.readString(primary);
        if(!text.startsWith("STARFALL-CHUNKS-1\n"))return text;
        String[] lines=text.split("\n");StringBuilder data=new StringBuilder();
        for(int n=0;n<Integer.parseInt(lines[2]);n++) {
            Path part=primary.resolveSibling("world-part-"+lines[1]+"-"+n+".sav");
            assertTrue(Files.size(part)<=1048576);data.append(Files.readString(part));
        }
        return data.toString();
    }
    @Test void debugViewsRenderWithoutSavingOrReplacingRealProgress() throws Exception {
        Path saves=work.resolve("saves");
        try(var kit=open(work.resolve("mods"),saves)) {
            for(String command:List.of("play","craft","inventory","journal","map","cavern","warden","victory")) {
                assertTrue(kit.sceneHost().debugJump(command));kit.tick();assertFalse(kit.draw().isEmpty());
                assertTrue(kit.findings().isEmpty(),kit.findings()::toString);
            }
        }
        try(var files=Files.walk(saves)){assertTrue(files.noneMatch(p->p.getFileName().toString().endsWith(".sav")));}
    }
}
