package cafe.shigure.ShigureCafeBackend.service;

import cafe.shigure.ShigureCafeBackend.dto.ArticleRequest;
import cafe.shigure.ShigureCafeBackend.dto.ArticleResponse;
import cafe.shigure.ShigureCafeBackend.model.Article;
import cafe.shigure.ShigureCafeBackend.model.ArticleType;
import cafe.shigure.ShigureCafeBackend.model.User;
import cafe.shigure.ShigureCafeBackend.repository.ArticleReactionRepository;
import cafe.shigure.ShigureCafeBackend.repository.ArticleRepository;
import cafe.shigure.ShigureCafeBackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleReactionRepository articleReactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private ArticleService articleService;

    private User author;
    private User assignee;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(1L);
        author.setUsername("author");

        assignee = new User();
        assignee.setId(2L);
        assignee.setUsername("assignee");
    }

    @Test
    void createArticle_CommissionWithTypeAndAssignee() {
        ArticleRequest request = ArticleRequest.builder()
                .title("Test Commission")
                .content("Content")
                .type(ArticleType.COMMISSION)
                .assigneeId(2L)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            article.setId(100L);
            return article;
        });

        ArticleResponse response = articleService.createArticle(request, author);

        assertNotNull(response);
        assertEquals("Test Commission", response.getTitle());
        assertEquals(ArticleType.COMMISSION, response.getType());
        assertEquals("assignee", response.getAssigneeUsername());
        verify(cacheService).updateTimestamp(ArticleService.ARTICLE_LIST_KEY);
    }

    @Test
    void createArticle_RegularArticle() {
        ArticleRequest request = ArticleRequest.builder()
                .title("Regular News")
                .content("Some content")
                .type(ArticleType.REGULAR)
                .pinned(true)
                .build();

        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            article.setId(101L);
            return article;
        });

        ArticleResponse response = articleService.createArticle(request, author);

        assertNotNull(response);
        assertTrue(response.isPinned());
        assertEquals(ArticleType.REGULAR, response.getType());
        assertNull(response.getAssigneeUsername());
    }
}
