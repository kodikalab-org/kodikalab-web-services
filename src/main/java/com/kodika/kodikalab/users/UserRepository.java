package com.kodika.kodikalab.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Compare-and-set: solo cambia el hash si sigue siendo {@code currentHash} (devuelve 1) y no pisa un cambio concurrente. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.passwordHash = :newHash where u.id = :id and u.passwordHash = :currentHash")
    int updatePasswordHashIfCurrent(@Param("id") Integer id, @Param("currentHash") String currentHash,
                                    @Param("newHash") String newHash);
}
