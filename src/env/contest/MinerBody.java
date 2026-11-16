package contest;

import cartago.*;
import jason.asSyntax.*;

/** Private perception/action artifact; never publishes another agent's view. */
public class MinerBody extends Artifact {
    int id;
    void init(String config,int id) throws Exception {
        RuntimeMatch.open(config);this.id=id;
        synchronized(RuntimeMatch.class){RuntimeMatch.registered.add(id);}
        defineObsProperty("duty",new Atom("scout"));defineObsProperty("round",0);defineObsProperty("pos",0,0);defineObsProperty("load",0);
        defineObsProperty("capacity",RuntimeMatch.game.config.capacity);
        defineObsProperty("variant",new Atom(RuntimeMatch.game.config.variants[RuntimeMatch.game.team(id)].toLowerCase()));
        defineObsProperty("finished",false);defineObsProperty("score",0,0);
        defineObsProperty("outcome",new Atom("ready"));
        RuntimeMatch.finish();refresh();
    }
    private boolean owner(){if(!RuntimeMatch.name(id).equals(getOpUserName())){failed("private_body");return false;}return true;}
    @OPERATION void organisation_ready(){if(owner())RuntimeMatch.orgReady.add(id);}
    @OPERATION void bid_list(OpFeedbackParam<Term> bids){
        if(!owner())return;
        ListTermImpl list=new ListTermImpl();
        RuntimeMatch.strategy.bids(id).forEach((p,cost)->list.add(ASSyntax.createStructure("bid",ASSyntax.createNumber(p.x()),ASSyntax.createNumber(p.y()),ASSyntax.createNumber(cost))));
        bids.set(list);
    }
    @OPERATION void recommend(OpFeedbackParam<Term> action){
        if(!owner())return;
        String variant=RuntimeMatch.game.config.variants[RuntimeMatch.game.team(id)];
        action.set(new Atom(RuntimeMatch.strategy.action(id)));
    }
    @OPERATION void execute(String action) throws Exception {
        if(!owner())return;
        Game g=RuntimeMatch.game;int round=g.round();
        synchronized(g){
            // Count duplicate active destinations once per round, before resolving it.
            int submitted=0;for(String p:g.pending)if(p!=null)submitted++;
            if(submitted==g.positions.length-1)for(int t=0;t<2;t++){
                java.util.Set<Game.Pos> seen=new java.util.HashSet<>();
                for(int i=t*g.config.agents;i<(t+1)*g.config.agents;i++){
                    Game.Pos target=RuntimeMatch.strategy.targets[i];if(target!=null&&!seen.add(target))g.duplicates[t]++;
                }
            }
            g.submit(id,round,action);
        }
        while(g.round()==round&&!g.ended())await_time(5);
        RuntimeMatch.finish();refresh();
    }
    private void refresh(){
        Game g=RuntimeMatch.game;Game.View v=g.view(id);RuntimeMatch.observe(id);
        getObsProperty("duty").updateValue(new Atom(RuntimeMatch.shared[g.team(id)].size()>=g.config.width*g.config.height*0.85?"miner":"scout"));
        getObsProperty("round").updateValue(v.round());getObsProperty("pos").updateValues(v.pos().x(),v.pos().y());
        getObsProperty("load").updateValue(v.cargo().size());getObsProperty("finished").updateValue(v.ended());
        getObsProperty("score").updateValues(v.red(),v.blue());getObsProperty("outcome").updateValue(new Atom(v.result()));
        while(hasObsProperty("visible"))removeObsProperty("visible");
        for(Game.Cell c:v.cells())defineObsProperty("visible",c.pos().x(),c.pos().y(),new Atom(c.obstacle()?"obstacle":c.ore()!=null?c.ore().type():c.depot()?"depot":"empty"),c.ore()==null?0:c.ore().value(),c.occupant());
    }
}
