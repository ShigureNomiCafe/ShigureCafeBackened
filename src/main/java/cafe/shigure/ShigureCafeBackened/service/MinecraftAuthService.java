package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MinecraftAuthService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${application.microsoft.minecraft.client-id}")
    private String clientId;

    @Value("${application.microsoft.minecraft.client-secret}")
    private String clientSecret;

    @Value("${application.microsoft.minecraft.tenant-id}")
    private String tenantId;

    public String getMinecraftUuid(String code, String redirectUri) {
        // 1. Exchange code for Microsoft Access Token
        String msAccessToken = getMicrosoftAccessToken(code, redirectUri);

        // 2. Exchange MS Access Token for Xbox Live Token
        Map<String, String> xboxAuth = getXboxLiveToken(msAccessToken);
        String xboxToken = xboxAuth.get("token");
        String uhs = xboxAuth.get("uhs");

        // 3. Exchange Xbox Live Token for XSTS Token
        String xstsToken = getXstsToken(xboxToken);

        // 4. Exchange XSTS Token for Minecraft Access Token
        String mcAccessToken = getMinecraftAccessToken(xstsToken, uhs);

        // 5. Get Minecraft Profile
        return getMinecraftProfile(mcAccessToken);
    }

    private String getMicrosoftAccessToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("code", code);
        map.add("grant_type", "authorization_code");
        map.add("redirect_uri", redirectUri);
        map.add("scope", "XboxLive.signin offline_access");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        // Using the modern v2.0 consumers endpoint
        String tokenUrl = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            System.err.println("Microsoft Auth Error: " + responseBody);
            try {
                Map errorMap = objectMapper.readValue(responseBody, Map.class);
                if (errorMap != null && errorMap.containsKey("error")) {
                    String error = (String) errorMap.get("error");
                    if ("invalid_grant".equals(error)) {
                        throw new BusinessException("MICROSOFT_INVALID_GRANT");
                    }
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception ignored) {}
            throw new BusinessException("MICROSOFT_AUTH_FAILED");
        }
        throw new BusinessException("MICROSOFT_AUTH_FAILED");
    }

    private Map<String, String> getXboxLiveToken(String msAccessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        Map<String, Object> body = Map.of(
                "Properties", Map.of(
                        "AuthMethod", "RPS",
                        "SiteName", "user.auth.xboxlive.com",
                        "RpsTicket", "d=" + msAccessToken
                ),
                "RelyingParty", "http://auth.xboxlive.com",
                "TokenType", "JWT"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity("https://user.auth.xboxlive.com/user/authenticate", request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String token = (String) response.getBody().get("Token");
                Map displayClaims = (Map) response.getBody().get("DisplayClaims");
                List xui = (List) displayClaims.get("xui");
                Map firstXui = (Map) xui.get(0);
                String uhs = (String) firstXui.get("uhs");
                return Map.of("token", token, "uhs", uhs);
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            System.err.println("Xbox Live Auth Error: " + responseBody);
            throw new BusinessException("XBOX_LIVE_AUTH_FAILED");
        }
        throw new BusinessException("XBOX_LIVE_AUTH_FAILED");
    }

    private String getXstsToken(String xboxToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        Map<String, Object> body = Map.of(
                "Properties", Map.of(
                        "SandboxId", "RETAIL",
                        "UserTokens", List.of(xboxToken)
                ),
                "RelyingParty", "rp://api.minecraftservices.com/",
                "TokenType", "JWT"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity("https://xsts.auth.xboxlive.com/xsts/authorize", request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("Token");
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            System.err.println("XSTS Auth Error Body: " + responseBody);
            
            try {
                // Try parsing JSON to get exact XErr code
                Map<String, Object> errorMap = objectMapper.readValue(responseBody, Map.class);
                if (errorMap != null && errorMap.containsKey("XErr")) {
                    Object xErrObj = errorMap.get("XErr");
                    long xErr = 0;
                    if (xErrObj instanceof Number) {
                        xErr = ((Number) xErrObj).longValue();
                    } else if (xErrObj instanceof String) {
                        xErr = Long.parseLong((String) xErrObj);
                    }
                    
                    // 2148916233 (decimal) -> Account not found
                    if (xErr == 2148916233L) {
                        throw new BusinessException("XBOX_ACCOUNT_NOT_FOUND");
                    }
                    // 2148916238 (decimal) -> Child account
                    if (xErr == 2148916238L) {
                        throw new BusinessException("XBOX_CHILD_ACCOUNT_RESTRICTION");
                    }
                    // 2148916235 (decimal) -> Xbox not available in country
                    if (xErr == 2148916235L) {
                        throw new BusinessException("XBOX_NOT_AVAILABLE_IN_COUNTRY");
                    }
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception ignored) {}

            // Fallback string checks
            if (responseBody.contains("2148916233") || responseBody.contains("8015DC09") || responseBody.contains("-2146051063") || responseBody.contains("Account doesn't have an Xbox account")) {
                throw new BusinessException("XBOX_ACCOUNT_NOT_FOUND");
            }
            if (responseBody.contains("2148916238") || responseBody.contains("8015DC0E") || responseBody.contains("-2146051058")) {
                throw new BusinessException("XBOX_CHILD_ACCOUNT_RESTRICTION");
            }
            throw new BusinessException("XSTS_AUTH_FAILED");
        }
        throw new BusinessException("XSTS_AUTH_FAILED");
    }

    private String getMinecraftAccessToken(String xstsToken, String uhs) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        Map<String, Object> body = Map.of(
                "identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity("https://api.minecraftservices.com/authentication/login_with_xbox", request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            System.err.println("Minecraft Login Error: " + responseBody);
            
            try {
                Map errorMap = objectMapper.readValue(responseBody, Map.class);
                if (errorMap != null) {
                    if (errorMap.containsKey("details")) {
                        Map details = (Map) errorMap.get("details");
                        if ("ACCOUNT_SUSPENDED".equals(details.get("reason"))) {
                            throw new BusinessException("MINECRAFT_ACCOUNT_SUSPENDED");
                        }
                    }
                    if (errorMap.containsKey("errorMessage")) {
                        String errMsg = (String) errorMap.get("errorMessage");
                        if (errMsg.contains("suspended")) {
                            throw new BusinessException("MINECRAFT_ACCOUNT_SUSPENDED");
                        }
                    }
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception ignored) {}

            if (responseBody.contains("Invalid app registration")) {
                throw new BusinessException("MINECRAFT_APP_REGISTRATION_INVALID");
            }
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException("RATE_LIMIT_EXCEEDED");
            }
            throw new BusinessException("MINECRAFT_AUTH_FAILED");
        }
        throw new BusinessException("MINECRAFT_AUTH_FAILED");
    }

    private String getMinecraftProfile(String mcAccessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mcAccessToken);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange("https://api.minecraftservices.com/minecraft/profile", HttpMethod.GET, request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("id");
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            System.err.println("Minecraft Profile Error: " + responseBody);
            
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new BusinessException("MINECRAFT_PROFILE_NOT_FOUND");
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new BusinessException("MINECRAFT_UNAUTHORIZED");
            }
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException("RATE_LIMIT_EXCEEDED");
            }
            
            throw new BusinessException("MINECRAFT_PROFILE_FAILED");
        }
        throw new BusinessException("MINECRAFT_PROFILE_FAILED");
    }
}
