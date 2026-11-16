package contest;

import cartago.*;
import jason.asSyntax.*;
import java.util.*;

/** Contract Net: CFP -> proposals -> lowest cost award, once each round. */
public class Auction extends Artifact {
    int team;
    int auctionRound=-1;
    boolean orgReady;
    final Map<Integer,Map<Game.Pos,Double>> proposals=new HashMap<>();
    void init(String config,int team) throws Exception{RuntimeMatch.open(config);this.team=team;}
    @OPERATION void organisation_ready(){orgReady=true;}
    @OPERATION void participants(OpFeedbackParam<Term> names){
        ListTermImpl list=new ListTermImpl();for(int i=0;i<RuntimeMatch.game.config.agents;i++)list.add(new Atom(RuntimeMatch.name(team*RuntimeMatch.game.config.agents+i)));names.set(list);
    }
    @OPERATION void start_auction(int round,OpFeedbackParam<Boolean> active){
        while(RuntimeMatch.registered.size()<RuntimeMatch.game.positions.length)await_time(5);
        String v=RuntimeMatch.game.config.variants[team];
        if(v.equals("D")){
            while(!orgReady||java.util.stream.IntStream.range(team*RuntimeMatch.game.config.agents,(team+1)*RuntimeMatch.game.config.agents).anyMatch(i->!RuntimeMatch.orgReady.contains(i)))await_time(5);
        }
        active.set(v.equals("C")||v.equals("D"));
        auctionRound=round;proposals.clear();
        if(v.equals("C")||v.equals("D"))RuntimeMatch.game.messages[team]+=RuntimeMatch.game.config.agents;
    }
    @OPERATION void proposal(int round,String name,String text,OpFeedbackParam<Boolean> complete) throws Exception{
        int id=RuntimeMatch.id(name);
        if(RuntimeMatch.game.team(id)!=team||round!=auctionRound||proposals.containsKey(id)){complete.set(false);return;}
        Map<Game.Pos,Double> offers=new LinkedHashMap<>();
        for(Term term:ASSyntax.parseList(text)){
            Structure b=(Structure)term;
            Game.Pos pos=new Game.Pos((int)((NumberTerm)b.getTerm(0)).solve(),(int)((NumberTerm)b.getTerm(1)).solve());
            double cost=((NumberTerm)b.getTerm(2)).solve();
            if(Double.isFinite(cost)&&cost>=0)offers.put(pos,cost);
        }
        proposals.put(id,offers);RuntimeMatch.game.messages[team]++;
        boolean done=proposals.size()==RuntimeMatch.game.config.agents;
        if(done){RuntimeMatch.strategy.allocate(team,proposals);RuntimeMatch.game.messages[team]+=RuntimeMatch.game.config.agents;}
        complete.set(done);
    }
    @OPERATION void next_round(int previous,OpFeedbackParam<Integer> round,OpFeedbackParam<Boolean> ended){
        while(RuntimeMatch.game.round()==previous&&!RuntimeMatch.game.ended())await_time(5);
        round.set(RuntimeMatch.game.round());ended.set(RuntimeMatch.game.ended());
    }
}
