package cafe.shigure.ShigureCafeBackened.controller;

import cafe.shigure.ShigureCafeBackened.service.CacheService;
import cafe.shigure.ShigureCafeBackened.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CacheService cacheService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private cafe.shigure.ShigureCafeBackened.repository.TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    void getUpdates_shouldReturnTimestamps() throws Exception {
        when(cacheService.getTimestamp(CacheService.NOTICE_LIST_KEY)).thenReturn(100L);
        when(cacheService.getTimestamp(CacheService.USER_LIST_KEY)).thenReturn(200L);
        when(cacheService.getTimestamp(CacheService.AUDIT_LIST_KEY)).thenReturn(300L);

        mockMvc.perform(get("/api/v1/system/updates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noticeLastUpdated").value(100))
                .andExpect(jsonPath("$.userLastUpdated").value(200))
                .andExpect(jsonPath("$.auditLastUpdated").value(300));
    }

    @Test
    void getReactionTypes_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/system/reaction-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].emoji").exists());
    }
}
