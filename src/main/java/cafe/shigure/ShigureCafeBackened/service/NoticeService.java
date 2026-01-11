package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.dto.NoticeRequest;
import cafe.shigure.ShigureCafeBackened.dto.NoticeResponse;
import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import cafe.shigure.ShigureCafeBackened.model.Notice;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public List<NoticeResponse> getAllNotices() {
        return noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public NoticeResponse getNoticeById(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));
        return mapToResponse(notice);
    }

    @Transactional
    public NoticeResponse createNotice(NoticeRequest request, User author) {
        Notice notice = new Notice(request.getTitle(), request.getContent(), request.isPinned(), author);
        Notice savedNotice = noticeRepository.save(notice);
        return mapToResponse(savedNotice);
    }

    @Transactional
    public NoticeResponse updateNotice(Long id, NoticeRequest request) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTICE_NOT_FOUND"));
        
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setPinned(request.isPinned());
        
        Notice updatedNotice = noticeRepository.save(notice);
        return mapToResponse(updatedNotice);
    }

    @Transactional
    public void deleteNotice(Long id) {
        if (!noticeRepository.existsById(id)) {
            throw new BusinessException("NOTICE_NOT_FOUND");
        }
        noticeRepository.deleteById(id);
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
