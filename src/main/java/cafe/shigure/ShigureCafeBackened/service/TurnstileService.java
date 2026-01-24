package cafe.shigure.ShigureCafeBackened.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TurnstileService {

    private final RestTemplate restTemplate;

    @Value("${application.turnstile.secret-key}")
    private String secretKey;

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Data
    public static class TurnstileResponse {
        private boolean success;
        private List<String> errorCodes;
        private String challenge_ts;
        private String hostname;
        private String action;
        private String cdata;
    }

    public boolean verifyToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("secret", secretKey);
        map.add("response", token);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            TurnstileResponse response = restTemplate.postForObject(VERIFY_URL, request, TurnstileResponse.class);
            return response != null && response.isSuccess();
        } catch (Exception e) {
            System.err.println("Turnstile verification failed: " + e.getMessage());
            return false;
        }
    }
}
