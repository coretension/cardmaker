package io.github.parseworks.cardmaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ConditionElement extends ParentCardElement {
    private final StringProperty condition = new SimpleStringProperty("");

    public ConditionElement() {
        this("Condition");
    }

    public ConditionElement(String name) {
        super(name);
    }

    public String getCondition() { return condition.get(); }
    public void setCondition(String value) { condition.set(value); }
    @JsonIgnore
    public StringProperty conditionProperty() { return condition; }
}
