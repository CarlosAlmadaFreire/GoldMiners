#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
exec ./executar.sh python3 experimentos.py "$@"
