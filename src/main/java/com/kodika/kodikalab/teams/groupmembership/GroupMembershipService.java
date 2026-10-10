package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.util.List;
import java.util.Optional;

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

    // Consulta de integrantes del equipo (ranking y reportes de analytics)
    List<GroupMemberData> findMembersByTeamId(Integer teamId);

    // Membresía propia con bloqueo, para registrar resoluciones (US-14)
    Optional<GroupMembership> findForUpdate(Integer teamId, Integer userId);
}
