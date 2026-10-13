package chaos;

import com.openggf.mods.*;
import java.util.*;

/** Behavioral checks for rarity, admission/rewind, rejected settings and actual sidegame input. */
public final class ChaosModelChecks {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class Port implements ChaosSchedule.Admission {
        long id; final List<String> log=new ArrayList<>(); final Set<Long> alive=new HashSet<>();
        @Override public boolean eligible(ChaosCatalog.Effect effect,Map<String,Object> options) {return true;}
        @Override public long activate(ChaosCatalog.Effect effect,Map<String,Object> options,int duration) {
            alive.add(++id);log.add(effect.id()+":"+new TreeMap<>(options)+":"+duration);return id;
        }
        @Override public void cancel(long activation) {alive.remove(activation);log.add("cancel:"+activation);}
    }
    public static void emptyPoolPreservesRandomnessAndRarePoolCannotEscalate() {
        var catalogue=new ChaosCatalog();
        var empty=new ChaosSchedule(catalogue,new ChaosSettings(5,4,Set.of(),true,1),17,DecisionSource.local());
        long seed=empty.capture().random();Port port=new Port();
        for(int n=0;n<10000;n++) empty.presentation(port);
        check(seed==empty.capture().random() && port.id==0,"Empty pool consumed RNG or activated an effect");
        var rare=new ChaosSchedule(catalogue,new ChaosSettings(5,4,Set.of("gravity-roulette"),true,1),17,DecisionSource.local());
        for(int n=0;n<150000;n++) rare.presentation(port);
        check(port.id>0 && port.id<15,"Rare-only selection renormalized upwards or never ran");
    }
    public static void capturedScheduleReplaysChoicesAndExpiry() {
        var c=new ChaosCatalog();Port port=new Port();
        var schedule=new ChaosSchedule(c,new ChaosSettings(5,1,Set.of("moon-gravity","heavy-gravity","bobble-head"),true,1),77,DecisionSource.local());
        for(int n=0;n<1700;n++) schedule.presentation(port);
        var snapshot=schedule.capture();long id=port.id;Set<Long> alive=Set.copyOf(port.alive);
        port.log.clear();for(int n=0;n<8000;n++) schedule.presentation(port);
        var original=List.copyOf(port.log);var state=schedule.capture();
        schedule.restore(snapshot);port.id=id;port.alive.clear();port.alive.addAll(alive);port.log.clear();
        for(int n=0;n<8000;n++) schedule.presentation(port);
        check(original.equals(port.log),"Restored schedule changed configuration/order/expiry");
        check(state.equals(schedule.capture()),"Restored schedule changed its captured state");
        check(schedule.active().size()<=2,"Overlap exceeded the budget");
        schedule.edit(schedule.settings().enabled(false),port);
        check(schedule.active().isEmpty() && port.alive.isEmpty(),"Pause left an owned policy active");
        var grace=new ChaosSchedule(c,new ChaosSettings(30,4,Set.of("brake-check"),true,1),77,DecisionSource.local());
        grace.edit(grace.settings().interval(5),port);
        check(grace.countdown()==600,"Editing interval erased initial five-second grace");
    }
    private static final class Storage implements ModStorage {
        final Map<String,String> files=new HashMap<>(); boolean writable=true;
        @Override public Optional<String> read(String name) {return Optional.ofNullable(files.get(name));}
        @Override public boolean write(String name,String text) {if(!writable)return false;files.put(name,text);return true;}
        @Override public Optional<byte[]> readBytes(String name) {return Optional.empty();}
        @Override public boolean writeBytes(String name,byte[] bytes) {return false;}
        @Override public boolean delete(String name) {return files.remove(name)!=null;}
        @Override public List<String> list() {return files.keySet().stream().sorted().toList();}
    }
    public static void settingsPreserveRejectedBytesAndRetryWithoutApplyingGameplay() {
        var c=new ChaosCatalog();var store=new Storage();var prefs=new ChaosPreferences(store,c);
        var wanted=ChaosSettings.defaults(c).enabled(true).interval(5).multiplier(3).select("ring-pong",true,c);
        check(prefs.save(wanted),"Valid preference publication failed");
        check(new ChaosPreferences(store,c).saved().equals(wanted),"Preferences did not round trip");
        String original=store.files.get("settings.txt");store.writable=false;
        check(!prefs.save(wanted.interval(120)),"Failed storage publication was accepted");
        check(prefs.saved().equals(wanted) && store.files.get("settings.txt").equals(original),"Failure changed admitted settings or bytes");
        store.writable=true;check(prefs.save(wanted.interval(120)),"Retry did not publish");
        store.files.put("settings.txt","formatVersion=99\nprivate=keep-me\n");
        var future=new ChaosPreferences(store,c);check(!future.save(wanted),"Future settings overwritten without reset");
        check(store.files.get("settings.txt").contains("keep-me"),"Unknown user bytes were discarded");
        check(future.reset(),"Explicit reset could not replace rejected bytes");
        store.files.remove("settings-backup.txt");
        store.files.put("settings.txt",original+"interval=5\n");
        check(!new ChaosPreferences(store,c).save(wanted),"Ambiguous duplicate fields were accepted and overwritten");
    }
    public static void allSidegamesCaptureInputAndHaveBoundedCancellation() {
        var neutral=new ChaosSidegame.Input(false,false,false,false,0,false);
        for(var kind:ChaosSidegame.Kind.values()) {
            var game=new ChaosSidegame(kind,123);
            for(int n=0;n<60;n++) game.step(neutral);
            var snapshot=game.capture();
            var input=new ChaosSidegame.Input(true,false,false,false,1,false);
            for(int n=0;n<120;n++) game.step(input);
            var a=game.capture();game.restore(snapshot);
            for(int n=0;n<120;n++) game.step(input);
            var b=game.capture();
            check(a.age()==b.age() && a.score()==b.score() && a.random()==b.random()
                    && Arrays.equals(a.state(),b.state()) && Arrays.equals(a.board(),b.board()),"Sidegame replay changed: "+kind);
            var fresh=new ChaosSidegame(kind,9);fresh.step(new ChaosSidegame.Input(false,false,false,false,0,true));
            check(fresh.cancelled() && fresh.finished() && fresh.quietSeconds()==0,"Cancellation awarded or trapped player: "+kind);
            for(int n=0;n<1300;n++) game.step(neutral);
            check(game.finished() && game.age()<=1200,"Deadline failed for "+kind);
        }
        var snake=new ChaosSidegame(ChaosSidegame.Kind.SNAKE,1);int head=snake.capture().board()[0];
        for(int n=0;n<8;n++) snake.step(neutral);
        check(snake.capture().board()[0]==head+1,"Snake input/movement did not operate");
        var stack=new ChaosSidegame(ChaosSidegame.Kind.STACK,1);stack.step(new ChaosSidegame.Input(false,false,false,false,1,false));
        check(Arrays.stream(stack.capture().board()).anyMatch(v->v!=0),"Stack hard drop did not lock a real piece");
        var rhythm=new ChaosSidegame(ChaosSidegame.Kind.RHYTHM,2);
        for(int n=0;n<82;n++) rhythm.step(neutral);
        int lane=rhythm.capture().state()[0];rhythm.step(new ChaosSidegame.Input(false,false,false,false,1<<lane,false));
        check(rhythm.score()==1,"Rhythm hit did not score");
        var defence=new ChaosSidegame(ChaosSidegame.Kind.DEFENCE,8);
        for(int n=0;n<48;n++) defence.step(neutral);
        int occupied=-1;for(int i=0;i<24;i++) if(defence.capture().board()[i]>0) {occupied=i/8;break;}
        check(occupied>=0,"Defence never spawned an enemy");
        defence.step(new ChaosSidegame.Input(false,false,false,false,1<<occupied,false));
        for(int n=0;n<12;n++) defence.step(neutral);
        check(defence.score()==1,"Defender input failed to defeat the admitted enemy");
    }
}
