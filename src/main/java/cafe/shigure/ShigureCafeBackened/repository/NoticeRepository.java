package cafe.shigure.ShigureCafeBackened.repository;

import cafe.shigure.ShigureCafeBackened.model.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {
    @Query(value = "SELECT n FROM Notice n JOIN FETCH n.author ORDER BY n.pinned DESC, n.updatedAt DESC", countQuery = "SELECT count(n) FROM Notice n")
    Page<Notice> findAllByOrderByPinnedDescUpdatedAtDesc(Pageable pageable);

    @Query("SELECT MAX(n.updatedAt) FROM Notice n")
    Optional<Long> findMaxUpdatedAt();
}
