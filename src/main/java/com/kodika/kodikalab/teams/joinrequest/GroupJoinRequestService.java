package com.kodika.kodikalab.teams.joinrequest;

public interface GroupJoinRequestService {

    // US05 - Solicitar ingreso
    GroupJoinRequest requestJoin(Integer groupId);

    // US06 - Aceptar o rechazar solicitud
    GroupJoinRequest reviewRequest(
            Integer groupId,
            Integer requestId,
            boolean accept
    );
}
