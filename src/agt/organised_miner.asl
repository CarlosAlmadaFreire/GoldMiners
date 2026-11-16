{ include("organisation.asl") }


// Role adoption is performed by the JCM launcher; missions are committed here.
+!jcm::initial_roles(role(O,G,scout),Try)
    <- .concat("/main/",O,Full); joinWorkspace(Full,W);
       lookupArtifact(G,Group)[wid(W)]; focus(Group)[wid(W)];
       adoptRole(scout)[artifact_id(Group)];
       !mission_ready;
       lookupArtifact(expedition,Scheme)[wid(W)]; focus(Scheme)[wid(W)];
       commitMission(survey)[artifact_id(Scheme)];
       +org_ready(Group,Scheme,scout); organisation_ready.
+!jcm::initial_roles(role(O,G,miner),Try)
    <- .concat("/main/",O,Full); joinWorkspace(Full,W);
       lookupArtifact(G,Group)[wid(W)]; focus(Group)[wid(W)];
       adoptRole(miner)[artifact_id(Group)];
       !mission_ready;
       lookupArtifact(expedition,Scheme)[wid(W)]; focus(Scheme)[wid(W)];
       commitMission(mining_work)[artifact_id(Scheme)];
       +org_ready(Group,Scheme,miner); organisation_ready.

+!before_action : org_ready(Group,Scheme,scout) & duty(miner)
    <- goalAchieved(explore)[artifact_id(Scheme)];
       leaveMission(survey)[artifact_id(Scheme)];
       leaveRole(scout)[artifact_id(Group)]; adoptRole(miner)[artifact_id(Group)];
       commitMission(mining_work)[artifact_id(Scheme)];
       -org_ready(Group,Scheme,scout); +org_ready(Group,Scheme,miner); .print("Scout assumiu papel miner.").
{ include("competitor.asl") }
