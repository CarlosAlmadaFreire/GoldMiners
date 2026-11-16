{ include("organisation.asl") }

+!jcm::initial_roles(role(O,G,coordinator),Try)
    <- .concat("/main/",O,Full); joinWorkspace(Full,W);
       lookupArtifact(G,Group)[wid(W)]; focus(Group)[wid(W)];
       adoptRole(coordinator)[artifact_id(Group)];
       !mission_ready;
       lookupArtifact(expedition,Scheme)[wid(W)]; focus(Scheme)[wid(W)];
       commitMission(allocation)[artifact_id(Scheme)]; organisation_ready.

{ include("coordinator.asl") }
