package cafe.shigure.ShigureCafeBackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IdRequest {
    @NotNull(message = "ID_REQUIRED")
    private Long id;
}
