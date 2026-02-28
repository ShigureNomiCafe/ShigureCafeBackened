package cafe.shigure.ShigureCafeBackend.service;

import cafe.shigure.ShigureCafeBackend.dto.*;
import cafe.shigure.ShigureCafeBackend.exception.BusinessException;
import cafe.shigure.ShigureCafeBackend.model.Article;
import cafe.shigure.ShigureCafeBackend.model.ArticleReaction;
import cafe.shigure.ShigureCafeBackend.model.ArticleType;
import cafe.shigure.ShigureCafeBackend.model.User;
import cafe.shigure.ShigureCafeBackend.repository.ArticleReactionRepository;
import cafe.shigure.ShigureCafeBackend.repository.ArticleRepository;
import cafe.shigure.ShigureCafeBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final ArticleReactionRepository articleReactionRepository;
    private final CacheService cacheService;

    @Transactional(readOnly = true)
    public PagedResponse<NoticeResponse> getAllNotices(Pageable pageable) {
        Page<NoticeResponse> page = articleRepository.findByTypeOrderByPinnedDescUpdatedAtDesc(ArticleType.ANNOUNCEMENT, pageable)
                .map(this::mapToResponse);
        return PagedResponse.fromPage(page, cacheService.getTimestamp(CacheService.NOTICE_LIST_KEY));
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNoticeById(Long id) {
        Article notice = articleRepository.findById(id)
                .filter(a -> a.getType() == ArticleType.ANNOUNCEMENT)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));
        return mapToResponse(notice);
    }

    @Transactional
    public NoticeResponse createNotice(NoticeRequest request, User author) {
        Article notice = new Article(request.getTitle(), request.getContent(), ArticleType.ANNOUNCEMENT, request.isPinned(), author);
        Article savedNotice = articleRepository.saveAndFlush(notice);
        cacheService.updateTimestamp(CacheService.NOTICE_LIST_KEY);
        return mapToResponse(savedNotice);
    }

    @Transactional
    public NoticeResponse updateNotice(Long id, NoticeRequest request, User currentUser) {
        Article notice = articleRepository.findById(id)
                .filter(a -> a.getType() == ArticleType.ANNOUNCEMENT)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));

        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setPinned(request.isPinned());

        Article updatedNotice = articleRepository.saveAndFlush(notice);
        cacheService.updateTimestamp(CacheService.NOTICE_LIST_KEY);
        return mapToResponse(updatedNotice);
    }

    @Transactional
    public void deleteNotice(Long id) {
        Article notice = articleRepository.findById(id)
                .filter(a -> a.getType() == ArticleType.ANNOUNCEMENT)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));
        articleRepository.delete(notice);
        cacheService.updateTimestamp(CacheService.NOTICE_LIST_KEY);
    }

    @Transactional
    public List<NoticeReactionDTO> toggleReaction(Long noticeId, User user, String type) {
        Article notice = articleRepository.findById(noticeId)
                .filter(a -> a.getType() == ArticleType.ANNOUNCEMENT)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));

        Optional<ArticleReaction> reactionOpt = articleReactionRepository.findByArticleAndUserAndType(notice, user, type);

        if (reactionOpt.isPresent()) {
            articleReactionRepository.delete(reactionOpt.get());
        } else {
            User managedUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
            ArticleReaction reaction = new ArticleReaction(notice, managedUser, type);
            articleReactionRepository.save(reaction);
        }

        return getReactions(noticeId, user);
    }

    @Transactional(readOnly = true)
    public List<NoticeReactionDTO> getReactions(Long noticeId, User currentUser) {
        Article notice = articleRepository.findById(noticeId)
                .filter(a -> a.getType() == ArticleType.ANNOUNCEMENT)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));
        
        List<ArticleReaction> reactions = articleReactionRepository.findByArticle(notice);
        return mapReactionsToDTO(reactions, currentUser);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<NoticeReactionDTO>> getReactionsBatch(List<Long> noticeIds, User currentUser) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Article> notices = articleRepository.findAllById(noticeIds).stream()
                .filter(a -> a.getType() == ArticleType.ANNOUNCEMENT)
                .collect(Collectors.toList());
        List<ArticleReaction> allReactions = articleReactionRepository.findByArticleIn(notices);

        Map<Long, List<ArticleReaction>> reactionsByNotice = allReactions.stream()
                .collect(Collectors.groupingBy(r -> r.getArticle().getId()));

        Map<Long, List<NoticeReactionDTO>> result = new HashMap<>();
        for (Long noticeId : noticeIds) {
            List<ArticleReaction> reactions = reactionsByNotice.getOrDefault(noticeId, Collections.emptyList());
            result.put(noticeId, mapReactionsToDTO(reactions, currentUser));
        }

        return result;
    }

    private List<NoticeReactionDTO> mapReactionsToDTO(List<ArticleReaction> reactions, User currentUser) {
        Map<String, Long> counts = reactions.stream()
                .collect(Collectors.groupingBy(ArticleReaction::getType, Collectors.counting()));

        List<String> userTypes = reactions.stream()
                .filter(r -> r.getUser().getId().equals(currentUser.getId()))
                .map(ArticleReaction::getType)
                .collect(Collectors.toList());

        return counts.entrySet().stream()
                .map(entry -> NoticeReactionDTO.builder()
                        .type(entry.getKey())
                        .count(entry.getValue())
                        .reacted(userTypes.contains(entry.getKey()))
                        .build())
                .collect(Collectors.toList());
    }

    private NoticeResponse mapToResponse(Article notice) {
        return NoticeResponse.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .pinned(notice.isPinned())
                .authorUsername(notice.getAuthor().getUsername())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}
