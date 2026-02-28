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
    public PagedResponse<ArticleResponse> getAllArticles(Pageable pageable, User currentUser) {
        Page<ArticleResponse> page = articleRepository.findAllByOrderByPinnedDescUpdatedAtDesc(pageable)
                .map(article -> mapToResponse(article, currentUser));
        return PagedResponse.fromPage(page, cacheService.getTimestamp(ARTICLE_LIST_KEY));
    }

    @Transactional(readOnly = true)
    public ArticleResponse getArticleById(Long id, User currentUser) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ARTICLE_NOT_FOUND"));
        return mapToResponse(article, currentUser);
    }

    @Transactional
    public ArticleResponse createArticle(ArticleRequest request, User author) {
        if (request.getType() == ArticleType.ANNOUNCEMENT && !author.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            throw new BusinessException("ADMIN_REQUIRED_FOR_ANNOUNCEMENT");
        }

        Article article = new Article(request.getTitle(), request.getContent(), request.getType(), request.isPinned(), author);
        
        if (request.getType() == ArticleType.COMMISSION && request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new BusinessException("ASSIGNEE_NOT_FOUND"));
            article.setAssignee(assignee);
        }

        Article savedArticle = articleRepository.saveAndFlush(article);
        cacheService.updateTimestamp(ARTICLE_LIST_KEY);
        return mapToResponse(savedArticle, author);
    }

    @Transactional
    public ArticleResponse updateArticle(Long id, ArticleRequest request, User currentUser) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ARTICLE_NOT_FOUND"));

        boolean isAdmin = currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"));
        boolean isAuthor = article.getAuthor().getId().equals(currentUser.getId());

        if (!isAdmin && !isAuthor) {
            throw new BusinessException("UNAUTHORIZED_ARTICLE_UPDATE");
        }

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
        return mapToResponse(updatedArticle, currentUser);
    }

    @Transactional
    public void deleteArticle(Long id, User currentUser) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ARTICLE_NOT_FOUND"));

        boolean isAdmin = currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"));
        boolean isAuthor = article.getAuthor().getId().equals(currentUser.getId());

        if (!isAdmin && !isAuthor) {
            throw new BusinessException("UNAUTHORIZED_ARTICLE_DELETE");
        }

        articleRepository.delete(article);
        cacheService.updateTimestamp(ARTICLE_LIST_KEY);
    }

    @Transactional
    public List<NoticeReactionDTO> toggleReaction(Long articleId, User user, String type) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException("ARTICLE_NOT_FOUND"));

        java.util.Optional<ArticleReaction> reactionOpt = articleReactionRepository.findByArticleAndUserAndType(article, user, type);

        if (reactionOpt.isPresent()) {
            articleReactionRepository.delete(reactionOpt.get());
        } else {
            User managedUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
            ArticleReaction reaction = new ArticleReaction(article, managedUser, type);
            articleReactionRepository.save(reaction);
        }

        return getReactions(articleId, user);
    }

    @Transactional(readOnly = true)
    public List<NoticeReactionDTO> getReactions(Long articleId, User currentUser) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException("ARTICLE_NOT_FOUND"));
        
        List<ArticleReaction> reactions = articleReactionRepository.findByArticle(article);
        return mapReactionsToDTO(reactions, currentUser);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<NoticeReactionDTO>> getReactionsBatch(List<Long> articleIds, User currentUser) {
        if (articleIds == null || articleIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        List<Article> articles = articleRepository.findAllById(articleIds);
        List<ArticleReaction> allReactions = articleReactionRepository.findByArticleIn(articles);

        Map<Long, List<ArticleReaction>> reactionsByArticle = allReactions.stream()
                .collect(Collectors.groupingBy(r -> r.getArticle().getId()));

        Map<Long, List<NoticeReactionDTO>> result = new java.util.HashMap<>();
        for (Long articleId : articleIds) {
            List<ArticleReaction> reactions = reactionsByArticle.getOrDefault(articleId, java.util.Collections.emptyList());
            result.put(articleId, mapReactionsToDTO(reactions, currentUser));
        }

        return result;
    }

    private ArticleResponse mapToResponse(Article article, User currentUser) {
        return ArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .type(article.getType())
                .pinned(article.isPinned())
                .authorUsername(article.getAuthor().getUsername())
                .assigneeUsername(article.getAssignee() != null ? article.getAssignee().getUsername() : null)
                .reactions(mapReactionsToDTO(article.getReactions(), currentUser))
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    private List<NoticeReactionDTO> mapReactionsToDTO(List<ArticleReaction> reactions, User currentUser) {
        if (reactions == null) return List.of();
        
        Map<String, Long> counts = reactions.stream()
                .collect(Collectors.groupingBy(ArticleReaction::getType, Collectors.counting()));

        List<String> userTypes = currentUser != null ? reactions.stream()
                .filter(r -> r.getUser().getId().equals(currentUser.getId()))
                .map(ArticleReaction::getType)
                .collect(Collectors.toList()) : List.of();

        return counts.entrySet().stream()
                .map(entry -> NoticeReactionDTO.builder()
                        .type(entry.getKey())
                        .count(entry.getValue())
                        .reacted(userTypes.contains(entry.getKey()))
                        .build())
                .collect(Collectors.toList());
    }
}
