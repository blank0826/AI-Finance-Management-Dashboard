package com.finance.repository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.finance.entity.User;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepo;

    @Test
    void saveAndFindByEmail_andExistsByEmail() {
        User u = new User();
        u.setEmail("test.user@example.com");
        u.setName("Test User");
        u.setPassword("password");

        User saved = userRepo.save(u);
        assertNotNull(saved.getId());

        Optional<User> opt = userRepo.findByEmail("test.user@example.com");
        assertTrue(opt.isPresent());
        assertEquals("Test User", opt.get().getName());

        assertTrue(userRepo.existsByEmail("test.user@example.com"));
    }
}
