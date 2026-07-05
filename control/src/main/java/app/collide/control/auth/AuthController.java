package app.collide.control.auth;

import app.collide.control.auth.dto.AuthResponse;
import app.collide.control.auth.dto.ChangePasswordRequest;
import app.collide.control.auth.dto.GoogleLoginRequest;
import app.collide.control.auth.dto.LoginRequest;
import app.collide.control.auth.dto.RefreshRequest;
import app.collide.control.auth.dto.SignupRequest;
import app.collide.control.auth.dto.UpdateProfileRequest;
import app.collide.control.auth.dto.UserDto;
import app.collide.control.common.ApiException;
import app.collide.control.common.ApiResponse;
import app.collide.control.common.ClientInfo;
import app.collide.control.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth API. Thin adapter: extracts client metadata, applies per-IP rate limits, and
 * delegates to {@link AuthService}. Every response uses the uniform success envelope;
 * failures are turned into the error envelope by the global exception handler.
 *
 * Public (see SecurityConfig): signup, login, google, refresh, logout.
 * Authenticated: logout-all, me, change-password, update-profile, delete-account.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final RateLimiter rateLimiter;
    private final int loginMax;
    private final int loginWindow;
    private final int signupMax;
    private final int signupWindow;

    public AuthController(
            AuthService auth,
            RateLimiter rateLimiter,
            @Value("${collide.security.rate-limit.login-max-attempts}") int loginMax,
            @Value("${collide.security.rate-limit.login-window-seconds}") int loginWindow,
            @Value("${collide.security.rate-limit.signup-max-attempts}") int signupMax,
            @Value("${collide.security.rate-limit.signup-window-seconds}") int signupWindow) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
        this.loginMax = loginMax;
        this.loginWindow = loginWindow;
        this.signupMax = signupMax;
        this.signupWindow = signupWindow;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest req, HttpServletRequest http) {
        ClientInfo client = ClientInfo.from(http);
        rateLimit("signup:" + client.ip(), signupMax, signupWindow);
        return ApiResponse.ok("account created", auth.signup(req, client));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        ClientInfo client = ClientInfo.from(http);
        rateLimit("login:" + client.ip(), loginMax, loginWindow);
        return ApiResponse.ok("login successful", auth.login(req, client));
    }

    @PostMapping("/google")
    public ApiResponse<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest req, HttpServletRequest http) {
        ClientInfo client = ClientInfo.from(http);
        rateLimit("google:" + client.ip(), loginMax, loginWindow);
        return ApiResponse.ok("login successful", auth.loginWithGoogle(req, client));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        ClientInfo client = ClientInfo.from(http);
        rateLimit("refresh:" + client.ip(), loginMax, loginWindow);
        return ApiResponse.ok(auth.refresh(req.refreshToken(), client));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
        return ApiResponse.message("logged out");
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal AuthPrincipal me) {
        auth.logoutAll(me.id());
        return ApiResponse.message("logged out of all sessions");
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal AuthPrincipal me) {
        return ApiResponse.ok(auth.me(me.id()));
    }

    @PatchMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(me.id(), req);
        return ApiResponse.message("password changed, please sign in again");
    }

    @PatchMapping("/update-profile")
    public ApiResponse<UserDto> updateProfile(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok("profile updated", auth.updateProfile(me.id(), req));
    }

    @DeleteMapping("/delete-account")
    public ApiResponse<Void> deleteAccount(@AuthenticationPrincipal AuthPrincipal me) {
        auth.deleteAccount(me.id());
        return ApiResponse.message("account deleted");
    }

    private void rateLimit(String key, int max, int window) {
        if (!rateLimiter.allow(key, max, window)) {
            throw ApiException.tooManyRequests("too many requests, please slow down");
        }
    }
}
