package cafe.shigure.ShigureCafeBackened.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void updateTimestamp_shouldSetCurrentTime() {
        cacheService.updateTimestamp("test-key");
        verify(valueOperations).set(eq("test-key"), anyString());
    }

    @Test
    void getTimestamp_shouldReturnExistingValue() {
        when(valueOperations.get("test-key")).thenReturn("123456789");
        
        Long result = cacheService.getTimestamp("test-key");
        
        assertEquals(123456789L, result);
    }

    @Test
    void getTimestamp_shouldSetAndReturnNow_whenNotExists() {
        when(valueOperations.get("test-key")).thenReturn(null);
        
        Long result = cacheService.getTimestamp("test-key");
        
        assertNotNull(result);
        verify(valueOperations).set(eq("test-key"), anyString());
    }
}
