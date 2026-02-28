package cafe.shigure.ShigureCafeBackend.controller;

import cafe.shigure.ShigureCafeBackend.dto.*;
import cafe.shigure.ShigureCafeBackend.model.*;
import cafe.shigure.ShigureCafeBackend.repository.ArticleRepository;
import cafe.shigure.ShigureCafeBackend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackend.repository.ArticleReactionRepository articleReactionRepository;

    @Autowired
    private cafe.shigure.ShigureCafeBackend.repository.UserAuditRepository userAuditRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StringRedisTemplate redisTemplate;
    
    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    private User adminUser;
    private User regularUser;
    private User otherUser;

    @BeforeEach
    public void setup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        articleReactionRepository.deleteAll();
        articleRepository.deleteAll();
        userAuditRepository.deleteAll();
        userRepository.deleteAll();

        String suffix = String.valueOf(System.currentTimeMillis());

        adminUser = new User();
        adminUser.setUsername("admin" + suffix);
        adminUser.setNickname("Admin");
        adminUser.setEmail("admin" + suffix + "@example.com");
        adminUser.setPassword("password");
        adminUser.setRole(Role.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(adminUser);

        regularUser = new User();
        regularUser.setUsername("user" + suffix);
        regularUser.setNickname("User");
        regularUser.setEmail("user" + suffix + "@example.com");
        regularUser.setPassword("password");
        regularUser.setRole(Role.USER);
        regularUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(regularUser);

        otherUser = new User();
        otherUser.setUsername("other" + suffix);
        otherUser.setNickname("Other");
        otherUser.setEmail("other" + suffix + "@example.com");
        otherUser.setPassword("password");
        otherUser.setRole(Role.USER);
        otherUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(otherUser);
    }

    @Test
    public void testGetArticleList() throws Exception {
        Article article = new Article("Title", "Content", ArticleType.REGULAR, false, adminUser);
        articleRepository.save(article);

        mockMvc.perform(get("/api/v2/getArticleList")
                        .with(user(regularUser))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Title"));
    }

    @Test
    public void testCreateArticleAsUser() throws Exception {
        ArticleRequest request = ArticleRequest.builder()
                .title("User Article")
                .content("User Content")
                .type(ArticleType.REGULAR)
                .pinned(false)
                .build();

        mockMvc.perform(post("/api/v2/createArticle")
                        .with(user(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("User Article"))
                .andExpect(jsonPath("$.authorUsername").value(regularUser.getUsername()));
    }

    @Test
    public void testCreateAnnouncementAsUserShouldFail() throws Exception {
        ArticleRequest request = ArticleRequest.builder()
                .title("Announcement")
                .content("Content")
                .type(ArticleType.ANNOUNCEMENT)
                .pinned(true)
                .build();

        mockMvc.perform(post("/api/v2/createArticle")
                        .with(user(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED_FOR_ANNOUNCEMENT"));
    }

    @Test
    public void testCreateAnnouncementAsAdmin() throws Exception {
        ArticleRequest request = ArticleRequest.builder()
                .title("Admin Announcement")
                .content("Admin Content")
                .type(ArticleType.ANNOUNCEMENT)
                .pinned(true)
                .build();

        mockMvc.perform(post("/api/v2/createArticle")
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Admin Announcement"))
                .andExpect(jsonPath("$.type").value("ANNOUNCEMENT"));
    }

    @Test
    public void testUpdateArticleByAuthor() throws Exception {
        Article article = new Article("Old Title", "Old Content", ArticleType.REGULAR, false, regularUser);
        article = articleRepository.save(article);

        ArticleUpdateRequest request = new ArticleUpdateRequest();
        request.setId(article.getId());
        request.setTitle("New Title");
        request.setContent("New Content");
        request.setType(ArticleType.REGULAR);

        mockMvc.perform(post("/api/v2/updateArticle")
                        .with(user(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"));
    }

    @Test
    public void testUpdateArticleByOtherUserShouldFail() throws Exception {
        Article article = new Article("Old Title", "Old Content", ArticleType.REGULAR, false, regularUser);
        article = articleRepository.save(article);

        ArticleUpdateRequest request = new ArticleUpdateRequest();
        request.setId(article.getId());
        request.setTitle("Hacked Title");
        request.setContent("Hacked Content");
        request.setType(ArticleType.REGULAR);

        mockMvc.perform(post("/api/v2/updateArticle")
                        .with(user(otherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ARTICLE_UPDATE"));
    }

    @Test
    public void testDeleteArticleByAdmin() throws Exception {
        Article article = new Article("To Be Deleted", "Content", ArticleType.REGULAR, false, regularUser);
        article = articleRepository.save(article);

        IdRequest request = new IdRequest();
        request.setId(article.getId());

        mockMvc.perform(post("/api/v2/deleteArticle")
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assert(!articleRepository.existsById(article.getId()));
    }

    @Test
    public void testToggleReaction() throws Exception {
        Article article = new Article("Reaction Test", "Content", ArticleType.REGULAR, false, adminUser);
        article = articleRepository.save(article);

        ArticleReactionToggleRequest request = new ArticleReactionToggleRequest();
        request.setArticleId(article.getId());
        request.setType("LIKE");

        mockMvc.perform(post("/api/v2/toggleArticleReaction")
                        .with(user(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("LIKE"))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].reacted").value(true));
    }
}
