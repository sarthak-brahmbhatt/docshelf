// Everything an extractor may need besides the text: ids, member roster, original image bytes for the vision path
package com.docshelf.extract;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentContext(
        UUID documentId,
        UUID memberId,
        DocType typeHint,
        byte[] imageBytes,
        String imageMimeType,
        List<MemberRef> members,
        LocalDate today) {

    /** Minimal roster entry (names only; never ID numbers). */
    public record MemberRef(UUID id, String fullName, String relation, LocalDate dob, boolean self) {
    }

    public DocumentContext {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public boolean hasImage() {
        return imageBytes != null && imageBytes.length > 0;
    }
}
