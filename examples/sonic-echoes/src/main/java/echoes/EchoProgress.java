package echoes;

import com.openggf.mods.ModStorage;
import java.util.*;

/** Owner-scoped, versioned atomic text save. Parse transactionally; protect corrupt generations. */
public final class EchoProgress {
    public record Best(int echoes, int frames, int stars) { }
    private final ModStorage storage;
    private final Map<String, Best> rooms = new TreeMap<>();
    private boolean writable = true, dirty;
    private int retry;
    public EchoProgress(ModStorage storage) { this.storage = Objects.requireNonNull(storage); }
    public EchoProgress load() {
        try {
            var text=storage.read("progress.txt");
            if(text.isEmpty()) {
                // An unreadable existing file is not permission to overwrite it.
                writable=!storage.list().contains("progress.txt");
                return this;
            }
            Properties p=new Properties(); p.load(new java.io.StringReader(text.get()));
            if(!"1".equals(p.getProperty("formatVersion"))) throw new IllegalArgumentException("save version");
            Map<String,Best> parsed=new TreeMap<>();
            for(String key:p.stringPropertyNames()) {
                if(key.equals("formatVersion")) continue;
                if(!key.matches("[1-3]-[1-4]")) throw new IllegalArgumentException("room ID");
                String[] parts=p.getProperty(key).split(",",-1);
                if(parts.length!=3) throw new IllegalArgumentException("room record");
                int echoes=Integer.parseInt(parts[0]),frames=Integer.parseInt(parts[1]),stars=Integer.parseInt(parts[2]);
                if(echoes<0||echoes>5||frames<0||frames>36000||stars<1||stars>7||(stars&1)==0)
                    throw new IllegalArgumentException("room values");
                parsed.put(key,new Best(echoes,frames,stars));
            }
            rooms.clear(); rooms.putAll(parsed);
        } catch(Exception bad) { writable=false; }
        return this;
    }
    public Best best(String id) { return rooms.get(id); }
    public int totalStars() { return rooms.values().stream().mapToInt(b->Integer.bitCount(b.stars())).sum(); }
    public boolean unlocked(String id) { return requirement(id).isEmpty(); }
    public String requirement(String id) {
        int world=id.charAt(0)-'0',room=id.charAt(2)-'0',stars=totalStars();
        int needed=world==2?3:world==3?9:0;
        if(stars<needed) return needed+" TOTAL STARS";
        if(id.equals("3-4")&&stars<24) return "24 TOTAL STARS";
        if(room>1&&!rooms.containsKey(world+"-"+(room-1))) return "CLEAR "+world+"-"+(room-1);
        return "";
    }
    /** Independent minima; stars are a lifetime OR, shards require one complete loop. */
    public boolean record(RoomDefinition room,int echoes,int frames,int shards) {
        int stars=1|(echoes<=room.par()?2:0)|(shards>=3?4:0);
        Best old=rooms.get(room.id());
        boolean improved=old==null||echoes<old.echoes()||frames<old.frames()||(stars&~old.stars())!=0;
        rooms.put(room.id(),new Best(old==null?echoes:Math.min(echoes,old.echoes()),
                old==null?frames:Math.min(frames,old.frames()),stars|(old==null?0:old.stars())));
        dirty=true; save(); return improved;
    }
    public void tick() { if(dirty&&--retry<=0) save(); }
    private boolean save() {
        retry=60;
        if(!writable||!dirty) return false;
        StringBuilder out=new StringBuilder("formatVersion=1\n");
        rooms.forEach((id,b)->out.append(id).append('=').append(b.echoes()).append(',').append(b.frames()).append(',').append(b.stars()).append('\n'));
        try { if(storage.write("progress.txt",out.toString())) { dirty=false; return true; } }
        catch(RuntimeException ignored) { }
        return false;
    }
}
