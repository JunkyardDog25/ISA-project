package com.example.jutjubic.services;

import com.example.jutjubic.dto.LoginUserDto;
import com.example.jutjubic.dto.RegisterUserDto;
import com.example.jutjubic.dto.VerifyUserDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.repositories.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
    }

    public User register(RegisterUserDto registerUserDto) {
        User user = new User(
                registerUserDto.getUsername(),
                passwordEncoder.encode(registerUserDto.getPassword()),
                registerUserDto.getEmail(),
                registerUserDto.getFirstName(),
                registerUserDto.getLastName(),
                registerUserDto.getAddress()
        );
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        user.setEnabled(false);
        sendVerificationEmail(user);
        return userRepository.save(user);
    }

    public User authenticate(LoginUserDto loginUserDto) {
        // obtain current request from RequestContextHolder if available and delegate
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest req = attrs != null ? attrs.getRequest() : null;
        return authenticate(loginUserDto, req);
    }

    public User authenticate(LoginUserDto loginUserDto, HttpServletRequest request) {
        logger.debug("Attempting to authenticate user with email: {}", loginUserDto.getEmail());

        User user = userRepository.findByEmail(loginUserDto.getEmail())
                .orElseThrow(() -> {
                    logger.warn("User not found with email: {}", loginUserDto.getEmail());
                    return new RuntimeException("User not found with email: " + loginUserDto.getEmail());
                });

        if (!user.isEnabled()) {
            logger.warn("User account is not enabled for email: {}", loginUserDto.getEmail());
            throw new RuntimeException("User is not enabled. Please verify your email first.");
        }

        try {
            logger.debug("Authenticating user with AuthenticationManager");
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginUserDto.getEmail(),
                            loginUserDto.getPassword()
                    )
            );
            logger.info("User successfully authenticated: {}", user.getUsername());
        } catch (BadCredentialsException e) {
            logger.warn("Bad credentials for email: {}", loginUserDto.getEmail());
            throw new RuntimeException("Invalid email or password");
        }

        // Try to obtain location: prefer client-provided location string (lat,lon), otherwise approximate by IP
        try {
            String locStr = loginUserDto.getLocation();
            boolean saved = false;
            if (locStr != null && locStr.contains(",")) {
                String[] parts = locStr.split(",");
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                user.setLatitude(lat);
                user.setLongitude(lon);
                userRepository.save(user);
                saved = true;
                logger.debug("Saved user-provided location for {}: {},{}", user.getEmail(), lat, lon);
            }

            if (!saved) {
                String ip = extractClientIp(request);
                if (ip != null && !ip.isBlank()) {
                    GeoResult approx = geoLocateIp(ip);
                    if (approx != null) {
                        user.setLatitude(approx.lat);
                        user.setLongitude(approx.lon);
                        userRepository.save(user);
                        logger.debug("Saved IP-approximated location for {}: {},{} (ip={})", user.getEmail(), approx.lat, approx.lon, ip);
                    }
                }
            }
        } catch (Exception e) {
            // Don't fail login if geolocation fails
            logger.warn("Failed to parse/resolve location for user {}: {}", loginUserDto.getEmail(), e.getMessage());
        }

        return user;
    }

    public void verifyUser(VerifyUserDto verifyUserDto) {
        Optional<User> optionalUser = userRepository.findByEmail(verifyUserDto.getEmail());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now()))
                throw new RuntimeException("Verification code has expired");

            if (user.getVerificationCode().equals(verifyUserDto.getVerificationCode())) {
                user.setEnabled(true);
                user.setGuest(false);
                user.setVerificationCode(null);
                user.setVerificationCodeExpiresAt(null);
                userRepository.save(user);
            }
            else throw new RuntimeException("Invalid verification code");
        }
        else throw new RuntimeException("User not found");
    }

    public void resendVerificationCode(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.isEnabled()) {
                throw new RuntimeException("Account is already verified");
            }
            user.setVerificationCode(generateVerificationCode());
            user.setVerificationCodeExpiresAt(LocalDateTime.now().plusHours(1));
            sendVerificationEmail(user);
            userRepository.save(user);
        } else {
            throw new RuntimeException("User not found");
        }
    }

    // Extract client IP from request headers (X-Forwarded-For preferred)
    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class GeoResult { double lat; double lon; }

    private GeoResult geoLocateIp(String ip) {
        try {
            URL url = new URL("http://ip-api.com/json/" + ip + "?fields=status,message,lat,lon");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            if (code != 200) return null;

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            String body = sb.toString();

            if (body.contains("\"status\":\"success\"")) {
                String latStr = extractJsonValue(body, "lat");
                String lonStr = extractJsonValue(body, "lon");
                if (latStr != null && lonStr != null) {
                    GeoResult r = new GeoResult();
                    r.lat = Double.parseDouble(latStr);
                    r.lon = Double.parseDouble(lonStr);
                    return r;
                }
            }
        } catch (Exception e) {
            logger.warn("IP geolocation failed for {}: {}", ip, e.getMessage());
        }
        return null;
    }

    private String extractJsonValue(String json, String key) {
        String look = "\"" + key + "\":";
        int idx = json.indexOf(look);
        if (idx < 0) return null;
        int start = idx + look.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        boolean isString = json.charAt(start) == '"';
        if (isString) {
            start++;
            end = json.indexOf('"', start);
            if (end < 0) return null;
            return json.substring(start, end);
        } else {
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
            return json.substring(start, end);
        }
    }

    private void sendVerificationEmail(User user) {
        int expiresIn = user.getVerificationCodeExpiresAt().minusMinutes(LocalDateTime.now().getMinute()).getMinute();
        String subject = "Account Verification";
        String verificationCode = user.getVerificationCode();
        String htmlMessage = "<!doctype html>"
                + "<html lang=\"en\">"
                + "<head>"
                + "  <meta charset=\"utf-8\">"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "  <style>"
                + "    .card{max-width:600px;margin:0 auto;border-radius:10px;overflow:hidden;box-shadow:0 8px 30px rgba(0,0,0,0.08);}"
                + "    .header{background:linear-gradient(90deg,#a71d2a 0%,#c43b3b 100%);padding:28px;text-align:center;color:#fff;font-family:Arial,Helvetica,sans-serif;}"
                + "    .content{background:#fff;padding:32px;font-family:Arial,Helvetica,sans-serif;color:#333;}"
                + "    .code{font-family:'Courier New',monospace;background:#f7f7f9;border-radius:8px;padding:18px 24px;display:inline-block;font-size:28px;letter-spacing:6px;color:#b21f2a;font-weight:700;}"
                + "    .footer{background:#fafafa;padding:14px 20px;text-align:center;color:#888;font-size:13px;}"
                + "  </style>"
                + "</head>"
                + "<body style=\"margin:0;padding:20px;background:#ffffff;\">"
                + "  <div class=\"card\">"
                + "    <div class=\"header\">"
                + "      <h2 style=\"margin:0;font-size:20px;\">Jutjubic</h2>"
                + "      <div style=\"margin-top:6px;font-size:13px;opacity:0.95\">Verify your account</div>"
                + "    </div>"
                + "    <div class=\"content\">"
                + "      <p style=\"margin:0 0 12px 0;\">Hi " + escapeHtml(user.getUsername()) + ",</p>"
                + "      <p style=\"margin:0 0 18px 0;color:#555;line-height:1.5;\">Thanks for creating an account with Jutjubic. Enter the code below in the app to activate your account. The code expires in " + expiresIn + " minutes.</p>"
                + "      <div style=\"text-align:center;margin:20px 0;\">"
                + "        <span class=\"code\">" + escapeHtml(verificationCode) + "</span>"
                + "      </div>"
                + "      <p style=\"margin:14px 0 0 0;color:#888;font-size:13px;\">If you didn't request this, ignore this message or contact support.</p>"
                + "    </div>"
                + "    <div class=\"footer\">Need help? Reply to this email or visit our support page.</div>"
                + "  </div>"
                + "</body>"
                + "</html>";

        try {
            emailService.sendVerificationEmail(user.getEmail(), subject, htmlMessage);
        } catch (MessagingException e) {
            logger.error("Failed to send verification email to {}", user.getEmail(), e);
        }
    }

    private String generateVerificationCode() {
        return String.valueOf((int)(Math.random() * 900000) + 100000);
    }

    private String escapeHtml(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
