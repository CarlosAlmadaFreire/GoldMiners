# Gold Miners — JaCaMo

Implementação do projeto MAPC e do relatório preliminar: dois times Jason disputam
minerais em um grid, com artefatos CArtAgO e uma variante organizacional Moise.
O tutorial original permanece em `src/env/mining`, `miner.asl` e `tutorial.jcm`.
A simulação competitiva usa `src/env/contest` e roda sem interface gráfica.

## Docker e experimentos

A estrutura Docker segue os projetos de Contrato de Agentes e Leilão Japonês.
Não é necessário instalar Java ou Gradle no host:

```bash
./executar.sh
./executar-experimentos.sh --validar
./executar-experimentos.sh
```

O último comando executa 84 partidas com A–D, dois cenários, duas explorações,
três sementes e troca dos lados. Logs, parâmetros, métricas e resumo estatístico
ficam em subpastas datadas de `resultados/`. Veja [COMO-EXECUTAR.md](COMO-EXECUTAR.md)
para parâmetros, validações e descrição dos arquivos exportados.

## Executar sem Docker

Requer **JDK 21 completo** (incluindo `javac`), Python 3 para a bateria de partidas
e acesso à internet na primeira resolução das dependências. Configure `JAVA_HOME`
se o Java padrão for somente um JRE.

```bash
./gradlew test
./gradlew run
./gradlew run -Pred=C -Pblue=B -Pseed=43 -Psteps=300
./gradlew run -Pred=D -Pblue=A
./gradlew run -Pconfig=config/small.properties
./gradlew run -Pred=D -Pblue=D -Pagents=3
```

O launcher compartilhado por Gradle e pela bateria gera `build/match.jcm` com os agentes, artefatos e organizações necessários.
O resultado fica em `build/results/match.csv`; altere com `-Poutput=caminho.csv`.
O processo termina automaticamente depois de salvar o resultado. Cada processo
executa uma partida, evitando estado estático herdado de partidas anteriores.

Os parâmetros do cenário estão em `config/default.properties`: largura, altura,
agentes por time, capacidade, rodadas, minerais, obstáculos, valores de ouro/prata/
bronze, depósitos, semente, exploração e duração das reservas. `-Pred`, `-Pblue`,
`-Pseed`, `-Psteps`, `-Pagents` e `-Pexploration` sobrescrevem os respectivos valores.
Use `-Pconfig=arquivo.properties` para selecionar outro cenário.
A geração valida espaço disponível e mantém conectadas todas as células livres.

## Variantes implementadas

| Variante | Conhecimento e alocação |
| --- | --- |
| A | Mapa privado por agente; sem reservas ou negociação. |
| B | TeamMap por time e reservas atômicas no ClaimBoard. |
| C | B mais Contract Net: coordinator anuncia, miners enviam lances, coordinator atribui alvos. |
| D | C mais grupos, papéis e missões Moise; scout passa a miner com 85% do mapa conhecido. |

Em D, o primeiro agente é scout e os demais são miners. O coordinator assume a
missão de alocação. O scout assume `survey`, depois sai dessa missão, troca o
papel e assume `mining_work`. As missões são de manutenção: a partida exige
explorar, alocar, coletar e entregar repetidamente, em paralelo. As normas concedem
permissão de compromisso conforme o papel. Com um único agente por time, ele inicia como scout e assume a mineração após
a exploração; a cardinalidade mínima de miners permite essa configuração.

A política calcula valor por custo de caminho até o mineral e até o depósito mais
próximo, favorece minerais próximos de adversários observados recentemente e
rejeita trajetos que não permitem entrega antes do limite. Coleta no caminho se
houver capacidade e reserva disponível. O scout de D prioriza exploração até a
transição de papel. Há exploração por fronteira próxima e por setores verticais.
A divisão é uma preferência; agentes exploram fora do setor quando necessário.

O planejamento usa A* sobre obstáculos conhecidos e ocupação percebida. Células
desconhecidas são tratadas como transitáveis; o agente replaneja quando percebe
um obstáculo. `jia.astar` também disponibiliza esse algoritmo como ação interna
Jason, recebendo explicitamente o mapa conhecido. O suporte de estratégia Java
é chamado pelos artefatos privados; os planos Jason conduzem o ciclo de ação e
a negociação. O algoritmo genético, outra AOP e o conselheiro LLM são extensões
opcionais do relatório e não estão implementados.

## Regras da simulação

- Cada minerador submete exatamente uma ação por rodada: mover em quatro direções,
  `pick`, `drop` ou `skip`. A rodada só avança depois de todas as submissões.
- A resolução segue ordem circular dos IDs, começando em `rodada % totalAgentes`.
  A ordem de chegada das threads não altera o resultado. Movimentos são resolvidos
  sequencialmente nessa ordem; trocas simultâneas de posição não são permitidas.
- Uma célula comporta um agente. Limites, obstáculos e ocupação bloqueiam movimento.
  Uma célula de mineral contém um item; a coleta é atômica. Quando aplicável,
  concorrentes posteriores recebem `missing`. O teste do árbitro exercita também
  a coleta concorrente diretamente.
- A capacidade conta itens, não valor. `drop` em qualquer depósito entrega toda
  a carga e credita seu valor ao time. Fora do depósito a ação é recusada.
- A percepção contém somente a janela 3×3 centrada no agente, inclusive células
  vazias, para remover minerais desaparecidos do mapa. Depósitos e dimensões são
  conhecidos desde o início. Nenhum time recebe o mapa do adversário.
- O fim ocorre no limite de rodadas ou depois da entrega de todos os itens.
  Carga ainda em trânsito no limite não pontua.
- Para eliminar influência da ordem das threads na política, as observações da
  rodada são incorporadas juntas e as decisões do time usam ordem circular fixa.
  Na variante A cada agente continua com seu próprio mapa.

É uma adaptação local das regras descritas nos documentos, não um cliente do
servidor oficial MAPC 2006. Não foram adicionadas falhas aleatórias de ação ou
outras regras não especificadas no relatório.

## Avaliação

Bateria completa com os agentes reais, configurações preservadas e troca de lados:

```bash
./executar-experimentos.sh --repeticoes 5
./executar-experimentos.sh --repeticoes 3 --todos-confrontos
```

As saídas são `resultados/<execucao>/metricas.csv` e `resumo.csv`, acompanhadas de
manifesto, logs e configurações por partida. Há timeout, validação das métricas
e detecção de falhas de agentes. O executor retorna código 1 se houver falhas.
O script antigo `scripts/experiments.py` continua disponível para compatibilidade;
use `experimentos.py` para a bateria com proveniência e resumo estatístico.

Para uma execução rápida somente das políticas Java:

```bash
./gradlew experiment -Pseeds=5
```

Esse segundo comando compara A/B/C contra A nas duas explorações. Ele não executa
Jason nem Moise: a contagem de mensagens é o custo teórico do protocolo (três
mensagens por minerador por rodada), e não deve ser usada para medir a integração.
Use a bateria de partidas reais para comparar as tecnologias e a variante D.

O CSV registra semente, variantes, exploração, rodadas, placares, vencedor,
rodada em que o último mineral saiu do mapa (`-1` se isso não ocorreu), mensagens
CFP/proposta/resultado por time, destinos minerais duplicados por rodada e minerais reservados como alvo que
foram coletados pelo adversário. Cada mineral perdido conta uma vez por time,
quando o desaparecimento é observado ou uma tentativa de coleta falha. Empates são preservados. Como agentes não podem compartilhar
células, disputas normalmente ocorrem por bloqueio de acesso e antecipação da coleta.

Os testes verificam a barreira de rodadas, coleta atômica, capacidade, depósitos,
valores, percepção local, obstáculos, término, reservas, remoção de informação
obsoleta, A*, conservação de valor e reprodução por semente.

Referências de implementação: [configuração JCM](https://jacamo-lang.github.io/jacamo/jcm.html)
e APIs Moise/CArtAgO distribuídas com JaCaMo 1.3.0.
