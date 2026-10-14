package com.rbctcsworld.ecommerce.common;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.Role;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;

    private static MockEnvironment prod() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }

    @Test
    void firstStartCreatesTheAdminWithANormalisedEmailAndAHashedPassword() {
        when(users.findByEmail("admin@shop.test")).thenReturn(Optional.empty());
        when(encoder.encode("Strong-Pass-2026")).thenReturn("hash");

        new DataSeeder(users, encoder, " Admin@Shop.TEST ", "Strong-Pass-2026", new MockEnvironment()).run();

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@shop.test");
        assertThat(saved.getValue().getPassword()).isEqualTo("hash");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void anExistingAdminIsLeftAlone() {
        when(users.findByEmail("admin@shop.test")).thenReturn(Optional.of(new AppUser("admin@shop.test", "old", Role.ADMIN)));
        new DataSeeder(users, encoder, "admin@shop.test", "Strong-Pass-2026", new MockEnvironment()).run();
        verify(users, never()).save(any());
    }

    @Test
    void developmentPasswordIsFineOnADeveloperMachine() {
        when(users.findByEmail("admin@shop.test")).thenReturn(Optional.empty());
        new DataSeeder(users, encoder, "admin@shop.test", DataSeeder.DEV_DEFAULT_PASSWORD, new MockEnvironment()).run();
        verify(users).save(any());
    }

    @Test
    void developmentPasswordStopsTheStartInProductionAndInThePublicDemo() {
        assertThatThrownBy(() -> new DataSeeder(users, encoder, "a@b.c", DataSeeder.DEV_DEFAULT_PASSWORD, prod()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_PASSWORD");
        MockEnvironment demo = new MockEnvironment().withProperty("app.demo.enabled", "true");
        assertThatThrownBy(() -> new DataSeeder(users, encoder, "a@b.c", DataSeeder.DEV_DEFAULT_PASSWORD, demo))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users);
    }

    @Test
    void aRealPasswordStartsInProduction() {
        new DataSeeder(users, encoder, "a@b.c", "Strong-Pass-2026", prod());   // no exception
        verifyNoInteractions(users);                                         // nothing happens before run()
    }
}
