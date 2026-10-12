package eggsky;

import com.openggf.mods.scene.*;
import eggsky.art.Art;
import eggsky.world.Biome;
import java.lang.reflect.Proxy;
import java.util.*;

/** Original test pixels behind the SDK boundary, with recipe identity metadata; no ROM pixel authority. */
final class ArtFixture {
    long ticks;
    int spriteLoads;
    int paletteLoads;
    RuntimeException paletteFailure;
    RuntimeException spriteFailure;
    boolean primaryAvailable=true;
    final Storage storage = new Storage();
    final SceneLevelKit kit;
    final SceneContext context;
    final SceneRomArt rom;
    final SceneSprite sprite = new SceneSprite(new SceneImage(1,1,new int[]{0xFFFF2020}),0,0);
    ArtFixture() {
        int size=128;
        int[] pixels=new int[size*size];Arrays.fill(pixels,0xFFC08040);
        SceneImage solid=new SceneImage(size,size,pixels),empty=new SceneImage(size,size,new int[pixels.length]);
        kit=new SceneLevelKit() {
            public int blockSize(){return size;}public int columns(){return 4;}public int rows(){return 3;}
            public int blockCount(){return 2;}
            public int block(int x,int y){return x>=0&&x<4&&y>=1&&y<3?1:0;}
            public SceneImage blockImage(int id){return id==1?solid:empty;}
            public byte[] blockSolidity(int id){byte[] mask=new byte[size*size];if(id==1)Arrays.fill(mask,SOLID);return mask;}
            public int[] playableArea(){return new int[]{0,0,4*size,3*size};}
            public SceneBackdrop backdrop(){return new SceneBackdrop(solid,List.of(new SceneBackdrop.Band(0,size,.5,0)));}
            public int[] palette(){int[] colors=new int[64];Arrays.fill(colors,0xFFC08040);return colors;}
        };
        SceneSpriteSet sprites=new SceneSpriteSet() {
            public int frameCount(){return 4;}public SceneSprite frame(int index){return sprite;}
            public int[] animationFrames(int animation){return new int[]{0};}public int animationDelay(int animation){return 8;}
        };
        rom=(SceneRomArt)Proxy.newProxyInstance(SceneRomArt.class.getClassLoader(),new Class<?>[]{SceneRomArt.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "gameId"->"s3k";case "romSha1"->"cfbf98c36c776677290a872547ac47c53d2761d6";case "levelKit"->kit;case "hasLevelKit"->true;
                    case "sprites"->{spriteLoads++;if(spriteFailure!=null)throw spriteFailure;yield sprites;}
                    case "character","characterAccessory"->sprites;
                    case "palette"->{paletteLoads++;if(paletteFailure!=null)throw paletteFailure;int[] colors=new int[(int)args[1]];Arrays.fill(colors,0xFFC08040);yield colors;}
                    default->throw new UnsupportedOperationException(method.getName());
                });
        SceneArt art=new SceneArt() {
            public SceneImage png(byte[] data){throw new UnsupportedOperationException();}
            public SceneRomArt rom(){return primaryAvailable?rom:null;}
            public SceneRomArt rom(String game){return primaryAvailable&&game.equals("s3k")?rom:null;}
        };
        context=(SceneContext)Proxy.newProxyInstance(SceneContext.class.getClassLoader(),new Class<?>[]{SceneContext.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "art"->art;case "storage"->storage;case "ownerModId"->"eggmans-sky";
                    case "width"->400;case "height"->224;case "ticks"->ticks;
                    default->throw new UnsupportedOperationException(method.getName());
                });
    }
    Game game() {
        Game game=new Game();game.ctx=context;game.art=new Art(context);
        game.usableBiomes=List.of(new Biome("s3k",0,0,"Fixture",Biome.CLIMATE_TEMPERATE,Biome.WEATHER_NONE,1,Biome.FLORA_JUNGLE,1,false));
        return game;
    }
    static final class Storage implements SceneStorage {
        final Map<String,String> files=new HashMap<>();
        public Optional<String> read(String name){return Optional.ofNullable(files.get(name));}
        public boolean write(String name,String text){files.put(name,text);return true;}
        public Optional<byte[]> readBytes(String name){return Optional.empty();}
        public boolean writeBytes(String name,byte[] data){return false;}
        public boolean delete(String name){return files.remove(name)!=null;}
        public List<String> list(){return List.copyOf(files.keySet());}
    }
}
