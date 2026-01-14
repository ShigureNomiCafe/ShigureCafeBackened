package cafe.shigure.ShigureCafeBackened.repository;

import cafe.shigure.ShigureCafeBackened.model.Notice;
import cafe.shigure.ShigureCafeBackened.model.NoticeReaction;
import cafe.shigure.ShigureCafeBackened.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoticeReactionRepository extends JpaRepository<NoticeReaction, Long> {
    List<NoticeReaction> findByNotice(Notice notice);
    Optional<NoticeReaction> findByNoticeAndUserAndEmoji(Notice notice, User user, String emoji);
    List<NoticeReaction> findByNoticeAndUser(Notice notice, User user);
    List<NoticeReaction> findByNoticeIn(List<Notice> notices);
    void deleteByNoticeAndUserAndEmoji(Notice notice, User user, String emoji);
}
