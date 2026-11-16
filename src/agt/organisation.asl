+!jcm::initial_roles([],_).
+!jcm::initial_roles([H|T],Try) <- !jcm::initial_roles(H,Try); !jcm::initial_roles(T,Try).
-!jcm::initial_roles(Role,Try) : Try > 0 <- .wait(100); !jcm::initial_roles(Role,Try-1).

+!mission_ready : schemes(L) & .member(expedition,L) <- true.
+!mission_ready <- .wait(10); !mission_ready.
