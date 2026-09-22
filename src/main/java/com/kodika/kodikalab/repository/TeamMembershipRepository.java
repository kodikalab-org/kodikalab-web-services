package com.kodika.kodikalab.repository;

import com.kodika.kodikalab.entity.TeamMembership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {
}
