#!/usr/bin/env python3
"""Branch-channel master catalogue for CI, from the hostname-only multiplayer endpoints work (2026-11-01).

Input: racing/deploy/master-channels.properties, the single channel=host:port list that player
builds also read (pom openggf.multiplayer.channel through BundledNetworkDefaults).

  master_channels.py endpoint <channel>   print wss://host:port/master for amp_deploy.py
  master_channels.py check-srv <channel>  require the published _openggf._tcp.<host> SRV
                                          records to name <host> and the catalogue port

check-srv only reads DNS (dig); it never edits records. A mismatch, a missing record or a
lookup failure exits 1 so the deploy job stops before redeploying.
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

CATALOGUE = Path(__file__).resolve().with_name('master-channels.properties')
SERVICE = '_openggf._tcp.'
_LINE = re.compile(r'^([a-z][a-z0-9-]*)=([a-z0-9.-]+):([0-9]{1,5})$')
_SRV = re.compile(r'^([0-9]+) ([0-9]+) ([0-9]+) (\S+)$')


def parse_catalogue(text):
    channels = {}
    for number, raw in enumerate(text.split('\n'), 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        match = _LINE.match(line)
        if not match or not 1 <= int(match.group(3)) <= 65535 or '.' not in match.group(2):
            raise ValueError(f'master-channels.properties:{number}: expected channel=host:port')
        if match.group(1) in channels:
            raise ValueError(f'master-channels.properties:{number}: duplicate channel {match.group(1)}')
        channels[match.group(1)] = (match.group(2), int(match.group(3)))
    if not channels:
        raise ValueError('master-channels.properties lists no channels')
    return channels


def entry(channels, channel):
    if channel not in channels:
        raise ValueError(f'unknown multiplayer channel {channel!r}; expected one of {sorted(channels)}')
    return channels[channel]


def endpoint(channels, channel):
    host, port = entry(channels, channel)
    return f'wss://{host}:{port}/master'


def parse_dig(output):
    """Parse `dig +short SRV` lines into (priority, weight, port, target) tuples."""
    records = []
    for raw in output.splitlines():
        line = raw.strip()
        if not line:
            continue
        match = _SRV.match(line)
        if not match:
            raise ValueError(f'unexpected DNS answer: {line}')
        records.append((int(match.group(1)), int(match.group(2)), int(match.group(3)),
                        match.group(4).rstrip('.').lower()))
    return records


def drift(channels, channel, records):
    """Problems between the catalogue and the published records; empty when they agree."""
    host, port = entry(channels, channel)
    if not records:
        return [f'no SRV record is published at {SERVICE}{host}']
    problems = []
    for priority, weight, published_port, target in records:
        if target != host:
            problems.append(f'{SERVICE}{host} names target {target}, not {host}')
        if published_port != port:
            problems.append(f'{SERVICE}{host} publishes port {published_port}; the catalogue says {port}')
    return problems


def lookup(owner):
    try:
        completed = subprocess.run(['dig', '+short', '+time=3', '+tries=2', 'SRV', owner],
                                   capture_output=True, text=True, timeout=30)
    except FileNotFoundError:
        raise ValueError('dig is required to check the published SRV record')
    if completed.returncode != 0:
        raise ValueError(f'DNS lookup of {owner} failed: {completed.stderr.strip() or completed.returncode}')
    return parse_dig(completed.stdout)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('command', choices=('endpoint', 'check-srv'))
    parser.add_argument('channel')
    parser.add_argument('--catalogue', type=Path, default=CATALOGUE)
    args = parser.parse_args(argv)
    try:
        channels = parse_catalogue(args.catalogue.read_text(encoding='utf-8'))
        if args.command == 'endpoint':
            print(endpoint(channels, args.channel))
            return 0
        host, port = entry(channels, args.channel)
        problems = drift(channels, args.channel, lookup(SERVICE + host))
    except ValueError as failure:
        print(f'master_channels: {failure}', file=sys.stderr)
        return 1
    if problems:
        for problem in problems:
            print(f'SRV drift: {problem}', file=sys.stderr)
        print('Fix the DNS record or racing/deploy/master-channels.properties; CI never edits DNS.',
              file=sys.stderr)
        return 1
    print(f'{SERVICE}{host}: SRV matches {host}:{port}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
