package com.ajinkya.linky.integration;

import com.ajinkya.linky.entity.Role;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.repository.UserRepository;
import com.ajinkya.linky.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtUtil jwtUtil;

    @MockBean
    protected org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @MockBean
    protected org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @MockBean(name = "redisTemplate")
    protected RedisTemplate<String, String> redisTemplate;
    
    @MockBean
    protected ValueOperations<String, String> valueOperations;

    protected User testUser;
    protected User adminUser;
    protected String userToken;
    protected String adminToken;

    @BeforeEach
    public void setUpBase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Create standard user
        if (!userRepository.existsByEmail("testuser@example.com")) {
            testUser = new User();
            testUser.setName("Test User");
            testUser.setEmail("testuser@example.com");
            testUser.setPassword(passwordEncoder.encode("password123"));
            testUser.setRole(Role.USER);
            testUser = userRepository.save(testUser);
        } else {
            testUser = userRepository.findByEmail("testuser@example.com").get();
        }

        // Create admin user
        if (!userRepository.existsByEmail("admin@example.com")) {
            adminUser = new User();
            adminUser.setName("Admin User");
            adminUser.setEmail("admin@example.com");
            adminUser.setPassword(passwordEncoder.encode("password123"));
            adminUser.setRole(Role.ADMIN);
            adminUser = userRepository.save(adminUser);
        } else {
            adminUser = userRepository.findByEmail("admin@example.com").get();
        }

        userToken = "Bearer " + jwtUtil.generateAccessToken(testUser.getEmail(), testUser.getRole().name());
        adminToken = "Bearer " + jwtUtil.generateAccessToken(adminUser.getEmail(), adminUser.getRole().name());
    }
}
