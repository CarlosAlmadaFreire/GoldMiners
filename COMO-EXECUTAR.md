# Execução com Docker — Gold Miners

A estrutura segue os projetos `contrato-agentes-alex-carlos-guilherme` e
`leilao-japones`: serviço Compose `simulacao`, cache persistente do Gradle,
execução sem GUI e resultados em diretórios datados. A imagem contém JDK 21,
Gradle 8.13 e Python 3. O host precisa apenas de Docker com Compose.

## Uma partida

Na pasta do Gold Miners:

```bash
./executar.sh
```

O script constrói a imagem e executa uma partida B contra A. A imagem e as
bibliotecas ficam em cache. O resultado é salvo em `resultados/partida.csv`.
Para escolher as estratégias e o cenário:

```bash
./executar.sh gradle run -Pred=D -Pblue=A -Pseed=42 -Pconfig=config/small.properties -Poutput=resultados/minha-partida.csv --no-daemon --console=plain
```

As variantes são A (mapa privado), B (artefatos compartilhados), C (Contract Net)
e D (organização Moise). Também é possível alterar `-Psteps`, `-Pagents` e
`-Pexploration=frontier|sectors`. Cada `.properties` configura dimensões,
minerais, obstáculos, depósitos, capacidade, valores e duração das reservas.

Os scripts ajustam o UID/GID da imagem ao usuário do host para que os arquivos
exportados pertençam a esse usuário. O código é copiado para a imagem; os
resultados são montados em `/app/resultados`. Ao mudar o código, os scripts
reconstroem a imagem usando o cache das camadas anteriores.

Para usar Compose diretamente, configure os mesmos IDs e crie o diretório:

```bash
export LOCAL_UID="$(id -u)" LOCAL_GID="$(id -g)"
mkdir -p resultados
docker compose up --build --abort-on-container-exit --exit-code-from simulacao
docker compose down
```

O volume `gradle-cache` preserva as dependências entre execuções. As portas dos
inspectores JaCaMo não são publicadas no host.

## Validar a integração

```bash
./executar-experimentos.sh --validar
```

Executa oito partidas curtas no cenário pequeno: A–A, B–A, A–B, C–A, A–C,
D–A, A–D e uma repetição de A–A com a mesma semente. Verifica placares,
vencedor, limite de rodadas, mensagens do protocolo, marcador de término e
reprodução das métricas. Falhas de agentes, exceções, erros normativos,
timeouts e resultados ausentes tornam a execução inválida. A repetição de
semente compara métricas da partida; o tempo de processo pode variar.

Para executar os testes automatizados:

```bash
./executar.sh gradle test --no-daemon --console=plain
./executar.sh python3 -m unittest discover -s tests -v
```

## Bateria de experimentos

```bash
./executar-experimentos.sh
```

A matriz padrão contém **84 partidas**:

- dois cenários: `small.properties` e `default.properties`;
- estratégias A–D contra A, com troca dos lados: sete confrontos únicos;
- exploração por fronteira e por setores;
- três sementes: 42, 43 e 44.

Cada repetição muda a semente; os confrontos usam as mesmas sementes para
permitir comparação. A–A é executado uma vez por combinação de cenário,
exploração e semente. A compilação ocorre uma única vez, pela tarefa
`preparar`; cada partida inicia uma JVM independente, reutilizando o classpath.
As partidas são sequenciais para não disputar portas dos inspectores JaCaMo.

Exemplos:

```bash
# Inspecionar a matriz sem executar partidas
./executar-experimentos.sh --planejar

# Um cenário, duas sementes, limite de 100 rodadas
./executar-experimentos.sh --cenarios config/small.properties --repeticoes 2 --passos 100

# Todos os 16 confrontos ordenados entre A, B, C e D
./executar-experimentos.sh --todos-confrontos --repeticoes 1

# Selecionar variantes, exploração, primeira semente e timeout por partida
./executar-experimentos.sh --variantes B D --exploracoes frontier --semente 100 --timeout 240
```

`--variantes` seleciona os times avaliados contra A. Para confrontos entre essas
variantes, use também `--todos-confrontos`. O modo `--validar` usa sua própria
matriz fixa; `--passos` pode alterar sua duração.

## Arquivos exportados

Cada execução cria `resultados/AAAAMMDD-HHMMSS-microssegundos-experimentos/`
ou uma pasta com sufixo `-validacao`:

| Arquivo | Conteúdo |
| --- | --- |
| `metricas.csv` | Uma linha por partida, inclusive falhas, com cenário, semente, estratégias, placares, mensagens, duplicações, perdas e tempo. |
| `resumo.csv` | Médias, desvio padrão amostral do valor, diferença de placar, vitórias, empates e derrotas por cenário, exploração, variante e adversário. |
| `manifesto.json` | Datas, versão Java, sementes, número de partidas, falhas e hashes dos fontes usados. |
| `0001-.../cenario.properties` | Parâmetros exatos da partida. |
| `0001-.../match.jcm` | Agentes, artefatos e organizações gerados pelo launcher. |
| `0001-.../comando.json` | Argumentos usados para iniciar a JVM. |
| `0001-.../execucao.log` | Saída completa da simulação. |
| `0001-.../resultado.csv` | Métricas brutas produzidas pelo árbitro. |

O executor continua após uma partida inválida para preservar as demais
observações e retorna código 1 ao fim se houve qualquer falha. O resumo usa
somente partidas válidas. `observacoes_time` conta perspectivas de times:
num confronto A–A, ambos os times entram no mesmo grupo de estatísticas.
Essas observações são pareadas; o desvio padrão é descritivo, não um teste de
significância. O tempo mede a JVM inteira, incluindo inicialização de JaCaMo,
sem incluir compilação ou construção da imagem.

## Sem Docker

Com JDK 21 completo, Python 3.10+ e Gradle ou o wrapper:

```bash
./gradlew run
python3 experimentos.py --validar
```

O executor usa `gradle` quando disponível; caso contrário, usa `./gradlew`.
`GRADLE_CMD` permite escolher outro executável. Essa bateria executa os agentes
Jason, os artefatos CArtAgO e a organização Moise reais. A tarefa
`./gradlew experiment` continua disponível para benchmarks apenas das políticas
Java, sem medir a integração JaCaMo.
