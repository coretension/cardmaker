package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;

public class TextElement extends CardElement {
    private final StringProperty text = new SimpleStringProperty("Text");
    private final DoubleProperty fontSize = new SimpleDoubleProperty(14);
    private final StringProperty color = new SimpleStringProperty("#000000");

    public TextElement() {
        this("Text");
    }

    public TextElement(String name) {
        setName(name);
    }

    public String getText() { return text.get(); }
    public void setText(String value) { text.set(value); }
    @JsonIgnore
    public StringProperty textProperty() { return text; }

    public double getFontSize() { return fontSize.get(); }
    public void setFontSize(double value) { fontSize.set(value); }
    @JsonIgnore
    public DoubleProperty fontSizeProperty() { return fontSize; }

    public String getColor() { return color.get(); }
    public void setColor(String value) { color.set(value); }
    @JsonIgnore
    public StringProperty colorProperty() { return color; }
}
