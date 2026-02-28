package cafe.shigure.ShigureCafeBackend.dto;

import cafe.shigure.ShigureCafeBackend.model.ArticleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponse {
    private Long id;
    private String title;
    private String content;
    private ArticleType type;
    private boolean pinned;
    private String authorUsername;
    private String assigneeUsername;
    private List<NoticeReactionDTO> reactions;
    private long createdAt;
    private long updatedAt;
}
