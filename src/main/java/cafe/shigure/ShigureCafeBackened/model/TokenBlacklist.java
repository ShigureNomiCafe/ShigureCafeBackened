package cafe.shigure.ShigureCafeBackened.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "token_blacklist")
public class TokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(nullable = false)
    private long expirationDate;

    public TokenBlacklist(String token, long expirationDate) {
        this.token = token;
        this.expirationDate = expirationDate;
    }
}
