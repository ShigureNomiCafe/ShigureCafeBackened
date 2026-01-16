package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.dto.NoticeRequest;
import cafe.shigure.ShigureCafeBackened.dto.NoticeResponse;
import cafe.shigure.ShigureCafeBackened.dto.NoticeReactionDTO;
import cafe.shigure.ShigureCafeBackened.model.Notice;
import cafe.shigure.ShigureCafeBackened.model.NoticeReaction;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.repository.NoticeReactionRepository;
import cafe.shigure.ShigureCafeBackened.repository.NoticeRepository;
import cafe.shigure.ShigureCafeBackened.repository.UserRepository;
import cafe.shigure.ShigureCafeBackened.model.ReactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;
    @Mock
    private NoticeReactionRepository noticeReactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CacheService cacheService;

    @InjectMocks
    private NoticeService noticeService;

    private User user;
    private Notice notice;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setNickname("Tester");

        notice = new Notice();
        notice.setId(1L);
        notice.setTitle("Test Title");
        notice.setContent("Test Content");
        notice.setAuthor(user);
        notice.setReactions(new ArrayList<>());
    }

    @Test
    void toggleReaction_shouldAddSecondReactionSuccessfully() {
        String type1 = ReactionType.THUMBS_UP.name();
        String type2 = ReactionType.HEART.name();

        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        when(noticeReactionRepository.findByNoticeAndUserAndType(notice, user, type1)).thenReturn(Optional.empty());
        when(noticeReactionRepository.findByNoticeAndUserAndType(notice, user, type2)).thenReturn(Optional.empty());
        
        List<NoticeReaction> reactionsAfterFirst = new ArrayList<>();
        reactionsAfterFirst.add(new NoticeReaction(notice, user, type1));
        
        List<NoticeReaction> reactionsAfterSecond = new ArrayList<>();
        reactionsAfterSecond.add(new NoticeReaction(notice, user, type1));
        reactionsAfterSecond.add(new NoticeReaction(notice, user, type2));

        when(noticeReactionRepository.findByNotice(notice))
            .thenReturn(reactionsAfterFirst)
            .thenReturn(reactionsAfterSecond);

        // Action 1: Add first reaction
        List<NoticeReactionDTO> result1 = noticeService.toggleReaction(1L, user, type1);
        verify(noticeReactionRepository).save(any(NoticeReaction.class));
        assertEquals(1, result1.size());
        assertEquals(type1, result1.get(0).getType());

        // Action 2: Add second reaction
        List<NoticeReactionDTO> result2 = noticeService.toggleReaction(1L, user, type2);
        verify(noticeReactionRepository, times(2)).save(any(NoticeReaction.class));
        
        assertEquals(2, result2.size(), "Should have two reactions");
        assertTrue(result2.stream().anyMatch(r -> r.getType().equals(type1)));
        assertTrue(result2.stream().anyMatch(r -> r.getType().equals(type2)));
    }

    @Test
    void toggleReaction_shouldRemoveReaction_whenSameEmojiToggled() {
        String type = ReactionType.THUMBS_UP.name();
        NoticeReaction reaction = new NoticeReaction(notice, user, type);
        
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(noticeReactionRepository.findByNoticeAndUserAndType(notice, user, type)).thenReturn(Optional.of(reaction));
        when(noticeReactionRepository.findByNotice(notice)).thenReturn(new ArrayList<>());

        noticeService.toggleReaction(1L, user, type);

        verify(noticeReactionRepository).delete(reaction);
    }

    @Test
    void toggleReaction_shouldNotUpdateTimestamp() {
        String type = ReactionType.THUMBS_UP.name();
        long oldTimestamp = System.currentTimeMillis() - 10000;
        notice.setUpdatedAt(oldTimestamp);

        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(noticeReactionRepository.findByNoticeAndUserAndType(notice, user, type)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(noticeReactionRepository.findByNotice(notice)).thenReturn(new ArrayList<>());

        noticeService.toggleReaction(1L, user, type);

        assertEquals(oldTimestamp, notice.getUpdatedAt(), "UpdatedAt timestamp should NOT be updated");
    }

    @Test
    void createNotice_shouldUpdateGlobalTimestamp() {
        NoticeRequest request = new NoticeRequest();
        request.setTitle("New Notice");
        request.setContent("New Content");
        request.setPinned(false);

        when(noticeRepository.saveAndFlush(any(Notice.class))).thenReturn(notice);

        noticeService.createNotice(request, user);

        verify(cacheService).updateTimestamp(CacheService.NOTICE_LIST_KEY);
    }
}