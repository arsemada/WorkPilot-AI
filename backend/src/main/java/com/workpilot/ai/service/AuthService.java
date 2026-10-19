package com.workpilot.ai.service;

import com.workpilot.ai.domain.User;
import com.workpilot.ai.dto.LoginRequest;
import com.workpilot.ai.dto.MembershipResponse;
import com.workpilot.ai.dto.RegisterRequest;
import com.workpilot.ai.dto.UserProfileResponse;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ConflictException;
import com.workpilot.ai.exception.UnauthorizedException;
import com.workpilot.ai.repository.OrganizationMembershipRepository;
import com.workpilot.ai.repository.UserRepository;
import com.workpilot.ai.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthService(
            UserRepository userRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationService organizationService,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim()
        );
        userRepository.save(user);

        String organizationName = request.organizationName() == null ? "" : request.organizationName().trim();
        if (!organizationName.isEmpty()) {
            if (organizationName.length() < 2) {
                throw new BadRequestException("Organization name must be between 2 and 255 characters");
            }
            organizationService.createForOwner(user, organizationName, null);
        }
        return profile(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = normalizeEmail(request.email());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            HttpSession existingSession = httpRequest.getSession(false);
            if (existingSession != null) {
                httpRequest.changeSessionId();
            }
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            User user = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
            return profile(user);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (AuthenticationException ex) {
            log.info("Authentication failed");
            throw new UnauthorizedException("Invalid email or password");
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUser(CustomUserDetails principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
        return profile(user);
    }

    private UserProfileResponse profile(User user) {
        List<MembershipResponse> memberships = membershipRepository.findAllWithOrganizationByUserId(user.getId()).stream()
                .map(MembershipResponse::fromEntity)
                .sorted(Comparator.comparing(MembershipResponse::organizationName))
                .toList();
        return UserProfileResponse.of(user, memberships);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
