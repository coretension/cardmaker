package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DeckStorage {
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Property.class, new PropertySerializer());
        module.addDeserializer(DoubleProperty.class, new DoublePropertyDeserializer());
        module.addDeserializer(StringProperty.class, new StringPropertyDeserializer());
        module.addDeserializer(IntegerProperty.class, new IntegerPropertyDeserializer());
        module.addDeserializer(BooleanProperty.class, new BooleanPropertyDeserializer());
        module.addDeserializer(ObjectProperty.class, new ObjectPropertyDeserializer());
        module.addSerializer(ObservableList.class, new ObservableListSerializer());
        module.addDeserializer(ObservableList.class, new ObservableListDeserializer());
        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static void save(CardTemplate template, File file) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, template);
    }

    public static CardTemplate load(File file) throws IOException {
        return mapper.readValue(file, CardTemplate.class);
    }

    public static void saveSettings(AppSettings settings) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(getSettingsFile(), settings);
    }

    public static AppSettings loadSettings() throws IOException {
        File file = getSettingsFile();
        if (file.exists()) {
            return mapper.readValue(file, AppSettings.class);
        }
        return new AppSettings();
    }

    public static File getSettingsFile() {
        String userHome = System.getProperty("user.home");
        Path path = Paths.get(userHome, ".cardmaker", "settings.json");
        File file = path.toFile();
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return file;
    }

    public static <T> T clone(T object, Class<T> clazz) throws IOException {
        String json = mapper.writeValueAsString(object);
        return mapper.readValue(json, clazz);
    }

    public static File getTempFile() {
        String userHome = System.getProperty("user.home");
        Path path = Paths.get(userHome, ".cardmaker", "temp_deck.json");
        File file = path.toFile();
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return file;
    }

    private static class PropertySerializer extends JsonSerializer<Property> {
        @Override
        public void serialize(Property value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null || value.getValue() == null) {
                gen.writeNull();
            } else {
                gen.writeObject(value.getValue());
            }
        }
    }

    private static class DoublePropertyDeserializer extends JsonDeserializer<DoubleProperty> {
        @Override
        public DoubleProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new SimpleDoubleProperty(p.getValueAsDouble());
        }
    }

    private static class StringPropertyDeserializer extends JsonDeserializer<StringProperty> {
        @Override
        public StringProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new SimpleStringProperty(p.getValueAsString());
        }
    }

    private static class IntegerPropertyDeserializer extends JsonDeserializer<IntegerProperty> {
        @Override
        public IntegerProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new SimpleIntegerProperty(p.getValueAsInt());
        }
    }

    private static class BooleanPropertyDeserializer extends JsonDeserializer<BooleanProperty> {
        @Override
        public BooleanProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new SimpleBooleanProperty(p.getValueAsBoolean());
        }
    }

    private static class ObjectPropertyDeserializer extends JsonDeserializer<ObjectProperty> {
        @Override
        public ObjectProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isTextual()) {
                String textValue = node.asText();
                try {
                    return new SimpleObjectProperty(ContainerElement.LayoutType.valueOf(textValue));
                } catch (IllegalArgumentException e0) {
                    try {
                        return new SimpleObjectProperty(ContainerElement.Alignment.valueOf(textValue));
                    } catch (IllegalArgumentException e1a) {
                        try {
                            return new SimpleObjectProperty(javafx.scene.text.FontWeight.valueOf(textValue));
                        } catch (IllegalArgumentException e1) {
                            try {
                                return new SimpleObjectProperty(javafx.scene.text.FontPosture.valueOf(textValue));
                            } catch (IllegalArgumentException e2) {
                                return new SimpleObjectProperty(textValue);
                            }
                        }
                    }
                }
            }
            return new SimpleObjectProperty(node);
        }
    }

    private static class ObservableListSerializer extends JsonSerializer<ObservableList> {
        @Override
        public void serialize(ObservableList value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartArray();
            for (Object item : value) {
                gen.writeObject(item);
            }
            gen.writeEndArray();
        }
    }

    private static class ObservableListDeserializer extends JsonDeserializer<ObservableList> {
        @Override
        public ObservableList deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            List<CardElement> list = ctxt.readValue(p, mapper.getTypeFactory().constructCollectionType(List.class, CardElement.class));
            return FXCollections.observableArrayList(list);
        }
    }
}
