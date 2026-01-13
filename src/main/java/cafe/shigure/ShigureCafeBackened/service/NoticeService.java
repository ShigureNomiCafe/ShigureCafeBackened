package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.dto.NoticeRequest;
import cafe.shigure.ShigureCafeBackened.dto.NoticeResponse;
import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import cafe.shigure.ShigureCafeBackened.model.Notice;
import cafe.shigure.ShigureCafeBackened.model.NoticeReaction;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.repository.NoticeReactionRepository;
import cafe.shigure.ShigureCafeBackened.repository.NoticeRepository;
import cafe.shigure.ShigureCafeBackened.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cafe.shigure.ShigureCafeBackened.dto.PagedResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean checkModified(Long t) {
        if (t == null) {
            return true;
        }
        return noticeRepository.findMaxUpdatedAt()
                .map(upd -> upd > t)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public PagedResponse<NoticeResponse> getAllNotices(Pageable pageable, User currentUser) {
        Page<NoticeResponse> page = noticeRepository.findAllByOrderByPinnedDescUpdatedAtDesc(pageable)
                .map(notice -> mapToResponse(notice, currentUser));
        Long timestamp = noticeRepository.findMaxUpdatedAt()
                .orElse(0L);
        return PagedResponse.fromPage(page, timestamp);
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNoticeById(Long id, User currentUser) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));
        return mapToResponse(notice, currentUser);
    }

    @Transactional
    public NoticeResponse createNotice(NoticeRequest request, User author) {
        Notice notice = new Notice(request.getTitle(), request.getContent(), request.isPinned(), author);
        // Force flush to ensure timestamps and ID are generated before mapping to
        // response
        Notice savedNotice = noticeRepository.saveAndFlush(notice);
        return mapToResponse(savedNotice, author);
    }

    @Transactional
    public NoticeResponse updateNotice(Long id, NoticeRequest request, User currentUser) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));

        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setPinned(request.isPinned());

        Notice updatedNotice = noticeRepository.saveAndFlush(notice);
        return mapToResponse(updatedNotice, currentUser);
    }

    @Transactional
    public void deleteNotice(Long id) {
        if (!noticeRepository.existsById(id)) {
            throw new BusinessException("NOTICE_NOT_FOUND");
        }
        noticeRepository.deleteById(id);
    }

    @Transactional
    public NoticeResponse toggleReaction(Long noticeId, User user, String emoji) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));

        // Use noticeRepository to get managed user if possible, or just use the passed
        // user
        // Actually, better to use userRepository if we want to be 100% sure it's
        // managed
        // But let's try to just ensure we compare by ID correctly.

        Optional<NoticeReaction> reactionOpt = notice.getReactions().stream()
                .filter(r -> r.getUser().getId().equals(user.getId()) && r.getEmoji().equals(emoji))
                .findFirst();

        if (reactionOpt.isPresent()) {
            notice.getReactions().remove(reactionOpt.get());
        } else {
            // Ensure we use a managed user entity to avoid detached entity issues
            User managedUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
            NoticeReaction reaction = new NoticeReaction(notice, managedUser, emoji);
            notice.getReactions().add(reaction);
        }

        Notice savedNotice = noticeRepository.saveAndFlush(notice);
        return mapToResponse(savedNotice, user);
    }

    private NoticeResponse mapToResponse(Notice notice, User currentUser) {
        List<NoticeReaction> reactions = notice.getReactions();

        Map<String, Long> counts = reactions.stream()
                .collect(Collectors.groupingBy(NoticeReaction::getEmoji, Collectors.counting()));

        List<String> userEmojis = reactions.stream()
                .filter(r -> r.getUser().getId().equals(currentUser.getId()))
                .map(NoticeReaction::getEmoji)
                .collect(Collectors.toList());

        List<NoticeResponse.ReactionCount> reactionCounts = counts.entrySet().stream()
                .map(entry -> NoticeResponse.ReactionCount.builder()
                        .emoji(entry.getKey())
                        .count(entry.getValue())
                        .reacted(userEmojis.contains(entry.getKey()))
                        .build())
                .collect(Collectors.toList());

        return NoticeResponse.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .pinned(notice.isPinned())
                .authorNickname(notice.getAuthor().getNickname())
                .reactions(reactionCounts)
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}
