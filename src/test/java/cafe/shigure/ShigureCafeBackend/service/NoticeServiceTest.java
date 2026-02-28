package cafe.shigure.ShigureCafeBackend.service;

import cafe.shigure.ShigureCafeBackend.dto.NoticeRequest;
import cafe.shigure.ShigureCafeBackend.dto.NoticeResponse;
import cafe.shigure.ShigureCafeBackend.dto.NoticeReactionDTO;
import cafe.shigure.ShigureCafeBackend.model.Article;
import cafe.shigure.ShigureCafeBackend.model.ArticleReaction;
import cafe.shigure.ShigureCafeBackend.model.ArticleType;
import cafe.shigure.ShigureCafeBackend.model.User;
import cafe.shigure.ShigureCafeBackend.repository.ArticleReactionRepository;
import cafe.shigure.ShigureCafeBackend.repository.ArticleRepository;
import cafe.shigure.ShigureCafeBackend.repository.UserRepository;
import cafe.shigure.ShigureCafeBackend.model.ReactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private ArticleReactionRepository articleReactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CacheService cacheService;

    @InjectMocks
    private NoticeService noticeService;

    private User user;
    private Article noticeArticle;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setNickname("Tester");

        noticeArticle = new Article();
        noticeArticle.setId(1L);
        noticeArticle.setTitle("Test Title");
        noticeArticle.setContent("Test Content");
        noticeArticle.setType(ArticleType.ANNOUNCEMENT);
        noticeArticle.setAuthor(user);
        noticeArticle.setReactions(new ArrayList<>());
    }

    @Test
    void toggleReaction_shouldAddSecondReactionSuccessfully() {
        String type1 = ReactionType.THUMBS_UP.name();
        String type2 = ReactionType.HEART.name();

        when(articleRepository.findById(1L)).thenReturn(Optional.of(noticeArticle));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        when(articleReactionRepository.findByArticleAndUserAndType(noticeArticle, user, type1)).thenReturn(Optional.empty());
        when(articleReactionRepository.findByArticleAndUserAndType(noticeArticle, user, type2)).thenReturn(Optional.empty());
        
        List<ArticleReaction> reactionsAfterFirst = new ArrayList<>();
        reactionsAfterFirst.add(new ArticleReaction(noticeArticle, user, type1));
        
        List<ArticleReaction> reactionsAfterSecond = new ArrayList<>();
        reactionsAfterSecond.add(new ArticleReaction(noticeArticle, user, type1));
        reactionsAfterSecond.add(new ArticleReaction(noticeArticle, user, type2));

        when(articleReactionRepository.findByArticle(noticeArticle))
            .thenReturn(reactionsAfterFirst)
            .thenReturn(reactionsAfterSecond);

        // Action 1: Add first reaction
        List<NoticeReactionDTO> result1 = noticeService.toggleReaction(1L, user, type1);
        verify(articleReactionRepository).save(any(ArticleReaction.class));
        assertEquals(1, result1.size());
        assertEquals(type1, result1.get(0).getType());

        // Action 2: Add second reaction
        List<NoticeReactionDTO> result2 = noticeService.toggleReaction(1L, user, type2);
        verify(articleReactionRepository, times(2)).save(any(ArticleReaction.class));
        
        assertEquals(2, result2.size(), "Should have two reactions");
        assertTrue(result2.stream().anyMatch(r -> r.getType().equals(type1)));
        assertTrue(result2.stream().anyMatch(r -> r.getType().equals(type2)));
    }

    @Test
    void toggleReaction_shouldRemoveReaction_whenSameEmojiToggled() {
        String type = ReactionType.THUMBS_UP.name();
        ArticleReaction reaction = new ArticleReaction(noticeArticle, user, type);
        
        when(articleRepository.findById(1L)).thenReturn(Optional.of(noticeArticle));
        when(articleReactionRepository.findByArticleAndUserAndType(noticeArticle, user, type)).thenReturn(Optional.of(reaction));
        when(articleReactionRepository.findByArticle(noticeArticle)).thenReturn(new ArrayList<>());

        noticeService.toggleReaction(1L, user, type);

        verify(articleReactionRepository).delete(reaction);
    }

    @Test
    void toggleReaction_shouldNotUpdateTimestamp() {
        String type = ReactionType.THUMBS_UP.name();
        long oldTimestamp = System.currentTimeMillis() - 10000;
        noticeArticle.setUpdatedAt(oldTimestamp);

        when(articleRepository.findById(1L)).thenReturn(Optional.of(noticeArticle));
        when(articleReactionRepository.findByArticleAndUserAndType(noticeArticle, user, type)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(articleReactionRepository.findByArticle(noticeArticle)).thenReturn(new ArrayList<>());

        noticeService.toggleReaction(1L, user, type);

        assertEquals(oldTimestamp, noticeArticle.getUpdatedAt(), "UpdatedAt timestamp should NOT be updated");
    }

    @Test
    void createNotice_shouldUpdateGlobalTimestamp() {
        NoticeRequest request = new NoticeRequest();
        request.setTitle("New Notice");
        request.setContent("New Content");
        request.setPinned(false);

        when(articleRepository.saveAndFlush(any(Article.class))).thenReturn(noticeArticle);

        noticeService.createNotice(request, user);

        verify(cacheService).updateTimestamp(CacheService.NOTICE_LIST_KEY);
    }
}
