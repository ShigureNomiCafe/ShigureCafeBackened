package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.dto.*;
import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import cafe.shigure.ShigureCafeBackened.model.Notice;
import cafe.shigure.ShigureCafeBackened.model.NoticeReaction;
import cafe.shigure.ShigureCafeBackened.model.ReactionType;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.repository.NoticeReactionRepository;
import cafe.shigure.ShigureCafeBackened.repository.NoticeRepository;
import cafe.shigure.ShigureCafeBackened.repository.UserRepository;
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

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final NoticeReactionRepository noticeReactionRepository;
    private final CacheService cacheService;

    @Transactional(readOnly = true)
    public PagedResponse<NoticeResponse> getAllNotices(Pageable pageable) {
        Page<NoticeResponse> page = noticeRepository.findAllByOrderByPinnedDescUpdatedAtDesc(pageable)
                .map(this::mapToResponse);
        return PagedResponse.fromPage(page, cacheService.getTimestamp(CacheService.NOTICE_LIST_KEY));
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNoticeById(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));
        return mapToResponse(notice);
    }

    @Transactional
    public NoticeResponse createNotice(NoticeRequest request, User author) {
        Notice notice = new Notice(request.getTitle(), request.getContent(), request.isPinned(), author);
        Notice savedNotice = noticeRepository.saveAndFlush(notice);
        cacheService.updateTimestamp(CacheService.NOTICE_LIST_KEY);
        return mapToResponse(savedNotice);
    }

    @Transactional
    public NoticeResponse updateNotice(Long id, NoticeRequest request, User currentUser) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));

        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setPinned(request.isPinned());

        Notice updatedNotice = noticeRepository.saveAndFlush(notice);
        cacheService.updateTimestamp(CacheService.NOTICE_LIST_KEY);
        return mapToResponse(updatedNotice);
    }

    @Transactional
    public void deleteNotice(Long id) {
        if (!noticeRepository.existsById(id)) {
            throw new BusinessException("NOTICE_NOT_FOUND");
        }
        noticeRepository.deleteById(id);
        cacheService.updateTimestamp(CacheService.NOTICE_LIST_KEY);
    }

    @Transactional
    public List<NoticeReactionDTO> toggleReaction(Long noticeId, User user, ReactionType type) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));

        Optional<NoticeReaction> reactionOpt = noticeReactionRepository.findByNoticeAndUserAndType(notice, user, type);

        if (reactionOpt.isPresent()) {
            noticeReactionRepository.delete(reactionOpt.get());
        } else {
            User managedUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
            NoticeReaction reaction = new NoticeReaction(notice, managedUser, type);
            noticeReactionRepository.save(reaction);
        }

        return getReactions(noticeId, user);
    }

    @Transactional(readOnly = true)
    public List<NoticeReactionDTO> getReactions(Long noticeId, User currentUser) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));
        
        List<NoticeReaction> reactions = noticeReactionRepository.findByNotice(notice);
        return mapReactionsToDTO(reactions, currentUser);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<NoticeReactionDTO>> getReactionsBatch(List<Long> noticeIds, User currentUser) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Notice> notices = noticeRepository.findAllById(noticeIds);
        List<NoticeReaction> allReactions = noticeReactionRepository.findByNoticeIn(notices);

        Map<Long, List<NoticeReaction>> reactionsByNotice = allReactions.stream()
                .collect(Collectors.groupingBy(r -> r.getNotice().getId()));

        Map<Long, List<NoticeReactionDTO>> result = new HashMap<>();
        for (Long noticeId : noticeIds) {
            List<NoticeReaction> reactions = reactionsByNotice.getOrDefault(noticeId, Collections.emptyList());
            result.put(noticeId, mapReactionsToDTO(reactions, currentUser));
        }

        return result;
    }

    private List<NoticeReactionDTO> mapReactionsToDTO(List<NoticeReaction> reactions, User currentUser) {
        Map<ReactionType, Long> counts = reactions.stream()
                .collect(Collectors.groupingBy(NoticeReaction::getType, Collectors.counting()));

        List<ReactionType> userTypes = reactions.stream()
                .filter(r -> r.getUser().getId().equals(currentUser.getId()))
                .map(NoticeReaction::getType)
                .collect(Collectors.toList());

        return counts.entrySet().stream()
                .map(entry -> NoticeReactionDTO.builder()
                        .type(entry.getKey())
                        .count(entry.getValue())
                        .reacted(userTypes.contains(entry.getKey()))
                        .build())
                .collect(Collectors.toList());
    }

    private NoticeResponse mapToResponse(Notice notice) {
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