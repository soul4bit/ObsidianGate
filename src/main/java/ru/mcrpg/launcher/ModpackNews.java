package ru.mcrpg.launcher;

import java.util.ArrayList;
import java.util.List;

public final class ModpackNews {

    private String title;
    private String date;
    private String body;
    private List<String> highlights = new ArrayList<String>();
    private List<String> newMods = new ArrayList<String>();
    private List<String> removedMods = new ArrayList<String>();
    private List<String> important = new ArrayList<String>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = sanitizeList(highlights);
    }

    public List<String> getNewMods() {
        return newMods;
    }

    public void setNewMods(List<String> newMods) {
        this.newMods = sanitizeList(newMods);
    }

    public List<String> getRemovedMods() {
        return removedMods;
    }

    public void setRemovedMods(List<String> removedMods) {
        this.removedMods = sanitizeList(removedMods);
    }

    public List<String> getImportant() {
        return important;
    }

    public void setImportant(List<String> important) {
        this.important = sanitizeList(important);
    }

    public boolean hasContent() {
        return hasText(title)
            || hasText(date)
            || hasText(body)
            || hasAny(highlights)
            || hasAny(newMods)
            || hasAny(removedMods)
            || hasAny(important);
    }

    private static List<String> sanitizeList(List<String> values) {
        List<String> sanitized = new ArrayList<String>();
        if (values == null) {
            return sanitized;
        }
        for (String value : values) {
            if (hasText(value)) {
                sanitized.add(value.trim());
            }
        }
        return sanitized;
    }

    private static boolean hasAny(List<String> values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (hasText(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
