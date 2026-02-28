package cafe.shigure.ShigureCafeBackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "article_reactions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"article_id", "user_id", "type"})
})
public class ArticleReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false)
    private String type;

    public ArticleReaction(Article article, User user, String type) {
        this.article = article;
        this.user = user;
        this.type = type;
    }
}
