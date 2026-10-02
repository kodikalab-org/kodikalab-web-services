package com.kodika.kodikalab.repository;

import com.kodika.kodikalab.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Historical scaffold. The active repository is in the users module. */
@NoRepositoryBean
@Deprecated(forRemoval = false)
public interface UserRepository extends JpaRepository<User, Long> {
}
