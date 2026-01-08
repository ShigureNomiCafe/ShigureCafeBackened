package cafe.shigure.UserService.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<java.util.Map<String, String>> handleBusinessException(BusinessException e) {
        // 返回 400 Bad Request 和具体的错误信息 (JSON)
        return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("message", e.getMessage()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<String> handleDisabledException(DisabledException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Account is disabled or pending approval");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<java.util.Map<String, String>> handleAuthenticationException(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Collections.singletonMap("message", "Authentication failed: " + e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<java.util.Map<String, String>> handleValidationExceptions(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<java.util.Map<String, String>> handleAllExceptions(Exception e) {
        // Log the exception for debugging
        e.printStackTrace();
        
        String message = "Internal Server Error";
        if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
            message = "Data integrity violation: possibly field too long or duplicate unique value";
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(java.util.Collections.singletonMap("message", message));
    }
}
