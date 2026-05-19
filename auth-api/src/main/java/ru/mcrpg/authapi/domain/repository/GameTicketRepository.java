package ru.mcrpg.authapi.domain.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mcrpg.authapi.domain.entity.GameTicketEntity;

public interface GameTicketRepository extends JpaRepository<GameTicketEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from GameTicketEntity ticket where ticket.ticketHash = :ticketHash")
    Optional<GameTicketEntity> findForUpdateByTicketHash(@Param("ticketHash") String ticketHash);
}
