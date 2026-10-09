package starpost.realtown;

import com.openggf.game.rewind.RewindSnapshottable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.core.SaveSection;
import starpost.festivals.Festival;
import starpost.festivals.Festivals;
import starpost.people.Line;
import starpost.people.People;
import starpost.people.Speech;
import starpost.ui.Controls;
import starpost.valley.Pickups;

/** Live scene/act session; the adapter restores in place so suspended screens retain identity. */
public final class TownSession implements RewindSnapshottable<TownSession.Snapshot> {
    private Game game;
    private TownLayout layout;
    private TownPresentation presentation;
    private boolean active;
    private Speech speech;
    private String speaker;
    private String gift;
    private boolean asking;
    private boolean yes = true;
    private boolean invitation;
    private boolean inFestivalZone;
    private HandBack handBack;
    private long ticks;
    private boolean actPressed, upPressed, confirmPressed, choicePressed, menuPressed;
    private int hotbar = -1;
    private boolean initialized;
    private String notice = "";
    private int noticeTicks;

    public record HandBack(String place, String event, int returnX, int returnY) {
        public Map<String, String> payload() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("town.place", place); values.put("town.returnX", Integer.toString(returnX));
            values.put("town.returnY", Integer.toString(returnY));
            if (event != null) values.put("town.event", event);
            return Map.copyOf(values);
        }
    }

    /** Called before act entry. Return visits keep daily pickup bits; a different game resets them. */
    public void bind(Game game, TownLayout layout, TownPresentation presentation) {
        if (this.game != game) { initialized = false; ticks = 0; inFestivalZone = false; }
        this.game = java.util.Objects.requireNonNull(game);
        this.layout = java.util.Objects.requireNonNull(layout); this.presentation = presentation;
        if (game.section(People.class) == null || game.section(Pickups.class) == null)
            throw new IllegalArgumentException("Town needs the existing People and Pickups sections");
        active = true; speech = null; gift = null; speaker = null; handBack = null;
        asking = invitation = false; clearInput();
        game.section(Pickups.class).today(game, layout.ground, layout.springX, layout.loopX);
    }

    public void attachGround(ActGround ground) { layout = layout.withGround(ground); }
    public Game game() { return game; }
    public People people() { return game.section(People.class); }
    public Pickups pickups() { return game.section(Pickups.class); }
    public TownLayout layout() { return layout; }
    public TownPresentation presentation() { return presentation; }
    public boolean active() { return active && game != null; }
    public boolean modal() { return asking || speech != null || invitation || handBack != null; }
    public Speech speech() { return speech; }
    public boolean askingGift() { return asking; }
    public boolean invited() { return invitation; }
    public boolean yes() { return yes; }
    public String speaker() { return speaker; }
    public String gift() { return gift; }
    public long ticks() { return ticks; }
    public HandBack handBack() { return handBack; }
    public String notice() { return noticeTicks > 0 ? notice : ""; }

    /** Bridge consumes exactly once after E1 has returned to the scene. */
    public HandBack consumeHandBack() {
        HandBack result = handBack; handBack = null; active = false; clearInput(); return result;
    }

    public void request(String place, String event, int x, int y) {
        if (handBack == null) handBack = new HandBack(place, event, x, y);
    }

    /** Keys are latched until the persistent controller's update, never applied by drawing. */
    public void input(boolean act, boolean up, boolean confirm, boolean choice, boolean menu, int hotbar) {
        actPressed |= act; upPressed |= up; confirmPressed |= confirm; choicePressed |= choice;
        menuPressed |= menu;
        if (hotbar >= 0) this.hotbar = hotbar;
    }
    public boolean action() { return actPressed; }
    public boolean doorAction() { return upPressed; }
    public void clearInput() {
        actPressed = upPressed = confirmPressed = choicePressed = menuPressed = false; hotbar = -1;
    }

    public void talk(String id) {
        if (modal()) return;
        speaker = id; yes = true;
        Line errand = people().errand(id, game);
        if (errand != null) { speech = Speech.of(people(), game, id, errand); return; }
        gift = game.inventory.selectedId();
        asking = gift != null && people().canGift(id, game.item(gift), game) == People.GiftStatus.GIVEN;
        if (!asking) say(people().talk(id, game).line());
    }
    private void say(Line line) { speech = Speech.of(people(), game, speaker, line); }

    /** Town clock and mod state advance only on executed world updates, not the ROM interrupt count. */
    public void tick(int x, int y, boolean grounded) {
        if (!active()) return;
        ticks++;
        if (!initialized) {
            initialized = true;
            people().morning(game);
            Festivals festivals = game.section(Festivals.class);
            if (festivals != null) festivals.morning(game);
        }
        if (noticeTicks > 0) noticeTicks--;
        if (handBack != null) return;
        if (hotbar >= 0 && !modal()) game.inventory.select(hotbar);
        if (asking || invitation) {
            if (choicePressed) yes = !yes;
            if (confirmPressed || actPressed) {
                if (invitation) {
                    invitation = false;
                    if (yes) request("festival", null, x, y);
                } else {
                    asking = false;
                    if (yes) say(people().gift(speaker, game.item(gift), game).line());
                    else say(people().talk(speaker, game).line());
                }
            }
            return;
        }
        if (speech != null) {
            Controls controls = new Controls(); controls.act = actPressed; controls.confirm = confirmPressed;
            if (speech.update(controls)) { speech = null; speaker = gift = null; }
            return;
        }
        if (menuPressed) { request("inventory", null, x, y); return; }
        game.calendar.tick();
        if (game.calendar.overtime()) { request("time_up", null, x, y); return; }
        Festivals festivals = game.section(Festivals.class);
        if (festivals != null) {
            if (ticks % 30 == 0) festivals.board.checkPops(game, people());
            Festival today = festivals.today(game);
            boolean near = today != null && Math.abs(x - layout.anchor(today.anchor)) < 80;
            if (near && !inFestivalZone && grounded && today.openAt(game.calendar)
                    && !festivals.joined(today.id, game.calendar.year())) {
                invitation = true; yes = true;
            }
            inFestivalZone = near;
        }
        if (!modal() && grounded && ticks % 30 == 0) {
            var event = people().dueEvent(game, false, x, layout::anchor);
            if (event != null) request("heart_event", event.id, x, y);
        }
    }

    public void collected(String item) {
        notice = item == null ? "" : "FOUND " + game.item(item).name(); noticeTicks = 120;
    }

    public record Slot(String id, int count) {}
    public record Snapshot(boolean bound, Calendar.Snapshot calendar, int rings, int momentum, int maxMomentum,
        int population, int water, long earned, long rng, Set<String> flags, List<Slot> inventory, int selected,
        Map<String, Map<String, String>> sections, Pickups.Snapshot pickups, Speech.Snapshot speech,
        String speaker, String gift, boolean asking, boolean yes, boolean invitation, boolean inZone,
        HandBack handBack, long ticks, boolean active, boolean initialized, String notice, int noticeTicks,
        boolean action, boolean up, boolean confirm, boolean choice, boolean menu, int hotbar) {
        public Snapshot {
            flags = Set.copyOf(flags); inventory = List.copyOf(inventory);
            Map<String, Map<String, String>> copy = new LinkedHashMap<>();
            sections.forEach((key, value) -> copy.put(key, Map.copyOf(value))); sections = Map.copyOf(copy);
        }
    }

    public String key() { return "town"; }
    public Snapshot capture() {
        if (game == null) return new Snapshot(false, null, 0,0,0,0,0,0,0, Set.of(),List.of(),0,Map.of(),
                null,null,null,null,false,true,false,false,null,0,false,false,"",0,false,false,false,false,false,-1);
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < game.inventory.size(); i++) slots.add(new Slot(game.inventory.id(i), game.inventory.count(i)));
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        for (SaveSection section : game.sections) {
            Map<String, String> keys = new LinkedHashMap<>(); section.save(keys); sections.put(section.prefix(), keys);
        }
        return new Snapshot(true, game.calendar.capture(),game.rings,game.momentum,game.maxMomentum,game.population,
            game.waterCharges,game.totalEarned,game.rng.snapshot(),game.flags,slots,game.inventory.selected(),sections,pickups().capture(),
            speech == null ? null : speech.capture(),speaker,gift,asking,yes,invitation,inFestivalZone,handBack,
            ticks,active,initialized,notice,noticeTicks,actPressed,upPressed,confirmPressed,choicePressed,menuPressed,hotbar);
    }
    public void restore(Snapshot state) {
        if (!state.bound()) { active = false; speech = null; handBack = null; clearInput(); return; }
        if (game == null) throw new IllegalStateException("Town snapshot needs its live scene game");
        game.calendar.restore(state.calendar()); game.rings=state.rings(); game.momentum=state.momentum();
        game.maxMomentum=state.maxMomentum(); game.population=state.population(); game.waterCharges=state.water(); game.totalEarned=state.earned();
        game.rng.restore(state.rng()); game.flags.clear(); game.flags.addAll(state.flags());
        for (int i=0; i<game.inventory.size(); i++) {
            Slot slot = i < state.inventory().size() ? state.inventory().get(i) : new Slot(null,0);
            game.inventory.set(i, slot.id(), slot.count());
        }
        game.inventory.select(state.selected());
        for (SaveSection section : game.sections) section.load(state.sections().get(section.prefix()), game.catalog);
        pickups().restore(state.pickups());
        speech=state.speech()==null?null:Speech.restore(state.speech()); speaker=state.speaker(); gift=state.gift();
        asking=state.asking(); yes=state.yes(); invitation=state.invitation(); inFestivalZone=state.inZone();
        handBack=state.handBack(); ticks=state.ticks(); active=state.active(); initialized=state.initialized();
        notice=state.notice(); noticeTicks=state.noticeTicks(); actPressed=state.action(); upPressed=state.up();
        confirmPressed=state.confirm(); choicePressed=state.choice(); menuPressed=state.menu(); hotbar=state.hotbar();
    }
    public void resetForMissingSnapshot() { active=false; speech=null; handBack=null; clearInput(); }
}
