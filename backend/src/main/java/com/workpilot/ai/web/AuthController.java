package com.workpilot.ai.web;

import com.workpilot.ai.dto.CsrfResponse;
import com.workpilot.ai.dto.LoginRequest;
import com.workpilot.ai.dto.MessageResponse;
import com.workpilot.ai.dto.RegisterRequest;
import com.workpilot.ai.dto.UserProfileResponse;
import com.workpilot.ai.security.CustomUserDetails;
import com.workpilot.ai.security.SecurityUtils;
import com.workpilot.ai.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Register an account and optionally create an organization")
    public UserProfileResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in with email and password and start a cookie session")
    public UserProfileResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return authService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current session")
    public MessageResponse logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return new MessageResponse("Logged out");
    }

    @GetMapping("/me")
    @Operation(summary = "Return the current user and organization memberships")
    public UserProfileResponse me() {
        CustomUserDetails principal = SecurityUtils.currentUser();
        return authService.currentUser(principal);
    }

    @GetMapping("/csrf")
    @SecurityRequirements
    @Operation(summary = "Issue the CSRF token required for cookie-authenticated requests")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName(), csrfToken.getParameterName());
    }
}
