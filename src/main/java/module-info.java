module io.github.coretension.cardmaker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;

    requires com.opencsv;
    requires com.fasterxml.jackson.databind;
    requires com.github.librepdf.openpdf;

    opens io.github.coretension.cardmaker to javafx.fxml, com.fasterxml.jackson.databind;
    exports io.github.coretension.cardmaker;
}