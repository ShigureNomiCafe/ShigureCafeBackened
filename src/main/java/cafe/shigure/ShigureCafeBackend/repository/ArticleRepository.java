package cafe.shigure.ShigureCafeBackend.repository;

import cafe.shigure.ShigureCafeBackend.model.Article;
import cafe.shigure.ShigureCafeBackend.model.ArticleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    @Query(value = "SELECT a FROM Article a JOIN FETCH a.author ORDER BY a.pinned DESC, a.updatedAt DESC", countQuery = "SELECT count(a) FROM Article a")
    Page<Article> findAllByOrderByPinnedDescUpdatedAtDesc(Pageable pageable);

    @Query(value = "SELECT a FROM Article a JOIN FETCH a.author WHERE a.type = :type ORDER BY a.pinned DESC, a.updatedAt DESC", countQuery = "SELECT count(a) FROM Article a WHERE a.type = :type")
    Page<Article> findByTypeOrderByPinnedDescUpdatedAtDesc(@Param("type") ArticleType type, Pageable pageable);
}
