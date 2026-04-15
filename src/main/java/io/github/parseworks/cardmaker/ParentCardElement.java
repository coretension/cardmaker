package io.github.parseworks.cardmaker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public abstract class ParentCardElement extends CardElement {
    private final ObservableList<CardElement> children = FXCollections.observableArrayList();

    public ParentCardElement() {
    }

    public ParentCardElement(String name) {
        setName(name);
    }

    public ObservableList<CardElement> getChildren() {
        return children;
    }
}
