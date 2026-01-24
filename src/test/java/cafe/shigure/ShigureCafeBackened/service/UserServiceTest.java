package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.dto.MinecraftWhitelistResponse;
import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.model.UserAudit;
import cafe.shigure.ShigureCafeBackened.model.UserStatus;
import cafe.shigure.ShigureCafeBackened.repository.TokenBlacklistRepository;
import cafe.shigure.ShigureCafeBackened.repository.UserAuditRepository;
import cafe.shigure.ShigureCafeBackened.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAuditRepository userAuditRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;
    @Mock
    private CacheService cacheService;
    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void sendVerificationCode_shouldThrowException_whenRateLimited() {
        String email = "test@example.com";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        doThrow(new BusinessException("RATE_LIMIT_EXCEEDED"))
            .when(rateLimitService).checkRateLimit(eq("verify:" + email), anyLong());

        assertThrows(BusinessException.class, () -> userService.sendVerificationCode(email, "REGISTER"));
        
        verify(emailService, never()).sendSimpleMessage(any(), any(), any());
    }

    @Test
    void sendVerificationCode_shouldSend_whenCooldownPassed() {
        String email = "test@example.com";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        userService.sendVerificationCode(email, "REGISTER");

        verify(rateLimitService).checkRateLimit(eq("verify:" + email), anyLong());
        verify(emailService).sendSimpleMessage(eq(email), any(), any());
        verify(valueOperations).set(eq("verify:code:" + email), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }
    
    @Test
    void sendVerificationCode_shouldSend_whenNoPriorCode() {
        String email = "new@example.com";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        userService.sendVerificationCode(email, "REGISTER");

        verify(rateLimitService).checkRateLimit(eq("verify:" + email), anyLong());
        verify(emailService).sendSimpleMessage(eq(email), any(), any());
        verify(valueOperations).set(eq("verify:code:" + email), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void getMinecraftWhitelist_shouldReturnList() {
        User user1 = new User();
        user1.setMinecraftUsername("Player1");
        user1.setMinecraftUuid("12345678-1234-1234-1234-123456789012");
        user1.setStatus(UserStatus.ACTIVE);

        User user2 = new User();
        user2.setMinecraftUsername("Player2");
        user2.setMinecraftUuid("abcdef1234567890abcdef1234567890"); // No hyphens
        user2.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByStatusAndMinecraftUuidIsNotNullAndMinecraftUsernameIsNotNull(UserStatus.ACTIVE))
                .thenReturn(List.of(user1, user2));

        var result = userService.getMinecraftWhitelist();

        assertEquals(2, result.size());

        // Check first user (already hyphenated)
        assertEquals("Player1", result.get(0).name());
        assertEquals("12345678-1234-1234-1234-123456789012", result.get(0).uuid());

        // Check second user (needs formatting)
        assertEquals("Player2", result.get(1).name());
        assertEquals("abcdef12-3456-7890-abcd-ef1234567890", result.get(1).uuid());
    }

    @Test
    void approveUser_shouldSetAuditToNullAndDeleteAudit() {
        String auditCode = "test-audit-code";
        User user = new User();
        user.setStatus(UserStatus.PENDING);
        
        UserAudit audit = new UserAudit();
        audit.setAuditCode(auditCode);
        audit.setUser(user);
        audit.setExpiryDate(System.currentTimeMillis() + 10000);
        user.setAudit(audit);

        when(userAuditRepository.findByAuditCode(auditCode)).thenReturn(Optional.of(audit));

        userService.approveUser(auditCode);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getAudit());
        verify(userRepository).save(user);
        verify(userAuditRepository).delete(audit);
        verify(cacheService).updateTimestamp(CacheService.USER_LIST_KEY);
        verify(cacheService).updateTimestamp(CacheService.AUDIT_LIST_KEY);
    }

    @Test
    void banUser_shouldSetAuditToNullAndDeleteAudit() {
        String auditCode = "test-audit-code";
        User user = new User();
        user.setStatus(UserStatus.PENDING);

        UserAudit audit = new UserAudit();
        audit.setAuditCode(auditCode);
        audit.setUser(user);
        user.setAudit(audit);

        when(userAuditRepository.findByAuditCode(auditCode)).thenReturn(Optional.of(audit));

        userService.banUser(auditCode);

        assertEquals(UserStatus.BANNED, user.getStatus());
        assertNull(user.getAudit());
        verify(userRepository).save(user);
        verify(userAuditRepository).delete(audit);
        verify(cacheService).updateTimestamp(CacheService.USER_LIST_KEY);
        verify(cacheService).updateTimestamp(CacheService.AUDIT_LIST_KEY);
    }
}

    