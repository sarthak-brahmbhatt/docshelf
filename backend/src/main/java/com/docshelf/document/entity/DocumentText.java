// Redacted extracted text per page (composite key document_id + page_no)
package com.docshelf.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "document_text")
@IdClass(DocumentText.Key.class)
public class DocumentText {

    public static class Key implements Serializable {
        private UUID documentId;
        private int pageNo;

        public Key() {
        }

        public Key(UUID documentId, int pageNo) {
            this.documentId = documentId;
            this.pageNo = pageNo;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && pageNo == k.pageNo && Objects.equals(documentId, k.documentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(documentId, pageNo);
        }
    }

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Id
    @Column(name = "page_no")
    private int pageNo;

    @Column(name = "text_redacted", nullable = false)
    private String textRedacted;

    public DocumentText() {
    }

    public DocumentText(UUID documentId, int pageNo, String textRedacted) {
        this.documentId = documentId;
        this.pageNo = pageNo;
        this.textRedacted = textRedacted;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public String getTextRedacted() {
        return textRedacted;
    }

    public void setTextRedacted(String textRedacted) {
        this.textRedacted = textRedacted;
    }
}
