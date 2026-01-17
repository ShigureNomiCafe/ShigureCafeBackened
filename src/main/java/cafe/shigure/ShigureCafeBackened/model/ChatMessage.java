package cafe.shigure.ShigureCafeBackened.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(updatable = false, nullable = false)
    private long createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now().toEpochMilli();
    }
}
