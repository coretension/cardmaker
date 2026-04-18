package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.Map;

public class FontLibrary {
    private final ObservableMap<String, FontElement> fonts = FXCollections.observableHashMap();

    public Map<String, FontElement> getFonts() {
        return fonts;
    }

    public void setFonts(Map<String, FontElement> newFonts) {
        fonts.clear();
        if (newFonts != null) {
            fonts.putAll(newFonts);
        }
    }

    @JsonIgnore
    public ObservableMap<String, FontElement> fontsProperty() {
        return fonts;
    }
}
