package cafe.shigure.ShigureCafeBackend.repository;

import cafe.shigure.ShigureCafeBackend.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
}
