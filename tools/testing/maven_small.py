"""Qualified low-heap test commands with conservative Maven admission.

This is an execution-budget policy, never a test selection or pass cache. The
explicit classes exercise bounded configuration/input, math, codec and headless
fixtures. Qualification and 1 GiB heap runs are recorded in the 2026-10-16
throughput investigation and 2026-10-24 lean-test qualification.
Unknown classes and custom invocation shapes keep normal admission. Adding a
class requires reviewing its allocations/fixtures and running it in the lean lane.
One-worker lean batches use 4 GiB/4-core admission; two-worker lean batches
use reviewed 5 GiB / 8-core shared admission for prepared category batches.
"""
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

from maven_resources import snapshot

MAX_SMALL_CLASSES = 50
QUALIFIED_CLASSES = frozenset({
    'com.openggf.TestInputBindingFactoryTempDirectory',
    'com.openggf.audio.runtime.TestAudioFrameClock',
    'com.openggf.audio.runtime.TestAudioRingBuffers',
    'com.openggf.audio.synth.TestFastFmWaveformAlignment',
    'com.openggf.configuration.CaptureConfigDefaultsTest',
    'com.openggf.configuration.FrameRateResolverTest',
    'com.openggf.configuration.TestAudioHardwareDefaults',
    'com.openggf.configuration.TestBundledConfigExamplePublication',
    'com.openggf.configuration.TestBundledConfigResource',
    'com.openggf.configuration.TestConfigCatalog',
    'com.openggf.configuration.TestConfigEnumValidation',
    'com.openggf.configuration.TestConfigFlattener',
    'com.openggf.configuration.TestConfigKeyChordResolution',
    'com.openggf.configuration.TestConfigKeyMeta',
    'com.openggf.configuration.TestConfigKeyNameResolution',
    'com.openggf.configuration.TestConfigMigrationService',
    'com.openggf.configuration.TestConfigServiceYamlRoundTrip',
    'com.openggf.configuration.TestConfigYamlWriter',
    'com.openggf.configuration.TestControllerInputConfig',
    'com.openggf.configuration.TestDisplayAspectResolution',
    'com.openggf.configuration.TestDisplaySettingsTrial',
    'com.openggf.configuration.TestDisplayWindowPolicy',
    'com.openggf.configuration.TestEngineSettingsDraft',
    'com.openggf.configuration.TestGlfwKeyNameResolver',
    'com.openggf.configuration.TestKeyChord',
    'com.openggf.configuration.TestLaunchToggleMigration',
    'com.openggf.configuration.TestLegacyConfigMigration',
    'com.openggf.configuration.TestLoadTimeSimulationConfiguration',
    'com.openggf.configuration.TestNativeConfigResolution',
    'com.openggf.configuration.TestPlaybackKeyDefaultsUnbound',
    'com.openggf.configuration.TestSettingTiming',
    'com.openggf.configuration.TestSonicConfigurationFileBootstrap',
    'com.openggf.configuration.TestSonicConfigurationSessionOverrides',
    'com.openggf.configuration.TestSparseUserConfig',
    'com.openggf.configuration.TestUnknownConfigPreservation',
    'com.openggf.configuration.TestWidescreenAspect',
    'com.openggf.configuration.TestYamlDependencyAvailable',
    'com.openggf.configuration.TraceVisibilityConfigDefaultsTest',
    'com.openggf.control.TestActionReducer',
    'com.openggf.control.TestButtonPrompts',
    'com.openggf.control.TestGamepadInputManager',
    'com.openggf.control.TestInputActionMasks',
    'com.openggf.control.TestInputHandler',
    'com.openggf.control.TestInputHandlerGamepadSuppression',
    'com.openggf.control.TestInputHandlerLogicalSnapshot',
    'com.openggf.control.TestInputHandlerMouse',
    'com.openggf.control.TestKeyboardInputMapper',
    'com.openggf.control.TestMenuInput',
    'com.openggf.control.TestMenuRepeat',
    'com.openggf.control.TestPlayerInputState',
    'com.openggf.control.TestRetainedGameplayInput',
    'com.openggf.control.TestScenePhysicalInput',
    'com.openggf.data.compression.TestKosinskiWindowBoundaries',
    'com.openggf.data.compression.TestResumableKosinskiDecoder',
    'com.openggf.game.TestGameRngGoldenSequence',
    'com.openggf.game.TestPhysicsProfile',
    'com.openggf.game.rewind.TestCompositeSnapshotOwnership',
    'com.openggf.game.rewind.TestRewindController',
    'com.openggf.game.rewind.TestRewindRegistry',
    'com.openggf.game.rewind.schema.TestRewindStateBuffer',
    'com.openggf.graphics.TestPatternAtlasLookup',
    'com.openggf.io.TestModAssetRoot',
    'com.openggf.io.TestModAssetRootConcurrentBudget',
    'com.openggf.level.TestLevelManagerRewindBoundary',
    'com.openggf.mods.code.TestGolfProtocol',
    'com.openggf.mods.code.TestModObjectKeyRegistry',
    'com.openggf.mods.state.TestModSaveSlot',
    'com.openggf.net.client.TestFrameAssembler',
    'com.openggf.net.ghost.TestGhostFrameCodec',
    'com.openggf.net.host.jdk.TestWebSocketFrames',
    'com.openggf.net.hub.TestGhostStreamValidator',
    'com.openggf.net.hub.TestRelevanceClassifier',
    'com.openggf.net.identity.TestDisplayNames',
    'com.openggf.net.identity.TestPlayerIdentity',
    'com.openggf.net.protocol.TestControlCodec',
    'com.openggf.net.protocol.TestControlCodecAdversarial',
    'com.openggf.net.protocol.TestControlCodecPolymorphism',
    'com.openggf.net.protocol.TestGhostPackets',
    'com.openggf.net.protocol.TestPhase3Protocol',
    'com.openggf.physics.TestFbzCalcRoomInFrontBackgroundCollision',
    'com.openggf.physics.TestObjectTerrainUtils',
    'com.openggf.physics.TestSwingMotion',
    'com.openggf.sprites.TestNativePositionOps',
    'com.openggf.sprites.playable.TestObjectControlState',
    'com.openggf.tests.TestCollisionLogic',
    'com.openggf.util.TestAnimationTimer',
    'com.openggf.util.TestDplcStaticFlattener',
    'com.openggf.util.TestLazyMappingHolder',
    'com.openggf.util.TestPatternDecompressor',
    'com.openggf.version.TestBuildIdentity',
})

# These additional tests were qualified only after normal-memory preparation.
# Full-project Java compilation does not fit the 1 GiB Maven heap reliably;
# do not extend the automatic focused lifecycle policy from batch evidence.
BATCH_ONLY_CLASSES = frozenset({
    'com.openggf.mods.code.TestEggmansSkyExample',
    'com.openggf.mods.code.TestFlappyTailsExample',
    'com.openggf.mods.code.TestGolfTransport',
    'com.openggf.mods.code.TestRobotnikTowerDefenseExample',
    'com.openggf.mods.code.TestSitarHeroOnlineMatch',
    'com.openggf.mods.code.TestSlayTheRobotnikExample',
    'com.openggf.mods.code.TestStarfallFrontierExample',
    'com.openggf.mods.code.TestStarpostValleyExample',
    'com.openggf.mods.code.TestThreeIslandsExample',
    'com.openggf.net.client.TestDirectRoomRegistration',
    'com.openggf.net.client.TestMasterClient',
    'com.openggf.net.client.TestPrivateRoomClient',
    'com.openggf.net.client.TestRaceClientLoopback',
    'com.openggf.net.host.jdk.TestJdkRaceHostLifecycle',
    'com.openggf.net.host.jdk.TestJdkRaceHostServer',
    'com.openggf.net.hub.TestPrivateRoomAdmission',
    'com.openggf.net.hub.TestRoomHostAdversarial',
    'com.openggf.net.hub.TestRoomInviteAuthority',
    'com.openggf.tests.TestModApiHookPolicy',
    'com.openggf.tools.modsdk.TestArtConverter',
    'com.openggf.tools.modsdk.TestBundledModPackager',
    'com.openggf.tools.modsdk.TestJarPackager',
    'com.openggf.tools.modsdk.TestModApiSdkPackager',
    'com.openggf.tools.modsdk.TestModJarValidator',
    'com.openggf.tools.modsdk.TestProjectScaffolder',
    'com.openggf.tools.modsdk.TestPurposeStarters',
})

_CLASS = r'[A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_][A-Za-z_0-9]*)*'
_SELECTOR = re.compile(_CLASS + r'(?:#[A-Za-z_][A-Za-z_0-9]*(?:\+[A-Za-z_][A-Za-z_0-9]*)*)?')
_SWITCHES = {'-B', '--batch-mode', '-q', '--quiet', '-e', '--errors', '-ntp',
             '--no-transfer-progress', '-o', '--offline', '-U', '--update-snapshots'}
_PROPERTIES = {'mse', 'sonic1.rom.path', 'sonic2.rom.path', 's3k.rom.path', 'kis2.rom.path',
               'openggf.headless.gl', 'openggf.surefire.reports', 'openggf.test.tmpdir'}


def _source_inventory(root):
    """Inventory source names once per classification, never cache across runs."""
    root = Path(root)
    from run_categories import test_source_roots
    pom = ET.parse(root / 'pom.xml').getroot()
    roots = test_source_roots(root, pom)
    source = (root / 'src/test/java').resolve()
    if source not in roots:
        raise ValueError('Qualified source root is not an ordinary test root')
    names = {}
    for directory in roots:
        for path in directory.rglob('*.java'):
            names.setdefault(path.stem, []).append(path)
    return source, roots, names


def _qualified_selectors(selectors, inventory, classes=None):
    classes = QUALIFIED_CLASSES if classes is None else classes
    if not 0 < len(selectors) <= MAX_SMALL_CLASSES:
        return False
    source, roots, names = inventory
    for selector in selectors:
        if not _SELECTOR.fullmatch(selector):
            return False
        name = selector.split('#', 1)[0]
        if '.' in name:
            paths = [directory.joinpath(*name.split('.')).with_suffix('.java') for directory in roots]
            paths = [path for path in paths if path.is_file()]
        else:
            paths = names.get(name, [])
        if len(paths) != 1:
            return False
        path = paths[0]
        if not path.is_relative_to(source):
            return False
        qualified = '.'.join(path.relative_to(source).with_suffix('').parts)
        if qualified not in classes or path.is_symlink():
            return False
        text = path.read_text()
        # Qualification cannot silently extend to an inherited fixture,
        # newly tagged expensive test or a moved/package-mismatched source.
        package = qualified.rsplit('.', 1)[0]
        if (not re.search(r'\bpackage\s+' + re.escape(package) + r'\s*;', text)
                or re.search(r'@(?:Tag|RequiresRom|RequiresGameModule)\b|\bextends\s+', text)):
            return False
    return True


def qualified_selectors(selectors, root, *, classes=None):
    """Resolve exact selectors in the caller's tree, rejecting ambiguous names."""
    if not 0 < len(selectors) <= MAX_SMALL_CLASSES:
        return False
    try:
        return _qualified_selectors(selectors, _source_inventory(root), classes)
    except (OSError, ValueError, ET.ParseError):
        return False


def _automatic_environment():
    if sys.platform != 'linux' or os.environ.get('OPENGGF_MAVEN_QUEUE', 'auto') != 'auto':
        return False
    resources = snapshot()
    return resources is not None and resources[1] >= 8


def qualified_candidates(candidates, root):
    """Classify a complete category selection with one fail-closed inventory.

    Membership only changes batch grouping, never selection. Re-read source on
    every classification; run_plan's tree fingerprint guards preparation/waits.
    The final batch still goes through batch_lean before acquiring admission.
    """
    from maven_queue import lean_command
    if not candidates or not _automatic_environment():
        return frozenset()
    try:
        # Apply the same environment/.mvn/build-override checks as automatic_lean.
        lean_command(['-Dmse=off', '-Dtest=qualification', 'test'], root=root)
        inventory = _source_inventory(root)
        return frozenset(candidate for candidate in candidates
                         if _qualified_selectors(
                             [candidate.removesuffix('.java').replace('/', '.')], inventory,
                             QUALIFIED_CLASSES | BATCH_ONLY_CLASSES))
    except (OSError, ValueError, ET.ParseError):
        return frozenset()


def automatic_lean(args, root, *, classes=None):
    """Return the bounded command/environment, or None without changing inputs."""
    from maven_queue import lean_command
    if [arg for arg in args if arg.startswith('-Dmse=')] != ['-Dmse=off']:
        return None
    if not _automatic_environment():
        return None
    selectors = []
    for arg in args:
        if arg.startswith('-Dtest='):
            selectors.append(arg.split('=', 1)[1])
        elif arg.startswith('-D'):
            key, separator, value = arg[2:].partition('=')
            if key not in _PROPERTIES or not separator:
                return None
            if key == 'openggf.headless.gl' and value != 'surfaceless-egl':
                return None
        elif arg not in _SWITCHES | {'clean', 'test'}:
            return None
    if len(selectors) != 1 or not qualified_selectors(selectors[0].split(','), root, classes=classes):
        return None
    try:
        return lean_command(args, root=root)
    except (OSError, ValueError):
        return None


def batch_backfill_eligible(candidates):
    # Batch-only qualification proves heap behavior after preparation. It does
    # not prove the short service time required for the one-job head exception.
    return bool(candidates) and len(candidates) <= MAX_SMALL_CLASSES and all(
        candidate.removesuffix('.java').replace('/', '.') in QUALIFIED_CLASSES
        for candidate in candidates)


def batch_lean(candidates, root, *, workers=1):
    """Qualify includes batches for 1 GiB heaps; two workers require normal admission."""
    if workers not in (1, 2):
        return None
    selectors = [candidate.removesuffix('.java').replace('/', '.') for candidate in candidates]
    lean = automatic_lean(['-Dmse=off', '-Dtest=' + ','.join(selectors), 'test'], root,
                          classes=QUALIFIED_CLASSES | BATCH_ONLY_CLASSES)
    if lean is None or workers == 1:
        return lean
    # The reviewed existing two-fork profile keeps Jupiter serial within each
    # JVM. The category runner gives this prepared shape 5 GiB / 8-core admission.
    command, environment = lean
    command = ['-Dsurefire.forkCount=2' if arg == '-Dsurefire.forkCount=1' else arg
               for arg in command]
    return [*command, '-Ptest-concurrent'], environment
