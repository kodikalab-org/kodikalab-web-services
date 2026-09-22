package com.kodika.kodikalab.repository;

import com.kodika.kodikalab.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
}
