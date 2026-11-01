package com.docket.app.repository;

import com.docket.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    // A database-side increment, so two simultaneous bumps can't cancel each other out.
    // Clearing stops anything in this transaction reading a stale version. Flushing first
    // matters just as much: clearing would otherwise silently discard unsaved work,
    // such as an audit event recorded a moment earlier.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.id = :userId")
    int incrementTokenVersion(@Param("userId") UUID userId);
}
