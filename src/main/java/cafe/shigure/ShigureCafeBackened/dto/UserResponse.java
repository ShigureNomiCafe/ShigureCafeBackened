package cafe.shigure.ShigureCafeBackened.dto;

import cafe.shigure.ShigureCafeBackened.model.Role;
import cafe.shigure.ShigureCafeBackened.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private String username;
    private String nickname;
    private String email;
    private Role role;
    private UserStatus status;
    private boolean twoFactorEnabled;
    private boolean email2faEnabled;
    private boolean totpEnabled;
}
