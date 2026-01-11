package cafe.shigure.ShigureCafeBackened.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String title;
    private String content;
    private boolean pinned;
    private String authorNickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
