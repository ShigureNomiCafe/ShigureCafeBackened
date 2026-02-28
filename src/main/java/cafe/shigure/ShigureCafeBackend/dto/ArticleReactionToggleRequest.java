package cafe.shigure.ShigureCafeBackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArticleReactionToggleRequest {
    @NotNull(message = "ARTICLE_ID_REQUIRED")
    private Long articleId;

    @NotBlank(message = "REACTION_TYPE_REQUIRED")
    private String type;
}
