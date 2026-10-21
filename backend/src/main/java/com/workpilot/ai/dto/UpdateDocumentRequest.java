package com.workpilot.ai.dto;

/**
 * Partial update. A JSON field that is present is applied, including null for
 * description. Omitted fields stay unchanged.
 */
public class UpdateDocumentRequest {

    private String title;
    private String description;
    private boolean titlePresent;
    private boolean descriptionPresent;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public boolean isTitlePresent() {
        return titlePresent;
    }

    public boolean isDescriptionPresent() {
        return descriptionPresent;
    }
}
