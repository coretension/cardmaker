package io.github.parseworks.cardmaker;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

public class DeckStorageTest {

    @Test
    public void testSaveAndLoad() throws IOException {
        CardTemplate template = new CardTemplate();
        template.setCsvPath("test.csv");
        
        TextElement text = new TextElement();
        text.setName("Test Text");
        text.setX(10);
        text.setY(20);
        text.setText("Hello {{Name}}");
        template.getElements().add(text);

        ContainerElement container = new ContainerElement("Test Container");
        container.setX(50);
        container.setY(60);
        container.setWidth(200);
        container.setHeight(150);
        container.setAlpha(0.5);
        container.setBackgroundColor("#FF0000");
        container.setLayoutType(ContainerElement.LayoutType.HORIZONTAL);
        container.setAlignment(ContainerElement.Alignment.RIGHT);
        template.getElements().add(container);
        
        File tempFile = Files.createTempFile("deck", ".json").toFile();
        try {
            DeckStorage.save(template, tempFile);
            
            CardTemplate loaded = DeckStorage.load(tempFile);
            assertEquals("test.csv", loaded.getCsvPath());
            assertEquals(2, loaded.getElements().size());
            
            CardElement el = loaded.getElements().get(0);
            assertTrue(el instanceof TextElement);
            TextElement loadedText = (TextElement) el;
            assertEquals("Test Text", loadedText.getName());
            assertEquals(10, loadedText.getX());
            assertEquals(20, loadedText.getY());
            assertEquals("Hello {{Name}}", loadedText.getText());

            CardElement el2 = loaded.getElements().get(1);
            assertTrue(el2 instanceof ContainerElement);
            ContainerElement loadedContainer = (ContainerElement) el2;
            assertEquals("Test Container", loadedContainer.getName());
            assertEquals(50, loadedContainer.getX());
            assertEquals(60, loadedContainer.getY());
            assertEquals(200, loadedContainer.getWidth());
            assertEquals(150, loadedContainer.getHeight());
            assertEquals(0.5, loadedContainer.getAlpha());
            assertEquals("#FF0000", loadedContainer.getBackgroundColor());
            assertEquals(ContainerElement.LayoutType.HORIZONTAL, loadedContainer.getLayoutType());
            assertEquals(ContainerElement.Alignment.RIGHT, loadedContainer.getAlignment());
        } finally {
            tempFile.delete();
        }
    }
}
