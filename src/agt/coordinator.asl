{ include("$jacamoJar/templates/common-cartago.asl") }
!begin(0).
+!begin(R) <- start_auction(R,Active); !announce(R,Active).
+!announce(R,true) <- participants(Names); .send(Names,tell,cfp(R)).
+!announce(R,false) <- true.
+offer(R,Name,Bids)[source(Name)]
    <- proposal(R,Name,Bids,Complete); -offer(R,Name,Bids); !awards(R,Complete).
+!awards(R,false) <- true.
+!awards(R,true)
    <- participants(Names); .send(Names,tell,award(R));
       next_round(R,Next,Ended); !continue(Next,Ended).
+!continue(R,false) <- !begin(R).
+!continue(R,true) <- true.
