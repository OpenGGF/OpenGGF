package starfall;

import com.openggf.mods.scene.SceneStorage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldSavesTest {
    private static final class Storage implements SceneStorage {
        final Map<String,String> files=new HashMap<>();String fail="";
        public Optional<String> read(String name){return Optional.ofNullable(files.get(name));}
        public boolean write(String name,String text) {
            assertTrue(text.length()<=1048576,"storage contract");
            if(name.contains(fail)&&!fail.isEmpty())return false;
            files.put(name,text);return true;
        }
        public boolean delete(String name){return files.remove(name)!=null;}
        public List<String> list(){return List.copyOf(files.keySet());}
    }
    @Test void chunkedPublicationRecoversBackupCleansOldGenerationsAndSurvivesInterruptedWrites() {
        Storage storage=new Storage();World world=new World(73,WorldSize.LARGE);
        // Dense, valid edits force multiple files regardless of terrain compression changes.
        Random random=new Random(12);for(int i=0;i<world.tiles.length;i++)world.tiles[i]=(byte)random.nextInt(World.EMBER+1);
        String first=SaveCodec.encode(world);assertTrue(first.length()>750000);
        assertTrue(WorldSaves.write(storage,first));assertEquals(first,WorldSaves.read(storage,"world.sav").orElseThrow());
        String primary=storage.files.get("world.sav");int count=storage.files.size();
        world.add(Content.Item.WOOD,10);String second=SaveCodec.encode(world);
        storage.fail="-1.sav";assertFalse(WorldSaves.write(storage,second));
        assertEquals(primary,storage.files.get("world.sav"));assertEquals(count+1,storage.files.size(),"only backup added, pending chunks removed");
        assertEquals(first,WorldSaves.read(storage,"world-backup.sav").orElseThrow());
        storage.fail="world.sav";assertFalse(WorldSaves.write(storage,second));
        assertEquals(primary,storage.files.get("world.sav"));
        storage.fail="";assertTrue(WorldSaves.write(storage,second));
        assertEquals(second,WorldSaves.read(storage,"world.sav").orElseThrow());
        assertEquals(first,WorldSaves.read(storage,"world-backup.sav").orElseThrow());
        String secondManifest=storage.files.get("world.sav");world.add(Content.Item.WOOD,1);
        assertTrue(WorldSaves.write(storage,SaveCodec.encode(world)));
        assertEquals(secondManifest,storage.files.get("world-backup.sav"));
        for(String name:storage.list())if(name.startsWith("world-part-"))assertFalse(name.contains(primary.split("\n")[1]),"first generation collected");
        String current=storage.files.get("world.sav");String id=current.split("\n")[1];
        storage.delete("world-part-"+id+"-0.sav");assertTrue(WorldSaves.read(storage,"world.sav").isEmpty());
        assertEquals(second,WorldSaves.read(storage,"world-backup.sav").orElseThrow());
    }
    @Test void invalidManifestCountsAndUnsupportedWorldDimensionsAreRejected() {
        Storage storage=new Storage();
        for(String tail:List.of("../escape\n1\n","a".repeat(32)+"\n999999\n","a".repeat(32)+"\n-1\n")) {
            storage.files.put("world.sav","STARFALL-CHUNKS-1\n"+tail);assertTrue(WorldSaves.read(storage,"world.sav").isEmpty());
        }
        assertThrows(IllegalArgumentException.class,()->new World(1,false,Integer.MAX_VALUE,384));
    }
}
