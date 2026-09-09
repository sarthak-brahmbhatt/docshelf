// Spring Data repository for reminder_rule
package com.docshelf.reminder;

import com.docshelf.reminder.entity.ReminderRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRuleRepository extends JpaRepository<ReminderRule, String> {

    List<ReminderRule> findByEnabledTrue();
}
