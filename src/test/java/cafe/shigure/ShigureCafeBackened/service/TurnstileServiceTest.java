package cafe.shigure.ShigureCafeBackened.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnstileServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TurnstileService turnstileService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(turnstileService, "secretKey", "test-secret-key");
    }

    @Test
    void verifyToken_shouldReturnTrue_whenSuccess() {
        TurnstileService.TurnstileResponse mockResponse = new TurnstileService.TurnstileResponse();
        mockResponse.setSuccess(true);

        when(restTemplate.postForObject(any(String.class), any(), eq(TurnstileService.TurnstileResponse.class)))
                .thenReturn(mockResponse);

        boolean result = turnstileService.verifyToken("valid-token");

        assertTrue(result);
    }

    @Test
    void verifyToken_shouldReturnFalse_whenFailure() {
        TurnstileService.TurnstileResponse mockResponse = new TurnstileService.TurnstileResponse();
        mockResponse.setSuccess(false);

        when(restTemplate.postForObject(any(String.class), any(), eq(TurnstileService.TurnstileResponse.class)))
                .thenReturn(mockResponse);

        boolean result = turnstileService.verifyToken("invalid-token");

        assertFalse(result);
    }

    @Test
    void verifyToken_shouldReturnFalse_whenException() {
        when(restTemplate.postForObject(any(String.class), any(), eq(TurnstileService.TurnstileResponse.class)))
                .thenThrow(new RuntimeException("API error"));

        boolean result = turnstileService.verifyToken("token");

        assertFalse(result);
    }

    @Test
    void verifyToken_shouldReturnFalse_whenTokenEmpty() {
        assertFalse(turnstileService.verifyToken(""));
        assertFalse(turnstileService.verifyToken(null));
    }
}
