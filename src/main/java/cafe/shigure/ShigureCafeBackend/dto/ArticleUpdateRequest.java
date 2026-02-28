package cafe.shigure.ShigureCafeBackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleUpdateRequest extends ArticleRequest {
    @NotNull(message = "ID_REQUIRED")
    private Long id;
}
