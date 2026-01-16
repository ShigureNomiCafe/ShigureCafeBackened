package cafe.shigure.ShigureCafeBackened.dto;

import cafe.shigure.ShigureCafeBackened.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationDetailsResponse {
    private String username;
    private String nickname;
    private String email;
    private UserStatus status;
    private String avatarUrl;
    private String auditCode;
    private boolean isExpired;
}
