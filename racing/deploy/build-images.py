#!/usr/bin/env python3
"""Build the master/relay and verifier images from one matching engine distribution.

Inputs: this checkout, Java 25/Maven, Docker; no ROM, secret or operator configuration.
Output: local openggf-racing-{master,verifier}:TAG images (never pushed).
Origin: docs/architecture/plans/2026-10-23-racing-docker-deployment.md.

Use --context-only to prepare target/racing-docker-context without a Docker daemon.
Use --skip-build only to reuse an already built target/racing-server/dist; its build
identity is retained, including any dirty marker. Image tags do not change it.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DISTRIBUTION = ROOT / "target" / "racing-server" / "dist"
CONTEXT = ROOT / "target" / "racing-docker-context"
DOCKERFILE = Path(__file__).with_name("Dockerfile")
PLATFORM = "linux/amd64"


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def allowed_artifact(name):
    path = PurePosixPath(name)
    if path.as_posix() != name or ".." in path.parts or path.is_absolute():
        return False
    return (name in {"bin/racing-server", "lib/racing-server.jar", "lib/openggf-engine.jar"}
            or (len(path.parts) == 3 and path.parts[:2] == ("lib", "deps")
                and path.suffix == ".jar"))


def prepare_context(distribution=DISTRIBUTION, context=CONTEXT, dockerfile=DOCKERFILE):
    """Copy the manifest's runtime artifacts, not arbitrary files beside them."""
    manifest = json.loads((distribution / "distribution.json").read_text())
    if manifest.get("formatVersion") != 1:
        raise ValueError("Unsupported racing distribution; rebuild with racing/server/build.py --distribution")
    if manifest.get("buildPlatform") != PLATFORM:
        raise ValueError(f"Docker deployment currently requires a {PLATFORM} distribution; "
                         f"got {manifest.get('buildPlatform')!r}")
    files = manifest["files"]
    required = {"bin/racing-server", "lib/racing-server.jar", "lib/openggf-engine.jar"}
    if not required.issubset(files) or not any(name.startswith("lib/deps/") for name in files):
        raise ValueError("Incomplete racing distribution")
    for name, checksum in files.items():
        if not allowed_artifact(name):
            raise ValueError(f"Unexpected runtime artifact: {name}")
        source = distribution / name
        if not source.resolve().is_relative_to(distribution.resolve()) or source.is_symlink():
            raise ValueError(f"Runtime artifact must be inside the distribution: {name}")
        if digest(source) != checksum:
            raise ValueError(f"Runtime artifact changed since packaging: {name}; rebuild the distribution")
    with zipfile.ZipFile(distribution / "lib" / "openggf-engine.jar") as engine:
        if hashlib.sha256(engine.read("version.properties")).hexdigest() != manifest["versionPropertiesSha256"]:
            raise ValueError("Packaged engine build identity does not match the distribution")
    if context.exists():
        shutil.rmtree(context)
    context.mkdir(parents=True)
    shutil.copyfile(dockerfile, context / "Dockerfile")
    # Defense in depth if an operator later places a file beside this generated context.
    (context / ".dockerignore").write_text("**\n!Dockerfile\n!dist/\n!dist/**\n")
    for name in files:
        destination = context / "dist" / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(distribution / name, destination)
        destination.chmod(0o755 if name == "bin/racing-server" else 0o644)
    (context / "dist" / "distribution.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (context / "dist" / "distribution.json").chmod(0o644)
    # COPY preserves host modes. Runtime UID 10001 must also traverse directories
    # when an operator builds with a restrictive (for example 0077) host umask.
    for path in (context / "dist", *(context / "dist").rglob("*")):
        if path.is_dir():
            path.chmod(0o755)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tag", required=True, help="local image tag; use a unique tag for each deployed build")
    parser.add_argument("--image-prefix", default="openggf-racing", help="image names PREFIX-master and PREFIX-verifier")
    parser.add_argument("--docker", default="docker", help="Docker executable or wrapper path (uses its selected context)")
    parser.add_argument("--java-image", default="eclipse-temurin:25-jre-noble", help="Java 25 runtime image; may include @sha256 digest")
    parser.add_argument("--skip-build", action="store_true", help="reuse the previously packaged distribution")
    parser.add_argument("--context-only", action="store_true", help="prepare the generated context without Docker")
    args = parser.parse_args()
    if not re.fullmatch(r"[\w][\w.-]{0,127}", args.tag, flags=re.ASCII):
        parser.error("--tag must be a valid Docker tag")
    if not args.skip_build:
        subprocess.run([sys.executable, str(ROOT / "racing/server/build.py"), "--distribution"],
                       cwd=ROOT, check=True)
    manifest = prepare_context()
    print(f"Prepared {CONTEXT} from {len(manifest['files'])} runtime artifacts only", flush=True)
    if args.context_only:
        return
    for service in ("master", "verifier"):
        image = f"{args.image_prefix}-{service}:{args.tag}"
        subprocess.run([args.docker, "build", "--platform", PLATFORM, "--target", service,
                        "--build-arg", f"JAVA_IMAGE={args.java_image}",
                        "--build-arg", f"BUILD_IDENTITY_SHA256={manifest['versionPropertiesSha256']}",
                        "--tag", image, str(CONTEXT)], check=True)
        print(f"Built local image {image}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print(f"Cannot build racing images: {error}", file=sys.stderr)
        sys.exit(1)
