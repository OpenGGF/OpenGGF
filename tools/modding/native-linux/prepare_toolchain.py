#!/usr/bin/env python3
"""Prepare an owned rootless Ubuntu 22.04/GraalVM CE Linux builder.

Origin: 2026-10-09 native friends build. Inputs are publisher-checksum-pinned
archives; package updates retain Ubuntu's glibc 2.35 ABI. No host package edits.
Requires Python, bubblewrap and network access. Output directory must be new.
"""
import argparse
import hashlib
import posixpath
from pathlib import Path
import subprocess
import tarfile
import urllib.request
from build_linux import GRAAL_SHA256

UBUNTU_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-amd64.tar.gz"
UBUNTU_SHA256="242cd8898b33ea806ef5f13b1076ed7c76f9f989d18384452f7166692438ff1a"
GRAAL_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-25.4.4.1.1/graalvm-community-jdk-25i4-25.0.4.1.1_linux-x64_bin.tar.gz"

def rootfs_filter(member,destination):
    # Ubuntu alternatives use absolute links inside the future root. Make them
    # relative before extraction so they cannot point into the host filesystem.
    if member.issym() and member.linkname.startswith("/"):
        member=member.replace(linkname=posixpath.relpath(member.linkname.lstrip("/"),posixpath.dirname(member.name)))
    elif member.islnk() and member.linkname.startswith("/"):
        member=member.replace(linkname=member.linkname.lstrip("/"))
    return tarfile.data_filter(member,destination)

def prepare(output):
    output=output.resolve();output.mkdir(parents=True,exist_ok=False)
    for name,url,checksum,destination in [
        ("ubuntu-base.tar.gz",UBUNTU_URL,UBUNTU_SHA256,output / "ubuntu-rootfs"),
        ("graalvm.tar.gz",GRAAL_URL,GRAAL_SHA256,output)]:
        archive=output / name;urllib.request.urlretrieve(url,archive)
        with archive.open('rb') as stream:
            if hashlib.file_digest(stream,'sha256').hexdigest()!=checksum:raise ValueError("Publisher checksum mismatch: "+name)
        destination.mkdir(parents=True,exist_ok=True)
        with tarfile.open(archive) as tar:tar.extractall(destination,filter=rootfs_filter)
    # A one-user namespace cannot drop to the _apt account. Keep root *inside*
    # this namespace; all writes are still the invoking user's outside it.
    subprocess.run(["bwrap","--bind",str(output / "ubuntu-rootfs"),"/","--unshare-user","--uid","0","--gid","0",
        "--proc","/proc","--dev","/dev","--ro-bind","/etc/resolv.conf","/etc/resolv.conf",
        "--setenv","TMPDIR","/tmp","--setenv","PATH","/usr/sbin:/usr/bin:/sbin:/bin",
        "--setenv","DEBIAN_FRONTEND","noninteractive","/bin/bash","-c",
        "apt-get -o APT::Sandbox::User=root update -qq && apt-get -o APT::Sandbox::User=root install -y -qq --no-install-recommends gcc libc6-dev zlib1g-dev"],check=True)

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--output",required=True,type=Path)
    prepare(parser.parse_args().output)
