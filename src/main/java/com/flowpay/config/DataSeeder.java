package com.flowpay.config;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.KycStatus;
import com.flowpay.common.enums.UserRole;
import com.flowpay.common.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_PASSWORD = "Password123!";

    @Override
    @Transactional
    public void run(String... args) {
        seedUser("user@flowpay.com", "Test", "User", "+1000000001", UserRole.USER);
        seedUser("merchant@flowpay.com", "Test", "Merchant", "+1000000002", UserRole.MERCHANT);
        seedUser("admin@flowpay.com", "Test", "Admin", "+1000000003", UserRole.ADMIN);
        log.info("Data seeding completed");
    }

    private void seedUser(String email, String firstName, String lastName, String phone, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            log.debug("User already exists: {}", email);
            return;
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .role(role)
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .emailVerified(true)
                .phoneVerified(true)
                .build();

        userRepository.save(user);
        log.info("Seeded {} user: {}", role, email);
    }
}
