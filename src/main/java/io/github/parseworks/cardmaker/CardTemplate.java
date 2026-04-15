package io.github.parseworks.cardmaker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class CardTemplate {
    private CardDimension dimension = CardDimension.POKER;
    private final ObservableList<CardElement> elements = FXCollections.observableArrayList();
    private String csvPath;
    private IconLibrary iconLibrary = new IconLibrary();
    private FontLibrary fontLibrary = new FontLibrary();

    public CardDimension getDimension() { return dimension; }
    public void setDimension(CardDimension dimension) { this.dimension = dimension; }

    public ObservableList<CardElement> getElements() { return elements; }

    public String getCsvPath() { return csvPath; }
    public void setCsvPath(String csvPath) { this.csvPath = csvPath; }

    public IconLibrary getIconLibrary() { return iconLibrary; }
    public void setIconLibrary(IconLibrary iconLibrary) { this.iconLibrary = iconLibrary; }

    public FontLibrary getFontLibrary() { return fontLibrary; }
    public void setFontLibrary(FontLibrary fontLibrary) { this.fontLibrary = fontLibrary; }
}
