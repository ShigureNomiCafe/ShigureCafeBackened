package cafe.shigure.ShigureCafeBackened.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoticeReactionRequest {
    @NotNull
    private String type;
}
