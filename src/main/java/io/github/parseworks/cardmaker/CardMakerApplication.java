package io.github.parseworks.cardmaker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;

public class CardMakerApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // Auto-detect screen DPI
        double screenDpi = Screen.getPrimary().getDpi();
        CardDimension.setDpi(screenDpi);

        FXMLLoader fxmlLoader = new FXMLLoader(CardMakerApplication.class.getResource("card-maker-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1000, 700);
        CardMakerController controller = fxmlLoader.getController();

        stage.setTitle("Card Maker");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            controller.saveTempDeck();
            controller.saveSettings();
        });
        stage.show();
    }
}
