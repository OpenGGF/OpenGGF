package starfall;

import com.openggf.mods.scene.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomeAudioTest {
    @Test void sceneSwitchesOncePerRegionOnContinueCrossingsBossExitAndRecall() throws Exception {
        World saved=new World(73);saved.x=68*World.T;saved.y=(saved.surface(68)+12)*World.T;
        var songs=new ArrayList<Integer>();var files=new HashMap<String,String>();files.put("world.sav",SaveCodec.encode(saved));
        SceneAudio audio=new SceneAudio() {
            public void playMusic(int id){songs.add(id);}public void playSfx(int id){}
            public void fadeOutMusic(){}public void stopMusic(){}
        };
        SceneStorage storage=new SceneStorage() {
            public Optional<String> read(String name){return Optional.ofNullable(files.get(name));}
            public boolean write(String name,String text){files.put(name,text);return true;}
            public boolean delete(String name){return files.remove(name)!=null;}public List<String> list(){return List.copyOf(files.keySet());}
        };
        SceneArt art=new SceneArt(){public SceneRomArt rom(){return null;}public SceneImage png(byte[] bytes){throw new UnsupportedOperationException();}};
        int[] key={SceneKeys.ENTER};
        SceneContext ctx=(SceneContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SceneContext.class},(proxy,method,args)->switch(method.getName()) {
            case "audio" -> audio;case "storage" -> storage;case "art" -> art;case "mouse" -> SceneMouse.none();
            case "width" -> 528;case "height" -> 224;
            case "keyPressed" -> (int)args[0]==key[0];
            default -> method.getReturnType()==boolean.class?false:method.getReturnType()==long.class?0L:method.getReturnType()==int.class?0:null;
        });
        var scene=new FrontierScene(getClass().getClassLoader().getResourceAsStream("art/font.txt").readAllBytes());
        scene.enter(ctx);scene.update(ctx);key[0]=-1;
        assertEquals(List.of(0x2F,3),songs,"continue selects the saved cavern's song immediately");
        World w=scene.world();
        clear(w,68,w.surface(68)+12);
        for(int n=0;n<4;n++)scene.update(ctx);assertEquals(2,songs.size(),"same region does not restart the song");
        clear(w,215,w.surface(215)+36);scene.update(ctx);assertEquals(0x13,songs.getLast());
        var boss=new World.Enemy(w.x+50,w.y-20,3);boss.shrine=0;w.enemies.add(boss);
        scene.update(ctx);assertEquals(0x19,songs.getLast());
        boss.hp=0;scene.update(ctx);assertEquals(0x13,songs.getLast());
        assertTrue(w.recall());scene.update(ctx);assertEquals(1,songs.getLast());
        clear(w,176,w.surface(176)-1);scene.update(ctx);assertEquals(0x0B,songs.getLast());
        key[0]=SceneKeys.P;scene.update(ctx);key[0]=-1;
        int count=songs.size();for(int n=0;n<20;n++)scene.update(ctx);assertEquals(count,songs.size());
    }
    private void clear(World w,int tx,int ty) {
        w.x=tx*World.T+6;w.y=ty*World.T;w.vx=w.vy=0;
        for(int a=-2;a<=2;a++)for(int b=-3;b<=2;b++)w.set(tx+a,ty+b,World.AIR);
        for(int a=-2;a<=2;a++)w.set(tx+a,ty+3,World.STONE);
    }
}
