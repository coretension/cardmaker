package io.github.parseworks.cardmaker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class CardTemplate {
    private CardDimension dimension = CardDimension.POKER;
    private final ObservableList<CardElement> elements = FXCollections.observableArrayList();
    private String csvPath;

    public CardDimension getDimension() { return dimension; }
    public void setDimension(CardDimension dimension) { this.dimension = dimension; }

    public ObservableList<CardElement> getElements() { return elements; }

    public String getCsvPath() { return csvPath; }
    public void setCsvPath(String csvPath) { this.csvPath = csvPath; }
}
