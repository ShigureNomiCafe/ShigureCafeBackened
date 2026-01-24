package cafe.shigure.ShigureCafeBackened.controller;

import cafe.shigure.ShigureCafeBackened.dto.UserResponse;
import cafe.shigure.ShigureCafeBackened.model.Role;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserResourceController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserResourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private MinecraftAuthService minecraftAuthService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private cafe.shigure.ShigureCafeBackened.repository.TokenBlacklistRepository tokenBlacklistRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getUserByUsername_shouldReturnUser() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        user.setNickname("Tester");
        user.setRole(Role.USER);

        UserResponse response = new UserResponse();
        response.setUsername("testuser");
        response.setNickname("Tester");

        when(userService.getUserByUsername("testuser")).thenReturn(user);
        when(userService.mapToUserResponse(user)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.nickname").value("Tester"));
    }

    @Test
    void getMicrosoftClientId_shouldReturnId() throws Exception {
        mockMvc.perform(get("/api/v1/users/config/microsoft-client-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").exists());
    }
}
