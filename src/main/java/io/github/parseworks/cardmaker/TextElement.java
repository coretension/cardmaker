package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TextElement extends CardElement {
    private final StringProperty text = new SimpleStringProperty("Text");
    private final DoubleProperty fontSize = new SimpleDoubleProperty(14);
    private final StringProperty color = new SimpleStringProperty("#000000");
    private final DoubleProperty angle = new SimpleDoubleProperty(0);
    private final DoubleProperty outlineWidth = new SimpleDoubleProperty(0);
    private final StringProperty outlineColor = new SimpleStringProperty("#000000");
    private final DoubleProperty wrappingWidth = new SimpleDoubleProperty(0);
    private final StringProperty fontConfigName = new SimpleStringProperty("Default");

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

    public double getAngle() { return angle.get(); }
    public void setAngle(double value) { angle.set(value); }
    @JsonIgnore
    public DoubleProperty angleProperty() { return angle; }

    public double getOutlineWidth() { return outlineWidth.get(); }
    public void setOutlineWidth(double value) { outlineWidth.set(value); }
    @JsonIgnore
    public DoubleProperty outlineWidthProperty() { return outlineWidth; }

    public String getOutlineColor() { return outlineColor.get(); }
    public void setOutlineColor(String value) { outlineColor.set(value); }
    @JsonIgnore
    public StringProperty outlineColorProperty() { return outlineColor; }

    public double getWrappingWidth() { return wrappingWidth.get(); }
    public void setWrappingWidth(double value) { wrappingWidth.set(value); }
    @JsonIgnore
    public DoubleProperty wrappingWidthProperty() { return wrappingWidth; }

    public String getFontConfigName() { return fontConfigName.get(); }
    public void setFontConfigName(String value) { fontConfigName.set(value); }
    @JsonIgnore
    public StringProperty fontConfigNameProperty() { return fontConfigName; }
}
