import csv
from pathlib import Path
import tempfile
import unittest

import experimentos as exp


class ExperimentTests(unittest.TestCase):
    def test_default_plan_is_balanced_and_has_no_duplicate_matches(self):
        plan = list(exp.match_plan([Path('small'), Path('default')], list('ABCD'), ['frontier', 'sectors'], 3, 42))
        self.assertEqual(84, len(plan))
        keys = {(str(c['scenario']), c['red'], c['blue'], c['exploration'], c['seed']) for c in plan}
        self.assertEqual(len(plan), len(keys))
        for c in plan:
            reverse = (str(c['scenario']), c['blue'], c['red'], c['exploration'], c['seed'])
            self.assertIn(reverse, keys)

    def test_all_pairs_are_not_duplicated(self):
        plan = list(exp.match_plan([Path('small')], list('ABCD'), ['frontier'], 1, 42, True))
        self.assertEqual(16, len(plan))

    def fixture(self):
        config = dict(seed='42', red='C', blue='A', exploration='frontier', steps='10', agents='2', minerals='20', values='20,6,1')
        row = dict(zip(exp.FIELDS, '42 C A frontier 10 20 6 red -1 60 0 0 1 0 1'.split()))
        output = 'MATCH_RESULT ' + ','.join(row[k] for k in exp.FIELDS) + '[red1] Partida encerrada.\n'
        return config, row, output

    def test_validates_actual_result_marker_and_protocol_cost(self):
        config, row, output = self.fixture()
        exp.validate(row, output, config)
        row['red_messages'] = '0'
        with self.assertRaises(ValueError):
            exp.validate(row, output, config)

    def test_rejects_silent_agent_failure_and_wrong_winner(self):
        config, row, output = self.fixture()
        with self.assertRaises(ValueError):
            exp.validate(row, output + 'No failure event was generated', config)
        row['winner'] = 'blue'
        with self.assertRaises(ValueError):
            exp.validate(row, output, config)

    def test_summary_uses_team_perspective_and_excludes_failed_runs(self):
        _, row, _ = self.fixture()
        row.update(cenario='small', status='ok', tempo_processo_ms='100')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'resumo.csv'
            exp.summaries([row, dict(row, status='erro')], path)
            with path.open() as stream:
                rows = list(csv.DictReader(stream))
        c = next(r for r in rows if r['variante'] == 'C')
        a = next(r for r in rows if r['variante'] == 'A')
        self.assertEqual('1', c['observacoes_time'])
        self.assertEqual('1', c['vitorias'])
        self.assertEqual('1', a['derrotas'])
        self.assertEqual(14.0, float(c['diferenca_media']))


if __name__ == '__main__':
    unittest.main()
