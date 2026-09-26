#!/usr/bin/env python3
"""Writes the jewelry affix set from Apotheosis's own affix files.

Each jewelry affix is an Apotheosis affix (same type and value table, so jewelry sits at gear parity before the
server's `jewelry.affixValueMultiplier`) retargeted to one Immersive Apotheosis jewelry category. Names and suffixes
come from Apotheosis's lang. A few affixes for Iron's Spellbooks attributes are authored here and load only when that
mod is present.

  python3 tools/generate_jewelry_affixes.py            # --check (default): report files that differ, write nothing
  python3 tools/generate_jewelry_affixes.py --write    # regenerate
  python3 tools/generate_jewelry_affixes.py --jar PATH # another Apotheosis jar (default: the Gradle cache)
"""
import argparse
import collections
import glob
import json
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
OUT = RES / 'data/immersive_apotheosis/affixes/jewelry'
LANG = RES / 'assets/immersive_apotheosis/lang/en_us.json'
MOD = 'immersive_apotheosis'
RARITIES = ['common', 'uncommon', 'rare', 'epic', 'mythic', 'ancient']
STAT_TYPES = {'apotheosis:attribute'}

# form -> Apotheosis affix paths (under data/apotheosis/affixes/). Every form has five stats valid from Common, so
# every rarity rolls cleanly; abilities use only triggers worn jewelry dispatches (attack, hurt, block break, damage).
FORMS = {
    'ring': [  # offense
        'sword/attribute/intricate', 'sword/attribute/lacerating', 'sword/attribute/vampiric', 'sword/attribute/piercing',
        'breaker/attribute/experienced', 'armor/attribute/fortunate', 'sword/attribute/spellbreaking',
        'sword/mob_effect/weakening', 'sword/mob_effect/elusive', 'heavy_weapon/mob_effect/bloodletting',
        'sword/mob_effect/sophisticated'],
    'amulet': [  # defense
        'armor/attribute/blessed', 'armor/attribute/ironforged', 'heavy_weapon/attribute/berserking', 'armor/attribute/windswept',
        'breaker/attribute/experienced', 'armor/attribute/stalwart', 'armor/attribute/spiritual', 'armor/attribute/steel_touched',
        'armor/mob_effect/bolstering', 'armor/dmg_reduction/runed', 'armor/dmg_reduction/dwarven', 'armor/dmg_reduction/blast_forged',
        'armor/mob_effect/revitalizing'],
    'bracelet': [  # dexterity
        'ranged/attribute/agile', 'ranged/attribute/elven', 'ranged/attribute/streamlined', 'sword/attribute/graceful',
        'breaker/attribute/destructive', 'breaker/attribute/lengthy',
        'armor/mob_effect/nimble', 'sword/mob_effect/elusive', 'breaker/mob_effect/swift', 'breaker/mob_effect/spelunkers',
        'armor/dmg_reduction/feathery'],
    'circlet': [  # mind
        'breaker/attribute/experienced', 'breaker/attribute/lengthy', 'sword/attribute/elongated', 'armor/attribute/blessed',
        'breaker/attribute/destructive', 'breaker/attribute/lucky', 'armor/attribute/spiritual',
        'armor/dmg_reduction/runed', 'sword/mob_effect/sophisticated', 'breaker/mob_effect/swift', 'armor/mob_effect/blinding'],
}


def steps(values):
    """Six rarities from (min, steps, step) triples, common to ancient."""
    return {r: {'min': m, 'steps': n, 'step': s} for r, (m, n, s) in zip(RARITIES, values)}


IRONS = {'type': 'forge:mod_loaded', 'modid': 'irons_spellbooks'}
# Iron's Spellbooks attributes for circlets, at a gear-like scale (spell power +5% at Common to +25% at Ancient).
AUTHORED = {
    'circlet': {
        'arcane': ('Arcane', 'of the Magus', 'irons_spellbooks:spell_power', 'MULTIPLY_BASE',
                   steps([(0.05, 5, 0.01), (0.06, 5, 0.01), (0.08, 5, 0.01), (0.10, 6, 0.01), (0.12, 8, 0.01), (0.15, 10, 0.01)])),
        'deep_welled': ('Deep-Welled', 'of the Wellspring', 'irons_spellbooks:max_mana', 'ADDITION',
                        steps([(25, 5, 5), (35, 5, 5), (50, 5, 5), (60, 6, 5), (75, 8, 5), (100, 10, 5)])),
        'replenishing': ('Replenishing', 'of Flowing Thought', 'irons_spellbooks:mana_regen', 'MULTIPLY_BASE',
                         steps([(0.05, 5, 0.01), (0.06, 5, 0.01), (0.08, 5, 0.01), (0.10, 6, 0.01), (0.12, 8, 0.01), (0.15, 10, 0.01)])),
        'quickened': ('Quickened', 'of the Swift Tongue', 'irons_spellbooks:cooldown_reduction', 'MULTIPLY_BASE',
                      steps([(0.02, 3, 0.01), (0.03, 3, 0.01), (0.04, 4, 0.01), (0.05, 4, 0.01), (0.06, 5, 0.01), (0.08, 6, 0.01)])),
    },
}


def default_jar():
    found = glob.glob(str(pathlib.Path.home() / '.gradle/caches/modules-2/files-2.1/maven.modrinth/apotheosis/*/*/apotheosis-*.jar'))
    found = [f for f in found if 'sources' not in f]
    if not found:
        sys.exit('No Apotheosis jar in the Gradle cache; build once or pass --jar')
    return found[0]


def dump(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + '\n'


def build(jar):
    files, lang = {}, {}
    with zipfile.ZipFile(jar) as z:
        apoth_lang = json.loads(z.read('assets/apotheosis/lang/en_us.json'))
        for form, sources in FORMS.items():
            category = f'{MOD}:{form}'
            for source in sources:
                data = json.loads(z.read(f'data/apotheosis/affixes/{source}.json'))
                data['types'] = [category]
                name = source.split('/')[-1]
                files[OUT / form / f'{name}.json'] = dump(data)
                key = f'affix.{MOD}:jewelry/{form}/{name}'
                lang[key] = apoth_lang[f'affix.apotheosis:{source}']
                lang[key + '.suffix'] = apoth_lang[f'affix.apotheosis:{source}.suffix']
    for form, affixes in AUTHORED.items():
        for name, (title, suffix, attribute, operation, values) in affixes.items():
            data = {'conditions': [IRONS], 'type': 'apotheosis:attribute', 'attribute': attribute, 'operation': operation,
                    'values': values, 'types': [f'{MOD}:{form}']}
            files[OUT / form / f'{name}.json'] = dump(data)
            key = f'affix.{MOD}:jewelry/{form}/{name}'
            lang[key], lang[key + '.suffix'] = title, suffix
    return files, lang


def coverage(files):
    """Stats and abilities available per form and rarity; every form needs a stat at every rarity."""
    table = collections.defaultdict(lambda: collections.Counter())
    for path, text in files.items():
        data = json.loads(text)
        if data.get('conditions'):
            continue  # optional; coverage must hold without other mods
        kind = 'stat' if data['type'] in STAT_TYPES else 'ability'
        for rarity in data['values']:
            table[(path.parent.name, rarity)][kind] += 1
    ok = True
    for form in FORMS:
        row = [f'{r[:3]} {table[(form, r)]["stat"]}s/{table[(form, r)]["ability"]}a' for r in RARITIES]
        print(f'  {form:9s} ' + '  '.join(row))
        ok &= all(table[(form, r)]['stat'] >= 3 for r in RARITIES)
    return ok


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    parser.add_argument('--jar')
    args = parser.parse_args()
    files, lang = build(args.jar or default_jar())
    print('coverage (stats/abilities per rarity):')
    if not coverage(files):
        sys.exit('a form lacks three stat affixes at some rarity')

    current = json.loads(LANG.read_text()) if LANG.exists() else {}
    stale_keys = [k for k in current if k.startswith(f'affix.{MOD}:jewelry/') and k not in lang]
    merged = {k: v for k, v in current.items() if k not in stale_keys}
    merged.update(lang)
    files[LANG] = dump(merged)
    existing = set(OUT.rglob('*.json')) if OUT.exists() else set()
    orphans = sorted(existing - set(files))

    differ = [p for p, text in files.items() if not p.exists() or p.read_text() != text]
    if not args.write:
        for p in differ:
            print(f'differs: {p.relative_to(ROOT)}')
        for p in orphans:
            print(f'orphan:  {p.relative_to(ROOT)}')
        print(f'{len(files) - len(differ)}/{len(files)} generated files up to date')
        return 1 if differ or orphans else 0
    for p in orphans:
        p.unlink()
    for p, text in files.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)
    print(f'wrote {len(files)} files, removed {len(orphans)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
