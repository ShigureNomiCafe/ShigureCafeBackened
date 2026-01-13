package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.repository.TokenBlacklistRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final TokenBlacklistRepository tokenBlacklistRepository;

    // 每天执行一次清理
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        tokenBlacklistRepository.deleteByExpirationDateBefore(System.currentTimeMillis());
    }
}
