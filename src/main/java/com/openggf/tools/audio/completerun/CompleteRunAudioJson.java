package com.openggf.tools.audio.completerun;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical JSON writer and exact token-stream reader for complete-run audio records. */
final class CompleteRunAudioJson {
    static final JsonFactory FACTORY = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final ObjectMapper WRITER = new ObjectMapper(FACTORY);

    private CompleteRunAudioJson() { }

    static String writeRecord(CompleteRunAudioTrace.Record record) throws IOException {
        StringWriter out = new StringWriter();
        try (JsonGenerator json = FACTORY.createGenerator(out)) {
            json.writeStartObject();
            json.writeStringField("type", type(record));
            json.writeFieldName("value");
            WRITER.writeValue(json, record);
            json.writeEndObject();
        }
        return out.toString();
    }

    static String writeMetadata(Metadata metadata) throws IOException {
        StringWriter out = new StringWriter();
        try (JsonGenerator json = FACTORY.createGenerator(out)) {
            WRITER.writeValue(json, metadata);
        }
        return out.toString();
    }

    static CompleteRunAudioTrace.Record readRecord(String line) {
        try (JsonParser p = FACTORY.createParser(line)) {
            start(p, "record"); field(p, "type"); String type = text(p, "record type"); field(p, "value");
            CompleteRunAudioTrace.Record record = switch (type) {
                case "baseline" -> baseline(p);
                case "frame" -> frame(p);
                case "lifecycle" -> lifecycle(p);
                case "terminal" -> terminal(p);
                default -> throw invalid("unknown record type: " + type);
            };
            end(p, "record"); eof(p, "record");
            return record;
        } catch (IOException failure) { throw invalid("invalid complete-run record JSON", failure); }
    }

    static Metadata readMetadata(JsonParser p) throws IOException {
        startCurrent(p, "metadata");
        field(p,"schema"); String schema=text(p,"metadata schema");
        field(p,"profileId"); String profile=text(p,"metadata profile ID");
        field(p,"fixture"); CompleteRunFixture fixture=fixture(p);
        field(p,"producerKind"); ProducerKind kind=enumValue(p,ProducerKind.class,"producer kind");
        field(p,"producerRuntimeIdentity"); ProducerRuntimeIdentity runtime=runtime(p);
        field(p,"observerProof"); ObserverProof proof=proof(p);
        field(p,"chunkPolicy"); ChunkPolicy policy=policy(p);
        field(p,"hardwareRoles"); List<HardwareRole> roles=enums(p,HardwareRole.class,"hardware roles");
        field(p,"stateInventory"); StateInventory inventory=inventory(p);
        end(p,"metadata"); return new Metadata(schema,profile,fixture,kind,runtime,proof,policy,roles,inventory);
    }

    private static Baseline baseline(JsonParser p) throws IOException { startCurrent(p,"baseline"); field(p,"absoluteFrame"); int frame=intValue(p,"baseline frame"); field(p,"state"); NormalizedState state=state(p); end(p,"baseline"); return new Baseline(frame,state); }
    private static Frame frame(JsonParser p) throws IOException { startCurrent(p,"frame"); field(p,"absoluteFrame"); int absolute=intValue(p,"frame"); field(p,"segment"); String segment=nullableText(p,"frame segment"); field(p,"lag"); boolean lag=booleanValue(p,"frame lag"); field(p,"requests"); List<Request> requests=requests(p); field(p,"services"); List<DriverService> services=services(p); end(p,"frame"); return new Frame(absolute,segment,lag,requests,services); }
    private static Lifecycle lifecycle(JsonParser p) throws IOException { startCurrent(p,"lifecycle"); field(p,"ordinal"); long ordinal=longValue(p,"lifecycle ordinal"); field(p,"absoluteFrame"); int frame=intValue(p,"lifecycle frame"); field(p,"kind"); String kind=text(p,"lifecycle kind"); field(p,"details"); Map<String,Object> details=objectValues(p,"lifecycle details"); end(p,"lifecycle"); return new Lifecycle(ordinal,frame,kind,details); }
    private static Terminal terminal(JsonParser p) throws IOException { startCurrent(p,"terminal"); field(p,"exclusiveEnd"); int end=intValue(p,"terminal end"); field(p,"frameCount"); long frames=longValue(p,"terminal frames"); field(p,"requestCount"); long requests=longValue(p,"terminal requests"); field(p,"serviceCount"); long services=longValue(p,"terminal services"); field(p,"decisionCount"); long decisions=longValue(p,"terminal decisions"); field(p,"ymCount"); long ym=longValue(p,"terminal YM"); field(p,"psgCount"); long psg=longValue(p,"terminal PSG"); field(p,"lifecycleCount"); long lifecycle=longValue(p,"terminal lifecycle"); field(p,"rootDigest"); String digest=text(p,"terminal root digest"); end(p,"terminal"); return new Terminal(end,frames,requests,services,decisions,ym,psg,lifecycle,digest); }

    private static List<Request> requests(JsonParser p) throws IOException { array(p,"requests"); List<Request> result=new ArrayList<>(); while(p.nextToken()!=JsonToken.END_ARRAY) result.add(request(p)); return List.copyOf(result); }
    private static Request request(JsonParser p) throws IOException { startCurrent(p,"request"); field(p,"ordinal"); long ordinal=longValue(p,"request ordinal"); field(p,"ownerClass"); OwnerClass owner=enumValue(p,OwnerClass.class,"request owner"); field(p,"contentKey"); String key=text(p,"request key"); field(p,"nativeId"); int nativeId=intValue(p,"request ID"); field(p,"queueSource"); String source=text(p,"request source"); field(p,"queueSlot"); Integer slot=nullableInt(p,"request slot"); end(p,"request"); return new Request(ordinal,owner,key,nativeId,source,slot); }
    private static List<DriverService> services(JsonParser p) throws IOException { array(p,"services"); List<DriverService> result=new ArrayList<>(); while(p.nextToken()!=JsonToken.END_ARRAY) result.add(service(p)); return List.copyOf(result); }
    private static DriverService service(JsonParser p) throws IOException { startCurrent(p,"service"); field(p,"ordinal"); long ordinal=longValue(p,"service ordinal"); field(p,"kind"); String kind=text(p,"service kind"); field(p,"decisions"); List<Decision> decisions=decisions(p); field(p,"state"); NormalizedState state=state(p); field(p,"chipEvents"); List<ChipEvent> chips=chips(p); end(p,"service"); return new DriverService(ordinal,kind,decisions,state,chips); }
    private static List<Decision> decisions(JsonParser p) throws IOException { array(p,"decisions"); List<Decision> result=new ArrayList<>(); while(p.nextToken()!=JsonToken.END_ARRAY) result.add(decision(p)); return List.copyOf(result); }
    private static Decision decision(JsonParser p) throws IOException { startCurrent(p,"decision"); field(p,"requestOrdinal"); long ordinal=longValue(p,"decision request"); field(p,"resolvedNativeId"); int id=intValue(p,"decision ID"); field(p,"resolvedContentKey"); String key=text(p,"decision key"); field(p,"accepted"); boolean accepted=booleanValue(p,"decision accepted"); field(p,"reason"); String reason=text(p,"decision reason"); field(p,"priorityBefore"); Integer before=nullableInt(p,"priority before"); field(p,"priorityAfter"); Integer after=nullableInt(p,"priority after"); field(p,"requestedRoles"); List<HardwareRole> roles=enums(p,HardwareRole.class,"requested roles"); field(p,"roleDecisions"); List<RoleDecision> roleDecisions=roleDecisions(p); end(p,"decision"); return new Decision(ordinal,id,key,accepted,reason,before,after,roles,roleDecisions); }
    private static List<RoleDecision> roleDecisions(JsonParser p) throws IOException { array(p,"role decisions"); List<RoleDecision> result=new ArrayList<>(); while(p.nextToken()!=JsonToken.END_ARRAY) { startCurrent(p,"role decision"); field(p,"role"); HardwareRole role=enumValue(p,HardwareRole.class,"role decision role"); field(p,"displacedOwner"); OwnerRef displaced=owner(p); field(p,"finalOwner"); OwnerRef finalOwner=owner(p); end(p,"role decision"); result.add(new RoleDecision(role,displaced,finalOwner)); } return List.copyOf(result); }
    private static OwnerRef owner(JsonParser p) throws IOException { startCurrent(p,"owner"); field(p,"ownerClass"); OwnerClass owner=enumValue(p,OwnerClass.class,"owner class"); field(p,"contentKey"); String key=text(p,"owner key"); field(p,"nativeId"); int id=intValue(p,"owner ID"); field(p,"requestOrdinal"); long ordinal=longValue(p,"owner ordinal"); end(p,"owner"); return new OwnerRef(owner,key,id,ordinal); }
    private static NormalizedState state(JsonParser p) throws IOException { startCurrent(p,"state"); field(p,"fields"); List<StateField> fields=stateFields(p); field(p,"roles"); List<RoleState> roles=roles(p); end(p,"state"); return new NormalizedState(fields,roles); }
    private static List<StateField> stateFields(JsonParser p) throws IOException { array(p,"state fields"); List<StateField> result=new ArrayList<>(); while(p.nextToken()!=JsonToken.END_ARRAY) { startCurrent(p,"state field"); field(p,"name"); String name=text(p,"state name"); field(p,"value"); Object value=value(p); end(p,"state field"); result.add(new StateField(name,value)); } return List.copyOf(result); }
    private static List<RoleState> roles(JsonParser p) throws IOException { array(p,"roles"); List<RoleState> result=new ArrayList<>(); while(p.nextToken()!=JsonToken.END_ARRAY) { startCurrent(p,"role"); field(p,"role"); HardwareRole role=enumValue(p,HardwareRole.class,"role"); field(p,"active"); boolean active=booleanValue(p,"role active"); field(p,"fields"); List<StateField> fields=stateFields(p); end(p,"role"); result.add(new RoleState(role,active,fields)); } return List.copyOf(result); }
    private static List<ChipEvent> chips(JsonParser p) throws IOException { array(p,"chip events"); List<ChipEvent> result=new ArrayList<>(); while(p.nextToken()!=JsonToken.END_ARRAY) { startCurrent(p,"chip event"); field(p,"ordinal"); long ordinal=longValue(p,"chip ordinal"); if(p.nextToken()!=JsonToken.FIELD_NAME)throw invalid("chip event type field"); if("port".equals(p.currentName())) { p.nextToken(); int port=intValue(p,"YM port"); field(p,"register"); int register=intValue(p,"YM register"); field(p,"value"); int value=intValue(p,"YM value"); end(p,"YM event"); result.add(new YmWrite(ordinal,port,register,value)); } else if("value".equals(p.currentName())) { p.nextToken(); int value=intValue(p,"PSG value"); end(p,"PSG event"); result.add(new PsgWrite(ordinal,value)); } else throw invalid("unknown chip event type"); } return List.copyOf(result); }

    private static CompleteRunFixture fixture(JsonParser p) throws IOException { startCurrent(p,"fixture"); field(p,"romSha1"); String sha1=text(p,"ROM SHA1"); field(p,"romCrc32"); String crc=text(p,"ROM CRC"); field(p,"bk2Sha256"); String bk2=text(p,"BK2 hash"); field(p,"bk2RowCount"); long rows=longValue(p,"BK2 rows"); field(p,"runManifestSha256"); String manifest=text(p,"manifest hash"); field(p,"segments"); List<ManifestSegment> segments=segments(p); field(p,"firstFrame"); int first=intValue(p,"first frame"); field(p,"exclusiveEnd"); int end=intValue(p,"end frame"); end(p,"fixture"); return new CompleteRunFixture(sha1,crc,bk2,rows,manifest,segments,first,end); }
    private static List<ManifestSegment> segments(JsonParser p) throws IOException { array(p,"segments"); List<ManifestSegment> result=new ArrayList<>(); while(p.nextToken()!=JsonToken.END_ARRAY) { startCurrent(p,"segment"); field(p,"id");String id=text(p,"segment ID");field(p,"firstFrame");int first=intValue(p,"segment first");field(p,"exclusiveEnd");int end=intValue(p,"segment end");end(p,"segment");result.add(new ManifestSegment(id,first,end)); } return List.copyOf(result); }
    private static ProducerRuntimeIdentity runtime(JsonParser p) throws IOException { startCurrent(p,"runtime"); field(p,"producerName");String name=text(p,"runtime producer");field(p,"producerVersion");String pv=text(p,"runtime producer version");field(p,"emulatorName");String en=text(p,"runtime emulator");field(p,"emulatorVersion");String ev=text(p,"runtime emulator version");field(p,"coreName");String cn=text(p,"runtime core");field(p,"coreVersion");String cv=text(p,"runtime core version");field(p,"artifactSha256"); Map<RuntimeArtifact,String> hashes=runtimeHashes(p);end(p,"runtime");return new ProducerRuntimeIdentity(name,pv,en,ev,cn,cv,hashes); }
    private static Map<RuntimeArtifact,String> runtimeHashes(JsonParser p) throws IOException { startCurrent(p,"runtime hashes"); Map<RuntimeArtifact,String> result=new EnumMap<>(RuntimeArtifact.class); while(p.nextToken()!=JsonToken.END_OBJECT){ if(p.currentToken()!=JsonToken.FIELD_NAME)throw invalid("runtime hash field"); RuntimeArtifact artifact;try{artifact=RuntimeArtifact.valueOf(p.currentName());}catch(IllegalArgumentException failure){throw invalid("unknown runtime artifact",failure);} p.nextToken();result.put(artifact,text(p,"runtime hash")); }return Map.copyOf(result); }
    private static ObserverProof proof(JsonParser p) throws IOException { startCurrent(p,"observer proof");field(p,"observerProfile");String profile=text(p,"observer profile");field(p,"callbackSource");String source=text(p,"callback source");field(p,"callbacks");array(p,"callbacks");List<CallbackProof> callbacks=new ArrayList<>();while(p.nextToken()!=JsonToken.END_ARRAY){startCurrent(p,"callback");field(p,"callback");String name=text(p,"callback");field(p,"observations");long count=longValue(p,"callback count");end(p,"callback");callbacks.add(new CallbackProof(name,count));}end(p,"observer proof");return new ObserverProof(profile,source,callbacks); }
    private static ChunkPolicy policy(JsonParser p) throws IOException { startCurrent(p,"chunk policy");field(p,"frameRows");int rows=intValue(p,"chunk rows");field(p,"compression");String compression=text(p,"compression");field(p,"gzipTimestamp");int time=intValue(p,"gzip timestamp");end(p,"chunk policy");return new ChunkPolicy(rows,compression,time); }
    private static StateInventory inventory(JsonParser p) throws IOException { startCurrent(p,"inventory");field(p,"globalFields");List<String> global=texts(p,"global fields");field(p,"activeRoleFields");List<String> active=texts(p,"role fields");end(p,"inventory");return new StateInventory(global,active); }

    private static Object value(JsonParser p) throws IOException { return switch(p.currentToken()){case VALUE_STRING->p.getText();case VALUE_TRUE->true;case VALUE_FALSE->false;case VALUE_NUMBER_INT->integerOrLong(p.getLongValue());case START_ARRAY->{List<Object> list=new ArrayList<>();while(p.nextToken()!=JsonToken.END_ARRAY)list.add(value(p));yield List.copyOf(list);}case START_OBJECT->objectValuesCurrent(p);default->throw invalid("state value must be canonical JSON scalar/container");}; }
    private static Object integerOrLong(long value) { if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return Integer.valueOf((int) value); return Long.valueOf(value); }
    private static Map<String,Object> objectValues(JsonParser p,String label)throws IOException{startCurrent(p,label);return objectValuesBody(p,label);} private static Map<String,Object> objectValuesCurrent(JsonParser p)throws IOException{return objectValuesBody(p,"object");} private static Map<String,Object> objectValuesBody(JsonParser p,String label)throws IOException{Map<String,Object> map=new LinkedHashMap<>();while(p.nextToken()!=JsonToken.END_OBJECT){if(p.currentToken()!=JsonToken.FIELD_NAME||map.containsKey(p.currentName()))throw invalid(label+" key");String key=p.currentName();p.nextToken();map.put(key,value(p));}return Map.copyOf(map);}
    private static List<String> texts(JsonParser p,String label)throws IOException{array(p,label);List<String> list=new ArrayList<>();while(p.nextToken()!=JsonToken.END_ARRAY)list.add(text(p,label));return List.copyOf(list);} private static <E extends Enum<E>> List<E> enums(JsonParser p,Class<E> type,String label)throws IOException{array(p,label);List<E> list=new ArrayList<>();while(p.nextToken()!=JsonToken.END_ARRAY)list.add(enumValue(p,type,label));return List.copyOf(list);} private static <E extends Enum<E>> E enumValue(JsonParser p,Class<E> type,String label)throws IOException{try{return Enum.valueOf(type,text(p,label));}catch(IllegalArgumentException failure){throw invalid("unknown "+label,failure);}}
    private static String type(CompleteRunAudioTrace.Record record){if(record instanceof Baseline)return"baseline";if(record instanceof Frame)return"frame";if(record instanceof Lifecycle)return"lifecycle";if(record instanceof Terminal)return"terminal";throw invalid("unknown record class");}
    private static void start(JsonParser p,String label)throws IOException{if(p.nextToken()!=JsonToken.START_OBJECT)throw invalid(label+" must start object");} private static void startCurrent(JsonParser p,String label){if(p.currentToken()!=JsonToken.START_OBJECT)throw invalid(label+" must be object");} private static void field(JsonParser p,String expected)throws IOException{if(p.nextToken()!=JsonToken.FIELD_NAME||!expected.equals(p.currentName()))throw invalid("expected field: "+expected);if(p.nextToken()==null)throw invalid("missing field value: "+expected);} private static void end(JsonParser p,String label)throws IOException{if(p.nextToken()!=JsonToken.END_OBJECT)throw invalid(label+" contains unknown or missing fields");} private static void eof(JsonParser p,String label)throws IOException{if(p.nextToken()!=null)throw invalid("trailing JSON after "+label);} private static void array(JsonParser p,String label){if(p.currentToken()!=JsonToken.START_ARRAY)throw invalid(label+" must be array");}
    private static String text(JsonParser p,String label)throws IOException{if(p.currentToken()!=JsonToken.VALUE_STRING)throw invalid(label+" must be string");return p.getText();} private static String nullableText(JsonParser p,String label)throws IOException{if(p.currentToken()==JsonToken.VALUE_NULL)return null;return text(p,label);} private static boolean booleanValue(JsonParser p,String label)throws IOException{if(p.currentToken()!=JsonToken.VALUE_TRUE&&p.currentToken()!=JsonToken.VALUE_FALSE)throw invalid(label+" must be boolean");return p.getBooleanValue();} private static int intValue(JsonParser p,String label)throws IOException{if(p.currentToken()!=JsonToken.VALUE_NUMBER_INT||!p.canReadTypeId()&&(!p.hasCurrentToken()))throw invalid(label+" must be integer");try{return p.getIntValue();}catch(Exception failure){throw invalid(label+" out of int range",failure);}} private static long longValue(JsonParser p,String label)throws IOException{if(p.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw invalid(label+" must be integer");return p.getLongValue();} private static Integer nullableInt(JsonParser p,String label)throws IOException{return p.currentToken()==JsonToken.VALUE_NULL?null:intValue(p,label);} static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);} static IllegalArgumentException invalid(String message,Throwable cause){return new IllegalArgumentException(message,cause);}
}
