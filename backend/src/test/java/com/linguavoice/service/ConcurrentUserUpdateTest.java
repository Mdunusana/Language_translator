package com.linguavoice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.linguavoice.entity.User;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression test: a password change and a profile edit that overlap must not overwrite each other.
 * Found while testing the UI: without @DynamicUpdate, Hibernate's UPDATE wrote every column, so the
 * slower password transaction put the old full name back.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentUserUpdateTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private TranslationRepository translationRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        translationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("overlapping updates of different columns both survive")
    void overlappingUpdatesKeepBothChanges() {
        long id = userRepository.save(new User("Old Name", "ada@example.com", "$2a$old")).getId();

        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(passwordTx -> {
            User forPassword = userRepository.findById(id).orElseThrow();   // "password request" loads the user

            inner.executeWithoutResult(profileTx -> {                       // "profile request" runs and commits
                User forProfile = userRepository.findById(id).orElseThrow();
                forProfile.setFullName("New Name");
            });

            forPassword.setPasswordHash("$2a$new");                         // password request commits last
        });

        User result = userRepository.findById(id).orElseThrow();
        assertThat(result.getFullName()).isEqualTo("New Name");
        assertThat(result.getPasswordHash()).isEqualTo("$2a$new");
    }
}
