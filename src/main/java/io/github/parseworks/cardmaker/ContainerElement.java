package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;

public class ContainerElement extends ParentCardElement {
    public enum LayoutType { POSITIONAL, FLOW, VERTICAL, HORIZONTAL, STACK }
    public enum Alignment { LEFT, CENTER, RIGHT }

    private final DoubleProperty width = new SimpleDoubleProperty(100);
    private final DoubleProperty height = new SimpleDoubleProperty(100);
    private final BooleanProperty lockAspectRatio = new SimpleBooleanProperty(false);
    private final DoubleProperty alpha = new SimpleDoubleProperty(1.0);
    private final StringProperty backgroundColor = new SimpleStringProperty("#FFFFFF00"); // Transparent by default
    private final ObjectProperty<LayoutType> layoutType = new SimpleObjectProperty<>(LayoutType.POSITIONAL);
    private final ObjectProperty<Alignment> alignment = new SimpleObjectProperty<>(Alignment.LEFT);
    private final DoubleProperty spacing = new SimpleDoubleProperty(0);
    private final BooleanProperty locked = new SimpleBooleanProperty(false);

    public ContainerElement() {
        this("Container");
    }

    public ContainerElement(String name) {
        setName(name);
    }

    public double getWidth() { return width.get(); }
    public void setWidth(double value) { width.set(value); }
    @JsonIgnore
    public DoubleProperty widthProperty() { return width; }

    public double getHeight() { return height.get(); }
    public void setHeight(double value) { height.set(value); }
    @JsonIgnore
    public DoubleProperty heightProperty() { return height; }

    public double getAlpha() { return alpha.get(); }
    public void setAlpha(double value) { alpha.set(value); }
    @JsonIgnore
    public DoubleProperty alphaProperty() { return alpha; }

    public String getBackgroundColor() { return backgroundColor.get(); }
    public void setBackgroundColor(String value) { backgroundColor.set(value); }
    @JsonIgnore
    public StringProperty backgroundColorProperty() { return backgroundColor; }

    public LayoutType getLayoutType() { return layoutType.get(); }
    public void setLayoutType(LayoutType value) { layoutType.set(value); }
    @JsonIgnore
    public ObjectProperty<LayoutType> layoutTypeProperty() { return layoutType; }

    public Alignment getAlignment() { return alignment.get(); }
    public void setAlignment(Alignment value) { alignment.set(value); }
    @JsonIgnore
    public ObjectProperty<Alignment> alignmentProperty() { return alignment; }

    public double getSpacing() { return spacing.get(); }
    public void setSpacing(double value) { spacing.set(value); }
    @JsonIgnore
    public DoubleProperty spacingProperty() { return spacing; }

    public boolean isLocked() { return locked.get(); }
    public void setLocked(boolean value) { locked.set(value); }
    @JsonIgnore
    public BooleanProperty lockedProperty() { return locked; }

    public boolean isLockAspectRatio() { return lockAspectRatio.get(); }
    public void setLockAspectRatio(boolean value) { lockAspectRatio.set(value); }
    @JsonIgnore
    public BooleanProperty lockAspectRatioProperty() { return lockAspectRatio; }

    @Override
    public String toString() {
        return getName();
    }
}
