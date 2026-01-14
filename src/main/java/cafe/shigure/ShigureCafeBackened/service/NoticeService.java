package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.dto.NoticeRequest;
import cafe.shigure.ShigureCafeBackened.dto.NoticeResponse;
import cafe.shigure.ShigureCafeBackened.dto.NoticeReactionDTO;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cafe.shigure.ShigureCafeBackened.dto.PagedResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final NoticeReactionRepository noticeReactionRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String NOTICE_LIST_LAST_UPDATED_KEY = "notice_list:last_updated";

    private void updateGlobalNoticeListTimestamp() {
        redisTemplate.opsForValue().set(NOTICE_LIST_LAST_UPDATED_KEY, String.valueOf(System.currentTimeMillis()));
    }

    public Long getGlobalNoticeListTimestamp() {
        String ts = redisTemplate.opsForValue().get(NOTICE_LIST_LAST_UPDATED_KEY);
        return ts != null ? Long.parseLong(ts) : 0L;
    }

    @Transactional(readOnly = true)
    public PagedResponse<NoticeResponse> getAllNotices(Pageable pageable) {
        Page<NoticeResponse> page = noticeRepository.findAllByOrderByPinnedDescUpdatedAtDesc(pageable)
                .map(notice -> mapToResponse(notice));
        Long timestamp = getGlobalNoticeListTimestamp();
        if (timestamp == 0L) {
            timestamp = System.currentTimeMillis();
            updateGlobalNoticeListTimestamp();
        }
        return PagedResponse.fromPage(page, timestamp);
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
        // Force flush to ensure timestamps and ID are generated before mapping to
        // response
        Notice savedNotice = noticeRepository.saveAndFlush(notice);
        updateGlobalNoticeListTimestamp();
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
        updateGlobalNoticeListTimestamp();
        return mapToResponse(updatedNotice);
    }

    @Transactional
    public void deleteNotice(Long id) {
        if (!noticeRepository.existsById(id)) {
            throw new BusinessException("NOTICE_NOT_FOUND");
        }
        noticeRepository.deleteById(id);
        updateGlobalNoticeListTimestamp();
    }

    @Transactional
    public List<NoticeReactionDTO> toggleReaction(Long noticeId, User user, String emoji) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));

        Optional<NoticeReaction> reactionOpt = noticeReactionRepository.findByNoticeAndUserAndEmoji(notice, user, emoji);

        if (reactionOpt.isPresent()) {
            noticeReactionRepository.delete(reactionOpt.get());
        } else {
            // Ensure we use a managed user entity
            User managedUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
            NoticeReaction reaction = new NoticeReaction(notice, managedUser, emoji);
            noticeReactionRepository.save(reaction);
        }

        // We explicitly do NOT update the notice timestamp here.
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

        // Group reactions by notice ID
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
        Map<String, Long> counts = reactions.stream()
                .collect(Collectors.groupingBy(NoticeReaction::getEmoji, Collectors.counting()));

        List<String> userEmojis = reactions.stream()
                .filter(r -> r.getUser().getId().equals(currentUser.getId()))
                .map(NoticeReaction::getEmoji)
                .collect(Collectors.toList());

        return counts.entrySet().stream()
                .map(entry -> NoticeReactionDTO.builder()
                        .emoji(entry.getKey())
                        .count(entry.getValue())
                        .reacted(userEmojis.contains(entry.getKey()))
                        .build())
                .collect(Collectors.toList());
    }

    private NoticeResponse mapToResponse(Notice notice) {
        return NoticeResponse.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .pinned(notice.isPinned())
                .authorNickname(notice.getAuthor().getNickname())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}