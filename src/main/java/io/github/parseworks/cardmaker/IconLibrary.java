package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.Map;

public class IconLibrary {
    private final ObservableMap<String, Map<String, String>> mappings = FXCollections.observableHashMap();

    public Map<String, Map<String, String>> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, Map<String, String>> newMappings) {
        mappings.clear();
        if (newMappings != null) {
            mappings.putAll(newMappings);
        }
    }

    @JsonIgnore
    public ObservableMap<String, Map<String, String>> mappingsProperty() {
        return mappings;
    }
}
