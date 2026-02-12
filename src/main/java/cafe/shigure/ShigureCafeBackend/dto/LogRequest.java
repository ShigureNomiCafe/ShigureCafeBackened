package cafe.shigure.ShigureCafeBackend.dto;

import lombok.Data;

@Data
public class LogRequest {
    private String level;
    private String source;
    private String content;
    private Long timestamp; // Optional: if provided, use it; otherwise use current time
}
