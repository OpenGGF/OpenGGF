package com.openggf.route;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TestQualificationCoverage {
 @Test void namedNativeGoalsRoundTripWithoutClaimingUnlistedState() throws Exception {
  var route=RouteArtifact.of(SyntheticRouteWorlds.launch(),SyntheticRouteWorlds.right(4),new Provenance("test","","",List.of()));
  var checks=new ArrayList<>(List.of("slot-in-use-in-view","resident-mid-flip-in-view"));
  var stamp=QualificationStamp.passedChecks(ReplayScope.BIZHAWK,route.content(),"goals-only; accepted native lag boundary",4,checks);
  checks.clear();assertEquals(2,stamp.coverage().size());
  var annotated=new RouteArtifact(route.manifest().withQualification(List.of(stamp)),route.tape());
  assertEquals(annotated,RouteCodec.parseTransport(RouteCodec.formatTransport(annotated)));
  assertEquals(route.content(),annotated.content());
  assertFalse(stamp.coverage().contains("full-state-parity"));
  assertThrows(IllegalArgumentException.class,()->QualificationStamp.passedChecks(ReplayScope.BIZHAWK,route.content(),"x",4,List.of()));
  var json=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(RouteCodec.formatTransport(annotated));
  ((com.fasterxml.jackson.databind.node.ObjectNode)json.get("qualification").get(0)).putArray("coverage").add(123);
  assertThrows(java.io.IOException.class,()->RouteCodec.parseTransport(json.toString()));
 }
 @Test void legacyStampCoverageRemainsUnspecifiedAndReadable() throws Exception {
  var route=RouteArtifact.of(SyntheticRouteWorlds.launch(),SyntheticRouteWorlds.right(2),new Provenance("test","","",List.of()));
  var stamp=QualificationStamp.passed(ReplayScope.ENGINE,route.content(),"legacy",2);
  assertTrue(stamp.coverage().isEmpty());
  var tagged=new RouteArtifact(route.manifest().withQualification(List.of(stamp)),route.tape());
  assertFalse(RouteCodec.formatTransport(tagged).contains("coverage"));
  assertEquals(tagged,RouteCodec.parseTransport(RouteCodec.formatTransport(tagged)));
 }
}
