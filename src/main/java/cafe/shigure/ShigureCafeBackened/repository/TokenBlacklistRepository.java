package cafe.shigure.ShigureCafeBackened.repository;

import cafe.shigure.ShigureCafeBackened.model.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {
    boolean existsByToken(String token);
    void deleteByExpirationDateBefore(long now);
}
