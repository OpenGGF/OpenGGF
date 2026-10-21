package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.save.SavePayloadReader;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class S3kSaveProgressions {

    private S3kSaveProgressions() {
    }

    static int progressCodeForPayload(Map<String, Object> payload) {
        Object raw = payload.get("progressCode");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        int zone = SavePayloadReader.readInt(payload, "zone", Sonic3kZoneIds.ZONE_AIZ);
        int act = SavePayloadReader.readInt(payload, "act", 0);
        boolean clear = Boolean.TRUE.equals(payload.get("clear"));
        List<Integer> superEmeralds = readEmeraldList(payload, "superEmeralds");
        SelectedTeam team = SavePayloadReader.readTeam(payload);
        return progressCodeForState(zone, act, team, clear, superEmeralds);
    }

    static int progressCodeForState(int zone, int act, SelectedTeam team, boolean clear, List<Integer> superEmeralds) {
        if (clear) {
            if (isKnuckles(team)) {
                return 12;
            }
            return hasAllSuperEmeralds(superEmeralds) ? 14 : 13;
        }
        return switch (zone) {
            case Sonic3kZoneIds.ZONE_AIZ -> 1;
            case Sonic3kZoneIds.ZONE_HCZ -> 2;
            case Sonic3kZoneIds.ZONE_MGZ -> 3;
            case Sonic3kZoneIds.ZONE_CNZ -> 4;
            case Sonic3kZoneIds.ZONE_ICZ -> 5;
            case Sonic3kZoneIds.ZONE_LBZ -> 6;
            case Sonic3kZoneIds.ZONE_MHZ -> 7;
            case Sonic3kZoneIds.ZONE_FBZ -> 8;
            case Sonic3kZoneIds.ZONE_SOZ -> 9;
            case Sonic3kZoneIds.ZONE_LRZ -> 10;
            case Sonic3kZoneIds.ZONE_HPZ -> 11;
            case Sonic3kZoneIds.ZONE_SSZ -> 12;
            case Sonic3kZoneIds.ZONE_DEZ -> 13;
            case Sonic3kZoneIds.ZONE_DDZ -> 14;
            default -> Math.max(0, act) + 1;
        };
    }

    static int clearStateForPayload(Map<String, Object> payload) {
        Object raw = payload.get("clearState");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (!Boolean.TRUE.equals(payload.get("clear"))) {
            return 0;
        }
        return hasAllSuperEmeralds(readEmeraldList(payload, "superEmeralds")) ? 2 : 1;
    }

    static List<Integer> chaosEmeraldsForPayload(Map<String, Object> payload) {
        return readEmeraldList(payload, "chaosEmeralds");
    }

    static List<Integer> superEmeraldsForPayload(Map<String, Object> payload) {
        return readEmeraldList(payload, "superEmeralds");
    }

    static boolean hasAllSuperEmeralds(List<Integer> superEmeralds) {
        return superEmeralds.size() >= 7
                && superEmeralds.containsAll(List.of(0, 1, 2, 3, 4, 5, 6));
    }

    static List<DataSelectDestination> clearRestartDestinations(Map<String, Object> payload) {
        if (!Boolean.TRUE.equals(payload.get("clear"))) {
            return List.of();
        }

        SelectedTeam team = SavePayloadReader.readTeam(payload);
        int terminalIndex = terminalClearMarkerIndex(payload);
        if (terminalIndex <= 0) {
            return List.of();
        }
        List<DataSelectDestination> destinations = new ArrayList<>(terminalIndex);
        for (int index = 0; index < terminalIndex; index++) {
            destinations.add(destinationForIndex(index, team));
        }
        return destinations;
    }

    static int terminalClearMarkerIndex(Map<String, Object> payload) {
        if (!Boolean.TRUE.equals(payload.get("clear"))) {
            return -1;
        }
        SelectedTeam team = SavePayloadReader.readTeam(payload);
        int clearState = Math.max(0, clearStateForPayload(payload));
        return Math.max(0, maxSelectableIndex(team, clearState));
    }

    static int clearRestartSelectionCount(Map<String, Object> payload) {
        int terminalIndex = terminalClearMarkerIndex(payload);
        return terminalIndex < 0 ? 0 : terminalIndex + 1;
    }

    private static int maxSelectableIndex(SelectedTeam team, int clearState) {
        if (isKnuckles(team)) {
            return 11;
        }
        return clearState >= 2 ? 13 : 12;
    }

    private static DataSelectDestination destinationForIndex(int index, SelectedTeam team) {
        if (isKnuckles(team) && index == 11) {
            return new DataSelectDestination(Sonic3kZoneIds.ZONE_SSZ, 1);
        }
        return switch (index) {
            case 0 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_AIZ, 0);
            case 1 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_HCZ, 0);
            case 2 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_MGZ, 0);
            case 3 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_CNZ, 0);
            case 4 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_ICZ, 0);
            case 5 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_LBZ, 0);
            case 6 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_MHZ, 0);
            case 7 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_FBZ, 0);
            case 8 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_SOZ, 0);
            case 9 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_LRZ, 0);
            case 10 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_HPZ, 1);
            case 11 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_SSZ, 0);
            case 12 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_DEZ, 0);
            case 13 -> new DataSelectDestination(Sonic3kZoneIds.ZONE_DDZ, 0);
            default -> new DataSelectDestination(Sonic3kZoneIds.ZONE_AIZ, 0);
        };
    }

    /**
     * {@code SaveGame_NextLevel} (sonic3k.asm:15860-15886), indexed by
     * {@code Current_zone_and_act} as {@code zone*2 + act} (sonic3k.asm:15896-15898).
     */
    private static final int[] SAVE_GAME_NEXT_LEVEL = {
            1, 1,       // $00 AIZ
            2, 2,       // $01 HCZ
            3, 3,       // $02 MGZ
            4, 4,       // $03 CNZ
            8, 8,       // $04 FBZ
            5, 5,       // $05 ICZ
            6, 6,       // $06 LBZ
            7, 7,       // $07 MHZ
            9, 9,       // $08 SOZ
            0xA, 0xA,   // $09 LRZ
            0xC, 0xC,   // $0A SSZ
            0xD, 0xD,   // $0B DEZ
            0xE, 0xE,   // $0C DDZ
            0, 0,       // $0D
            0, 0,       // $0E
            0, 0,       // $0F
            0, 0,       // $10
            0, 0,       // $11
            0, 0,       // $12
            0, 0,       // $13
            0, 0,       // $14
            0, 0,       // $15
            0xA, 0xB,   // $16 LRZ boss act, HPZ
            0xD, 0      // $17 DEZ boss arena, $1701
    };

    /** {@code LevelList_DA6E} (sonic3k.asm:17535-17549): level code to zone/act word. */
    private static final int[] LEVEL_LIST = {
            0x000, 0x100, 0x200, 0x300, 0x500, 0x600, 0x700, 0x400, 0x800, 0x900, 0x1601, 0xA00, 0xB00, 0xC00
    };

    /**
     * Load destination SaveGame stores at {@code Current_zone_and_act}: the code from
     * {@code SaveGame_NextLevel}, loaded through data select {@code sub_DA4E}
     * (sonic3k.asm:17516-17532), which turns code {@code $B} into {@code $A01} for
     * Player_mode 3 only. Empty when the table has no destination: code 0 belongs only to
     * rows no SaveGame caller occupies, and code {@code $E} (Doomsday) is always written as a
     * completed file, whose loads use the clear-restart list instead of this code.
     */
    static java.util.Optional<DataSelectDestination> saveGameNextLevel(int zone, int act, SelectedTeam team) {
        int index = zone * 2 + (act & 1);
        if (zone < 0 || index >= SAVE_GAME_NEXT_LEVEL.length) {
            return java.util.Optional.empty();
        }
        int code = SAVE_GAME_NEXT_LEVEL[index];
        if (code == 0 || code >= LEVEL_LIST.length) {
            return java.util.Optional.empty();
        }
        int word = isKnuckles(team) && code == 0xB ? 0xA01 : LEVEL_LIST[code];
        return java.util.Optional.of(new DataSelectDestination(word >>> 8, word & 0xFF));
    }

    private static boolean isKnuckles(SelectedTeam team) {
        return team != null && "knuckles".equalsIgnoreCase(team.mainCharacter());
    }

    private static List<Integer> readEmeraldList(Map<String, Object> payload, String key) {
        Object raw = payload.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object value : list) {
            if (value instanceof Number number) {
                int index = number.intValue();
                if (index >= 0 && index <= 6 && !result.contains(index)) {
                    result.add(index);
                }
            }
        }
        result.sort(Integer::compareTo);
        return List.copyOf(result);
    }
}
