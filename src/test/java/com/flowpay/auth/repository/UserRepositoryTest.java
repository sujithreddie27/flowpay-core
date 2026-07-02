package com.flowpay.auth.repository;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.KycStatus;
import com.flowpay.common.enums.UserRole;
import com.flowpay.common.enums.UserStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User createAndPersistUser(String email, String phone, UserStatus status,
                                       KycStatus kycStatus, UserRole role, boolean emailVerified) {
        User user = User.builder()
                .email(email)
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Test")
                .lastName("User")
                .phone(phone)
                .status(status)
                .kycStatus(kycStatus)
                .role(role)
                .emailVerified(emailVerified)
                .phoneVerified(false)
                .build();
        return entityManager.persistAndFlush(user);
    }

    @Nested
    @DisplayName("FindByEmail")
    class FindByEmail {

        @Test
        @DisplayName("should find user by email")
        void shouldFindByEmail() {
            createAndPersistUser("findme@test.com", "+1000000001", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);

            Optional<User> result = userRepository.findByEmail("findme@test.com");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("findme@test.com");
        }

        @Test
        @DisplayName("should return empty for non-existent email")
        void shouldReturnEmptyForNonExistent() {
            Optional<User> result = userRepository.findByEmail("nonexistent@test.com");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("FindByPhone")
    class FindByPhone {

        @Test
        @DisplayName("should find user by phone number")
        void shouldFindByPhone() {
            createAndPersistUser("phone-user@test.com", "+9876543210", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);

            Optional<User> result = userRepository.findByPhone("+9876543210");

            assertThat(result).isPresent();
            assertThat(result.get().getPhone()).isEqualTo("+9876543210");
        }
    }

    @Nested
    @DisplayName("ExistsByEmailAndPhone")
    class ExistsByEmailAndPhone {

        @Test
        @DisplayName("should check if email exists")
        void shouldCheckEmailExists() {
            createAndPersistUser("exists@test.com", "+1234567890", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);

            assertThat(userRepository.existsByEmail("exists@test.com")).isTrue();
            assertThat(userRepository.existsByEmail("notexists@test.com")).isFalse();
        }

        @Test
        @DisplayName("should check if phone exists")
        void shouldCheckPhoneExists() {
            createAndPersistUser("phone-check@test.com", "+5555555555", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);

            assertThat(userRepository.existsByPhone("+5555555555")).isTrue();
            assertThat(userRepository.existsByPhone("+9999999999")).isFalse();
        }
    }

    @Nested
    @DisplayName("FindByStatus")
    class FindByStatus {

        @Test
        @DisplayName("should find users by status")
        void shouldFindByStatus() {
            createAndPersistUser("active1@test.com", "+1000000001", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);
            createAndPersistUser("active2@test.com", "+1000000002", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.MERCHANT, true);
            createAndPersistUser("suspended@test.com", "+1000000003", UserStatus.SUSPENDED,
                    KycStatus.VERIFIED, UserRole.USER, true);

            List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
            List<User> suspendedUsers = userRepository.findByStatus(UserStatus.SUSPENDED);

            assertThat(activeUsers).hasSize(2);
            assertThat(suspendedUsers).hasSize(1);
        }

        @Test
        @DisplayName("should support pagination for status queries")
        void shouldSupportPagination() {
            for (int i = 0; i < 12; i++) {
                createAndPersistUser("page" + i + "@test.com", "+200000000" + String.format("%01d", i),
                        UserStatus.ACTIVE, KycStatus.VERIFIED, UserRole.USER, true);
            }

            Page<User> page = userRepository.findByStatus(UserStatus.ACTIVE, PageRequest.of(0, 5));

            assertThat(page.getContent()).hasSize(5);
            assertThat(page.getTotalElements()).isEqualTo(12);
            assertThat(page.getTotalPages()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("FindByKycStatus")
    class FindByKycStatus {

        @Test
        @DisplayName("should find users by KYC status")
        void shouldFindByKycStatus() {
            createAndPersistUser("kyc-v@test.com", "+3000000001", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);
            createAndPersistUser("kyc-p@test.com", "+3000000002", UserStatus.ACTIVE,
                    KycStatus.PENDING, UserRole.USER, true);
            createAndPersistUser("kyc-r@test.com", "+3000000003", UserStatus.ACTIVE,
                    KycStatus.REJECTED, UserRole.USER, true);

            List<User> verified = userRepository.findByKycStatus(KycStatus.VERIFIED);
            List<User> pending = userRepository.findByKycStatus(KycStatus.PENDING);

            assertThat(verified).hasSize(1);
            assertThat(pending).hasSize(1);
        }
    }

    @Nested
    @DisplayName("FindByStatusAndKycStatus")
    class FindByStatusAndKycStatus {

        @Test
        @DisplayName("should find users by combined status and KYC status")
        void shouldFindByCombinedStatus() {
            createAndPersistUser("combo1@test.com", "+4000000001", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);
            createAndPersistUser("combo2@test.com", "+4000000002", UserStatus.ACTIVE,
                    KycStatus.PENDING, UserRole.USER, true);
            createAndPersistUser("combo3@test.com", "+4000000003", UserStatus.SUSPENDED,
                    KycStatus.VERIFIED, UserRole.USER, true);

            List<User> activeVerified = userRepository.findByStatusAndKycStatus(
                    UserStatus.ACTIVE, KycStatus.VERIFIED);

            assertThat(activeVerified).hasSize(1);
            assertThat(activeVerified.get(0).getEmail()).isEqualTo("combo1@test.com");
        }
    }

    @Nested
    @DisplayName("FindByEmailVerified")
    class FindByEmailVerified {

        @Test
        @DisplayName("should find users by email verification status")
        void shouldFindByEmailVerified() {
            createAndPersistUser("verified@test.com", "+5000000001", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);
            createAndPersistUser("unverified@test.com", "+5000000002", UserStatus.ACTIVE,
                    KycStatus.PENDING, UserRole.USER, false);

            List<User> verified = userRepository.findByEmailVerifiedTrue();
            List<User> unverified = userRepository.findByEmailVerifiedFalse();

            assertThat(verified).hasSize(1);
            assertThat(unverified).hasSize(1);
        }
    }

    @Nested
    @DisplayName("UpdateLastLoginAt")
    class UpdateLastLoginAt {

        @Test
        @DisplayName("should update user last login timestamp")
        void shouldUpdateLastLoginAt() {
            User user = createAndPersistUser("login@test.com", "+6000000001", UserStatus.ACTIVE,
                    KycStatus.VERIFIED, UserRole.USER, true);

            OffsetDateTime loginTime = OffsetDateTime.now().withNano(0);
            userRepository.updateLastLoginAt(user.getId(), loginTime);
            entityManager.clear();

            User updated = entityManager.find(User.class, user.getId());
            assertThat(updated.getLastLoginAt()).isNotNull();
            assertThat(updated.getLastLoginAt().toEpochSecond()).isEqualTo(loginTime.toEpochSecond());
        }
    }
}
