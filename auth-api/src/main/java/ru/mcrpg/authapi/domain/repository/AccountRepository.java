package ru.mcrpg.authapi.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mcrpg.authapi.domain.entity.AccountEntity;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    boolean existsByUsernameNormalized(String usernameNormalized);

    boolean existsByEmailNormalized(String emailNormalized);

    Optional<AccountEntity> findByUsernameNormalizedOrEmailNormalized(String usernameNormalized, String emailNormalized);
}
