package cafe.shigure.ShigureCafeBackend.controller;

import cafe.shigure.ShigureCafeBackend.dto.LoginRequest;
import cafe.shigure.ShigureCafeBackend.dto.RegisterRequest;
import cafe.shigure.ShigureCafeBackend.model.Role;
import cafe.shigure.ShigureCafeBackend.model.User;
import cafe.shigure.ShigureCafeBackend.model.UserStatus;
import cafe.shigure.ShigureCafeBackend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackend.repository.ArticleRepository articleRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;
    
    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    @Autowired
    private cafe.shigure.ShigureCafeBackend.repository.UserAuditRepository userAuditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    public void setup() {
        articleRepository.deleteAll();
        userAuditRepository.deleteAll();
        userRepository.deleteAll();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    public void testRegister() throws Exception {
        String email = "test@example.com";
        String code = "123456";

        // Mock Redis behavior for verification
        String codeKey = "verify:code:" + email;
        when(valueOperations.get(codeKey)).thenReturn(code);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setEmail(email);
        request.setVerificationCode(code);

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.auditCode").exists());
    }

    @Test
    public void testLogin() throws Exception {
        // Create active user
        User user = new User();
        user.setUsername("loginuser");
        user.setNickname("Login User");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setEmail("login@example.com");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("loginuser");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    public void testLoginWithPendingUser() throws Exception {
        // Create pending user
        User user = new User();
        user.setUsername("pendinguser");
        user.setNickname("Pending User");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setEmail("pending@example.com");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.PENDING);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("pendinguser");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));
    }

    @Test
    public void testLoginFailure() throws Exception {
        // Create active user
        User user = new User();
        user.setUsername("activeuser");
        user.setNickname("Active User");
        user.setPassword(passwordEncoder.encode("correctpassword"));
        user.setEmail("active@example.com");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // Try with wrong password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("activeuser");
        loginRequest.setPassword("wrongpassword");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
}
