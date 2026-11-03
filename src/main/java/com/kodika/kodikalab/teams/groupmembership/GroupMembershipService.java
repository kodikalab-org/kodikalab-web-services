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

    // US06: Consultar solicitudes por estado (PENDIENTE o ACLARACION_SOLICITADA)
    List<GroupMembership> getRequests(
            Integer groupId,
            MembershipStatus status
    );

    // US06: Aceptar, rechazar o pedir una aclaración al postulante
    GroupMembership reviewRequest(
            Integer groupId,
            Integer membershipId,
            MembershipDecision decision,
            String message
    );

    // US06: El postulante responde la aclaración y la solicitud vuelve a PENDIENTE
    GroupMembership respondClarification(
            Integer groupId,
            Integer membershipId,
            String response
    );

    // Consulta de integrantes del equipo (ranking y reportes de analytics)
    List<GroupMemberData> findMembersByTeamId(Integer teamId);

    // Membresía propia con bloqueo, para registrar resoluciones (US-14)
    Optional<GroupMembership> findForUpdate(Integer teamId, Integer userId);
}
