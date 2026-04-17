package io.github.parseworks.cardmaker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ContainerElementTest {

    @Test
    public void testDefaultValues() {
        ContainerElement ce = new ContainerElement();
        assertEquals(ContainerElement.LayoutType.POSITIONAL, ce.getLayoutType());
        assertEquals(ContainerElement.Alignment.LEFT, ce.getAlignment());
        assertEquals(0.0, ce.getSpacing());
        assertEquals(100.0, ce.getWidth());
        assertEquals(100.0, ce.getHeight());
        assertFalse(ce.isLocked());
    }

    @Test
    public void testSetProperties() {
        ContainerElement ce = new ContainerElement();
        ce.setLayoutType(ContainerElement.LayoutType.VERTICAL);
        ce.setAlignment(ContainerElement.Alignment.CENTER);
        ce.setSpacing(10.0);
        ce.setLocked(true);
        
        assertEquals(ContainerElement.LayoutType.VERTICAL, ce.getLayoutType());
        assertEquals(ContainerElement.Alignment.CENTER, ce.getAlignment());
        assertEquals(10.0, ce.getSpacing());
        assertTrue(ce.isLocked());
    }

    @Test
    public void testProperties() {
        ContainerElement ce = new ContainerElement();
        ce.layoutTypeProperty().set(ContainerElement.LayoutType.HORIZONTAL);
        ce.alignmentProperty().set(ContainerElement.Alignment.RIGHT);

        assertEquals(ContainerElement.LayoutType.HORIZONTAL, ce.getLayoutType());
        assertEquals(ContainerElement.Alignment.RIGHT, ce.getAlignment());
    }
}
