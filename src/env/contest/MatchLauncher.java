package contest;

import java.nio.file.*;

/** Shared launcher for Gradle, Docker and experiment subprocesses. */
public final class MatchLauncher {
    static String project(Config config, String scenario) {
        String escaped = scenario.replace("\\", "\\\\").replace("\"", "\\\"");
        StringBuilder text = new StringBuilder("mas gold_miners {\n");
        String[] teams = {"red", "blue"};
        for (int t = 0; t < 2; t++) {
            String team = teams[t];
            boolean org = config.variants[t].equals("D");
            for (int i = 1; i <= config.agents; i++) {
                text.append("    agent %s%d : %s.asl {\n        focus: arena.%s%dbody\n    }\n".formatted(
                        team, i, org ? "organised_miner" : "competitor", team, i));
            }
            text.append("    agent %scoordinator : %s.asl {\n        focus: arena.%sauction\n    }\n".formatted(
                    team, org ? "organised_coordinator" : "coordinator", team));
        }
        text.append("    workspace arena {\n");
        for (int t = 0; t < 2; t++) {
            String team = teams[t];
            for (int i = 1; i <= config.agents; i++) {
                text.append("        artifact %s%dbody: contest.MinerBody(\"%s\", %d)\n".formatted(
                        team, i, escaped, t * config.agents + i - 1));
            }
            text.append("        artifact %smap: contest.TeamMap(\"%s\", %d)\n".formatted(team, escaped, t));
            text.append("        artifact %sclaims: contest.ClaimBoard(\"%s\", %d)\n".formatted(team, escaped, t));
            text.append("        artifact %sauction: contest.Auction(\"%s\", %d)\n".formatted(team, escaped, t));
        }
        text.append("    }\n");
        for (int t = 0; t < 2; t++) {
            if (!config.variants[t].equals("D")) continue;
            String team = teams[t];
            text.append("    organisation %sorg : mining.xml {\n        group %steam : team {\n            responsible-for: expedition\n            players: %s1 scout\n".formatted(team, team, team));
            for (int i = 2; i <= config.agents; i++) text.append("                     %s%d miner\n".formatted(team, i));
            text.append("                     %scoordinator coordinator\n        }\n        scheme expedition : mining\n    }\n".formatted(team));
        }
        return text.append("    asl-path: \"src/agt\"\n    org-path: \"src/org\"\n}\n").toString();
    }
    public static void main(String[] args) throws Exception {
        String scenario = args.length > 0 ? args[0] : "config/default.properties";
        Config config = Config.load(scenario);
        Path generated = Path.of(System.getProperty("contest.project", "build/match.jcm"));
        Files.createDirectories(generated.toAbsolutePath().getParent());
        Files.createDirectories(Path.of("log"));
        Files.writeString(generated, project(config, scenario));
        jacamo.infra.JaCaMoLauncher.main(new String[]{generated.toString()});
    }
}
