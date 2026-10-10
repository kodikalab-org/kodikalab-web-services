package com.kodika.kodikalab.teams.studygroup;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Integer> {

    List<StudyGroup> findByStatusOrderByIdAsc(GroupStatus status);
}