{ include("$jacamoJar/templates/common-cartago.asl") }

!start.
+!start : finished(true) <- !finish.
+!start : variant(a) | variant(b) <- !turn.
+!start : variant(c) | variant(d) <- true.
+!start <- .wait(50); !start.

// Jason chooses when to request a route and commits one action per round.
+!turn : finished(false) <- !before_action; recommend(Action); execute(Action); !turn.
+!turn : finished(true) <- !finish.

// Each proposal is a Jason message. The coordinator awards after all proposals.
+cfp(R)[source(C)] : finished(false)
    <- .my_name(Name); bid_list(Bids); .term2string(Bids,Text); .send(C,tell,offer(R,Name,Text)); -cfp(R).
+award(R)[source(_)] : finished(false)
    <- !before_action; recommend(Action); execute(Action); -award(R); !check_end.
+!check_end : finished(true) <- !finish.
+!check_end : finished(false) <- true.
+!finish <- .my_name(Name); !shutdown(Name).
+!shutdown(red1) <- .print("Partida encerrada."); .stopMAS.
+!shutdown(Name) : Name \== red1 <- true.
-!turn <- .print("Falha no agente minerador"); .stopMAS.

+!before_action <- true.
