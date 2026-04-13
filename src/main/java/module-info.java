module io.github.parseworks.cardmaker {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;

    requires com.opencsv;
    requires com.fasterxml.jackson.databind;

    opens io.github.parseworks.cardmaker to javafx.fxml, com.fasterxml.jackson.databind;
    exports io.github.parseworks.cardmaker;
}