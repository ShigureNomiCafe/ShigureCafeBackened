package cafe.shigure.ShigureCafeBackened.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeReactionDTO implements Serializable {
    private String emoji;
    private Long count;
    private boolean reacted;
}
