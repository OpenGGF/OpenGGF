#!/usr/bin/env python3
"""Build the experimental native Linux x64 friends ZIP (2026-10-09).

Inputs: Java 21 Maven artifacts, source-built mods, pinned CE GraalVM and an
optional Ubuntu 22.04 rootfs for a glibc 2.35 compile baseline. Run under the
Maven queue's resource reservation. Production native policy is unchanged.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/modding/native-windows"))
from build_windows import digest, run, extract_sdk, extract_jar, archive_bundle
from build_inputs import tool

GRAAL_VERSION = "25.4.4.1.1+1.1"
GRAAL_SHA256 = "05ccbbe783210b6886ff7b08fcd0b061c5dce4852b05db87284fc0e24abb08e2"
MAIN = "com.openggf.tools.nativelinux.LinuxNativeEngine"
RECIPES = {
    "starfall-frontier": ["play", "craft", "cavern", "warden"],
    "eggmans-sky": ["new:0x5eed", "space", "station", "planet:1:0x5eed"],
    "flappy-tails": ["seed:0x5eed", "autopilot:on", "play:classic", "play:sonic:1"],
    "robotnik-tower-defense": ["wave:1", "wave:4", "win"],
    "slay-the-robotnik": ["sonic:42:fight:hcz:big_shaker", "sonic:7:room:Shop:2", "sonic:7:map:1"],
    "sitar-hero": ["perform:angel-island-1:SITAR:sonic", "autoplay", "pause", "resume"],
}

def elf_x64(path):
    data = Path(path).read_bytes()[:64]
    if len(data)<64 or data[:6]!=b"\x7fELF\x02\x01" or struct.unpack_from("<H",data,18)[0]!=62:
        raise ValueError("Expected Linux x64 ELF: " + str(path))

def assemble(bundle, image, engine, inputs, contract, graal, evidence):
    bundle.mkdir(parents=True, exist_ok=False)
    shutil.copy2(image,bundle / "OpenGGF")
    elf_x64(bundle / "OpenGGF")
    (bundle / "OpenGGF").chmod(0o755)
    for library in image.parent.glob("*.so"):
        elf_x64(library); shutil.copy2(library,bundle / library.name)
    with zipfile.ZipFile(engine) as jar:
        libraries=[name for name in jar.namelist() if name.startswith("linux/x64/org/lwjgl/") and name.endswith(".so")]
        if len(libraries)<5: raise ValueError("Missing Linux x64 LWJGL libraries")
        for name in libraries:
            target=bundle / Path(name).name
            target.write_bytes(jar.read(name)); elf_x64(target)
    catalog=json.loads((inputs / "build-info.json").read_text())
    shutil.copytree(inputs / "mods",bundle / "mods")
    shutil.copy2(contract / "members.tsv",bundle / "native-mod-members.tsv")
    catalog.update({"experimental":True,"platform":"linux-x64","graalVersion":GRAAL_VERSION,
        "graalArchiveSha256":GRAAL_SHA256,"nativeSha256":digest(image),"validation":evidence})
    (bundle / "build-info.json").write_text(json.dumps(catalog,indent=2)+"\n")
    (bundle / "config.yaml").write_text(
        '# Put your own ROMs beside this file.\nroms:\n  sonic1: "s1.gen"\n  sonic2: "s2.gen"\n'
        '  sonic3k: "s3k.gen"\n  directory: "."\n  default: "s2"\nstartup:\n'
        '  titleScreen: true\n  masterTitleScreen: true\n  legalDisclaimer: true\n')
    launcher='#!/bin/sh\nset -eu\ncd -- "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"\n'
    (bundle / "OpenGGF.sh").write_text(launcher+'exec ./OpenGGF "$@"\n')
    (bundle / "OpenGGF.sh").chmod(0o755)
    for mod in catalog["mods"]:
        slug=mod["slug"]
        if not slug or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in slug):
            raise ValueError("Unsafe launcher slug: " + slug)
        jar=bundle / "mods" / (slug+".jar")
        if digest(jar)!=mod["jarSha256"]: raise ValueError("Mod hash drift: " + slug)
        extract_jar(jar,bundle / "quick-mods" / slug)
        shortcut=bundle / ("Launch "+slug+".sh")
        shortcut.write_text(launcher+'exec ./OpenGGF "-Dggfmod.dev.modDir=$PWD/quick-mods/'+slug+'" "$@"\n')
        shortcut.chmod(0o755)
    shutil.copy2(ROOT / "LICENSE",bundle / "LICENSE")
    shutil.copytree(ROOT / "LICENSES",bundle / "LICENSES")
    for name in ("CREDITS.md","NOTICE.md"):
        if (ROOT / name).is_file(): shutil.copy2(ROOT / name,bundle / name)
    notices=bundle / "LICENSES/GraalVM";notices.mkdir()
    for name in ("LICENSE.txt","THIRD_PARTY_LICENSE.txt"): shutil.copy2(graal / name,notices / name)
    shutil.copytree(graal / "legal",notices / "legal")
    (bundle / "README.txt").write_text(
        "OpenGGF experimental native Linux x64 build\n\n"
        "Extract the entire ZIP. Run ./OpenGGF.sh or a Launch *.sh shortcut.\n"
        "Java is not required. Keep the executable, shared libraries and folders together.\n"
        "Use Linux x86_64 with glibc 2.35 or newer (Ubuntu 22.04 baseline),\n"
        "an X11/XWayland desktop and compatible OpenGL graphics drivers.\n"
        "Typical Debian/Ubuntu runtime packages: libgl1 libx11-6 libxcursor1\n"
        "libxrandr2 libxinerama1 libxi6 libasound2 libfontconfig1.\n"
        "If your extractor removes executable bits, run chmod +x OpenGGF *.sh.\n"
        "For ROM-dependent mods supply your own s1.gen, s2.gen and s3k.gen beside config.yaml.\n"
        "Authored standalone samples do not require ROMs. Use the game's controls/prompts.\n"
        "Shortcuts select a development mod directory; OpenGGF.sh shows engine menus.\n\n"
        "This is experimental GraalVM runtime class loading. See build-info.json for\n"
        "completed checks. Rendered checks cover representative paths, not every route,\n"
        "network session or performance/driver combination. Production native policy is unchanged.\n"
        "Run ./OpenGGF --audit or --check-mods for bounded diagnostic checks.\n\n"
        "OpenGGF: GNU GPL version 3. Corresponding engine, mods and build source:\n"
        f"https://github.com/OpenGGF/OpenGGF/tree/{catalog['sourceCommit']}\n"
        "GraalVM Community source and release:\n"
        "https://github.com/oracle/graal/tree/95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b\n"
        "https://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.4.4.1.1\n"
        "Compiler/runtime and third-party notices are retained under LICENSES.\n")

def prepare(args):
    work=args.output.resolve(); work.mkdir(parents=True,exist_ok=False)
    engine,inputs,graal=args.engine_jar.resolve(strict=True),args.inputs.resolve(strict=True),args.graal.resolve(strict=True)
    if digest(engine)!=json.loads((inputs / "build-info.json").read_text())["engineSha256"]:
        raise ValueError("Engine/mod input identity changed")
    if digest(graal.parent / "graalvm.tar.gz")!=GRAAL_SHA256: raise ValueError("GraalVM checksum mismatch")
    sdk=work / "hosted-sdk.jar"; extract_sdk(graal,sdk)
    sources=sorted((ROOT / "tools/modding/native-feasibility").glob("NativeModMember*.java"))
    sources += [ROOT / "tools/modding/native-feasibility/NativeModRegistrationProbe.java"]
    sources += sorted((ROOT / "tools/modding/native-windows").glob("*.java"))
    sources += sorted(Path(__file__).parent.glob("*.java"))
    classes=work / "host-classes"
    run([tool("javac"),"--release","21","-cp",os.pathsep.join(map(str,(engine,sdk,ROOT / "target/classes"))),"-d",classes,*sources],work)
    java=[tool("java"),"-cp",os.pathsep.join(map(str,(classes,engine)))]
    run([*java,"com.openggf.tools.NativeModMemberContractTest",work / "fixtures"],work)
    contract=work / "contract"
    run([*java,"com.openggf.tools.NativeModMemberContract",engine,inputs / "mods",contract],work)
    run([*java,"com.openggf.tools.NativeModMemberAudit",contract / "members.tsv"],work)
    metadata=work / "metadata";metadata.mkdir()
    (metadata / "reflect-config.json").write_text('[{"name":"sun.net.www.protocol.jar.Handler","allDeclaredConstructors":true}]\n')
    deps=args.runtime_classpath.resolve(strict=True).read_text().strip()
    native_cp=os.pathsep.join(map(str,(classes,contract / "preserved-engine.jar",ROOT / "target/classes")))+os.pathsep+deps
    native=work / "native";native.mkdir()
    arguments=["-march=compatibility","-J-Xmx5g","--parallelism=4",
        "--initialize-at-run-time=org.lwjgl,java.awt,javax.swing,sun.awt,sun.java2d",
        "-H:+UnlockExperimentalVMOptions","-H:+RuntimeClassLoading",
        f"-H:Preserve=path={contract / 'preserved-engine.jar'},package=java.lang,package=java.lang.invoke,package=java.util,package=java.util.function",
        r"-H:IncludeResources=com/openggf/.*\.class",f"-H:ConfigurationFileDirectories={metadata}",
        "-J-Dopenggf.experimental.native.mods=true",
        "-J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.hub=ALL-UNNAMED",
        "-J--add-exports=org.graalvm.nativeimage.shared/com.oracle.svm.shared.option=ALL-UNNAMED",
        "--features=com.openggf.tools.nativewindows.ExperimentalNativeFeature","-cp",native_cp,MAIN,str(native / "OpenGGF")]
    (work / "native-image.args").write_text("\n".join(json.dumps(arg) for arg in arguments))

def compile_image(args):
    work=args.output.resolve();graal=args.graal.resolve(strict=True)
    command=[graal / "bin/native-image","@"+str(work / "native-image.args")]
    if args.rootfs:
        command=["bwrap","--bind",args.rootfs.resolve(strict=True),"/","--unshare-user","--uid","0","--gid","0",
            "--proc","/proc","--dev","/dev","--bind",ROOT,ROOT,"--ro-bind",Path.home()/".m2",Path.home()/".m2",
            "--setenv","TMPDIR","/tmp","--setenv","PATH","/usr/bin:/bin","--chdir",work / "native",*command]
    run(command,work / "native")

def qualify(args):
    work=args.output.resolve();engine=args.engine_jar.resolve(strict=True);inputs=args.inputs.resolve(strict=True);graal=args.graal.resolve(strict=True)
    image=work / "native/OpenGGF"; contract=work / "contract"
    evidence={"memberAudit":"pending","registrations":[],"engineBootRegistrations":[],"renderedGameplay":[]}
    bundle=work / "OpenGGF-experimental-linux-x64-with-mods"
    assemble(bundle,image,engine,inputs,contract,graal,evidence)
    run([bundle / "OpenGGF","--audit"],bundle)
    original=(bundle / "native-mod-members.tsv").read_bytes()
    for kind,desc in (("F","I"),("M","()V")):
        try:
            (bundle / "native-mod-members.tsv").write_text(f"{kind}\tcom.openggf.game.rules.GameRules\t__absent_control\t{desc}\n")
            run([bundle / "OpenGGF","--audit"],bundle,"MISSING native member: "+kind)
        finally: (bundle / "native-mod-members.tsv").write_bytes(original)
    for mod in json.loads((inputs / "build-info.json").read_text())["mods"]:
        owner,slug=mod["id"],mod["slug"]
        output=run([bundle / "OpenGGF","--check-mods",owner],bundle)
        if f"PASS registration: {owner};" not in output: raise AssertionError("No registration result: "+owner)
        evidence["registrations"].append(owner)
        with zipfile.ZipFile(inputs / "mods" / (slug+".jar")) as jar:
            code=any(name.endswith(".class") for name in jar.namelist())
        if code:
            output=run([bundle / "OpenGGF",f"-Dggfmod.dev.modDir={bundle / 'quick-mods' / slug}","--check-engine",owner],bundle)
            if f"PASS engine native boot registration: {owner}" not in output: raise AssertionError("No engine result: "+owner)
            evidence["engineBootRegistrations"].append(owner)
        if args.roms:
            captures=args.captures.resolve() / slug
            output=run([bundle / "OpenGGF","--check-gameplay",slug,captures,*map(lambda p:p.resolve(strict=True),args.roms),*RECIPES.get(slug,[])],bundle)
            if f"PASS native rendered gameplay: {owner}" not in output: raise AssertionError("No gameplay result: "+owner)
            evidence["renderedGameplay"].append(owner)
    evidence["memberAudit"]="passed; missing field and method controls rejected with ordinary exit 1"
    (work / "qualification.json").write_text(json.dumps(evidence,indent=2)+"\n")
    shutil.rmtree(bundle)
    assemble(bundle,image,engine,inputs,contract,graal,evidence)
    artifact=work / (bundle.name+".zip");archive_bundle(bundle,artifact)
    print(f"PASS Linux native ZIP: {artifact}; SHA256 {digest(artifact)}")

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("engine-jar","inputs","runtime-classpath","graal","output"): parser.add_argument("--"+name,required=True,type=Path)
    parser.add_argument("--rootfs",type=Path)
    parser.add_argument("--stage",choices=("prepare","compile","qualify","all"),default="all")
    parser.add_argument("--roms",type=Path,nargs=3)
    parser.add_argument("--captures",type=Path)
    args=parser.parse_args()
    if bool(args.roms)!=bool(args.captures): parser.error("--roms and --captures must be supplied together")
    if args.stage in ("prepare","all"):prepare(args)
    if args.stage in ("compile","all"):compile_image(args)
    if args.stage in ("qualify","all"):qualify(args)
