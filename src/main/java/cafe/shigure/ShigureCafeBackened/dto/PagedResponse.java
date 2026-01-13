package cafe.shigure.ShigureCafeBackened.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private Long timestamp;

    public static <T> PagedResponse<T> fromPage(Page<T> page) {
        return fromPage(page, System.currentTimeMillis());
    }

    public static <T> PagedResponse<T> fromPage(Page<T> page, Long timestamp) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast(),
                timestamp);
    }
}
