// Spring Data repository for chat_message
package com.docshelf.chat;

import com.docshelf.chat.entity.ChatMessage;
import com.docshelf.chat.entity.ChatRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderBySeqAsc(UUID sessionId);

    List<ChatMessage> findBySessionIdAndRoleInOrderBySeqAsc(UUID sessionId, Collection<ChatRole> roles);

    Optional<ChatMessage> findFirstBySessionIdOrderBySeqDesc(UUID sessionId);
}
