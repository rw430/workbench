package com.xiaoc.workbench.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoc.workbench.auth.domain.UserAccount;
import com.xiaoc.workbench.auth.domain.UserRole;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserAccountRepositoryTest extends PostgresIntegrationTest {
    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void savesUserWithRole() {
        UserRole role = userRoleRepository.save(new UserRole("ADMIN", "Administrator"));
        UserAccount user = new UserAccount(
            "user-admin",
            "admin@example.com",
            "Admin",
            "{bcrypt}demo",
            true
        );
        user.addRole(role);

        userAccountRepository.save(user);

        UserAccount reloaded = userAccountRepository.findByEmail("admin@example.com").orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("Admin");
        assertThat(reloaded.getRoles()).extracting(UserRole::getCode).containsExactly("ADMIN");
    }
}
