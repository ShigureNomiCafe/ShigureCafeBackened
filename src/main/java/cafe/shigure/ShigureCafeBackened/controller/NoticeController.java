package cafe.shigure.ShigureCafeBackened.controller;

import cafe.shigure.ShigureCafeBackened.dto.NoticeRequest;
import cafe.shigure.ShigureCafeBackened.dto.NoticeResponse;
import cafe.shigure.ShigureCafeBackened.dto.NoticeReactionRequest;
import cafe.shigure.ShigureCafeBackened.dto.NoticeReactionDTO;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.service.NoticeService;
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
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;
    private final cafe.shigure.ShigureCafeBackened.service.RateLimitService rateLimitService;

    @GetMapping
    public ResponseEntity<?> getAllNotices(
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser != null) {
            rateLimitService.checkRateLimit("notices:list:" + currentUser.getId(), 1);
        }
        return ResponseEntity.ok(noticeService.getAllNotices(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoticeResponse> getNoticeById(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(noticeService.getNoticeById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<NoticeResponse> createNotice(
            @Valid @RequestBody NoticeRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(noticeService.createNotice(request, currentUser));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<NoticeResponse> updateNotice(
            @PathVariable Long id,
            @Valid @RequestBody NoticeRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(noticeService.updateNotice(id, request, currentUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteNotice(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/reactions")
    public ResponseEntity<List<NoticeReactionDTO>> getReactions(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(noticeService.getReactions(id, currentUser));
    }

    @PostMapping("/reactions/batch")
    public ResponseEntity<Map<Long, List<NoticeReactionDTO>>> getReactionsBatch(
            @RequestBody List<Long> noticeIds,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(noticeService.getReactionsBatch(noticeIds, currentUser));
    }

    @PostMapping("/{id}/reactions")
    public ResponseEntity<List<NoticeReactionDTO>> toggleReaction(
            @PathVariable Long id,
            @Valid @RequestBody NoticeReactionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(noticeService.toggleReaction(id, currentUser, request.getEmoji()));
    }
}
