package cafe.shigure.ShigureCafeBackened.controller;

import cafe.shigure.ShigureCafeBackened.model.Role;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.model.UserStatus;
import cafe.shigure.ShigureCafeBackened.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class MinecraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackened.repository.UserAuditRepository userAuditRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackened.repository.NoticeRepository noticeRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackened.repository.ChatMessageRepository chatMessageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    public void setup() {
        chatMessageRepository.deleteAll();
        noticeRepository.deleteAll();
        userAuditRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testGetWhitelist() throws Exception {
        // ... (existing test logic)
    }

    @Test
    public void testPushChatMessage() throws Exception {
        String json = """
                {
                    "name": "TestPlayer",
                    "message": "Hello, world!"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/minecraft/chat")
                        .header("X-API-KEY", "shigure-cafe-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(1, chatMessageRepository.count());
        cafe.shigure.ShigureCafeBackened.model.ChatMessage saved = chatMessageRepository.findAll().get(0);
        org.junit.jupiter.api.Assertions.assertEquals("TestPlayer", saved.getName());
        org.junit.jupiter.api.Assertions.assertEquals("Hello, world!", saved.getMessage());
    }

    @Test
    public void testPushChatMessageInvalidApiKey() throws Exception {
        String json = """
                {
                    "name": "TestPlayer",
                    "message": "Hello, world!"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/minecraft/chat")
                        .header("X-API-KEY", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }
}
