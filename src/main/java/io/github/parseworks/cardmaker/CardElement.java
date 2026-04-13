package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextElement.class, name = "text"),
    @JsonSubTypes.Type(value = ImageElement.class, name = "image"),
    @JsonSubTypes.Type(value = FontElement.class, name = "font"),
    @JsonSubTypes.Type(value = ContainerElement.class, name = "container")
})
public abstract class CardElement {
    protected final DoubleProperty x = new SimpleDoubleProperty(0);
    protected final DoubleProperty y = new SimpleDoubleProperty(0);
    protected final StringProperty name = new SimpleStringProperty("Element");

    public double getX() { return x.get(); }
    public void setX(double value) { x.set(value); }
    @JsonIgnore
    public DoubleProperty xProperty() { return x; }

    public double getY() { return y.get(); }
    public void setY(double value) { y.set(value); }
    @JsonIgnore
    public DoubleProperty yProperty() { return y; }

    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }
    @JsonIgnore
    public StringProperty nameProperty() { return name; }
}
