package com.learnreserve.config;

import com.learnreserve.auth.*;
import com.learnreserve.availability.*;
import com.learnreserve.resource.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapData implements ApplicationRunner {
    private final UserRepository users;
    private final ResourceRepository resources;
    private final AvailabilityRepository slots;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Environment environment;
    private final String adminEmail;
    private final String adminPassword;

    public BootstrapData(UserRepository users, ResourceRepository resources, AvailabilityRepository slots,
                         PasswordEncoder encoder, Clock clock, Environment environment,
                         @Value("${app.admin.email:}") String adminEmail,
                         @Value("${app.admin.password:}") String adminPassword) {
        this.users = users; this.resources = resources; this.slots = slots; this.encoder = encoder;
        this.clock = clock; this.environment = environment; this.adminEmail = adminEmail; this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!adminEmail.isBlank()) {
            String email = AuthService.normalizeEmail(adminEmail);
            if (!users.existsByEmail(email)) {
                AuthService.validatePassword(adminPassword);
                users.save(new User("Administrator", email, encoder.encode(adminPassword), User.Role.ADMIN, clock.instant()));
            }
        }
        if (environment.acceptsProfiles(Profiles.of("demo")) && resources.count() == 0) {
            users.save(new User("Demo Learner", "learner@learnreserve.local", encoder.encode("DemoLearner!2026"),
                    User.Role.LEARNER, clock.instant()));
            var tomorrow = LocalDate.now(clock).plusDays(1).atTime(9, 0).toInstant(ZoneOffset.UTC);
            seed("Quiet Study Room", "A focused space with a whiteboard and reliable Wi-Fi.", LearningResource.Type.STUDY_ROOM, "Library, Floor 2", tomorrow);
            seed("Java Mentoring", "One-to-one Java and backend interview preparation.", LearningResource.Type.MENTORING, "Online meeting", tomorrow);
            seed("System Design Workshop", "Reserve an exclusive workshop session for your learning group.", LearningResource.Type.WORKSHOP, "Learning Lab A", tomorrow);
        }
    }

    private void seed(String name, String description, LearningResource.Type type, String location, Instant start) {
        var resource = resources.save(new LearningResource(name, description, type, location, clock.instant()));
        for (int day = 0; day < 7; day++) {
            var dayStart = start.plus(Duration.ofDays(day));
            slots.save(new AvailabilitySlot(resource, dayStart, dayStart.plus(Duration.ofHours(8))));
        }
    }
}
