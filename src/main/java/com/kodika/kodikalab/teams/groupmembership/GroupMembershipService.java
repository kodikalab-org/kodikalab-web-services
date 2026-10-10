package com.kodika.kodikalab.teams.groupmembership;

import java.util.List;

public interface GroupMembershipService {

    // US05: Solicitar ingreso o ingresar directamente
    GroupMembership requestJoin(
            Integer groupId,
            String invitationCode
    );

    // US06: Consultar solicitudes pendientes
    List<GroupMembership> getPendingRequests(Integer groupId);

    // US06: Aceptar o rechazar una solicitud
    GroupMembership reviewRequest(
            Integer groupId,
            Integer membershipId,
            boolean accept
    );
}
