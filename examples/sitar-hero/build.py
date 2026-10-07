#!/usr/bin/env python3
"""Build Sitar Hero, optionally launching with an explicit subset of existing ROMs.

Origin: Sitar Hero arcade proof of concept, 2026-10-06. ROMs are runtime inputs only.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--skip-engine", action="store_true", help="reuse the compiled engine")
    for game in ("s1", "s2", "s3k"):
        parser.add_argument("--" + game, type=Path, help="existing ROM path, used only at runtime")
    args = parser.parse_args()
    supplied = {game: getattr(args, game) for game in ("s1", "s2", "s3k")}
    if any(supplied.values()) and not args.run:
        parser.error("ROM options require --run")
    if args.run and not any(supplied.values()):
        parser.error("--run requires at least one of --s1, --s2, --s3k")
    for game, path in supplied.items():
        if path is not None:
            supplied[game] = path.expanduser().resolve()
            if not supplied[game].is_file():
                parser.error("--" + game + " must name an existing file")
    spec = importlib.util.spec_from_file_location("build_example", ROOT / "examples/build_example.py")
    shared = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(shared)
    shared.build("sitar-hero", skip_engine=args.skip_engine)
    if not args.run:
        return
    output = ROOT / "target/examples/sitar-hero"
    launch = output / "run"
    launch.mkdir(parents=True, exist_ok=True)
    # Isolate generated settings/trust state; never rewrite the user's engine config.
    config = "roms:\n" + "".join("  " + key + ": " + json.dumps(str(supplied[game]) if supplied[game] else "") + "\n"
                                for game, key in (("s1", "sonic1"), ("s2", "sonic2"), ("s3k", "sonic3k")))
    config += "  default: " + next(game for game, path in supplied.items() if path) + "\n"
    (launch / "config.yaml").write_text(config)
    classpath = os.pathsep.join((str(ROOT / "target/classes"), (ROOT / "target/examples-classpath.txt").read_text().strip()))
    home = os.environ.get("JAVA_HOME")
    java = str(Path(home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if home else "java"
    subprocess.run([java, "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli", "run", str(output / "classes")],
                   cwd=launch, check=True)


if __name__ == "__main__":
    main()
