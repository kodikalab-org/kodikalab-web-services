package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from GroupMembership m where m.group.id = :teamId and m.practitioner.userId = :userId")
    Optional<GroupMembership> findForUpdate(@Param("teamId") Integer teamId, @Param("userId") Integer userId);

    @Query("""
            select new com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData(
                m.id, g.id, u.id, u.fullName, m.status)
            from GroupMembership m
            left join m.group g
            left join m.practitioner p
            left join p.user u
            where g.id = :teamId
            order by m.id
            """)
    List<GroupMemberData> findMembersByTeamId(@Param("teamId") Integer teamId);
}
