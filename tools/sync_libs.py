#!/usr/bin/env python3
"""Copies the jars the build compiles against into libs/ (read-only on every source).

Sister mods come from their sibling repositories' build/libs (run their `build` first); ApothicCurios comes from the
Ultima CurseForge instance. Names follow gradle.properties: libs/<name>-<version>.jar.

  python3 tools/sync_libs.py            # copy what is missing or older
  python3 tools/sync_libs.py --check    # report only; exit 1 if anything is missing or stale
"""
import argparse
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PROJECTS = ROOT.parent
ULTIMA_MODS = pathlib.Path('/home/otectus/Documents/curseforge/minecraft/Instances/Ultima/mods')


def properties():
    props = {}
    for line in (ROOT / 'gradle.properties').read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            props[key.strip()] = value.strip()
    return props


def sources(props):
    for sister, repo in [('smithing', 'ImmersiveSmithing'), ('tanning', 'ImmersiveTanning'),
                         ('jewelry', 'ImmersiveJewelry'), ('enchanting', 'ImmersiveEnchanting')]:
        version = props[f'immersive_{sister}_version']
        yield PROJECTS / repo / 'build/libs' / f'immersive-{sister}-{version}.jar', f'immersive-{sister}-{version}.jar'
    version = props['apothic_curios_version']
    yield ULTIMA_MODS / f'ApothicCurios-1.20.1-{version}.jar', f'apothic-curios-{version}.jar'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    libs = ROOT / 'libs'
    libs.mkdir(exist_ok=True)
    problems = 0
    for source, name in sources(properties()):
        target = libs / name
        if not source.is_file():
            print(f'MISSING source {source}')
            problems += 1
            continue
        stale = not target.is_file() or source.stat().st_mtime > target.stat().st_mtime + 1
        if not stale:
            print(f'ok      {name}')
        elif args.check:
            print(f'STALE   {name} (from {source})')
            problems += 1
        else:
            shutil.copy2(source, target)
            print(f'copied  {name} <- {source}')
    return 1 if problems and args.check else 0


if __name__ == '__main__':
    sys.exit(main())
