package ru.mcrpg.authapi.domain.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mcrpg.authapi.domain.entity.LauncherSessionEntity;

public interface LauncherSessionRepository extends JpaRepository<LauncherSessionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from LauncherSessionEntity session where session.refreshTokenHash = :refreshTokenHash")
    Optional<LauncherSessionEntity> findForUpdateByRefreshTokenHash(@Param("refreshTokenHash") String refreshTokenHash);
}
