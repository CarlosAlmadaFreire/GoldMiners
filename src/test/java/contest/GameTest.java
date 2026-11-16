package contest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class GameTest {
    Config config(String... kv){Properties p=new Properties();p.setProperty("width","8");p.setProperty("height","8");p.setProperty("agents","1");p.setProperty("minerals","3");p.setProperty("obstacles","0");for(int i=0;i<kv.length;i+=2)p.setProperty(kv[i],kv[i+1]);return new Config(p);}
    void round(Game g,String a,String b){int r=g.round();g.submit(0,r,a);g.submit(1,r,b);}
    @Test void roundWaitsForBothTeamsAndRejectsDuplicates(){Game g=new Game(config());g.submit(0,0,"skip");assertEquals(0,g.round());assertThrows(IllegalStateException.class,()->g.submit(0,0,"skip"));g.submit(1,0,"skip");assertEquals(1,g.round());}
    @Test void capacityAndDepositsAndMineralValues(){
        Game g=new Game(config("capacity","2"));Game.Pos p=new Game.Pos(3,3);g.positions[0]=p;
        for(int i=0;i<3;i++){g.ores.put(p,new Game.Ore("gold",10));round(g,"pick","skip");}
        assertEquals(2,g.cargo.get(0).size());assertEquals("full",g.outcomes[0]);
        round(g,"drop","skip");assertEquals("not_at_depot",g.outcomes[0]);assertEquals(2,g.cargo.get(0).size());
        g.positions[0]=g.config.depots.get(1);round(g,"drop","skip");assertEquals(20,g.scores[0]);assertTrue(g.cargo.get(0).isEmpty());
    }
    @Test void contestedPickHasOneWinnerIndependentOfSubmissionOrder(){
        Game a=new Game(config()),b=new Game(config());
        for(Game g:List.of(a,b)){g.positions[0]=new Game.Pos(3,3);g.positions[1]=new Game.Pos(3,3);g.ores.put(g.positions[0],new Game.Ore("silver",5));}
        a.submit(0,0,"pick");a.submit(1,0,"pick");b.submit(1,0,"pick");b.submit(0,0,"pick");
        assertEquals(a.cargo,b.cargo);assertEquals(1,a.cargo.get(0).size());assertEquals("missing",a.outcomes[1]);assertEquals(1,a.stolen[1]);
    }
    @Test void perceptionIsLimitedAndEmptyCellsAreReported(){Game g=new Game(config());g.positions[0]=new Game.Pos(3,3);var v=g.view(0);assertEquals(9,v.cells().size());assertTrue(v.cells().stream().allMatch(c->Math.abs(c.pos().x()-3)<=1&&Math.abs(c.pos().y()-3)<=1));}
    @Test void blockedMovementAndTimeLimit(){Game g=new Game(config("steps","2"));g.positions[0]=new Game.Pos(0,0);round(g,"left","skip");assertEquals("blocked",g.outcomes[0]);round(g,"skip","skip");assertTrue(g.ended());}
    @Test void claimsAreAtomicExpireAndRespectOwner(){var b=new ClaimBoard.Board();var p=new Game.Pos(2,2);assertTrue(b.reserve(p,0,0,3));assertFalse(b.reserve(p,1,1,3));b.release(1);assertFalse(b.available(p,1,2));assertTrue(b.reserve(p,1,3,3));}
    @Test void aStarRoutesAroundKnownWalls(){Game g=new Game(config());Map<Game.Pos,Game.Cell> known=new HashMap<>();var wall=new Game.Pos(2,1);known.put(wall,new Game.Cell(wall,true,null,false,-1));var route=Planner.path(g,known,new Game.Pos(1,1),new Game.Pos(3,1),Set.of());assertEquals(4,route.size());assertFalse(route.contains(wall));}
    @Test void observationsRemoveStaleMinerals(){Game g=new Game(config());var k=new TeamMap.Knowledge();g.positions[0]=new Game.Pos(3,3);g.ores.put(g.positions[0],new Game.Ore("gold",10));k.observe(0,g.view(0),g);round(g,"pick","skip");k.observe(0,g.view(0),g);assertNull(k.cells.get(g.positions[0]).ore());}
    @Test void seedsReproduceResultsAndCargoIsConserved(){
        Config c=config("red","C","steps","60");Game a=Experiment.play(c),b=Experiment.play(c);
        assertEquals(a.csv(),b.csv());int initial=new Game(c).ores.values().stream().mapToInt(Game.Ore::value).sum();
        int remaining=a.ores.values().stream().mapToInt(Game.Ore::value).sum()+a.cargo.stream().flatMap(List::stream).mapToInt(Game.Ore::value).sum();
        assertEquals(initial,remaining+a.scores[0]+a.scores[1]);
        assertTrue(a.scores[0]>=0);assertTrue(a.scores[1]>=0);assertEquals(a.positions.length,Arrays.stream(a.positions).distinct().count());
    }
    @Test void jasonInternalActionUsesOnlyProvidedWalls() throws Exception {
        var output=new jason.asSyntax.VarTerm("D");
        var unifier=new jason.asSemantics.Unifier();
        jason.asSyntax.Term[] terms={jason.asSyntax.ASSyntax.createNumber(8),jason.asSyntax.ASSyntax.createNumber(8),
            jason.asSyntax.ASSyntax.createNumber(1),jason.asSyntax.ASSyntax.createNumber(1),
            jason.asSyntax.ASSyntax.createNumber(3),jason.asSyntax.ASSyntax.createNumber(1),
            jason.asSyntax.ASSyntax.parseList("[cell(2,1,obstacle,0,-1)]"),output};
        assertEquals(true,new jia.astar().execute(null,unifier,terms));
        assertTrue(Set.of("up","down").contains(unifier.get("D").toString()));
    }
    @Test void baselineKeepsPrivateMapsWhileBOnlySharesWithItsTeam(){
        for(String variant:List.of("A","B")){
            Game g=new Game(config("agents","2","red",variant));
            g.positions[0]=new Game.Pos(0,0);g.positions[1]=new Game.Pos(7,7);
            g.positions[2]=new Game.Pos(0,7);g.positions[3]=new Game.Pos(7,0);
            Strategy s=new Strategy(g,new TeamMap.Knowledge[]{new TeamMap.Knowledge(),new TeamMap.Knowledge()},new ClaimBoard.Board[]{new ClaimBoard.Board(),new ClaimBoard.Board()});
            s.observe(0);
            assertEquals(variant.equals("B"),s.knowledge(0).cells.containsKey(new Game.Pos(7,7)));
            assertFalse(s.knowledge(0).cells.containsKey(new Game.Pos(0,7)));
        }
    }
    @Test void auctionAssignsLowestBidAndUniqueTargets(){
        Game g=new Game(config("agents","2","red","C"));
        Strategy s=new Strategy(g,new TeamMap.Knowledge[]{new TeamMap.Knowledge(),new TeamMap.Knowledge()},new ClaimBoard.Board[]{new ClaimBoard.Board(),new ClaimBoard.Board()});
        var p=new Game.Pos(3,3);var q=new Game.Pos(4,4);
        s.allocate(0,Map.of(0,Map.of(p,3.0,q,4.0),1,Map.of(p,1.0,q,5.0)));
        assertEquals(p,s.targets[1]);assertEquals(q,s.targets[0]);
    }
    @Test void emptyScenarioEndsImmediately(){Game g=new Game(config("minerals","0"));assertTrue(g.ended());assertEquals(0,g.emptied);}
    @Test void rejectsImpossibleParameters(){assertThrows(IllegalArgumentException.class,()->config("capacity","0"));assertThrows(IllegalArgumentException.class,()->config("depots","9,9"));}
}
