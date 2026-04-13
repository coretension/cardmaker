package io.github.parseworks.cardmaker;

public enum CardDimension {
    POKER(2.5, 3.5, "Standard Poker"),
    TAROT(2.75, 4.75, "Standard Tarot"),
    BRIDGE(2.25, 3.5, "Standard Bridge"),
    SQUARE(2.5, 2.5, "Square (2.5x2.5)"),
    MINI(1.75, 2.5, "Mini (1.75x2.5)");

    private final double widthInches;
    private final double heightInches;
    private final String displayName;
    public static final double DPI = 96.0;

    CardDimension(double widthInches, double heightInches, String displayName) {
        this.widthInches = widthInches;
        this.heightInches = heightInches;
        this.displayName = displayName;
    }

    public double getWidthPx() { return widthInches * DPI; }
    public double getHeightPx() { return heightInches * DPI; }
    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}
