package cafe.shigure.ShigureCafeBackened.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

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
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false, unique = true)
    private String auditCode;

    @Column(nullable = false)
    private long expiryDate;

    @Column(updatable = false, nullable = false)
    private long createdAt;

    public UserAudit(User user, String auditCode, int expirationDays) {
        this.user = user;
        this.auditCode = auditCode;
        this.expiryDate = Instant.now().toEpochMilli() + (long) expirationDays * 24 * 60 * 60 * 1000;
    }

    public boolean isExpired() {
        return Instant.now().toEpochMilli() > expiryDate;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now().toEpochMilli();
    }
}