#!/usr/bin/env python3
"""Run complete Jason/CArtAgO/Moise matches, including both seat assignments."""
import argparse
import csv
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--config', type=Path, default=ROOT / 'config/default.properties')
parser.add_argument('--seeds', type=int, default=5)
parser.add_argument('--steps', type=int, default=300)
parser.add_argument('--variants', nargs='+', choices=list('ABCD'), default=list('ABCD'))
parser.add_argument('--all-pairs', action='store_true')
parser.add_argument('--output', type=Path, default=ROOT / 'build/results/mas.csv')
args = parser.parse_args()
if args.seeds < 1 or args.steps < 1:
    parser.error("seeds e steps devem ser positivos")
args.output.parent.mkdir(parents=True, exist_ok=True)
rows = []
pairs = [(v, w) for v in args.variants for w in args.variants] if args.all_pairs else [(v, 'A') for v in args.variants]
for red, blue in pairs:
    for first, second in dict.fromkeys([(red, blue), (blue, red)]):
        for exploration in ['frontier', 'sectors']:
            for seed in range(42, 42 + args.seeds):
                result = ROOT / f'build/results/matches/{first}-{second}-{exploration}-{seed}.csv'
                command = [str(ROOT / 'gradlew'), 'run', '--console=plain', '--no-daemon',
                           f'-Pred={first}', f'-Pblue={second}', f'-Pseed={seed}', f'-Pconfig={args.config.resolve()}',
                           f'-Psteps={args.steps}', f'-Pexploration={exploration}', f'-Poutput={result}']
                result.unlink(missing_ok=True)
                subprocess.run(command, cwd=ROOT, check=True, timeout=180)
                with result.open() as stream:
                    rows.extend(csv.DictReader(stream))
                with args.output.open('w', newline='') as stream:
                    writer = csv.DictWriter(stream, fieldnames=rows[0].keys())
                    writer.writeheader()
                    writer.writerows(rows)
print(args.output)
