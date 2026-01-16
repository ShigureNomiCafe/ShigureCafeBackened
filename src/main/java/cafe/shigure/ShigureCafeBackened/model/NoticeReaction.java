package cafe.shigure.ShigureCafeBackened.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notice_reactions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"notice_id", "user_id", "type"})
})
public class NoticeReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String type;

    public NoticeReaction(Notice notice, User user, String type) {
        this.notice = notice;
        this.user = user;
        this.type = type;
    }
}
