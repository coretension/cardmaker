package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;

public class IconElement extends CardElement {
    private final StringProperty value = new SimpleStringProperty("");
    private final DoubleProperty iconWidth = new SimpleDoubleProperty(32);
    private final DoubleProperty iconHeight = new SimpleDoubleProperty(32);
    private final StringProperty mappingName = new SimpleStringProperty("Default");

    public IconElement() {
        this("Icons");
    }

    public IconElement(String name) {
        setName(name);
    }

    public String getValue() { return value.get(); }
    public void setValue(String val) { value.set(val); }
    @JsonIgnore
    public StringProperty valueProperty() { return value; }

    public double getIconWidth() { return iconWidth.get(); }
    public void setIconWidth(double val) { iconWidth.set(val); }
    @JsonIgnore
    public DoubleProperty iconWidthProperty() { return iconWidth; }

    public double getIconHeight() { return iconHeight.get(); }
    public void setIconHeight(double val) { iconHeight.set(val); }
    @JsonIgnore
    public DoubleProperty iconHeightProperty() { return iconHeight; }

    public String getMappingName() { return mappingName.get(); }
    public void setMappingName(String val) { mappingName.set(val); }
    @JsonIgnore
    public StringProperty mappingNameProperty() { return mappingName; }
}
