package cafe.shigure.ShigureCafeBackend.controller;

import cafe.shigure.ShigureCafeBackend.annotation.RateLimit;
import cafe.shigure.ShigureCafeBackend.dto.*;
import cafe.shigure.ShigureCafeBackend.model.User;
import cafe.shigure.ShigureCafeBackend.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/getArticleList")
    @RateLimit(key = "articles:list", expression = "#currentUser != null ? #currentUser.id : 'anonymous'", period = 500, capacity = 10)
    public ResponseEntity<PagedResponse<ArticleResponse>> getArticleList(
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(articleService.getAllArticles(pageable, currentUser));
    }

    @GetMapping("/getArticleDetails")
    public ResponseEntity<ArticleResponse> getArticleDetails(
            @RequestParam Long id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(articleService.getArticleById(id, currentUser));
    }

    @PostMapping("/createArticle")
    public ResponseEntity<ArticleResponse> createArticle(
            @Valid @RequestBody ArticleRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(articleService.createArticle(request, currentUser));
    }

    @PostMapping("/updateArticle")
    public ResponseEntity<ArticleResponse> updateArticle(
            @Valid @RequestBody ArticleUpdateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(articleService.updateArticle(request.getId(), request, currentUser));
    }

    @PostMapping("/deleteArticle")
    public ResponseEntity<Void> deleteArticle(
            @Valid @RequestBody IdRequest request,
            @AuthenticationPrincipal User currentUser) {
        articleService.deleteArticle(request.getId(), currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/toggleArticleReaction")
    public ResponseEntity<List<NoticeReactionDTO>> toggleArticleReaction(
            @Valid @RequestBody ArticleReactionToggleRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(articleService.toggleReaction(request.getArticleId(), currentUser, request.getType()));
    }

    @PostMapping("/getArticlesReactionsBatch")
    public ResponseEntity<Map<Long, List<NoticeReactionDTO>>> getArticlesReactionsBatch(
            @RequestBody List<Long> articleIds,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(articleService.getReactionsBatch(articleIds, currentUser));
    }
}
