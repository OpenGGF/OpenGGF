package com.openggf.game.soniccd;

import com.openggf.game.save.RuntimeSaveFields;
import com.openggf.game.soniccd.media.CdInitialSaveAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.*;

/** Strict game-owned semantic save value/codec, NOT live progress or storage completion.
 * Native InitSaveData copies676+28 bytes; the declared706-byte structure's final2 remain unknown.
 * Fixed packed time/name records and named reserved fields preserve native data without a RAM/bus API.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Backup RAM.inc:36-55; Main CPU Functions.asm:223-244; IPX.asm:1420-1475; Sub.asm:521-551.
 */
final class CdSaveProfile {
    private static final String FORMAT="soniccd-native-profile";
    private static final Map<String,Object> EXTENT=Map.of("timeAttackCopiedBytes",676,"mainCopiedBytes",28,"copiedBytes",704,"declaredBytes",706);
    private record Tables(List<Long> times,List<Long> initials,long defaultInitials,List<Integer> beforeSpecial,List<Integer> afterFields) {
        private Tables {times=words(times,84);initials=words(initials,84);unsigned(defaultInitials,0xFFFFFFFFL);beforeSpecial=bytes(beforeSpecial,3);afterFields=bytes(afterFields,18);}
    }
    private record Tail(List<OptionalInt> values) {
        private Tail {values=List.copyOf(values);if(values.size()!=2)throw bad("Exactly two independent main-tail knowledge values required");for(var v:values)octet(v);}
    }
    private final CdInitialSaveAssets assets;
    private final Tables tables;
    private final CdIpxProgress.MainSaveFields main;
    private final Tail tail;
    /** Saved semantic policy knowledge, never an automatically restored current Module policy. */
    private final OptionalInt policy;
    private CdSaveProfile(CdInitialSaveAssets assets,Tables tables,CdIpxProgress.MainSaveFields main,Tail tail,OptionalInt policy){
        this.assets=Objects.requireNonNull(assets);this.tables=Objects.requireNonNull(tables);this.main=Objects.requireNonNull(main);this.tail=Objects.requireNonNull(tail);octet(policy);this.policy=policy;
    }
    /** Pure template projection only. The bootstrap calls this ONLY after its genuine MISSING read. */
    static CdSaveProfile fromTemplate(CdInitialSaveAssets assets){
        Objects.requireNonNull(assets);requireTemplate(assets);if(CdInitialSaveAssets.Transfer.INITIAL_TIME_ATTACK.copiedBytes()!=676||CdInitialSaveAssets.Transfer.INITIAL_MAIN.copiedBytes()!=28)throw bad("Original init copy extents differ");
        var raw=assets.view(CdInitialSaveAssets.Table.MAIN);var before=new ArrayList<Integer>();var after=new ArrayList<Integer>();for(int i=5;i<8;i++)before.add(raw.unsignedByte(i));for(int i=10;i<28;i++)after.add(raw.unsignedByte(i));
        var tables=new Tables(records(assets.view(CdInitialSaveAssets.Table.TIME_ATTACK_TIMES)),records(assets.view(CdInitialSaveAssets.Table.TIME_ATTACK_INITIALS)),assets.view(CdInitialSaveAssets.Table.DEFAULT_INITIALS).unsignedLong(0),before,after);
        return new CdSaveProfile(assets,tables,new CdIpxProgress.MainSaveFields(raw.unsignedByte(0),raw.unsignedByte(1),raw.unsignedByte(2),raw.unsignedByte(3),raw.unsignedByte(4),raw.unsignedByte(8),raw.unsignedByte(9)),new Tail(List.of(OptionalInt.empty(),OptionalInt.empty())),OptionalInt.empty());
    }
    CdIpxProgress.MainSaveFields mainFields(){return main;}
    List<Long> timeAttackTimes(){return tables.times();}List<Long> timeAttackInitials(){return tables.initials();}
    long defaultInitials(){return tables.defaultInitials();}List<Integer> reservedBeforeSpecial(){return tables.beforeSpecial();}List<Integer> reservedAfterFields(){return tables.afterFields();}
    List<OptionalInt> tail(){return tail.values();}OptionalInt policy(){return policy;}
    CdSaveProfile withTail(List<OptionalInt> values){return new CdSaveProfile(assets,tables,main,new Tail(values),policy);}
    CdSaveProfile withPolicy(OptionalInt value){return new CdSaveProfile(assets,tables,main,tail,value);}
    /** Reuses existing native ordered-prefix arithmetic; this value transform is not a write receipt. */
    CdSaveProfile withPatch(CdIpxProgress.SavePatch patch){
        Objects.requireNonNull(patch);int pair=patch.zoneAndUnlockWord().orElse(main.levelPairWord());
        var next=new CdIpxProgress.MainSaveFields(pair>>>8,pair&255,patch.unknownBackupByte().orElse(main.unknownBackupByte()),patch.goodFuturesByte().orElse(main.goodFuturesByte()),patch.titleFlagsByte().orElse(main.titleFlagsByte()),patch.currentSpecialStageByte().orElse(main.currentSpecialStageByte()),patch.timeStonesByte().orElse(main.timeStonesByte()));
        return new CdSaveProfile(assets,tables,next,tail,policy);
    }
    /** Pure ReadSaveData projection; actual caller owns GetBuRAMData return and current save policy. */
    CdIpxProgress.State projectAfterRead(CdIpxProgress.State current){return CdIpxProgress.readSaveDataFieldsAfterRead(current,main);}
    /** Native temporary copies704 bytes. Destination's untouched2-byte tail and external policy survive. */
    CdSaveProfile temporaryCopyTo(CdSaveProfile destination){
        Objects.requireNonNull(destination);if(assets!=destination.assets)throw bad("Foreign source owner for temporary profile copy");
        return new CdSaveProfile(assets,tables,main,destination.tail,destination.policy);
    }
    Map<String,Object> encode(){
        var m=new LinkedHashMap<String,Object>();m.put("format",FORMAT);m.put("schema",1);m.put("source",source(assets));m.put("extent",EXTENT);
        m.put("timeAttackTimes",tables.times().stream().map(CdSaveProfile::jsonInteger).toList());m.put("timeAttackInitials",tables.initials().stream().map(CdSaveProfile::jsonInteger).toList());m.put("defaultInitials",jsonInteger(tables.defaultInitials()));
        m.put("main",Map.of("zoneByte",main.zoneByte(),"timeAttackUnlockByte",main.timeAttackUnlockByte(),"unknownBackupByte",main.unknownBackupByte(),"goodFuturesByte",main.goodFuturesByte(),"titleFlagsByte",main.titleFlagsByte(),"currentSpecialStageByte",main.currentSpecialStageByte(),"timeStonesByte",main.timeStonesByte()));
        m.put("reservedBeforeSpecial",tables.beforeSpecial());m.put("reservedAfterFields",tables.afterFields());m.put("mainTail",tail.values().stream().map(CdSaveProfile::knowledge).toList());m.put("savedPolicyKnowledge",knowledge(policy));
        return RuntimeSaveFields.freeze(m);
    }
    static CdSaveProfile decode(CdInitialSaveAssets assets,Map<String,Object> raw){
        Objects.requireNonNull(assets);var m=object(raw,Set.of("format","schema","source","extent","timeAttackTimes","timeAttackInitials","defaultInitials","main","reservedBeforeSpecial","reservedAfterFields","mainTail","savedPolicyKnowledge"));
        if(!FORMAT.equals(m.get("format"))||integer(m.get("schema"),1)!=1||!source(assets).equals(m.get("source"))||!EXTENT.equals(m.get("extent")))throw bad("CD schema/source/native copy extents differ");
        var main=object(m.get("main"),Set.of("zoneByte","timeAttackUnlockByte","unknownBackupByte","goodFuturesByte","titleFlagsByte","currentSpecialStageByte","timeStonesByte"));
        var fields=new CdIpxProgress.MainSaveFields(o(main,"zoneByte"),o(main,"timeAttackUnlockByte"),o(main,"unknownBackupByte"),o(main,"goodFuturesByte"),o(main,"titleFlagsByte"),o(main,"currentSpecialStageByte"),o(main,"timeStonesByte"));
        var tail=sequence(m.get("mainTail"),2);var tables=new Tables(longList(m.get("timeAttackTimes"),84),longList(m.get("timeAttackInitials"),84),integer(m.get("defaultInitials"),0xFFFFFFFFL),byteList(m.get("reservedBeforeSpecial"),3),byteList(m.get("reservedAfterFields"),18));
        return new CdSaveProfile(assets,tables,fields,new Tail(List.of(readKnowledge(tail.get(0)),readKnowledge(tail.get(1)))),readKnowledge(m.get("savedPolicyKnowledge")));
    }
    private static void requireTemplate(CdInitialSaveAssets assets){
        var all=assets.initialData();
        try {
            if(all.length()!=706||!"c8e9bcc03252fd3cd3df44cbc2810c6bcc9ac1b1d155f2039b19ff76222abee1".equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(all.copy()))))throw bad("Original initial-save template differs");
        } catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        int cursor=0;for(var table:CdInitialSaveAssets.Table.values()){
            var view=assets.view(table);if(!Arrays.equals(view.copy(),all.slice(cursor,view.length()).copy()))throw bad("Initial-save view differs from authenticated template");cursor+=view.length();
        }
        if(cursor!=706)throw bad("Complete initial-save source fields required");
    }
    private static Map<String,Object> source(CdInitialSaveAssets assets){
        var s=Objects.requireNonNull(assets.identity());return Map.of("release",s.variant().name(),"initializationFile",s.initializationFile().name(),"subCpuFile",s.subCpuFile().name(),"initializationSha256",s.initializationSha256(),"subCpuSha256",s.subCpuSha256(),"wholeDiscSha256",s.observedWholeDiscSha256());
    }
    private static List<Long> records(CdAssetBytes bytes){if(bytes.length()!=336)throw bad("Complete84 native packed records required");var r=new ArrayList<Long>();for(int i=0;i<84;i++)r.add(bytes.unsignedLong(i*4));return List.copyOf(r);}
    private static List<Long> words(List<Long> values,int count){values=List.copyOf(values);if(values.size()!=count)throw bad("Native record count differs");for(long x:values)unsigned(x,0xFFFFFFFFL);return values;}
    private static List<Integer> bytes(List<Integer> values,int count){values=List.copyOf(values);if(values.size()!=count)throw bad("Native reserved-field count differs");for(int x:values)unsigned(x,255);return values;}
    private static List<Integer> knowledge(OptionalInt value){octet(value);return value.isPresent()?List.of(value.getAsInt()):List.of();}
    private static OptionalInt readKnowledge(Object raw){if(!(raw instanceof List<?> list)||list.size()>1)throw bad("Knowledge must be [] or [rawByte]");return list.isEmpty()?OptionalInt.empty():OptionalInt.of((int)integer(list.getFirst(),255));}
    private static void octet(OptionalInt value){Objects.requireNonNull(value);if(value.isPresent())unsigned(value.getAsInt(),255);}
    private static int o(Map<String,Object> m,String key){return (int)integer(m.get(key),255);}
    private static long integer(Object raw,long max){if(!(raw instanceof Byte||raw instanceof Short||raw instanceof Integer||raw instanceof Long))throw bad("Canonical JSON integer required");long v=((Number)raw).longValue();unsigned(v,max);return v;}
    private static Object jsonInteger(long value){return value<=Integer.MAX_VALUE?(Object)Integer.valueOf((int)value):Long.valueOf(value);}
    private static void unsigned(long value,long max){if(value<0||value>max)throw bad("Native unsigned width exceeded");}
    private static List<?> sequence(Object raw,int count){if(!(raw instanceof List<?> list)||list.size()!=count)throw bad("Complete native sequence required");return list;}
    private static List<Long> longList(Object raw,int count){var out=new ArrayList<Long>();for(var x:sequence(raw,count))out.add(integer(x,0xFFFFFFFFL));return out;}
    private static List<Integer> byteList(Object raw,int count){var out=new ArrayList<Integer>();for(var x:sequence(raw,count))out.add((int)integer(x,255));return out;}
    private static Map<String,Object> object(Object raw,Set<String> keys){if(!(raw instanceof Map<?,?> map)||!map.keySet().equals(keys))throw bad("Exact CD field schema required");var result=new LinkedHashMap<String,Object>();for(var e:map.entrySet()){if(!(e.getKey() instanceof String s))throw bad("String CD keys required");result.put(s,e.getValue());}return result;}
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
