package io.github.coretension.cardmaker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ImageElementOutlineTest {

    @Test
    public void testOutlineProperties() {
        ImageElement ie = new ImageElement();
        
        // Test default values
        assertEquals(0.0, ie.getOutlineWidth());
        assertEquals("#000000", ie.getOutlineColor());
        
        // Test setting values
        ie.setOutlineWidth(2.5);
        ie.setOutlineColor("#FF0000");
        
        assertEquals(2.5, ie.getOutlineWidth());
        assertEquals("#FF0000", ie.getOutlineColor());
        
        // Test property binding
        ie.outlineWidthProperty().set(5.0);
        ie.outlineColorProperty().set("#00FF00");
        
        assertEquals(5.0, ie.getOutlineWidth());
        assertEquals("#00FF00", ie.getOutlineColor());
    }
}
