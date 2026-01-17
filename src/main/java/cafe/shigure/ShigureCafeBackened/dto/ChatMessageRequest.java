package cafe.shigure.ShigureCafeBackened.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
    @NotBlank
    @Size(max = 32)
    String name,

    @NotBlank
    String message
) {
}
