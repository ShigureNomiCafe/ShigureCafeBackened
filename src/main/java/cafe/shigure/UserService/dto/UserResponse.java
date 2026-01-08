package cafe.shigure.UserService.dto;

import cafe.shigure.UserService.model.Role;
import cafe.shigure.UserService.model.UserStatus;
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
}
