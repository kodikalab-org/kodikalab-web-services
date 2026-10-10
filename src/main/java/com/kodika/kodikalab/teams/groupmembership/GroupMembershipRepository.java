package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.teams.groupmembership.dto.MembershipCount;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMembershipRepository
        extends JpaRepository<GroupMembership, Integer> {

    Optional<GroupMembership> findByGroupIdAndPractitionerUserId(
            Integer groupId,
            Integer practitionerId
    );

    long countByGroupIdAndStatus(
            Integer groupId,
            MembershipStatus status
    );

    /** Membresías del grupo en un estado, con el practicante y su cuenta ya cargados; la solicitud más antigua primero. */
    @Query("""
            select m from GroupMembership m
            join fetch m.practitioner p
            join fetch p.user
            where m.group.id = :groupId and m.status = :status
            order by m.joinedAt, m.id
            """)
    List<GroupMembership> findByGroupIdAndStatus(
            @Param("groupId") Integer groupId,
            @Param("status") MembershipStatus status
    );

    /** Todas las membresías del practicante (cualquier estado) con su grupo cargado, por grupo. */
    @Query("select m from GroupMembership m join fetch m.group g where m.practitioner.userId = :userId order by g.id")
    List<GroupMembership> findByPractitionerUserIdWithGroup(@Param("userId") Integer userId);

    /** Integrantes activos y solicitudes pendientes de cada grupo, para el resumen del coach. */
    @Query("""
            select new com.kodika.kodikalab.teams.groupmembership.dto.MembershipCount(g.id, m.status, count(m))
            from GroupMembership m
            join m.group g
            where g.id in :groupIds
              and m.status in (com.kodika.kodikalab.teams.groupmembership.MembershipStatus.ACTIVO,
                               com.kodika.kodikalab.teams.groupmembership.MembershipStatus.PENDIENTE)
            group by g.id, m.status
            """)
    List<MembershipCount> countActiveAndPendingByGroupIds(@Param("groupIds") Collection<Integer> groupIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from GroupMembership m where m.group.id = :teamId and m.practitioner.userId = :userId")
    Optional<GroupMembership> findForUpdate(@Param("teamId") Integer teamId, @Param("userId") Integer userId);

    /**
     * Membresías reales del equipo. Filtra por los estados de membresía para que las filas de solicitudes de
     * ingreso (PENDIENTE o RECHAZADO) no se mezclen con integrantes ni alteren el ranking ni los reportes.
     */
    @Query("""
            select new com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData(
                m.id, g.id, u.id, u.fullName, m.status)
            from GroupMembership m
            left join m.group g
            left join m.practitioner p
            left join p.user u
            where g.id = :teamId
              and m.status in (com.kodika.kodikalab.teams.groupmembership.MembershipStatus.ACTIVO,
                               com.kodika.kodikalab.teams.groupmembership.MembershipStatus.RETIRADO,
                               com.kodika.kodikalab.teams.groupmembership.MembershipStatus.EXPULSADO)
            order by m.id
            """)
    List<GroupMemberData> findMembersByTeamId(@Param("teamId") Integer teamId);
}
