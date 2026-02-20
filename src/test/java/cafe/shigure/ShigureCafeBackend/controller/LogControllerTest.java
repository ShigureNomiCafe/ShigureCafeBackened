package cafe.shigure.ShigureCafeBackend.controller;

import cafe.shigure.ShigureCafeBackend.dto.LogRequest;
import cafe.shigure.ShigureCafeBackend.model.Role;
import cafe.shigure.ShigureCafeBackend.model.SystemLog;
import cafe.shigure.ShigureCafeBackend.model.User;
import cafe.shigure.ShigureCafeBackend.model.UserStatus;
import cafe.shigure.ShigureCafeBackend.repository.SystemLogRepository;
import cafe.shigure.ShigureCafeBackend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "application.security.api-key=test-api-key")
@AutoConfigureMockMvc
public class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User admin;
    private User normalUser;

    @BeforeEach
    public void setup() {
        systemLogRepository.deleteAll();
        userRepository.deleteAll();

        admin = new User();
        admin.setUsername("admin");
        admin.setNickname("Admin");
        admin.setEmail("admin@example.com");
        admin.setPassword("password");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);

        normalUser = new User();
        normalUser.setUsername("user");
        normalUser.setNickname("User");
        normalUser.setEmail("user@example.com");
        normalUser.setPassword("password");
        normalUser.setRole(Role.USER);
        normalUser.setStatus(UserStatus.ACTIVE);
        normalUser = userRepository.save(normalUser);
    }

    @Test
    public void testGetLogs() throws Exception {
        // Prepare some data
        systemLogRepository.save(SystemLog.builder()
                .timestamp(Instant.now().toEpochMilli())
                .level("INFO")
                .source("TEST")
                .content("Test log 1")
                .build());
        systemLogRepository.save(SystemLog.builder()
                .timestamp(Instant.now().toEpochMilli())
                .level("ERROR")
                .source("TEST")
                .content("Test log 2")
                .build());

        mockMvc.perform(get("/api/v1/logs/latest")
                        .with(user(admin))
                        .param("level", "INFO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].level").value("INFO"));

        mockMvc.perform(get("/api/v1/logs/latest")
                        .with(user(admin))
                        .param("search", "log 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("Test log 2"));
    }

    @Test
    public void testAddLogs() throws Exception {
        LogRequest request1 = new LogRequest();
        request1.setLevel("WARN");
        request1.setSource("EXTERNAL_BOT");
        request1.setContent("External warning 1");
        request1.setTimestamp(Instant.now().toEpochMilli());

        LogRequest request2 = new LogRequest();
        request2.setLevel("ERROR");
        request2.setSource("EXTERNAL_BOT");
        request2.setContent("External error 2");
        request2.setTimestamp(Instant.now().toEpochMilli());

        java.util.List<LogRequest> requests = java.util.Arrays.asList(request1, request2);

        mockMvc.perform(post("/api/v1/logs")
                        .header("Cafe-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        assert systemLogRepository.count() == 2;
    }

    @Test
    public void testAddLogsV2Alias() throws Exception {
        LogRequest request = new LogRequest();
        request.setLevel("INFO");
        request.setSource("V2_TEST");
        request.setContent("V2 alias test");

        java.util.List<LogRequest> requests = java.util.Collections.singletonList(request);

        mockMvc.perform(post("/api/v2/uploadLogs")
                        .header("Cafe-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        assert systemLogRepository.count() == 1;
        SystemLog saved = systemLogRepository.findAll().get(0);
        assert saved.getContent().equals("V2 alias test");
    }

    @Test
    public void testGetLogsForbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/v1/logs/latest")
                        .with(user(normalUser)))
                .andExpect(status().isForbidden());
    }
}
