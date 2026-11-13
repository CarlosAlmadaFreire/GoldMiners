# GoldMiners
Gold Miners (MAS)

# GoldMiners

A Multi-Agent System (MAS) developed using **JaCaMo**, **Jason**, and **CArtAgO**, based on the Gold Miners scenario.

## Overview

GoldMiners is a multi-agent simulation in which autonomous agents explore a mining environment. The project uses Jason agents to implement agent behavior and CArtAgO artifacts to interface with the simulated environment.

The system configuration defines five agents:
- **Leader:** monitors and updates the miners' scores.
- **Miner 1:** explores the environment and performs mining activities.
- **Miners 2–4:** configured with the dummy agent implementation.

## Technologies

- Java
- JaCaMo 1.3.0
- Jason (AgentSpeak)
- CArtAgO
- Gradle
- Twitter4J

## Project Structure

```text
GoldMiners/
├── doc/                    # Documentation and project diagrams
├── gradle/                 # Gradle wrapper
├── lib/                    # Additional libraries
├── src/
│   ├── agt/                 # Jason agents and internal actions
│   └── env/                 # CArtAgO environment implementation
├── .gitignore
├── .jcm-deps.gradle
├── build.gradle
├── gold_miners.jcm          # JaCaMo MAS configuration
├── gradlew
├── gradlew.bat
└── README.md
```

## Agent Architecture

### Leader

The leader agent maintains the score of the miners. It listens for gold-dropping events and updates the corresponding agent's score.

### Miner

The miner agent explores the environment by selecting random target positions and navigating through the mining grid. Its behavior uses internal actions for direction calculation, neighborhood checking, and random number generation.

### Dummy

The dummy agent provides a minimal behavior implementation that periodically waits.

## Environment

The mining environment is implemented in Java using CArtAgO.

Main components:

- `MiningPlanet.java`: mining environment artifact.
- `WorldModel.java`: world model.
- `WorldView.java`: environment visualization.

## Documentation

The `doc/` directory contains the original project documentation, Prometheus model, and diagrams.

## Requirements

- Java Development Kit (JDK)
- Internet connection for downloading Gradle dependencies.

## Build

Clone the repository:

```bash
git clone https://github.com/CarlosAlmadaFreire/GoldMiners.git
cd GoldMiners
```

Compile the project:

```bash
./gradlew classes
```

## Run

Launch the JaCaMo application:

```bash
./gradlew run
```

## License

No license has been specified yet. Please contact the repository owner for usage and distribution permissions.
