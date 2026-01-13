package cafe.shigure.ShigureCafeBackened.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@Table(name = "user_audits")
public class UserAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String auditCode;

    @Column(nullable = false)
    private long expiryDate;

    @Column(updatable = false, nullable = false)
    private long createdAt;

    @Column(nullable = false)
    private long updatedAt;

    public UserAudit(User user, String auditCode, int expirationDays) {
        this.user = user;
        this.auditCode = auditCode;
        this.expiryDate = System.currentTimeMillis() + (long) expirationDays * 24 * 60 * 60 * 1000;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryDate;
    }

    @PrePersist
    protected void onCreate() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = System.currentTimeMillis();
    }
}
