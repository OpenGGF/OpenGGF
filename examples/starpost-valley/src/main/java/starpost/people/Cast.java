package starpost.people;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import starpost.people.cast.Barnaby;
import starpost.people.cast.Clementine;
import starpost.people.cast.Dandel;
import starpost.people.cast.ElderTotem;
import starpost.people.cast.Frost;
import starpost.people.cast.Hazel;
import starpost.people.cast.Knuckles;
import starpost.people.cast.Mail;
import starpost.people.cast.Moto;
import starpost.people.cast.Pip;
import starpost.people.cast.Pud;
import starpost.people.cast.Robotnik;
import starpost.people.cast.Rusty;
import starpost.people.cast.Sonic;
import starpost.people.cast.Tails;

/**
 * Everyone in the valley, their heart events and the post. Built per game from the cast classes
 * (instance-owned: the mod validator forbids static tables). Add a villager by writing a class
 * like {@code cast/Dandel.java} and calling it here.
 */
public final class Cast {
    private final Map<String, VillagerDef> villagers = new LinkedHashMap<>();
    private final Map<String, HeartEvent> events = new LinkedHashMap<>();
    private final Map<String, Letter> letters = new LinkedHashMap<>();

    public Cast() {
        Sonic.define(this);
        Tails.define(this);
        Knuckles.define(this);
        Robotnik.define(this);
        Rusty.define(this);
        Pip.define(this);
        Dandel.define(this);
        Clementine.define(this);
        Pud.define(this);
        Barnaby.define(this);
        Frost.define(this);
        Hazel.define(this);
        ElderTotem.define(this);
        Moto.define(this);
        Mail.define(this);
    }

    public VillagerDef villager(String id, String name) {
        VillagerDef def = new VillagerDef(id, name);
        if (villagers.putIfAbsent(id, def) != null) {
            throw new IllegalStateException("Duplicate villager " + id);
        }
        return def;
    }

    public HeartEvent event(String id, String villager, int hearts) {
        HeartEvent event = new HeartEvent(id, villager, hearts);
        if (events.putIfAbsent(id, event) != null) {
            throw new IllegalStateException("Duplicate event " + id);
        }
        return event;
    }

    public Letter letter(String id, String from) {
        Letter letter = new Letter(id, from);
        if (letters.putIfAbsent(id, letter) != null) {
            throw new IllegalStateException("Duplicate letter " + id);
        }
        return letter;
    }

    public VillagerDef get(String id) {
        return villagers.get(id);
    }

    public List<VillagerDef> all() {
        return Collections.unmodifiableList(new ArrayList<>(villagers.values()));
    }

    public HeartEvent event(String id) {
        return events.get(id);
    }

    public List<HeartEvent> events() {
        return Collections.unmodifiableList(new ArrayList<>(events.values()));
    }

    public Letter letter(String id) {
        return letters.get(id);
    }

    public List<Letter> letters() {
        return Collections.unmodifiableList(new ArrayList<>(letters.values()));
    }
}
