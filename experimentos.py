#!/usr/bin/env python3
"""Executa partidas JaCaMo reais e preserva configurações, logs e métricas."""
import argparse
import csv
from datetime import datetime
import hashlib
import itertools
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import time

ROOT = Path(__file__).resolve().parent
ERROR = re.compile(r"Exception|SEVERE|ERROR|No applicable plan|No failure event|normative failure|Error parsing", re.I)
FIELDS = "seed red_variant blue_variant exploration rounds red_score blue_score winner empty_round red_messages blue_messages red_duplicates blue_duplicates red_lost blue_lost".split()
META = "partida cenario repeticao status tempo_processo_ms erro".split()


def properties(path):
    result = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith(('#', '!')):
            key, value = line.split('=', 1)
            result[key.strip()] = value.strip()
    return result


def match_plan(scenarios, variants, explorations, repetitions, seed, all_pairs=False):
    pairs = list(itertools.product(variants, repeat=2)) if all_pairs else list(dict.fromkeys(
        pair for variant in variants for pair in [(variant, 'A'), ('A', variant)]))
    for scenario, pair, exploration, rep in itertools.product(scenarios, pairs, explorations, range(repetitions)):
        yield dict(scenario=scenario, red=pair[0], blue=pair[1], exploration=exploration,
                   repetition=rep + 1, seed=seed + rep)


def validate(row, output, config):
    if list(row) != FIELDS or any(row[k] is None for k in FIELDS):
        raise ValueError('Cabeçalho de métricas incompatível')
    for key in ('seed', 'red_variant', 'blue_variant', 'exploration'):
        expected = config[{'red_variant': 'red', 'blue_variant': 'blue'}.get(key, key)]
        if str(row[key]) != str(expected):
            raise ValueError(f'{key}: resultado não corresponde à configuração')
    for key in FIELDS:
        if key not in ('red_variant', 'blue_variant', 'exploration', 'winner'):
            int(row[key])
    rounds, limit = int(row['rounds']), int(config['steps'])
    if not 0 <= rounds <= limit:
        raise ValueError('Rodadas fora do limite')
    red, blue = int(row['red_score']), int(row['blue_score'])
    winner = 'draw' if red == blue else 'red' if red > blue else 'blue'
    if row['winner'] != winner or min(red, blue) < 0:
        raise ValueError('Placar ou vencedor inconsistente')
    if red + blue > int(config['minerals']) * max(map(int, config['values'].split(','))):
        raise ValueError('Valor entregue excede o máximo do cenário')
    empty = int(row['empty_round'])
    if empty != -1 and not 0 <= empty <= rounds:
        raise ValueError('Rodada de esvaziamento inválida')
    for team in ('red', 'blue'):
        for metric in ('messages', 'duplicates', 'lost'):
            if int(row[f'{team}_{metric}']) < 0:
                raise ValueError('Métrica negativa')
        expected = 3 * int(config['agents']) * rounds if config[team] in ('C', 'D') else 0
        if int(row[f'{team}_messages']) != expected:
            raise ValueError(f'Contagem de mensagens incorreta para {team}')
    markers = re.findall(r'^MATCH_RESULT (.+)$', output, re.M)
    # Agent console output can follow the marker on the same line.
    if len(markers) != 1 or not markers[0].startswith(','.join(row[k] for k in FIELDS)):
        raise ValueError('Marcador final ausente, duplicado ou diferente do CSV')
    if ERROR.search(output):
        raise ValueError('Erro de integração no log')


def run_match(case, classpath, folder, timeout):
    config = case['config']
    csv_path, log_path = folder / 'resultado.csv', folder / 'execucao.log'
    command = ['java', '-Djava.awt.headless=true',
               f'-Dcontest.output={csv_path}', f'-Dcontest.project={folder / "match.jcm"}',
               '-cp', classpath, 'contest.MatchLauncher', str(folder / 'cenario.properties')]
    (folder / 'comando.json').write_text(json.dumps(command, indent=2), encoding='utf-8')
    start = time.perf_counter()
    with log_path.open('w', encoding='utf-8') as log:
        try:
            process = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError(f'Timeout de {timeout}s; consulte {log_path}') from exc
    elapsed = round((time.perf_counter() - start) * 1000)
    if process.returncode:
        raise RuntimeError(f'Processo terminou com código {process.returncode}; consulte {log_path}')
    with csv_path.open(newline='') as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != 1:
        raise ValueError('A partida deve produzir exatamente uma linha')
    row = rows[0]
    validate(row, log_path.read_text(encoding='utf-8', errors='replace'), config)
    return row, elapsed


def summaries(records, path):
    """One row per strategy vs A, pairing both sides without mixing scenarios."""
    groups = {}
    for row in records:
        if row['status'] != 'ok':
            continue
        for team, opponent in [('red', 'blue'), ('blue', 'red')]:
            key = (row['cenario'], row['exploration'], row[f'{team}_variant'], row[f'{opponent}_variant'])
            stats = groups.setdefault(key, [])
            stats.append((int(row[f'{team}_score']), int(row[f'{opponent}_score']),
                          row['winner'] == team, row['winner'] == 'draw',
                          int(row[f'{team}_messages']), int(row[f'{team}_duplicates']),
                          int(row[f'{team}_lost']), int(row['tempo_processo_ms'])))
    with path.open('w', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(['cenario', 'exploracao', 'variante', 'adversario', 'observacoes_time', 'vitorias', 'empates',
                         'derrotas', 'valor_medio', 'desvio_padrao_valor', 'diferenca_media', 'mensagens_medias',
                         'duplicados_medios', 'perdidos_medios', 'tempo_medio_ms'])
        for key, stats in sorted(groups.items()):
            scores = [s[0] for s in stats]
            wins, draws = sum(s[2] for s in stats), sum(s[3] for s in stats)
            writer.writerow([*key, len(stats), wins, draws, len(stats)-wins-draws,
                             statistics.mean(scores), statistics.stdev(scores) if len(scores)>1 else 0,
                             statistics.mean(s[0]-s[1] for s in stats),
                             *[statistics.mean(s[i] for s in stats) for i in range(4, 8)]])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validar', action='store_true', help='Partidas curtas A–D e repetição determinística')
    parser.add_argument('--planejar', action='store_true', help='Mostra a matriz sem executar partidas')
    parser.add_argument('--repeticoes', '--seeds', type=int, default=3)
    parser.add_argument('--semente', type=int, default=42)
    parser.add_argument('--cenarios', nargs='+', type=Path, default=[ROOT/'config/small.properties', ROOT/'config/default.properties'])
    parser.add_argument('--variantes', nargs='+', choices=list('ABCD'), default=list('ABCD'))
    parser.add_argument('--exploracoes', nargs='+', choices=['frontier', 'sectors'], default=['frontier', 'sectors'])
    parser.add_argument('--todos-confrontos', action='store_true')
    parser.add_argument('--passos', type=int)
    parser.add_argument('--timeout', type=int, default=180)
    parser.add_argument('--saida', type=Path, default=ROOT/'resultados')
    args = parser.parse_args(argv)
    if args.repeticoes < 1 or args.timeout < 1 or (args.passos is not None and args.passos < 1):
        parser.error('repeticoes, timeout e passos devem ser positivos')
    scenarios = list(dict.fromkeys(p.resolve() for p in args.cenarios))
    if args.validar:
        scenarios = [ROOT/'config/small.properties']
    for scenario in scenarios:
        if not scenario.is_file():
            parser.error(f'Cenário inexistente: {scenario}')
    plan = list(match_plan(scenarios, list(dict.fromkeys(args.variantes)), list(dict.fromkeys(args.exploracoes)),
                           1 if args.validar else args.repeticoes, args.semente, args.todos_confrontos))
    if args.validar:
        plan = list(match_plan(scenarios, list('ABCD'), ['frontier'], 1, args.semente))
        plan += [dict(plan[0])]  # Same configuration, new JVM; every game metric must match.
    if args.planejar:
        for i, case in enumerate(plan, 1):
            print(i, case['scenario'].name, case['red'], case['blue'], case['exploration'], case['seed'])
        print(f'Total: {len(plan)} partidas')
        return 0
    gradle = os.environ.get('GRADLE_CMD') or ('gradle' if shutil.which('gradle') else str(ROOT/'gradlew'))
    subprocess.run([gradle, 'preparar', '--no-daemon', '--console=plain'], cwd=ROOT, check=True)
    classpath = (ROOT/'build/runtime-classpath.txt').read_text().strip()
    destination = args.saida.resolve() / (datetime.now().astimezone().strftime('%Y%m%d-%H%M%S-%f') + ('-validacao' if args.validar else '-experimentos'))
    destination.mkdir(parents=True)
    manifest = {'inicio': datetime.now().astimezone().isoformat(), 'validacao': args.validar,
                'timeout_s': args.timeout, 'partidas': len(plan), 'sementes': sorted({c['seed'] for c in plan}),
                'java': subprocess.run(['java', '-version'], capture_output=True, text=True).stderr,
                'fontes_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                                  for p in [ROOT/'build.gradle', *sorted((ROOT/'src').rglob('*.java')), *sorted((ROOT/'src/agt').rglob('*.asl')), *sorted((ROOT/'src/org').glob('*.xml'))]}}
    records, failures = [], 0
    with (destination/'metricas.csv').open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=META + FIELDS)
        writer.writeheader()
        for i, case in enumerate(plan, 1):
            folder = destination / f'{i:04d}-{case["scenario"].stem}-{case["red"]}-{case["blue"]}-{case["exploration"]}-{case["seed"]}'
            folder.mkdir()
            config = properties(case['scenario'])
            config.update(red=case['red'], blue=case['blue'], seed=str(case['seed']), exploration=case['exploration'])
            if args.passos or args.validar:
                config['steps'] = str(args.passos or 30)
            case['config'] = config
            (folder/'cenario.properties').write_text(''.join(f'{k}={v}\n' for k,v in config.items()), encoding='utf-8')
            row = dict.fromkeys(META + FIELDS, '')
            row.update(partida=i, cenario=str(case['scenario'].relative_to(ROOT)) if case['scenario'].is_relative_to(ROOT) else str(case['scenario']),
                       repeticao=case['repetition'], status='erro', seed=case['seed'], red_variant=case['red'], blue_variant=case['blue'], exploration=case['exploration'])
            try:
                result, elapsed = run_match(case, classpath, folder, args.timeout)
                row.update(result, tempo_processo_ms=elapsed, status='ok')
                if args.validar and i == len(plan):
                    if any(str(row[k]) != str(records[0][k]) for k in FIELDS):
                        raise ValueError('Repetição da mesma semente produziu resultado diferente')
                print(f'OK {i}/{len(plan)} {folder.name}: {row["red_score"]} x {row["blue_score"]}', flush=True)
            except (OSError, RuntimeError, ValueError) as exc:
                row.update(status='erro', erro=str(exc))
                failures += 1
                print(f'ERRO {i}/{len(plan)}: {exc}', flush=True)
            writer.writerow(row)
            stream.flush()
            records.append(row)
    summaries(records, destination/'resumo.csv')
    manifest.update(fim=datetime.now().astimezone().isoformat(), falhas=failures)
    (destination/'manifesto.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Resultados: {destination} ({failures} falhas)', flush=True)
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(main())
