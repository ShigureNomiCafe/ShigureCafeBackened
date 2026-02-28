package cafe.shigure.ShigureCafeBackend.dto;

import cafe.shigure.ShigureCafeBackend.model.ArticleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRequest {
    @NotBlank(message = "TITLE_REQUIRED")
    private String title;

    @NotBlank(message = "CONTENT_REQUIRED")
    private String content;

    @NotNull(message = "TYPE_REQUIRED")
    private ArticleType type;

    private boolean pinned;

    private Long assigneeId;
}
