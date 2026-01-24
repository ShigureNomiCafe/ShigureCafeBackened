package cafe.shigure.ShigureCafeBackened.controller;

import cafe.shigure.ShigureCafeBackened.config.JwtAuthenticationFilter;
import cafe.shigure.ShigureCafeBackened.dto.RegisterRequest;
import cafe.shigure.ShigureCafeBackened.dto.RegistrationDetailsResponse;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.service.JwtService;
import cafe.shigure.ShigureCafeBackened.service.RateLimitService;
import cafe.shigure.ShigureCafeBackened.service.TurnstileService;
import cafe.shigure.ShigureCafeBackened.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for simple unit test
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private TurnstileService turnstileService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private cafe.shigure.ShigureCafeBackened.repository.TokenBlacklistRepository tokenBlacklistRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void register_shouldReturnCreated_whenValid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setEmail("test@example.com");
        request.setVerificationCode("123456");
        request.setTurnstileToken("valid-token");

        when(turnstileService.verifyToken("valid-token")).thenReturn(true);
        when(userService.register(any(RegisterRequest.class))).thenReturn("audit-123");

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.auditCode").value("audit-123"));

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    void register_shouldReturnBadRequest_whenCaptchaInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setTurnstileToken("invalid-token");

        when(turnstileService.verifyToken("invalid-token")).thenReturn(false);

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAPTCHA"));
    }

    @Test
    void checkRegistration_shouldReturnDetails() throws Exception {
        RegistrationDetailsResponse response = new RegistrationDetailsResponse();
        response.setAuditCode("audit-123");
        response.setUsername("testuser");

        when(userService.getRegistrationDetails("audit-123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/registrations/audit-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditCode").value("audit-123"))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void approveRegistration_shouldReturnOk() throws Exception {
        mockMvc.perform(patch("/api/v1/registrations/audit-123")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(userService).approveUser("audit-123");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void banUser_shouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/v1/registrations/audit-123")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(userService).banUser("audit-123");
    }
}
