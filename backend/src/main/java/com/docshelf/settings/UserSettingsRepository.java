// Spring Data repository for the single user_settings row (id = 1)
package com.docshelf.settings;

import com.docshelf.settings.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Integer> {

    int SINGLETON_ID = 1;

    default UserSettings get() {
        return findById(SINGLETON_ID).orElseGet(() -> {
            UserSettings s = new UserSettings();
            s.setId(SINGLETON_ID);
            return save(s);
        });
    }
}
