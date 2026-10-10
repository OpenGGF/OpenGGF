package starpost.realruins;

import com.openggf.game.ActExit;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.state.SnapshotRandom;
import java.util.*;
import starpost.core.Game;
import starpost.realtown.TownSession;
import starpost.ruins.*;

/** Captured daily chamber progress; the scene remains the sole owner of the live Game. */
public final class RuinsSession implements RewindSnapshottable<RuinsSession.Snapshot> {
    private Game game;
    private TownSession town;
    private RuinsArt art;
    private starpost.scene.Shell shell;
    private boolean preserveLoad;
    private Chamber chamber;
    private int number=1;
    private boolean active, up, down, action, menu;
    private long ticks;
    private SnapshotRandom rng=new SnapshotRandom(1);
    private final BitSet taken=new BitSet();
    private final Map<Integer,Badnik> badniks=new LinkedHashMap<>();
    private final List<Badnik.Shot> shots=new ArrayList<>();
    private final List<Animal> animals=new ArrayList<>();
    private final List<Find> finds=new ArrayList<>();
    private int spawnedFinds;
    private final List<Puff> puffs=new ArrayList<>();
    private ActExit exit;
    private String target;
    private String notice="";
    private int noticeTicks;
    private int lastRings;
    public void attachShell(starpost.scene.Shell shell) { this.shell=shell; }
    public void updateMusic(boolean drowning) {
        if(shell==null || drowning) return;
        shell.music.want("s1",RuinsRules.music(chamber.band)); shell.music.update();
    }
    public void prepare(Game game,TownSession town,RuinsArt art,int number) { prepare(game,town,art,number,false); }
    public void prepare(Game game,TownSession town,RuinsArt art,int number,boolean retain) {
        preserveLoad=retain && this.game==game && this.number==number;
        this.game=game; this.town=town; this.art=art; this.number=number;
        active=true; exit=null; target=null; if(!preserveLoad) ticks=0; clearInput(); regenerate();
    }
    /** Every load gets the seed/day's fresh generated chamber. No saved collision/layout data. */
    public Chamber regenerate() {
        Snapshot retained=preserveLoad?capture():null;
        chamber=new ChamberGen(art).generate(number,RuinsRules.chamberSeed(RuinsSystem.section(game).seed,
                game.calendar.dayNumber(),number));
        if(chamber==null) throw new IllegalStateException("No Sonic 1 kit for chamber "+number);
        taken.clear(); badniks.clear(); shots.clear(); animals.clear(); finds.clear(); puffs.clear(); spawnedFinds=0;
        rng=new SnapshotRandom(RuinsRules.chamberSeed(RuinsSystem.section(game).seed,game.calendar.dayNumber(),number)^0x5245494EL);
        for(int i=0;i<chamber.things.size();i++) {
            var t=chamber.things.get(i);
            if(t.type()==Chamber.BADNIK) badniks.put(i,new Badnik(t.param(),t.x(),t.y()));
        }
        RuinsSystem.section(game).bestChamber=Math.max(RuinsSystem.section(game).bestChamber,number);
        if(retained!=null) { restoreRoom(retained); spawnedFinds=0; }
        return chamber;
    }
    public starpost.scene.Shell shell() { return shell; }
    public Game game() { return game; }
    public RuinsArt art() { return art; }
    public Chamber chamber() { return chamber; }
    public boolean active() { return active; }
    public long ticks() { return ticks; }
    public SnapshotRandom rng() { return rng; }
    public boolean taken(int index) { return taken.get(index); }
    public void take(int index) { taken.set(index); }
    public Badnik badnik(int index) { return badniks.get(index); }
    public List<Badnik.Shot> shots() { return shots; }
    public List<Animal> animals() { return animals; }
    public List<Find> finds() { return finds; }
    public List<Puff> puffs() { return puffs; }
    public void puff(float x,float y) { puffs.add(new Puff(x,y,0)); }
    public int nextFindToSpawn() { return spawnedFinds<finds.size()?spawnedFinds++:-1; }
    public boolean owned(String id) { return id.equals("super_sunflower_seeds")?RuinsSystem.section(game).seedFound:
        starpost.ruins.RuinsContent.recordSong(id)>=0 && game.flags.contains(starpost.ruins.RuinsContent.recordFlag(id)); }
    public void drop(String id,int count,float x,float feet) {
        if(id!=null && game.catalog.hasItem(id) && !owned(id)) finds.add(new Find(id,count,x,feet-16,0,-3,0));
    }
    public boolean up() { return up; }
    public boolean down() { return down; }
    public boolean action() { return action; }
    public ActExit exit() { return exit; }
    public String notice() { return noticeTicks>0?notice:""; }
    public void input(boolean up,boolean down,boolean action,boolean menu) {
        this.up|=up; this.down|=down; this.action|=action; this.menu|=menu;
    }
    public void clearInput() { up=down=action=menu=false; }
    public void tick(int rings) {
        ticks++; if(noticeTicks>0) noticeTicks--;
        if(rings>lastRings) game.restoreBySpeed(rings/2-lastRings/2);
        lastRings=rings;
        game.calendar.tick();
        if(game.calendar.overtime()) request(ActExit.TIME_UP,"time_up");
        else if(menu) request(ActExit.LEFT,"inventory");
    }
    public void sealed() { notice="THE WAY DOWN IS SEALED... FOR NOW"; noticeTicks=120; }
    public void request(ActExit exit,String target) { if(this.exit==null) { this.exit=exit; this.target=target; } }
    public Map<String,String> payload() { return Map.of("ruins.target",target,"ruins.chamber",Integer.toString(number)); }
    public void arrived(int rings) { lastRings=rings; }
    /** Apply the native ring result exactly once in scene resume. Chamber/menu round trips carry health. */
    public String finish(ActExit reason,int rings) {
        if(!active) throw new IllegalStateException("Ruins result already applied");
        active=false; clearInput();
        if(reason==ActExit.FAINTED) RuinsRules.faint(game,rng);
        else if(!"next".equals(target) && !"inventory".equals(target)) game.rings+=rings;
        return target==null?"leave":target;
    }
    public void find(String id,int count) {
        if(id==null || !game.catalog.hasItem(id)) return;
        if(RuinsRules.isRecord(game.item(id)) && game.flags.contains(starpost.ruins.RuinsContent.recordFlag(id))) return;
        int left=game.inventory.add(game.item(id),count);
        if(left==count) { notice="BAG FULL"; noticeTicks=120; return; }
        if(RuinsRules.isRecord(game.item(id))) game.flags.add(starpost.ruins.RuinsContent.recordFlag(id));
        if(id.equals("super_sunflower_seeds")) RuinsSystem.section(game).seedFound=true;
        notice="FOUND "+game.item(id).name(); noticeTicks=120;
    }
    public boolean breakRock(int index,boolean fire) {
        if(taken(index)) return false;
        if(!game.spend(fire?RuinsRules.FIRE_BREAK_COST:RuinsRules.ROLL_BREAK_COST)) {
            notice="OUT OF MOMENTUM: EAT SOMETHING"; noticeTicks=120; return false;
        }
        take(index); game.xp(starpost.core.Skills.SCRAPPING,3);
        Set<String> records=new HashSet<>();
        for(var item:game.catalog.items()) if(RuinsRules.isRecord(item)
                && (game.inventory.total(item.id())>0 || game.flags.contains(starpost.ruins.RuinsContent.recordFlag(item.id())))) records.add(item.id());
        for(var drop:RuinsRules.oreYield(chamber.band,number,fire,rng,records)) drop(drop.id(),drop.count(),chamber.things.get(index).x(),chamber.things.get(index).y());
        String relic=starpost.museum.Finds.ruinsRelic(chamber.band,rng); if(relic!=null) drop(relic,1,chamber.things.get(index).x(),chamber.things.get(index).y());
        return true;
    }
    public void pop(int index) {
        var b=badnik(index); if(b==null || !b.alive) return;
        b.alive=false; take(index); puff(b.x,b.y);
        var section=RuinsSystem.section(game); section.popped++; section.freed++; game.free();
        game.xp(starpost.core.Skills.BOPPING,10); game.restoreBySpeed(RuinsRules.BOP_MOMENTUM);
        if(RuinsRules.dropsScrap(game,rng)) drop("scrap",1,b.x,b.y);
        String part=starpost.museum.Finds.ruinsPart(b.kind,rng); if(part!=null) drop(part,1,b.x,b.y);
        String name=switch(chamber.band) {
            case RuinsRules.MARBLE -> rng.nextInt(2)==0?"ricky":"rocky";
            case RuinsRules.LABYRINTH -> rng.nextInt(2)==0?"pecky":"rocky";
            default -> rng.nextInt(2)==0?"pocky":"cucky";
        };
        animals.add(new Animal(name,b.x,b.y,-4,false,0));
    }
    public record Puff(float x,float y,int age) {}
    public record Find(String id,int count,float x,float y,float vx,float vy,int age) {}
    public record Animal(String name,float x,float feet,float vy,boolean hopping,int age) {}
    public record Shot(int kind,float x,float y,float vx,float vy,int life,boolean alive) {}
    public record Snapshot(TownSession.Snapshot gameState,int number,boolean active,long ticks,long rng,
            List<Integer> taken,Map<Integer,Badnik.Snapshot> badniks,List<Shot> shots,List<Animal> animals,List<Find> finds,int spawnedFinds,List<Puff> puffs,
            ActExit exit,String target,boolean up,boolean down,boolean action,boolean menu,String notice,int noticeTicks,int lastRings) {}
    public String key() { return "ruins"; }
    public Snapshot capture() {
        Map<Integer,Badnik.Snapshot> b=new LinkedHashMap<>(); badniks.forEach((i,v)->b.put(i,v.capture()));
        List<Shot> s=shots.stream().map(v->new Shot(v.kind,v.x,v.y,v.vx,v.vy,v.life,v.alive)).toList();
        return new Snapshot(town==null?null:town.capture(),number,active,ticks,rng.snapshot(),taken.stream().boxed().toList(),
                Map.copyOf(b),s,List.copyOf(animals),List.copyOf(finds),spawnedFinds,List.copyOf(puffs),exit,target,up,down,action,menu,notice,noticeTicks,lastRings);
    }
    public void restore(Snapshot s) {
        if(s.gameState()!=null) town.restore(s.gameState());
        restoreRoom(s);
    }
    private void restoreRoom(Snapshot s) {
        number=s.number(); active=s.active(); ticks=s.ticks(); rng.restore(s.rng()); taken.clear();
        s.taken().forEach(taken::set); badniks.clear(); s.badniks().forEach((i,b)->badniks.put(i,Badnik.restore(b)));
        shots.clear(); for(var v:s.shots()) { var shot=new Badnik.Shot(v.kind(),v.x(),v.y(),v.vx(),v.vy(),v.life()); shot.alive=v.alive(); shots.add(shot); }
        animals.clear(); animals.addAll(s.animals()); finds.clear(); finds.addAll(s.finds()); spawnedFinds=s.spawnedFinds(); puffs.clear(); puffs.addAll(s.puffs()); exit=s.exit(); target=s.target(); up=s.up(); down=s.down(); action=s.action(); menu=s.menu();
        notice=s.notice(); noticeTicks=s.noticeTicks(); lastRings=s.lastRings();
    }
    public void resetForMissingSnapshot() { active=false; clearInput(); }
}
