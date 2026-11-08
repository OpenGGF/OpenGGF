package com.openggf.game.sonic2.competition.view;

import com.openggf.game.sonic2.objects.Sonic2GameOverCardObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectManager;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Purpose: comparison-only Obj39 state and actual display-tail admission qualification.
 * Inputs: capture_execution_observations.lua state.csv(objects) and sibling events.csv(card).
 * Origin: lane F's EHZ TIME OVER display frontier7589, 2026-11-08. Never feeds gameplay.
 */
final class Sonic2CompetitionCardReference {
    record State(int slot, int id, int routine, int frame, int x, int y, int mappings,
                 int art, int flags, int priority, int parent, int timer, boolean displayed) { }
    static Map<Integer,List<State>> read(Path path) throws Exception {
        var tailCalls = new HashSet<String>();
        var events = Files.readAllLines(path.resolveSibling("events.csv"));
        var header = Arrays.asList(events.getFirst().split(","));
        for (String line : events.subList(1,events.size())) {
            var row = line.split(",");
            byte[] bytes = HexFormat.of().parseHex(row[header.indexOf("card")]);
            if ((bytes[0]&255) != 0x39) continue;
            int frame = Integer.parseInt(row[header.indexOf("inflight_frame")]);
            int slot = ((int)(Long.parseLong(row[header.indexOf("a0")]) & 65535)-0xB000)/64;
            tailCalls.add(frame+":"+slot);
        }
        var rows = Files.readAllLines(path); header = Arrays.asList(rows.getFirst().split(","));
        var result = new TreeMap<Integer,List<State>>();
        for (String line : rows.subList(1,rows.size())) {
            var row = line.split(","); int frame = Integer.parseInt(row[header.indexOf("frame")]);
            byte[] ram = HexFormat.of().parseHex(row[header.indexOf("objects")]);
            var cards = new ArrayList<State>();
            for (int slot=0;slot<ram.length/64;slot++) {
                int at=slot*64; if ((ram[at]&255)!=0x39) continue;
                cards.add(new State(slot,0x39,ram[at+0x24]&255,ram[at+0x1A]&255,
                        word(ram,at+8),word(ram,at+10),(word(ram,at+4)<<16)|word(ram,at+6),
                        word(ram,at+2),ram[at+1]&255,ram[at+0x18]&255,word(ram,at+0x3E),
                        word(ram,at+0x1E),tailCalls.contains(frame+":"+slot)));
            }
            result.put(frame,List.copyOf(cards));
        }
        return result;
    }
    private static int word(byte[] bytes,int at) { return ((bytes[at]&255)<<8)|(bytes[at+1]&255); }
    static List<State> observe(ObjectManager manager) {
        var result=new ArrayList<State>();
        for(int slot=0;slot<128;slot++) {
            var owner=NativeSstSlotAccess.currentOwner(manager,slot);
            if(!(owner instanceof Sonic2GameOverCardObjectInstance card)||card.getLiveObjectId()!=0x39) continue;
            var display=card.nativeCardState();
            result.add(new State(slot,card.getLiveObjectId(),card.getRoutine(),card.getMappingFrame(),
                    display.x(),display.y(),display.mapping().address(),display.artTile(),display.renderFlags(),
                    card.getPriorityBucket(),card.getNativeParentWord(),card.getWaitTimer(),card.isDisplayedThisFrame()));
        }
        return List.copyOf(result);
    }
    static void requireEqual(List<State> expected,List<State> actual,int frame) {
        assertEquals(expected,actual,"native Obj39 state/display tails at physical"+frame);
    }
}
