package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;

public class FontElement extends CardElement {
    private final StringProperty fontFamily = new SimpleStringProperty("Arial");
    private final DoubleProperty fontSize = new SimpleDoubleProperty(14);
    private final ObjectProperty<FontWeight> fontWeight = new SimpleObjectProperty<>(FontWeight.NORMAL);
    private final ObjectProperty<FontPosture> fontPosture = new SimpleObjectProperty<>(FontPosture.REGULAR);
    private final StringProperty color = new SimpleStringProperty("#000000");
    private final DoubleProperty angle = new SimpleDoubleProperty(0);
    private final DoubleProperty outlineWidth = new SimpleDoubleProperty(0);
    private final StringProperty outlineColor = new SimpleStringProperty("#000000");

    public FontElement() {
        this("Font");
    }

    public FontElement(String name) {
        setName(name);
    }

    public String getFontFamily() { return fontFamily.get(); }
    public void setFontFamily(String value) { fontFamily.set(value); }
    @JsonIgnore
    public StringProperty fontFamilyProperty() { return fontFamily; }

    public double getFontSize() { return fontSize.get(); }
    public void setFontSize(double value) { fontSize.set(value); }
    @JsonIgnore
    public DoubleProperty fontSizeProperty() { return fontSize; }

    public FontWeight getFontWeight() { return fontWeight.get(); }
    public void setFontWeight(FontWeight value) { fontWeight.set(value); }
    @JsonIgnore
    public ObjectProperty<FontWeight> fontWeightProperty() { return fontWeight; }

    public FontPosture getFontPosture() { return fontPosture.get(); }
    public void setFontPosture(FontPosture value) { fontPosture.set(value); }
    @JsonIgnore
    public ObjectProperty<FontPosture> fontPostureProperty() { return fontPosture; }

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
}
