package cafe.shigure.ShigureCafeBackend.repository;

import cafe.shigure.ShigureCafeBackend.model.Article;
import cafe.shigure.ShigureCafeBackend.model.ArticleReaction;
import cafe.shigure.ShigureCafeBackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleReactionRepository extends JpaRepository<ArticleReaction, Long> {
    Optional<ArticleReaction> findByArticleAndUserAndType(Article article, User user, String type);
    List<ArticleReaction> findByArticle(Article article);
    List<ArticleReaction> findByArticleIn(List<Article> articles);
}
