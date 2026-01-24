package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void checkRateLimit_shouldAllow_whenNoKey() {
        String key = "test-key";
        when(valueOperations.get("ratelimit:" + key)).thenReturn(null);

        assertDoesNotThrow(() -> rateLimitService.checkRateLimit(key, 1000));
        
        verify(valueOperations).set(eq("ratelimit:" + key), eq("1"), eq(1000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void checkRateLimit_shouldThrow_whenKeyExists() {
        String key = "test-key";
        when(valueOperations.get("ratelimit:" + key)).thenReturn("1");
        when(redisTemplate.getExpire("ratelimit:" + key, TimeUnit.MILLISECONDS)).thenReturn(500L);

        BusinessException exception = assertThrows(BusinessException.class, 
                () -> rateLimitService.checkRateLimit(key, 1000));
        
        assertEquals("RATE_LIMIT_EXCEEDED", exception.getMessage());
        assertEquals(500L, exception.getMetadata().get("retryAfter"));
    }

    @Test
    void getClientIp_shouldReturnForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        String ip = rateLimitService.getClientIp(request);
        assertEquals("1.2.3.4", ip);
    }

    @Test
    void getClientIp_shouldReturnRemoteAddr_whenNoHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String ip = rateLimitService.getClientIp(request);
        assertEquals("127.0.0.1", ip);
    }
}
