// Spring Data repository for chat_session
package com.docshelf.chat;

import com.docshelf.chat.entity.ChatSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    List<ChatSession> findAllByOrderByLastMessageAtDescCreatedAtDesc();
}
