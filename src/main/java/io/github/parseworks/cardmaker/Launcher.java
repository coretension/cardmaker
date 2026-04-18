package io.github.parseworks.cardmaker;

import javafx.application.Application;

import javax.imageio.ImageIO;

public class Launcher {
    public static void main(String[] args) {
        // Trigger ImageIO registration to catch NoClassDefFoundError early if dependencies are missing
        ImageIO.getReaderFormatNames();
        Application.launch(CardMakerApplication.class, args);
    }
}
