package cafe.shigure.ShigureCafeBackend.service;

import cafe.shigure.ShigureCafeBackend.dto.ArticleRequest;
import cafe.shigure.ShigureCafeBackend.dto.ArticleResponse;
import cafe.shigure.ShigureCafeBackend.dto.NoticeReactionDTO;
import cafe.shigure.ShigureCafeBackend.dto.PagedResponse;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final ArticleReactionRepository articleReactionRepository;
    private final CacheService cacheService;

    public static final String ARTICLE_LIST_KEY = "articles:list:timestamp";

    @Transactional(readOnly = true)
    public PagedResponse<ArticleResponse> getAllArticles(Pageable pageable) {
        Page<ArticleResponse> page = articleRepository.findAllByOrderByPinnedDescUpdatedAtDesc(pageable)
                .map(this::mapToResponse);
        return PagedResponse.fromPage(page, cacheService.getTimestamp(ARTICLE_LIST_KEY));
    }

    @Transactional(readOnly = true)
    public ArticleResponse getArticleById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ARTICLE_NOT_FOUND"));
        return mapToResponse(article);
    }

    @Transactional
    public ArticleResponse createArticle(ArticleRequest request, User author) {
        Article article = new Article(request.getTitle(), request.getContent(), request.getType(), request.isPinned(), author);
        
        if (request.getType() == ArticleType.COMMISSION && request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new BusinessException("ASSIGNEE_NOT_FOUND"));
            article.setAssignee(assignee);
        }

        Article savedArticle = articleRepository.saveAndFlush(article);
        cacheService.updateTimestamp(ARTICLE_LIST_KEY);
        return mapToResponse(savedArticle);
    }

    @Transactional
    public ArticleResponse updateArticle(Long id, ArticleRequest request) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ARTICLE_NOT_FOUND"));

        article.setTitle(request.getTitle());
        article.setContent(request.getContent());
        article.setType(request.getType());
        article.setPinned(request.isPinned());

        if (request.getType() == ArticleType.COMMISSION && request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new BusinessException("ASSIGNEE_NOT_FOUND"));
            article.setAssignee(assignee);
        } else {
            article.setAssignee(null);
        }

        Article updatedArticle = articleRepository.saveAndFlush(article);
        cacheService.updateTimestamp(ARTICLE_LIST_KEY);
        return mapToResponse(updatedArticle);
    }

    @Transactional
    public void deleteArticle(Long id) {
        if (!articleRepository.existsById(id)) {
            throw new BusinessException("ARTICLE_NOT_FOUND");
        }
        articleRepository.deleteById(id);
        cacheService.updateTimestamp(ARTICLE_LIST_KEY);
    }

    private ArticleResponse mapToResponse(Article article) {
        return ArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .type(article.getType())
                .pinned(article.isPinned())
                .authorUsername(article.getAuthor().getUsername())
                .assigneeUsername(article.getAssignee() != null ? article.getAssignee().getUsername() : null)
                .reactions(mapReactionsToDTO(article.getReactions(), null)) // Article service response may not always have current user
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    private List<NoticeReactionDTO> mapReactionsToDTO(List<ArticleReaction> reactions, User currentUser) {
        if (reactions == null) return List.of();
        
        Map<String, Long> counts = reactions.stream()
                .collect(Collectors.groupingBy(ArticleReaction::getType, Collectors.counting()));

        return counts.entrySet().stream()
                .map(entry -> NoticeReactionDTO.builder()
                        .type(entry.getKey())
                        .count(entry.getValue())
                        .reacted(currentUser != null && reactions.stream().anyMatch(r -> r.getUser().getId().equals(currentUser.getId()) && r.getType().equals(entry.getKey())))
                        .build())
                .collect(Collectors.toList());
    }
}
