package com.workpilot.ai.repository;

import com.workpilot.ai.domain.Document;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<Document> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    public static Specification<Document> textSearch(String rawQuery) {
        String escaped = rawQuery.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern, '\\'),
                cb.like(cb.lower(root.get("originalFileName")), pattern, '\\'),
                cb.like(cb.lower(cb.coalesce(root.get("extractedText"), "")), pattern, '\\')
        );
    }
}
