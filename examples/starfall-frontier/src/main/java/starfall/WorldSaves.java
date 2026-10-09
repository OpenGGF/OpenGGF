package starfall;

import com.openggf.mods.scene.SceneStorage;
import java.util.*;

/** Atomic manifest publication keeps large worlds inside the storage port's 1 MiB file limit. */
final class WorldSaves {
    private static final String HEADER="STARFALL-CHUNKS-1\n",PREFIX="world-part-";
    private static final int CHUNK=750000,MAX_CHUNKS=(SaveCodec.MAX_TEXT+CHUNK-1)/CHUNK;
    private WorldSaves() { }
    static Optional<String> read(SceneStorage storage,String name) {
        return storage.read(name).flatMap(value-> {
            if(!value.startsWith(HEADER))return Optional.of(value);
            List<String> names=parts(value);if(names.isEmpty())return Optional.empty();
            StringBuilder data=new StringBuilder();
            for(String part:names) {
                Optional<String> chunk=storage.read(part);
                if(chunk.isEmpty()||chunk.get().isEmpty()||chunk.get().length()>CHUNK)return Optional.empty();
                data.append(chunk.get());if(data.length()>SaveCodec.MAX_TEXT)return Optional.empty();
            }
            return Optional.of(data.toString());
        });
    }
    static boolean write(SceneStorage storage,String data) {
        if(data.length()>SaveCodec.MAX_TEXT)return false;
        Optional<String> old=storage.read("world.sav");
        // Back up only a fully readable, valid generation, never a corrupt primary.
        if(read(storage,"world.sav").flatMap(SaveCodec::decode).isPresent()
                &&!storage.write("world-backup.sav",old.orElseThrow()))return false;
        List<String> pending=new ArrayList<>();String published=data;
        if(data.length()>CHUNK) {
            String id=UUID.randomUUID().toString().replace("-","");
            int count=(data.length()+CHUNK-1)/CHUNK;
            published=HEADER+id+"\n"+count+"\n";
            for(int n=0;n<count;n++) {
                String name=PREFIX+id+"-"+n+".sav";pending.add(name);
                if(!storage.write(name,data.substring(n*CHUNK,Math.min(data.length(),(n+1)*CHUNK)))) {
                    pending.forEach(storage::delete);return false;
                }
            }
        }
        if(!storage.write("world.sav",published)){pending.forEach(storage::delete);return false;}
        Set<String> keep=new HashSet<>(parts(published));
        storage.read("world-backup.sav").ifPresent(v->keep.addAll(parts(v)));
        for(String name:storage.list())if(name.matches("world-part-[0-9a-f]{32}-[0-9]+\\.sav")&&!keep.contains(name))storage.delete(name);
        return true;
    }
    private static List<String> parts(String manifest) {
        if(!manifest.startsWith(HEADER))return List.of();
        String[] lines=manifest.split("\n");
        if(lines.length!=3||!lines[1].matches("[0-9a-f]{32}"))return List.of();
        try {
            int count=Integer.parseInt(lines[2]);if(count<1||count>MAX_CHUNKS)return List.of();
            List<String> names=new ArrayList<>();
            for(int n=0;n<count;n++)names.add(PREFIX+lines[1]+"-"+n+".sav");
            return names;
        }catch(NumberFormatException e){return List.of();}
    }
}
