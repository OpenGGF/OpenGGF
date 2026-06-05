package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.fail;

class TestBuildToolingGuard {

    private static final List<String> TRACE_REPLAY_DIAGNOSTIC_EXCLUDES = List.of(
            "**/Debug*.java",
            "**/*Debug*.java",
            "**/*Probe.java",
            "**/*Probe*.java");

    @Test
    void surefireShouldPreloadMockitoAsJavaAgent() throws Exception {
        String file = "pom.xml";
        Document pom = parsePom(file);
        List<String> violations = new ArrayList<>();

        if (property(pom, "mockito.version") == null) {
            violations.add(file + " does not define a reusable Mockito version property");
        }
        String mockitoAgentArgLine = property(pom, "mockito.agent.argLine");
        if (mockitoAgentArgLine == null) {
            violations.add(file + " does not define a reusable Mockito javaagent property");
        }
        String mockitoAgentPath = property(pom, "mockito.agent.path");
        if (mockitoAgentPath == null) {
            violations.add(file + " does not define a reusable quoted Mockito agent path property");
        }
        String cdsArgLine = property(pom, "test.cds.argLine");
        if (cdsArgLine == null) {
            violations.add(file + " does not define a reusable test JVM CDS toggle property");
        }
        String surefireArgLine = property(pom, "surefire.argLine");
        if (surefireArgLine == null) {
            violations.add(file + " does not define a reusable Surefire argLine property");
        }
        if (mockitoAgentArgLine != null
                && !mockitoAgentArgLine.contains("-javaagent:")
                && !mockitoAgentArgLine.contains("@{mockito.agent.path}")) {
            violations.add(file + " does not preload mockito-core as a Surefire javaagent");
        }
        if (mockitoAgentPath != null && !mockitoAgentPath.contains("mockito-core-${mockito.version}.jar")) {
            violations.add(file + " does not resolve the Mockito javaagent from the reusable versioned jar path");
        }
        if (mockitoAgentArgLine != null && !mockitoAgentArgLine.contains("${mockito.agent.path}")) {
            violations.add(file + " does not route the Mockito javaagent through the shared mockito.agent.path property");
        }
        if (mockitoAgentPath != null && !mockitoAgentPath.contains("\"")) {
            violations.add(file + " does not quote or escape the Mockito javaagent path for Maven repositories with spaces");
        }
        if (cdsArgLine != null && !"-Xshare:off".equals(cdsArgLine)) {
            violations.add(file + " does not disable CDS for test JVMs after adding the Mockito agent");
        }
        if (surefireArgLine != null && !surefireArgLine.contains("${test.cds.argLine}")) {
            violations.add(file + " does not thread the CDS toggle through Surefire argLine");
        }
        if (surefireArgLine != null && !surefireArgLine.contains("${mockito.agent.argLine}")) {
            violations.add(file + " does not thread the Mockito agent property through Surefire argLine");
        }
        if (!surefirePluginUsesSharedArgLine(pom)) {
            violations.add(file + " does not wire the Surefire plugin to the shared surefire.argLine property");
        }

        if (!violations.isEmpty()) {
            fail("Surefire should preload Mockito cleanly without runtime self-attach or CDS bootstrap warnings:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void traceReplayProfileShouldExcludeDiagnosticTraceProbes() throws Exception {
        String file = "pom.xml";
        Document pom = parsePom(file);
        Element profile = profileById(pom, "trace-replay");
        List<String> violations = new ArrayList<>();

        if (profile == null) {
            violations.add(file + " does not define the trace-replay profile");
        } else {
            List<String> excludes = textValues(profile, "exclude");
            for (String diagnosticExclude : TRACE_REPLAY_DIAGNOSTIC_EXCLUDES) {
                if (!excludes.contains(diagnosticExclude)) {
                    violations.add(file + " trace-replay profile does not exclude " + diagnosticExclude);
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("trace-replay should not select diagnostic Debug*/Probe tests by default:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void traceReplayProfileShouldNotUseBroadTraceIncludeWithoutDiagnosticExcludes() throws Exception {
        String file = "pom.xml";
        Document pom = parsePom(file);
        Element profile = profileById(pom, "trace-replay");
        List<String> violations = new ArrayList<>();

        if (profile == null) {
            violations.add(file + " does not define the trace-replay profile");
        } else {
            List<String> includes = textValues(profile, "include");
            List<String> excludes = textValues(profile, "exclude");
            boolean hasBroadTraceInclude = includes.contains("**/tests/trace/**/*.java");
            boolean hasAllDiagnosticExcludes = excludes.containsAll(TRACE_REPLAY_DIAGNOSTIC_EXCLUDES);
            if (hasBroadTraceInclude && !hasAllDiagnosticExcludes) {
                violations.add(file + " trace-replay profile uses the broad trace include without diagnostic excludes");
            }
        }

        if (!violations.isEmpty()) {
            fail("trace-replay broad includes must be paired with diagnostic test excludes:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    private static Document parsePom(String file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(Files.newBufferedReader(Path.of(file))));
    }

    private static String property(Document pom, String name) {
        NodeList nodes = pom.getElementsByTagName(name);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static boolean surefirePluginUsesSharedArgLine(Document pom) {
        NodeList argLines = pom.getElementsByTagName("argLine");
        for (int i = 0; i < argLines.getLength(); i++) {
            if ("${surefire.argLine}".equals(argLines.item(i).getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private static Element profileById(Document pom, String id) {
        NodeList profiles = pom.getElementsByTagName("profile");
        for (int i = 0; i < profiles.getLength(); i++) {
            Element profile = (Element) profiles.item(i);
            if (id.equals(directChildText(profile, "id"))) {
                return profile;
            }
        }
        return null;
    }

    private static List<String> textValues(Element root, String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent().trim());
        }
        return values;
    }

    private static String directChildText(Element root, String tagName) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }
}
