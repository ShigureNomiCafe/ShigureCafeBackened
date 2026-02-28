package cafe.shigure.ShigureCafeBackend.controller;

import cafe.shigure.ShigureCafeBackend.model.Role;
import cafe.shigure.ShigureCafeBackend.model.User;
import cafe.shigure.ShigureCafeBackend.model.UserStatus;
import cafe.shigure.ShigureCafeBackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "application.security.api-key=test-api-key")
@AutoConfigureMockMvc
public class MinecraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackend.repository.UserAuditRepository userAuditRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackend.repository.ArticleRepository articleRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackend.repository.ChatMessageRepository chatMessageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    public void setup() {
        redisTemplate.delete("minecraft:chat:latest");
        chatMessageRepository.deleteAll();
        articleRepository.deleteAll();
        userAuditRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testGetWhitelistWithApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/minecraft/whitelist")
                        .header("Cafe-API-Key", "test-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetWhitelistWithOldApiKeyHeader() throws Exception {
        mockMvc.perform(get("/api/v1/minecraft/whitelist")
                        .header("X-API-KEY", "test-api-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetWhitelistAsAdmin() throws Exception {
        User admin = new User();
        admin.setUsername("admin");
        admin.setNickname("Admin");
        admin.setEmail("admin@example.com");
        admin.setPassword("password");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);

        mockMvc.perform(get("/api/v1/minecraft/whitelist")
                        .with(user(admin)))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetWhitelistAsUser() throws Exception {
        User user = new User();
        user.setUsername("user");
        user.setNickname("User");
        user.setEmail("user@example.com");
        user.setPassword("password");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        mockMvc.perform(get("/api/v1/minecraft/whitelist")
                        .with(user(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetWhitelistUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/minecraft/whitelist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetChatMessages() throws Exception {
        // Create and save a user to satisfy @AuthenticationPrincipal User currentUser and #currentUser.id in @RateLimit
        User user = new User();
        user.setUsername("testuser");
        user.setNickname("TestUser");
        user.setEmail("test@example.com");
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        // Save some messages
        cafe.shigure.ShigureCafeBackend.model.ChatMessage msg1 = new cafe.shigure.ShigureCafeBackend.model.ChatMessage();
        msg1.setName("Player1");
        msg1.setMessage("Msg 1");
        msg1.setTimestamp(System.currentTimeMillis() - 1000);
        chatMessageRepository.save(msg1);

        cafe.shigure.ShigureCafeBackend.model.ChatMessage msg2 = new cafe.shigure.ShigureCafeBackend.model.ChatMessage();
        msg2.setName("Player2");
        msg2.setMessage("Msg 2");
        msg2.setTimestamp(System.currentTimeMillis());
        chatMessageRepository.save(msg2);

        mockMvc.perform(get("/api/v1/minecraft/chat")
                        .with(user(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Player2"))
                .andExpect(jsonPath("$.content[1].name").value("Player1"));
    }
}
