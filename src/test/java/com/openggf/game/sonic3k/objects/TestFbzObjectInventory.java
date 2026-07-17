package com.openggf.game.sonic3k.objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzObjectInventory {
    private static final Path ROOT = Path.of("docs/skdisasm/Levels/FBZ/Object Pos");

    @Test
    void lockedOnPlacementFilesMatchTheFrozenInventory() throws IOException {
        assertAct("1.bin", 421, 420, act1());
        assertAct("2.bin", 441, 440, act2());
    }

    @Test
    void everyVisualCheckpointHasExactlyOneCompleteDeterministicRecipe() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(
                Path.of("docs/s3k-zones/fbz-visual-checkpoints.json").toFile());
        Set<String> checkpointIds = new LinkedHashSet<>();
        manifest.path("checkpoints").forEach(checkpoint -> checkpointIds.add(checkpoint.path("id").asText()));

        Set<String> recipeIds = new LinkedHashSet<>();
        manifest.path("setup_recipes").fieldNames().forEachRemaining(recipeIds::add);
        assertEquals(checkpointIds, recipeIds, "checkpoint IDs and recipe keys must match exactly");

        for (String id : checkpointIds) {
            JsonNode recipe = manifest.path("setup_recipes").path(id);
            for (String field : List.of("initial_level", "act", "centre", "camera", "state",
                    "timers", "phase", "capture", "source")) {
                assertFalse(recipe.path(field).isMissingNode(), id + " missing " + field);
                assertFalse(recipe.path(field).isNull(), id + " has null " + field);
            }
            assertTrue(recipe.has("input") || recipe.has("approaches"),
                    id + " must pin input or forward/reverse approaches");
            assertTrue(recipe.path("capture").path("frame_count").isIntegralNumber(),
                    id + " must pin a capture frame count");
        }
    }

    private static void assertAct(String file, int rawRecords, int runtimeSpawns,
                                  Map<Integer, Map<Integer, Integer>> expected) throws IOException {
        byte[] bytes = Files.readAllBytes(ROOT.resolve(file));
        assertEquals(rawRecords * 6, bytes.length, "raw six-byte record count");
        assertEquals(List.of(0xFF, 0xFF, 0, 0, 0, 0),
                java.util.stream.IntStream.range(bytes.length - 6, bytes.length)
                        .map(index -> bytes[index] & 0xFF).boxed().toList(),
                "full six-byte terminator");

        List<ObjectSpawn> spawns = CommonPlacementParser.parseObjectRecords(new RomByteReader(bytes), 0);
        assertEquals(runtimeSpawns, spawns.size(), "runtime spawn count");
        assertEquals(expected, count(spawns), "ID/subtype placement matrix");
    }

    static List<ObjectSpawn> load(String file) throws IOException {
        return CommonPlacementParser.parseObjectRecords(
                new RomByteReader(Files.readAllBytes(ROOT.resolve(file))), 0);
    }

    private static Map<Integer, Map<Integer, Integer>> count(List<ObjectSpawn> spawns) {
        Map<Integer, Map<Integer, Integer>> result = new LinkedHashMap<>();
        for (ObjectSpawn spawn : spawns) {
            result.computeIfAbsent(spawn.objectId(), ignored -> new LinkedHashMap<>())
                    .merge(spawn.subtype(), 1, Integer::sum);
        }
        return result;
    }

    private static Map<Integer, Map<Integer, Integer>> matrix(String specification) {
        Map<Integer, Map<Integer, Integer>> result = new LinkedHashMap<>();
        for (String row : specification.split(";")) {
            String[] sides = row.split(":");
            int id = Integer.parseInt(sides[0], 16);
            Map<Integer, Integer> subtypes = new LinkedHashMap<>();
            for (String entry : sides[1].split(",")) {
                String[] pair = entry.split("=");
                subtypes.put(Integer.parseInt(pair[0], 16), Integer.parseInt(pair[1]));
            }
            result.put(id, subtypes);
        }
        return result;
    }

    private static Map<Integer, Map<Integer, Integer>> act1() {
        return matrix("01:01=3,03=3,05=1,06=2,08=2;02:02=1,09=8,0D=3,11=7,12=1,15=10,91=1;07:00=2,02=9,10=3,20=1;08:00=9,03=2,10=2,20=3,40=5;0F:00=1;26:04=2;28:11=5,21=3,22=8,31=4;2F:28=2,29=2,2A=1;33:20=2,21=1,22=1;34:01=1,02=1,03=1,04=1,05=1;6A:71=1;6B:11=2,13=1,41=10,F1=3;6F:10=6;70:00=2,01=5,02=2;71:00=2,10=6,20=3,30=2,38=2,41=1,45=1,46=1,49=1,4F=1;72:0F=1,14=1,1B=1,83=1,84=1,88=2,C3=1,C7=1,C8=1;73:00=8,01=2,80=21,81=21;74:0F=7;75:00=1,01=1,02=1,03=1,04=1,05=1,06=1,07=1;76:00=8,01=16,02=8;77:00=3,0C=3;78:00=6;79:79=1,99=1,B9=2,D9=2,F9=1;7A:11=1,12=1,20=1,50=1;7B:0C=2,0E=2,14=2,1A=2;7C:00=11;7D:28=1;7E:00=1,02=1,14=8;7F:02=5,72=3,F2=2;80:05=1,06=1;85:01=1,02=1;A8:08=1,20=9;A9:00=3,02=6,04=4;AA:00=1;CF:00=1,01=3,02=2;D0:00=5;E0:10=1,20=1;E1:00=32;E4:00=1,02=1,03=3,40=1,80=2");
    }

    private static Map<Integer, Map<Integer, Integer>> act2() {
        return matrix("00:00=1;01:01=1,03=2,05=1,06=2,08=1;02:01=1,09=2,0A=1,0E=3,11=1,16=2,21=1,61=1,91=3;07:00=1,02=3,04=1,10=1;08:00=8,10=3,30=1,40=77;0F:00=2;26:04=4,80=1;28:17=1,41=1,61=4;2A:10=2;2F:28=3,29=2,2C=4;33:20=1,21=1,22=1,23=1,24=1,25=1,26=1,27=1,28=1,29=1,2A=1,2B=1,2C=1,2D=1,2F=2;34:01=1,02=1,03=1,04=1,05=1,06=1;3D:04=1;6A:71=2;6B:13=1,41=11,51=4,61=6;6F:18=2,98=3,A4=1,A6=1;71:00=1,4F=1;72:05=1,12=1,16=1,1B=2,83=2,C3=3;73:00=6,80=28,81=28;74:0E=4,0F=9;78:00=5;79:89=1,A9=1,C9=1,E9=1;7A:0A=1,10=1,11=1,12=1,14=1,16=1,18=1,19=1,1A=1,1B=1,1F=2,2D=1,43=1,4C=1,55=2,57=1;7E:00=3,14=4;85:03=1,04=1;8A:00=2,04=9;A8:20=12,30=2;A9:00=5,02=14,04=7;AB:00=1;CE:00=1;CF:02=1;E1:00=28;E2:0F=1,1E=1,24=2,25=1,32=1,37=3,3B=1,4B=2;E3:00=6,02=1;E4:00=1,02=3,80=8;E5:2C=2;FF:00=4,80=2");
    }
}
