package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;

public class ImageElement extends CardElement {
    private final StringProperty imagePath = new SimpleStringProperty("");
    private final DoubleProperty width = new SimpleDoubleProperty(50);
    private final DoubleProperty height = new SimpleDoubleProperty(50);
    private final BooleanProperty lockAspectRatio = new SimpleBooleanProperty(true);

    public ImageElement() {
        this("Image");
    }

    public ImageElement(String name) {
        setName(name);
    }

    public String getImagePath() { return imagePath.get(); }
    public void setImagePath(String value) { imagePath.set(value); }
    @JsonIgnore
    public StringProperty imagePathProperty() { return imagePath; }

    public double getWidth() { return width.get(); }
    public void setWidth(double value) { width.set(value); }
    @JsonIgnore
    public DoubleProperty widthProperty() { return width; }

    public double getHeight() { return height.get(); }
    public void setHeight(double value) { height.set(value); }
    @JsonIgnore
    public DoubleProperty heightProperty() { return height; }

    public boolean isLockAspectRatio() { return lockAspectRatio.get(); }
    public void setLockAspectRatio(boolean value) { lockAspectRatio.set(value); }
    @JsonIgnore
    public BooleanProperty lockAspectRatioProperty() { return lockAspectRatio; }
}
