"""Qualified small test commands for automatic 4 GiB Maven admission.

This is an execution-budget policy, never a test selection or pass cache. The
explicit classes exercise bounded configuration/input, math, codec and headless
fixtures. Qualification and 1 GiB heap runs are recorded in the 2026-10-16
throughput investigation and 2026-10-24 lean-test qualification.
Unknown classes and custom invocation shapes keep normal admission. Adding a
class requires reviewing its allocations/fixtures and running it in the lean lane.
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
    'com.openggf.game.TestPhysicsProfile',
    'com.openggf.graphics.TestPatternAtlasLookup',
    'com.openggf.level.TestLevelManagerRewindBoundary',
    'com.openggf.mods.code.TestGolfProtocol',
    'com.openggf.net.ghost.TestGhostFrameCodec',
    'com.openggf.net.identity.TestDisplayNames',
    'com.openggf.net.identity.TestPlayerIdentity',
    'com.openggf.net.protocol.TestControlCodec',
    'com.openggf.net.protocol.TestControlCodecAdversarial',
    'com.openggf.net.protocol.TestControlCodecPolymorphism',
    'com.openggf.physics.TestSwingMotion',
    'com.openggf.sprites.TestNativePositionOps',
    'com.openggf.tests.TestCollisionLogic',
    'com.openggf.util.TestAnimationTimer',
    'com.openggf.util.TestDplcStaticFlattener',
    'com.openggf.util.TestLazyMappingHolder',
    'com.openggf.util.TestPatternDecompressor',
    'com.openggf.version.TestBuildIdentity',
})

_CLASS = r'[A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_][A-Za-z_0-9]*)*'
_SELECTOR = re.compile(_CLASS + r'(?:#[A-Za-z_][A-Za-z_0-9]*(?:\+[A-Za-z_][A-Za-z_0-9]*)*)?')
_SWITCHES = {'-B', '--batch-mode', '-q', '--quiet', '-e', '--errors', '-ntp',
             '--no-transfer-progress', '-o', '--offline', '-U', '--update-snapshots'}
_PROPERTIES = {'mse', 'sonic1.rom.path', 'sonic2.rom.path', 's3k.rom.path', 'kis2.rom.path',
               'openggf.headless.gl', 'openggf.surefire.reports', 'openggf.test.tmpdir'}


def qualified_selectors(selectors, root):
    """Resolve exact selectors in the caller's tree, rejecting ambiguous names."""
    if not 0 < len(selectors) <= MAX_SMALL_CLASSES:
        return False
    root = Path(root)
    try:
        # Alternate source roots could give an unqualified selector extra matches.
        from run_categories import test_source_roots
        pom = ET.parse(root / 'pom.xml').getroot()
        roots = test_source_roots(root, pom)
        source = (root / 'src/test/java').resolve()
        if source not in roots:
            return False
        names = {}
        for directory in roots:
            for path in directory.rglob('*.java'):
                names.setdefault(path.stem, []).append(path)
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
            if qualified not in QUALIFIED_CLASSES or path.is_symlink():
                return False
            text = path.read_text()
            # A qualification does not silently extend to an inherited fixture,
            # newly tagged expensive test or a moved/package-mismatched source.
            package = qualified.rsplit('.', 1)[0]
            if (not re.search(r'\bpackage\s+' + re.escape(package) + r'\s*;', text)
                    or re.search(r'@(?:Tag|RequiresRom|RequiresGameModule)\b|\bextends\s+', text)):
                return False
        return True
    except (OSError, ValueError, ET.ParseError):
        return False


def automatic_lean(args, root):
    """Return the bounded command/environment, or None without changing inputs."""
    from maven_queue import lean_command
    if [arg for arg in args if arg.startswith('-Dmse=')] != ['-Dmse=off']:
        return None
    if sys.platform != 'linux' or os.environ.get('OPENGGF_MAVEN_QUEUE', 'auto') != 'auto':
        return None
    resources = snapshot()
    if resources is None or resources[1] < 8:
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
    if len(selectors) != 1 or not qualified_selectors(selectors[0].split(','), root):
        return None
    try:
        return lean_command(args)
    except (OSError, ValueError):
        return None


def batch_lean(candidates, root, *, workers=1):
    """Qualify an unchanged ordinary includes batch for the same bounded shape."""
    if workers != 1:
        return None
    selectors = [candidate.removesuffix('.java').replace('/', '.') for candidate in candidates]
    return automatic_lean(['-Dmse=off', '-Dtest=' + ','.join(selectors), 'test'], root)
