package io.github.parseworks.cardmaker;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingFXUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CardMakerController {

    @FXML private TreeView<CardElement> elementTreeView;
    @FXML private Pane cardCanvas;
    @FXML private VBox propertiesPane;
    @FXML private Label recordLabel;
    @FXML private StackPane canvasContainer;
    @FXML private Label zoomLabel;
    @FXML private Label sizeLabel;
    @FXML private Label coordinatesLabel;

    private final Map<CardElement, ChangeListener<Number>> xListeners = new HashMap<>();
    private final Map<CardElement, ChangeListener<Number>> yListeners = new HashMap<>();

    private CardTemplate currentTemplate = new CardTemplate();
    private List<Map<String, String>> csvData = new ArrayList<>();
    private List<String> csvHeaders = new ArrayList<>();
    private int currentRecordIndex = -1;
    private final DataMerger dataMerger = new DataMerger();
    private File currentFile;
    private File lastOpenedDirectory;
    private AppSettings settings;
    private boolean previewMode = false;
    private boolean showClippedContent = false;
    private double zoomLevel = 1.0;
    private CardElement copiedElement;

    @FXML
    public void initialize() {
        loadSettings();
        setupTemplateListeners();
        updateCanvasSize();
        updateSizeLabel();
        setupZoomListeners();
        
        if (settings.getLastOpenedDeckPath() != null) {
            File lastFile = new File(settings.getLastOpenedDeckPath());
            if (lastFile.exists()) {
                loadDeck(lastFile);
            } else {
                loadTempDeck();
            }
        } else {
            loadTempDeck();
        }
        
        elementTreeView.setCellFactory(tv -> {
            TreeCell<CardElement> cell = new TreeCell<>() {
                @Override
                protected void updateItem(CardElement item, boolean empty) {
                    super.updateItem(item, empty);
                    textProperty().unbind();
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setContextMenu(null);
                    } else {
                        final String type;
                        if (item instanceof TextElement) type = "[T]";
                        else if (item instanceof ImageElement) type = "[I]";
                        else if (item instanceof ContainerElement) type = "[C]";
                        else if (item instanceof FontElement) type = "[F]";
                        else type = "[E]";
                        textProperty().bind(item.nameProperty().map(name -> type + " " + name));
                        
                        opacityProperty().bind(item.enabledProperty().map(e -> e ? 1.0 : 0.5));
                        
                        ContextMenu contextMenu = new ContextMenu();
                        MenuItem enableDisableItem = new MenuItem();
                        enableDisableItem.textProperty().bind(item.enabledProperty().map(e -> e ? "Disable" : "Enable"));
                        enableDisableItem.setOnAction(e -> {
                            item.setEnabled(!item.isEnabled());
                            renderTemplate();
                        });

                        MenuItem copyItem = new MenuItem("Copy");
                        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
                        copyItem.setOnAction(e -> handleCopyElement(null));

                        MenuItem pasteItem = new MenuItem("Paste");
                        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));
                        pasteItem.setOnAction(e -> handlePasteElement(null));
                        pasteItem.setDisable(copiedElement == null);

                        MenuItem deleteItem = new MenuItem("Delete");
                        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
                        deleteItem.setOnAction(e -> {
                            elementTreeView.getSelectionModel().select(getTreeItem());
                            handleDeleteElement(null);
                        });
                        MenuItem moveForward = new MenuItem("Move Forward");
                        moveForward.setOnAction(e -> handleMoveForward(null));
                        MenuItem moveBackward = new MenuItem("Move Backward");
                        moveBackward.setOnAction(e -> handleMoveBackward(null));
                        MenuItem bringToFront = new MenuItem("Bring to Front");
                        bringToFront.setOnAction(e -> handleBringToFront(null));
                        MenuItem sendToBack = new MenuItem("Send to Back");
                        sendToBack.setOnAction(e -> handleSendToBack(null));

                        MenuItem lockUnlockItem = new MenuItem();
                        if (item instanceof ContainerElement ce) {
                            lockUnlockItem.textProperty().bind(ce.lockedProperty().map(l -> l ? "Unlock Container" : "Lock Container"));
                            lockUnlockItem.setOnAction(e -> {
                                ce.setLocked(!ce.isLocked());
                                renderTemplate();
                            });
                        }

                        contextMenu.getItems().addAll(enableDisableItem);
                        if (item instanceof ContainerElement) {
                            contextMenu.getItems().add(lockUnlockItem);
                        }
                        contextMenu.getItems().addAll(new SeparatorMenuItem(),
                                copyItem, pasteItem, new SeparatorMenuItem(), 
                                moveForward, moveBackward, bringToFront, sendToBack,
                                new SeparatorMenuItem(), deleteItem);
                        setContextMenu(contextMenu);
                    }
                }
            };

            cell.setOnDragDetected(event -> {
                if (cell.getItem() != null) {
                    Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(cell.getItem().getName()); // Use name as a placeholder
                    db.setContent(content);
                    event.consume();
                }
            });

            cell.setOnDragOver(event -> {
                if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    
                    // Visual feedback for drop position
                    if (event.getY() < cell.getHeight() * 0.25) {
                        cell.setStyle("-fx-border-color: #0096C9; -fx-border-width: 2 0 0 0;");
                    } else if (event.getY() > cell.getHeight() * 0.75) {
                        cell.setStyle("-fx-border-color: #0096C9; -fx-border-width: 0 0 2 0;");
                    } else {
                        cell.setStyle("-fx-background-color: #E9F6FD;");
                    }
                }
                event.consume();
            });

            cell.setOnDragExited(event -> {
                cell.setStyle("");
            });

            cell.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    TreeItem<CardElement> draggedItem = elementTreeView.getSelectionModel().getSelectedItem();
                    if (draggedItem != null) {
                        CardElement draggedElement = draggedItem.getValue();
                        TreeItem<CardElement> targetItem = cell.getTreeItem();

                        if (draggedElement != null && targetItem != null) {
                            moveElement(draggedElement, targetItem, event.getY() / cell.getHeight());
                            success = true;
                        }
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });

            return cell;
        });

        elementTreeView.setOnKeyPressed(event -> {
            CardElement selected = getSelectedElement();
            if (selected == null) return;

            if (event.getCode() == KeyCode.UP) {
                saveExpandedState(elementTreeView.getRoot());
                ObservableList<CardElement> parentList = findParentList(selected);
                if (parentList != null) {
                    int index = parentList.indexOf(selected);
                    if (index > 0) {
                        parentList.remove(selected);
                        parentList.add(index - 1, selected);
                        renderTemplate();
                        selectElement(selected);
                        event.consume();
                    }
                }
            } else if (event.getCode() == KeyCode.DOWN) {
                saveExpandedState(elementTreeView.getRoot());
                ObservableList<CardElement> parentList = findParentList(selected);
                if (parentList != null) {
                    int index = parentList.indexOf(selected);
                    if (index < parentList.size() - 1) {
                        parentList.remove(selected);
                        parentList.add(index + 1, selected);
                        renderTemplate();
                        selectElement(selected);
                        event.consume();
                    }
                }
            } else if (event.getCode() == KeyCode.LEFT) {
                saveExpandedState(elementTreeView.getRoot());
                ParentCardElement parent = findParentElement(selected);
                if (parent != null) {
                    ObservableList<CardElement> parentList = parent.getChildren();
                    parentList.remove(selected);
                    
                    ObservableList<CardElement> grandparentList = findParentList(parent);
                    if (grandparentList != null) {
                        int parentIndex = grandparentList.indexOf(parent);
                        grandparentList.add(parentIndex + 1, selected);
                        renderTemplate();
                        selectElement(selected);
                        event.consume();
                    }
                }
            }
        });

        // Allow dropping on the TreeView itself (empty space) to move to root
        elementTreeView.setOnDragOver(event -> {
            if (event.getGestureSource() instanceof TreeCell && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        elementTreeView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                TreeItem<CardElement> draggedItem = elementTreeView.getSelectionModel().getSelectedItem();
                if (draggedItem != null) {
                    CardElement draggedElement = draggedItem.getValue();
                    if (draggedElement != null) {
                        saveExpandedState(elementTreeView.getRoot());
                        // If it wasn't already handled by a cell, it might be a drop on empty space
                        // We move it to the end of the root elements
                        ObservableList<CardElement> sourceParentList = findParentList(draggedElement);
                        if (sourceParentList != null) {
                            sourceParentList.remove(draggedElement);
                            currentTemplate.getElements().add(draggedElement);
                            selectElement(draggedElement);
                            success = true;
                        }
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
        
        elementTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            CardElement el = (newVal != null) ? newVal.getValue() : null;
            updatePropertiesPane(el);
            highlightOnCanvas(el);
            updateCoordinatesLabel(el);
        });
    }

    private void updateCoordinatesLabel(CardElement el) {
        if (el == null) {
            coordinatesLabel.setVisible(false);
            return;
        }
        coordinatesLabel.setVisible(true);
        coordinatesLabel.setText(String.format("X: %.1f, Y: %.1f", el.getX(), el.getY()));
        
        // Ensure we only have one listener per element to avoid leaks and duplicate updates
        if (!xListeners.containsKey(el)) {
            ChangeListener<Number> xListener = (obs, oldX, newX) -> {
                if (getSelectedElement() == el) {
                    coordinatesLabel.setText(String.format("X: %.1f, Y: %.1f", el.getX(), el.getY()));
                }
            };
            el.xProperty().addListener(xListener);
            xListeners.put(el, xListener);
        }
        if (!yListeners.containsKey(el)) {
            ChangeListener<Number> yListener = (obs, oldY, newY) -> {
                if (getSelectedElement() == el) {
                    coordinatesLabel.setText(String.format("X: %.1f, Y: %.1f", el.getX(), el.getY()));
                }
            };
            el.yProperty().addListener(yListener);
            yListeners.put(el, yListener);
        }
    }

    private void moveElement(CardElement element, TreeItem<CardElement> targetItem, double relativeY) {
        saveExpandedState(elementTreeView.getRoot());
        ObservableList<CardElement> sourceParentList = findParentList(element);
        if (sourceParentList == null) return;

        CardElement targetElement = targetItem.getValue();
        ObservableList<CardElement> targetParentList;
        int targetIndex;

        if (targetElement instanceof ParentCardElement pe && relativeY > 0.25 && relativeY < 0.75) {
            // Drop inside the parent element (Container or Condition)
            targetParentList = pe.getChildren();
            targetIndex = targetParentList.size(); // Add to end of children by default
        } else {
            // Drop as sibling
            targetParentList = findParentList(targetElement);
            if (targetParentList == null) return;
            targetIndex = targetParentList.indexOf(targetElement);
            if (relativeY > 0.75) {
                targetIndex++;
            }
        }

        // Avoid dropping an element onto its own descendants
        if (isDescendant(element, targetElement)) return;

        int sourceIndex = sourceParentList.indexOf(element);
        sourceParentList.remove(element);

        // Adjust index if moving within the same list from earlier to later position
        if (sourceParentList == targetParentList && targetIndex > sourceIndex) {
            targetIndex--;
        }

        if (targetIndex < 0) {
            targetParentList.add(element);
        } else if (targetIndex > targetParentList.size()) {
            targetParentList.add(element);
        } else {
            targetParentList.add(targetIndex, element);
        }
        
        selectElement(element);
    }

    private boolean isDescendant(CardElement ancestor, CardElement potentialDescendant) {
        if (ancestor == potentialDescendant) return true;
        if (ancestor instanceof ParentCardElement pe) {
            for (CardElement child : pe.getChildren()) {
                if (isDescendant(child, potentialDescendant)) return true;
            }
        }
        return false;
    }

    private final Set<CardElement> expandedElements = new HashSet<>();
    private final ListChangeListener<CardElement> nestedListener = c -> {
        saveExpandedState(elementTreeView.getRoot());
        rebuildTree();
        renderTemplate();
    };

    private void saveExpandedState(TreeItem<CardElement> item) {
        if (item == null) return;
        if (item.isExpanded() && item.getValue() != null) {
            expandedElements.add(item.getValue());
        } else {
            expandedElements.remove(item.getValue());
        }
        for (TreeItem<CardElement> child : item.getChildren()) {
            saveExpandedState(child);
        }
    }

    private void setupTemplateListeners() {
        rebuildTree();
        currentTemplate.getElements().addListener((ListChangeListener<CardElement>) c -> {
            saveExpandedState(elementTreeView.getRoot());
            rebuildTree();
            renderTemplate();
        });
    }

    private void rebuildTree() {
        CardElement selected = getSelectedElement();
        if (elementTreeView.getRoot() == null) {
            TreeItem<CardElement> root = new TreeItem<>(new ContainerElement("Root"));
            elementTreeView.setRoot(root);
            elementTreeView.setShowRoot(false);
        }
        
        refreshTreeItems(elementTreeView.getRoot(), currentTemplate.getElements());
        if (selected != null) {
            selectElement(selected);
        }
    }

    private void refreshTreeItems(TreeItem<CardElement> parentItem, ObservableList<CardElement> elements) {
        // Simple strategy: rebuild if sizes differ or values differ
        // For a more robust solution, we'd sync items more carefully
        parentItem.getChildren().clear();
        for (CardElement el : elements) {
            parentItem.getChildren().add(createTreeItemRecursive(el));
        }
    }

    private TreeItem<CardElement> createTreeItemRecursive(CardElement el) {
        TreeItem<CardElement> item = new TreeItem<>(el);
        item.setExpanded(expandedElements.contains(el) || (el instanceof ParentCardElement && !expandedElements.isEmpty() == false));
        // If expandedElements is empty (first time or reset), default to expanded
        if (expandedElements.isEmpty()) {
            item.setExpanded(true);
        }
        
        if (el instanceof ParentCardElement pe) {
            // Listen for children changes to refresh this branch
            pe.getChildren().removeListener(nestedListener); // avoid duplicates
            pe.getChildren().addListener(nestedListener);
            
            for (CardElement child : pe.getChildren()) {
                item.getChildren().add(createTreeItemRecursive(child));
            }
        }
        return item;
    }

    private void highlightOnCanvas(CardElement selectedEl) {
        // First clear all effects and hide resize handles
        clearAllHighlights(cardCanvas);

        if (selectedEl == null || previewMode) return;

        // Find the node corresponding to the selected element
        Node found = findNodeForElement(cardCanvas, selectedEl);
        if (found != null) {
            found.setEffect(new DropShadow(10, Color.BLUE));
            
            // Show resize handle if it's a container
            if (found instanceof Pane pane) {
                Node handle = pane.lookup(".resize-handle");
                if (handle != null) {
                    handle.setVisible(true);
                    handle.toFront();
                }
            } else if (found.getParent() instanceof Pane parentPane) {
                // If it's a child of a layout pane (HBox/VBox/FlowPane), 
                // we might want to highlight the parent if it's a ContainerElement?
                // But the user selected the child.
            }
        }
    }

    private void clearAllHighlights(Pane pane) {
        for (Node node : pane.getChildren()) {
            node.setEffect(null);
            if (node instanceof Pane childPane) {
                Node handle = childPane.lookup(".resize-handle");
                if (handle != null) {
                    handle.setVisible(false);
                }
                clearAllHighlights(childPane);
            }
        }
    }

    private Node findNodeForElement(Pane pane, CardElement el) {
        for (Node node : pane.getChildren()) {
            if (node.getProperties().get("cardElement") == el) {
                return node;
            }
            if (node instanceof Pane childPane) {
                Node found = findNodeForElement(childPane, el);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean hasOverflowingImage(ObservableList<CardElement> elements) {
        for (CardElement el : elements) {
            if (el instanceof ImageElement ie && ie.isAllowOverflow()) return true;
            if (el instanceof ParentCardElement pe) {
                if (hasOverflowingImage(pe.getChildren())) return true;
            }
        }
        return false;
    }

    private void updateCanvasSize() {
        double width = currentTemplate.getDimension().getWidthPx();
        double height = currentTemplate.getDimension().getHeightPx();
        cardCanvas.setMinWidth(width);
        cardCanvas.setMaxWidth(width);
        cardCanvas.setMinHeight(height);
        cardCanvas.setMaxHeight(height);
        
        updateSizeLabel();
        
        if (!showClippedContent) {
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(width, height);
            cardCanvas.setClip(clip);
        } else {
            cardCanvas.setClip(null);
        }
    }

    private void renderTemplate() {
        cardCanvas.getChildren().clear();
        Map<String, String> currentRecord = (currentRecordIndex >= 0 && currentRecordIndex < csvData.size()) 
                ? csvData.get(currentRecordIndex) : null;

        renderElements(currentTemplate.getElements(), cardCanvas, currentRecord, null, ContainerElement.LayoutType.POSITIONAL, ContainerElement.Alignment.LEFT, false, false);
        highlightOnCanvas(getSelectedElement());
    }

    public void renderElementsExternal(ObservableList<CardElement> elements, Pane targetPane, Map<String, String> currentRecord, boolean forFinalDesign) {
        renderElements(elements, targetPane, currentRecord, null, ContainerElement.LayoutType.POSITIONAL, ContainerElement.Alignment.LEFT, forFinalDesign, false);
    }

    private void renderElements(ObservableList<CardElement> elements, Pane targetPane, Map<String, String> currentRecord, FontElement inheritedFont, ContainerElement.LayoutType containerLayout, ContainerElement.Alignment containerAlignment, boolean forFinalDesign, boolean isLocked) {
        FontElement currentFont = inheritedFont;
        for (CardElement el : elements) {
            if (!el.isEnabled()) continue;
            if (el instanceof ConditionElement ce) {
                if (dataMerger.evaluateCondition(ce.getCondition(), currentRecord)) {
                    renderElements(ce.getChildren(), targetPane, currentRecord, currentFont, containerLayout, containerAlignment, forFinalDesign, isLocked);
                }
                continue;
            }
            if (el instanceof FontElement fe) {
                currentFont = fe;
                continue;
            }

            if (el instanceof IconElement ice && containerLayout != ContainerElement.LayoutType.POSITIONAL) {
                // Special handling for IconElement in layout containers: render individual icons as separate nodes
                List<Node> iconNodes = createIconNodes(ice, currentRecord, containerAlignment);
                for (Node node : iconNodes) {
                    targetPane.getChildren().add(node);
                    if (!forFinalDesign && !isLocked) {
                        makeDraggable(node, ice);
                    }
                    node.getProperties().put("cardElement", ice);
                }
                continue;
            }

            Node node = createNodeForElement(el, currentRecord, currentFont, containerLayout, containerAlignment, forFinalDesign, isLocked);
            if (node != null) {
                targetPane.getChildren().add(node);
                if (el instanceof ParentCardElement pe && node instanceof Pane childPane) {
                    ContainerElement.LayoutType childLayout = ContainerElement.LayoutType.POSITIONAL;
                    ContainerElement.Alignment childAlign = ContainerElement.Alignment.LEFT;
                    boolean nextLocked = isLocked;
                    
                    if (pe instanceof ContainerElement ce) {
                        childLayout = ce.getLayoutType();
                        childAlign = ce.getAlignment();
                        nextLocked |= ce.isLocked();
                    }
                    
                    renderElements(pe.getChildren(), childPane, currentRecord, currentFont, childLayout, childAlign, forFinalDesign, nextLocked);
                }
                if (node instanceof Pane pane) {
                    ensureResizeHandleOnTop(pane);
                }
            }
        }
    }

    private List<Node> createIconNodes(IconElement ice, Map<String, String> currentRecord, ContainerElement.Alignment parentAlignment) {
        List<Node> nodes = new ArrayList<>();
        String val = (currentRecord != null) ? dataMerger.merge(ice.getValue(), currentRecord) : ice.getValue();
        if (val != null) {
            Map<String, String> iconMap = currentTemplate.getIconLibrary().getMappings().get(ice.getMappingName());
            if (iconMap != null) {
                List<String> sortedKeys = iconMap.keySet().stream()
                        .filter(k -> !k.isEmpty())
                        .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                        .toList();

                String remaining = val;
                while (!remaining.isEmpty()) {
                    boolean found = false;
                    for (String key : sortedKeys) {
                        if (remaining.startsWith(key)) {
                            String iconPath = iconMap.get(key);
                            if (iconPath != null && !iconPath.isEmpty()) {
                                ImageView iv = new ImageView();
                                iv.setImage(loadSafeImage(iconPath));
                                if (iv.getImage() != null) {
                                    iv.fitWidthProperty().bind(ice.iconWidthProperty());
                                    iv.fitHeightProperty().bind(ice.iconHeightProperty());
                                    iv.setPreserveRatio(true);
                                    iv.setPickOnBounds(true);
                                    nodes.add(iv);
                                }
                            }
                            remaining = remaining.substring(key.length());
                            found = true;
                            break;
                        }
                    }
                    if (!found) remaining = remaining.substring(1);
                }
            }
        }
        return nodes;
    }

    private Node createNodeForElement(CardElement el, Map<String, String> currentRecord, FontElement fontConfig, ContainerElement.LayoutType parentLayout, ContainerElement.Alignment parentAlignment, boolean forFinalDesign, boolean isLocked) {
        Node node;
        boolean isPositional = parentLayout == null || parentLayout == ContainerElement.LayoutType.POSITIONAL;

        if (el instanceof TextElement te) {
            Text text = new Text();
            text.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> (currentRecord != null) ? dataMerger.merge(te.getText(), currentRecord) : te.getText(),
                te.textProperty()
            ));
            text.getStyleClass().add("text-element");
            
            // Resolve font config
            FontElement resolvedFont = fontConfig;
            if (te.getFontConfigName() != null && !te.getFontConfigName().equals("Default")) {
                FontElement libFont = currentTemplate.getFontLibrary().getFonts().get(te.getFontConfigName());
                if (libFont != null) {
                    resolvedFont = libFont;
                }
            }
            final FontElement effectiveFont = resolvedFont;

            if (effectiveFont != null) {
                text.fontProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> Font.font(effectiveFont.getFontFamily(), effectiveFont.getFontWeight(), effectiveFont.getFontPosture(), effectiveFont.getFontSize()),
                    effectiveFont.fontFamilyProperty(), effectiveFont.fontWeightProperty(), effectiveFont.fontPostureProperty(), effectiveFont.fontSizeProperty()
                ));
                text.fillProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> {
                        try { return Color.web(effectiveFont.getColor()); } catch (Exception e) { return Color.BLACK; }
                    }, effectiveFont.colorProperty()
                ));
                text.rotateProperty().bind(effectiveFont.angleProperty());
                text.strokeWidthProperty().bind(effectiveFont.outlineWidthProperty());
                text.strokeProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> {
                        try { return Color.web(effectiveFont.getOutlineColor()); } catch (Exception e) { return Color.TRANSPARENT; }
                    }, effectiveFont.outlineColorProperty()
                ));
            } else {
                text.fontProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> Font.font(te.getFontSize()), te.fontSizeProperty()
                ));
                text.fillProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> {
                        try { return Color.web(te.getColor()); } catch (Exception e) { return Color.BLACK; }
                    }, te.colorProperty()
                ));
                text.rotateProperty().bind(te.angleProperty());
                text.strokeWidthProperty().bind(te.outlineWidthProperty());
                text.strokeProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> {
                        try { return Color.web(te.getOutlineColor()); } catch (Exception e) { return Color.TRANSPARENT; }
                    }, te.outlineColorProperty()
                ));
            }
            text.wrappingWidthProperty().bind(te.wrappingWidthProperty());
            text.setTextAlignment(mapAlignmentToTextAlignment(parentAlignment));
            
            node = text;
            if (isPositional) {
                text.layoutXProperty().bind(te.xProperty());
                text.layoutYProperty().bind(te.yProperty().add(te.fontSizeProperty())); // Text layout Y is baseline
            }
        } else if (el instanceof ImageElement ie) {
            ImageView imageView = new ImageView();
            imageView.getStyleClass().add("image-element");
            
            // Listen to path changes and update image
            javafx.beans.value.ChangeListener<String> pathListener = (obs, old, newVal) -> {
                String p = (currentRecord != null) ? dataMerger.merge(newVal, currentRecord) : newVal;
                if (p != null && !p.isEmpty()) {
                    imageView.setImage(loadSafeImage(p));
                } else {
                    imageView.setImage(null);
                }
            };
            ie.imagePathProperty().addListener(pathListener);
            pathListener.changed(null, null, ie.getImagePath());

            imageView.fitWidthProperty().bind(ie.widthProperty());
            imageView.fitHeightProperty().bind(ie.heightProperty());
            imageView.preserveRatioProperty().bind(ie.lockAspectRatioProperty());
            
            if (ie.isAllowOverflow()) {
                imageView.setManaged(false);
            }
            
            node = imageView;
            if (isPositional) {
                imageView.layoutXProperty().bind(ie.xProperty());
                imageView.layoutYProperty().bind(ie.yProperty());
            }
        } else if (el instanceof ContainerElement ce) {
            Pane pane;
            switch (ce.getLayoutType()) {
                case VERTICAL:
                    VBox vbox = new VBox();
                    vbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), true));
                    ce.alignmentProperty().addListener((obs, old, newVal) -> vbox.setAlignment(mapAlignmentToPos(newVal, true)));
                    vbox.spacingProperty().bind(ce.spacingProperty());
                    pane = vbox;
                    break;
                case HORIZONTAL:
                    HBox hbox = new HBox();
                    hbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), false));
                    ce.alignmentProperty().addListener((obs, old, newVal) -> hbox.setAlignment(mapAlignmentToPos(newVal, false)));
                    hbox.spacingProperty().bind(ce.spacingProperty());
                    pane = hbox;
                    break;
                case FLOW:
                    FlowPane flowPane = new FlowPane();
                    flowPane.setAlignment(mapAlignmentToPos(ce.getAlignment(), false));
                    ce.alignmentProperty().addListener((obs, old, newVal) -> flowPane.setAlignment(mapAlignmentToPos(newVal, false)));
                    flowPane.hgapProperty().bind(ce.spacingProperty());
                    flowPane.vgapProperty().bind(ce.spacingProperty());
                    pane = flowPane;
                    break;
                case POSITIONAL:
                default:
                    pane = new Pane();
                    break;
            }
            pane.getStyleClass().add("container-element");
            
            pane.prefWidthProperty().bind(ce.widthProperty());
            pane.prefHeightProperty().bind(ce.heightProperty());
            pane.minWidthProperty().bind(ce.widthProperty());
            pane.minHeightProperty().bind(ce.heightProperty());
            
            if (!showClippedContent) {
                javafx.scene.shape.Rectangle paneClip = new javafx.scene.shape.Rectangle();
                paneClip.widthProperty().bind(ce.widthProperty());
                paneClip.heightProperty().bind(ce.heightProperty());
                pane.setClip(paneClip);
            } else {
                pane.setClip(null);
            }
            
            pane.setPickOnBounds(true); // allow dragging by clicking anywhere inside the bounds, even if transparent
            
            updatePaneStyle(pane, ce.getBackgroundColor(), ce.getAlpha(), forFinalDesign);
            ce.backgroundColorProperty().addListener((obs, old, newVal) -> updatePaneStyle(pane, newVal, ce.getAlpha(), forFinalDesign));
            ce.alphaProperty().addListener((obs, old, newVal) -> updatePaneStyle(pane, ce.getBackgroundColor(), newVal.doubleValue(), forFinalDesign));

            if (!forFinalDesign && (!isLocked || el instanceof ContainerElement)) {
                makeResizable(pane, ce);
            }

            node = pane;
            if (isPositional) {
                pane.layoutXProperty().bind(ce.xProperty());
                pane.layoutYProperty().bind(ce.yProperty());
            }
        } else if (el instanceof IconElement ice) {
            // This is only used for POSITIONAL layout now
            FlowPane flowPane = new FlowPane();
            flowPane.getStyleClass().add("icon-element");
            flowPane.setAlignment(mapAlignmentToPos(parentAlignment, false));
            flowPane.setPickOnBounds(false); // only pick icons, not the empty area of the flow pane
            flowPane.setMaxWidth(Region.USE_PREF_SIZE);
            flowPane.setMaxHeight(Region.USE_PREF_SIZE);
            
            // Listen to value changes and rebuild children
            javafx.beans.value.ChangeListener<Object> rebuildIcons = (obs, old, newVal) -> {
                flowPane.getChildren().clear();
                List<Node> iconNodes = createIconNodes(ice, currentRecord, parentAlignment);
                flowPane.getChildren().addAll(iconNodes);
            };
            ice.valueProperty().addListener(rebuildIcons);
            ice.mappingNameProperty().addListener(rebuildIcons);
            rebuildIcons.changed(null, null, null);

            node = flowPane;
            if (isPositional) {
                flowPane.layoutXProperty().bind(ice.xProperty());
                flowPane.layoutYProperty().bind(ice.yProperty());
            }
        } else {
            return null;
        }

        if (!forFinalDesign && (!isLocked || el instanceof ContainerElement)) {
            makeDraggable(node, el);
        }
        node.getProperties().put("cardElement", el);
        return node;
    }

    private Image loadSafeImage(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            File file = new File(path);
            if (!file.exists()) return null;

            if (path.toLowerCase().endsWith(".svg")) {
                try {
                    BufferedImage bufferedImage = ImageIO.read(file);
                    if (bufferedImage != null) {
                        return SwingFXUtils.toFXImage(bufferedImage, null);
                    }
                } catch (Exception e) {
                    // fall back to default loader
                }
            }
            return new Image(file.toURI().toString());
        } catch (Exception e) {
            return null;
        }
    }

    private javafx.geometry.Pos mapAlignmentToPos(ContainerElement.Alignment alignment, boolean vertical) {
        if (alignment == null) alignment = ContainerElement.Alignment.LEFT;
        if (vertical) {
            return switch (alignment) {
                case LEFT -> javafx.geometry.Pos.TOP_LEFT;
                case CENTER -> javafx.geometry.Pos.TOP_CENTER;
                case RIGHT -> javafx.geometry.Pos.TOP_RIGHT;
            };
        } else {
            return switch (alignment) {
                case LEFT -> javafx.geometry.Pos.CENTER_LEFT;
                case CENTER -> javafx.geometry.Pos.CENTER;
                case RIGHT -> javafx.geometry.Pos.CENTER_RIGHT;
            };
        }
    }

    private javafx.scene.text.TextAlignment mapAlignmentToTextAlignment(ContainerElement.Alignment alignment) {
        if (alignment == null) return javafx.scene.text.TextAlignment.LEFT;
        return switch (alignment) {
            case LEFT -> javafx.scene.text.TextAlignment.LEFT;
            case CENTER -> javafx.scene.text.TextAlignment.CENTER;
            case RIGHT -> javafx.scene.text.TextAlignment.RIGHT;
        };
    }

    private void updatePaneStyle(Pane pane, String color, double alpha, boolean forFinalDesign) {
        try {
            Color c = Color.web(color);
            String alphaColor = String.format("rgba(%d, %d, %d, %.2f)", 
                (int)(c.getRed() * 255), 
                (int)(c.getGreen() * 255), 
                (int)(c.getBlue() * 255), 
                alpha);
            
            // Ensure container is visible with a subtle dashed border even if background is transparent
            StringBuilder style = new StringBuilder("-fx-background-color: " + alphaColor + "; ");
            if (!previewMode && !forFinalDesign) {
                style.append("-fx-border-color: #888888; "); // Stronger border color
                style.append("-fx-border-style: dashed; ");
                style.append("-fx-border-width: 1; ");
            }
            pane.setStyle(style.toString());
        } catch (Exception e) {
            // Ignore styling errors
        }
    }

    private void ensureResizeHandleOnTop(Pane pane) {
        Node handle = pane.lookup(".resize-handle");
        if (handle != null) {
            handle.toFront();
        }
    }

    private void makeResizable(Pane pane, ContainerElement ce) {
        javafx.scene.shape.Rectangle handle = new javafx.scene.shape.Rectangle(10, 10, Color.BLUE);
        handle.getStyleClass().add("resize-handle");
        handle.setVisible(false); // Only show when selected
        handle.setCursor(javafx.scene.Cursor.SE_RESIZE);
        handle.setManaged(false); // Do not let layout managers (VBox, HBox, etc.) position this

        // Position the handle at the bottom right
        handle.layoutXProperty().bind(ce.widthProperty().subtract(10));
        handle.layoutYProperty().bind(ce.heightProperty().subtract(10));
        handle.toFront();

        final Delta dragDelta = new Delta();
        handle.setOnMousePressed(mouseEvent -> {
            dragDelta.x = mouseEvent.getSceneX();
            dragDelta.y = mouseEvent.getSceneY();
            mouseEvent.consume();
        });

        handle.setOnMouseDragged(mouseEvent -> {
            double deltaX = mouseEvent.getSceneX() - dragDelta.x;
            double deltaY = mouseEvent.getSceneY() - dragDelta.y;

            // Apply scaling if parent is scaled
            if (handle.getParent() != null) {
                deltaX /= handle.getParent().getLocalToSceneTransform().getMxx();
                deltaY /= handle.getParent().getLocalToSceneTransform().getMyy();
            }
            
            double newWidth = ce.getWidth() + deltaX;
            double newHeight = ce.getHeight() + deltaY;

            // Constrain new dimensions
            newWidth = Math.max(10, newWidth);
            newHeight = Math.max(10, newHeight);

            // Prevent extending outside card bounds
            double cardWidth = currentTemplate.getDimension().getWidthPx();
            double cardHeight = currentTemplate.getDimension().getHeightPx();
            
            // For now, we only constrain root-level elements or elements with absolute positions.
            // If they are in a container, their (x,y) is relative to the container.
            // To be truly safe, we'd need to calculate their absolute position on the card.
            // But let's start with a simple constraint: x + width <= cardWidth, y + height <= cardHeight
            // assuming x and y are relative to the card.
            
            if (ce.getX() + newWidth > cardWidth) {
                newWidth = cardWidth - ce.getX();
            }
            if (ce.getY() + newHeight > cardHeight) {
                newHeight = cardHeight - ce.getY();
            }

            if (ce.isLockAspectRatio()) {
                double ratio = ce.getWidth() / ce.getHeight();
                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                    newHeight = newWidth / ratio;
                    // Re-check bounds after aspect ratio adjustment
                    if (ce.getY() + newHeight > cardHeight) {
                        newHeight = cardHeight - ce.getY();
                        newWidth = newHeight * ratio;
                    }
                } else {
                    newWidth = newHeight * ratio;
                    // Re-check bounds after aspect ratio adjustment
                    if (ce.getX() + newWidth > cardWidth) {
                        newWidth = cardWidth - ce.getX();
                        newHeight = newWidth / ratio;
                    }
                }
            }

            ce.setWidth(newWidth);
            ce.setHeight(newHeight);

            dragDelta.x = mouseEvent.getSceneX();
            dragDelta.y = mouseEvent.getSceneY();
            
            mouseEvent.consume();
        });

        handle.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);

        pane.getChildren().add(handle);
    }

    private void makeDraggable(Node node, CardElement el) {
        final Delta dragDelta = new Delta();
        node.setOnMousePressed(mouseEvent -> {
            dragDelta.x = el.getX() - mouseEvent.getSceneX();
            dragDelta.y = el.getY() - mouseEvent.getSceneY();
            
            // Try to select in list; if not there, still update properties pane
            if (!selectElement(el)) {
                elementTreeView.getSelectionModel().clearSelection();
                updatePropertiesPane(el);
                highlightOnCanvas(el);
            }
            
            mouseEvent.consume();
        });
        node.setOnMouseDragged(mouseEvent -> {
            double newX = mouseEvent.getSceneX() + dragDelta.x;
            double newY = mouseEvent.getSceneY() + dragDelta.y;
            
            double cardWidth = currentTemplate.getDimension().getWidthPx();
            double cardHeight = currentTemplate.getDimension().getHeightPx();
            
            double width = 0;
            double height = 0;
            if (el instanceof ContainerElement ce) {
                width = ce.getWidth();
                height = ce.getHeight();
            } else if (el instanceof ImageElement ie) {
                width = ie.getWidth();
                height = ie.getHeight();
            } else if (el instanceof TextElement te) {
                // For text elements, use height based on font size (approximate)
                height = te.getFontSize();
                // Width is tricky for text, we'll assume 20 as a minimal safety margin
                width = 20; 
            }
            
            // Constrain X
            if (!(el instanceof ImageElement ie && ie.isAllowOverflow())) {
                newX = Math.max(0, newX);
                if (newX + width > cardWidth) {
                    newX = Math.max(0, cardWidth - width);
                }
            }
            
            // Constrain Y
            if (!(el instanceof ImageElement ie && ie.isAllowOverflow())) {
                newY = Math.max(0, newY);
                if (newY + height > cardHeight) {
                    newY = Math.max(0, cardHeight - height);
                }
            }

            el.setX(newX);
            el.setY(newY);
            mouseEvent.consume();
        });
    }

    private static class Delta { double x, y; }

    private boolean isUpdatingOtherAxis = false;
    private final List<ChangeListener<?>> activeListeners = new ArrayList<>();
    private final List<javafx.beans.value.ObservableValue<?>> activeProperties = new ArrayList<>();

    private <T> void addManagedListener(javafx.beans.value.ObservableValue<T> property, ChangeListener<T> listener) {
        property.addListener(listener);
        activeProperties.add(property);
        activeListeners.add(listener);
    }

    private void clearActiveListeners() {
        for (int i = 0; i < activeProperties.size(); i++) {
            @SuppressWarnings("unchecked")
            javafx.beans.value.ObservableValue<Object> prop = (javafx.beans.value.ObservableValue<Object>) activeProperties.get(i);
            @SuppressWarnings("unchecked")
            ChangeListener<Object> listener = (ChangeListener<Object>) activeListeners.get(i);
            prop.removeListener(listener);
        }
        activeProperties.clear();
        activeListeners.clear();
    }

    private HBox createSliderWithNumericField(javafx.beans.property.DoubleProperty property, double min, double max) {
        Slider slider = new Slider(min, max, property.get());
        slider.setMinWidth(150);
        slider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(slider, Priority.ALWAYS);
        slider.valueProperty().bindBidirectional(property);

        TextField textField = new TextField();
        textField.setPrefWidth(50);
        
        // Formatting function for numeric field
        Runnable updateText = () -> {
            if (max <= 1.0) {
                textField.setText(String.format("%.2f", property.get()));
            } else {
                textField.setText(String.format("%.0f", property.get()));
            }
        };

        updateText.run();
        
        property.addListener((obs, old, newVal) -> {
            if (!textField.isFocused()) {
                updateText.run();
            }
        });

        textField.setOnAction(e -> {
            try {
                double val = Double.parseDouble(textField.getText());
                property.set(Math.max(min, Math.min(max, val)));
                updateText.run();
            } catch (NumberFormatException ex) {
                updateText.run();
            }
        });

        textField.focusedProperty().addListener((obs, old, newVal) -> {
            if (!newVal) { // Lost focus
                try {
                    double val = Double.parseDouble(textField.getText());
                    property.set(Math.max(min, Math.min(max, val)));
                    updateText.run();
                } catch (NumberFormatException ex) {
                    updateText.run();
                }
            }
        });

        return new HBox(10, slider, textField);
    }

    private void updatePropertiesPane(CardElement el) {
        clearActiveListeners();
        propertiesPane.getChildren().clear();
        if (el == null) return;

        TextField nameField = new TextField(el.getName());
        nameField.textProperty().bindBidirectional(el.nameProperty());
        
        propertiesPane.getChildren().addAll(new Label("Name"), nameField);

        if (el instanceof ConditionElement ce) {
            TextField conditionField = new TextField(ce.getCondition());
            conditionField.textProperty().bindBidirectional(ce.conditionProperty());
            addManagedListener(ce.conditionProperty(), (obs, old, newVal) -> renderTemplate());
            propertiesPane.getChildren().addAll(new Label("Condition"), conditionField);
        }

        if (el instanceof TextElement te) {
            TextArea textArea = new TextArea(te.getText());
            textArea.setPrefRowCount(3);
            textArea.textProperty().bindBidirectional(te.textProperty());
            addManagedListener(te.textProperty(), (obs, old, newVal) -> renderTemplate());
        
            ComboBox<String> fontConfigCombo = new ComboBox<>();
            fontConfigCombo.getItems().add("Default");
            fontConfigCombo.getItems().addAll(currentTemplate.getFontLibrary().getFonts().keySet());
            fontConfigCombo.setValue(te.getFontConfigName());
            te.fontConfigNameProperty().bind(fontConfigCombo.valueProperty());
            addManagedListener(te.fontConfigNameProperty(), (obs, old, newVal) -> renderTemplate());

            javafx.beans.binding.BooleanBinding isNotDefault = te.fontConfigNameProperty().isNotEqualTo("Default");

            HBox sizeBox = createSliderWithNumericField(te.fontSizeProperty(), 8, 72);
            sizeBox.disableProperty().bind(isNotDefault);
            addManagedListener(te.fontSizeProperty(), (obs, old, newVal) -> renderTemplate());
            ColorPicker colorPicker = new ColorPicker(Color.web(te.getColor()));
            colorPicker.setStyle("-fx-color-label-visible: true;");
            colorPicker.setMaxWidth(Double.MAX_VALUE);
            colorPicker.disableProperty().bind(isNotDefault);
            colorPicker.setOnAction(e -> {
                te.setColor(toHexString(colorPicker.getValue()));
                renderTemplate();
            });

            HBox angleBox = createSliderWithNumericField(te.angleProperty(), -360, 360);
            angleBox.disableProperty().bind(isNotDefault);
            addManagedListener(te.angleProperty(), (obs, old, newVal) -> renderTemplate());

            HBox wrappingWidthBox = createSliderWithNumericField(te.wrappingWidthProperty(), 0, 1000);
            addManagedListener(te.wrappingWidthProperty(), (obs, old, newVal) -> renderTemplate());
            
            propertiesPane.getChildren().addAll(new Label("Text content (use {{header}} for merge)"), textArea, 
                                            new Label("Font Configuration"), fontConfigCombo,
                                            new Label("Font Size"), sizeBox,
                                            new Label("Color"), colorPicker,
                                            new Label("Angle"), angleBox,
                                            new Label("Wrapping Width (0 for none)"), wrappingWidthBox);
        } else if (el instanceof ImageElement ie) {
            TextField pathField = new TextField(ie.getImagePath());
            pathField.textProperty().bindBidirectional(ie.imagePathProperty());
            addManagedListener(ie.imagePathProperty(), (obs, old, newVal) -> {
                if (newVal != null && !newVal.isEmpty()) {
                    try {
                        File file = new File(newVal);
                        if (file.exists()) {
                            Image img = new Image(file.toURI().toString());
                            if (img.getWidth() > 0 && img.getHeight() > 0) {
                                double ratio = img.getHeight() / img.getWidth();
                                isUpdatingOtherAxis = true;
                                ie.setHeight(ie.getWidth() * ratio);
                                isUpdatingOtherAxis = false;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                renderTemplate();
            });
            
            Button browseBtn = new Button("Browse...");
            browseBtn.setOnAction(e -> {
                FileChooser fileChooser = new FileChooser();
                if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
                    fileChooser.setInitialDirectory(lastOpenedDirectory);
                }
                fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
                );
                File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
                if (file != null) {
                    lastOpenedDirectory = file.getParentFile();
                    pathField.setText(file.getAbsolutePath());
                }
            });

            HBox widthBox = createSliderWithNumericField(ie.widthProperty(), 10, 500);
            widthBox.disableProperty().bind(ie.imagePathProperty().isEmpty());
            addManagedListener(ie.widthProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ie.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ie.getHeight() / old.doubleValue();
                    ie.setHeight(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
            });

            HBox heightBox = createSliderWithNumericField(ie.heightProperty(), 10, 500);
            heightBox.disableProperty().bind(ie.imagePathProperty().isEmpty());
            addManagedListener(ie.heightProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ie.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ie.getWidth() / old.doubleValue();
                    ie.setWidth(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
            });

            CheckBox lockAspectBox = new CheckBox("Lock Aspect Ratio");
            lockAspectBox.selectedProperty().bindBidirectional(ie.lockAspectRatioProperty());

            CheckBox allowOverflowBox = new CheckBox("Allow Overflow (goes out of bounds)");
            allowOverflowBox.selectedProperty().bindBidirectional(ie.allowOverflowProperty());
            addManagedListener(ie.allowOverflowProperty(), (obs, old, newVal) -> {
                updateCanvasSize();
                renderTemplate();
            });

            propertiesPane.getChildren().addAll(new Label("Image Path"), new HBox(5, pathField, browseBtn),
                                            new Label("Width"), widthBox,
                                            new Label("Height"), heightBox,
                                            lockAspectBox,
                                            allowOverflowBox);
        } else if (el instanceof ContainerElement ce) {
            HBox widthBox = createSliderWithNumericField(ce.widthProperty(), 10, 500);
            addManagedListener(ce.widthProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ce.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ce.getHeight() / old.doubleValue();
                    ce.setHeight(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
            });

            HBox heightBox = createSliderWithNumericField(ce.heightProperty(), 10, 500);
            addManagedListener(ce.heightProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ce.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ce.getWidth() / old.doubleValue();
                    ce.setWidth(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
            });

            CheckBox lockAspectBox = new CheckBox("Lock Aspect Ratio");
            lockAspectBox.selectedProperty().bindBidirectional(ce.lockAspectRatioProperty());

            HBox alphaBox = createSliderWithNumericField(ce.alphaProperty(), 0.0, 1.0);
            ColorPicker colorPicker = new ColorPicker(Color.TRANSPARENT);
            colorPicker.setStyle("-fx-color-label-visible: true;");
            colorPicker.setMaxWidth(Double.MAX_VALUE);
            try {
                colorPicker.setValue(Color.web(ce.getBackgroundColor()));
            } catch (Exception e) {
                // Ignore
            }
            colorPicker.setOnAction(e -> {
                ce.setBackgroundColor(toHexString(colorPicker.getValue()));
            });

            ComboBox<ContainerElement.LayoutType> layoutBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(ContainerElement.LayoutType.values()));
            layoutBox.valueProperty().bindBidirectional(ce.layoutTypeProperty());
            addManagedListener(ce.layoutTypeProperty(), (obs, old, newVal) -> {
                if (newVal == ContainerElement.LayoutType.POSITIONAL && old != ContainerElement.LayoutType.POSITIONAL) {
                    // Update children's x and y to their current layoutX and layoutY
                    // Since layout properties are managed by the container's layout manager,
                    // we need to find the actual nodes for each child and use their current layout positions.
                    for (Node node : cardCanvas.lookupAll(".text-element, .image-element, .container-element")) {
                        CardElement childEl = (CardElement) node.getProperties().get("cardElement");
                        if (childEl != null && ce.getChildren().contains(childEl)) {
                            childEl.setX(node.getLayoutX());
                            childEl.setY(node.getLayoutY());
                            if (childEl instanceof TextElement te) {
                                // For Text elements, layoutY is baseline, but our property is top-left
                                // Adjust by font size since we bind yProperty().add(te.fontSizeProperty())
                                childEl.setY(node.getLayoutY() - te.getFontSize());
                            }
                        }
                    }
                }
                renderTemplate();
            });

            ComboBox<ContainerElement.Alignment> alignBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(ContainerElement.Alignment.values()));
            alignBox.valueProperty().bindBidirectional(ce.alignmentProperty());
            addManagedListener(ce.alignmentProperty(), (obs, old, newVal) -> renderTemplate());

            HBox spacingBox = createSliderWithNumericField(ce.spacingProperty(), 0, 100);
            addManagedListener(ce.spacingProperty(), (obs, old, newVal) -> renderTemplate());

            CheckBox lockedBox = new CheckBox("Locked (Children non-editable)");
            lockedBox.selectedProperty().bindBidirectional(ce.lockedProperty());
            addManagedListener(ce.lockedProperty(), (obs, old, newVal) -> renderTemplate());

            propertiesPane.getChildren().addAll(
                new Label("Width"), widthBox,
                new Label("Height"), heightBox,
                lockAspectBox,
                new Label("Background Alpha"), alphaBox,
                new Label("Background Color"), colorPicker,
                new Label("Layout Type"), layoutBox,
                new Label("Alignment"), alignBox,
                new Label("Spacing"), spacingBox,
                lockedBox
            );
        } else if (el instanceof IconElement ice) {
            TextField valueField = new TextField(ice.getValue());
            valueField.textProperty().bindBidirectional(ice.valueProperty());
            
            HBox iconWidthBox = createSliderWithNumericField(ice.iconWidthProperty(), 8, 200);
            HBox iconHeightBox = createSliderWithNumericField(ice.iconHeightProperty(), 8, 200);

            ComboBox<String> mappingBox = new ComboBox<>();
            mappingBox.getItems().addAll(currentTemplate.getIconLibrary().getMappings().keySet());
            mappingBox.valueProperty().bindBidirectional(ice.mappingNameProperty());

            propertiesPane.getChildren().addAll(
                new Label("Value (supports {{header}})"), valueField,
                new Label("Icon Width"), iconWidthBox,
                new Label("Icon Height"), iconHeightBox,
                new Label("Icon Mapping"), mappingBox
            );
        } else if (el instanceof FontElement fe) {
            ComboBox<String> familyBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(Font.getFamilies()));
            familyBox.setEditable(true);
            familyBox.valueProperty().bindBidirectional(fe.fontFamilyProperty());
            addManagedListener(fe.fontFamilyProperty(), (obs, old, newVal) -> renderTemplate());

            HBox sizeBox = createSliderWithNumericField(fe.fontSizeProperty(), 8, 72);
            addManagedListener(fe.fontSizeProperty(), (obs, old, newVal) -> renderTemplate());

            ChoiceBox<FontWeight> weightBox = new ChoiceBox<>(javafx.collections.FXCollections.observableArrayList(FontWeight.values()));
            weightBox.valueProperty().bindBidirectional(fe.fontWeightProperty());
            addManagedListener(fe.fontWeightProperty(), (obs, old, newVal) -> renderTemplate());

            ChoiceBox<FontPosture> postureBox = new ChoiceBox<>(javafx.collections.FXCollections.observableArrayList(FontPosture.values()));
            postureBox.valueProperty().bindBidirectional(fe.fontPostureProperty());
            addManagedListener(fe.fontPostureProperty(), (obs, old, newVal) -> renderTemplate());

            addManagedListener(fe.colorProperty(), (obs, old, newVal) -> renderTemplate());
            ColorPicker colorPicker = new ColorPicker(Color.web(fe.getColor()));
            colorPicker.setStyle("-fx-color-label-visible: true;");
            colorPicker.setMaxWidth(Double.MAX_VALUE);
            colorPicker.setOnAction(e -> {
                fe.setColor(toHexString(colorPicker.getValue()));
                renderTemplate();
            });

            HBox angleBox = createSliderWithNumericField(fe.angleProperty(), -360, 360);
            addManagedListener(fe.angleProperty(), (obs, old, newVal) -> renderTemplate());

            HBox outlineWidthBox = createSliderWithNumericField(fe.outlineWidthProperty(), 0, 20);
            addManagedListener(fe.outlineWidthProperty(), (obs, old, newVal) -> renderTemplate());

            ColorPicker outlineColorPicker = new ColorPicker(Color.web(fe.getOutlineColor()));
            outlineColorPicker.setStyle("-fx-color-label-visible: true;");
            outlineColorPicker.setMaxWidth(Double.MAX_VALUE);
            outlineColorPicker.setOnAction(e -> {
                fe.setOutlineColor(toHexString(outlineColorPicker.getValue()));
                renderTemplate();
            });

            propertiesPane.getChildren().addAll(
                new Label("Font Family"), familyBox,
                new Label("Font Size"), sizeBox,
                new Label("Font Weight"), weightBox,
                new Label("Font Posture"), postureBox,
                new Label("Color"), colorPicker,
                new Label("Angle"), angleBox,
                new Label("Outline Width"), outlineWidthBox,
                new Label("Outline Color"), outlineColorPicker
            );
        }
    }


    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255));
    }

    private void addMappingRow(Map<String, String> iconMap, String charStr, VBox container) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0));
        
        Label label = new Label(charStr + ":");
        label.setMinWidth(45);
        label.setStyle("-fx-font-family: monospace; -fx-font-weight: bold;");
        
        TextField pathField = new TextField(iconMap.getOrDefault(charStr, ""));
        pathField.setPromptText("Select image path...");
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathField.textProperty().addListener((obs, old, newVal) -> {
            iconMap.put(charStr, newVal == null ? "" : newVal);
            renderTemplate();
        });

        Button browseBtn = new Button("...");
        browseBtn.setTooltip(new Tooltip("Browse for image"));
        browseBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
                fileChooser.setInitialDirectory(lastOpenedDirectory);
            }
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
            if (file != null) {
                lastOpenedDirectory = file.getParentFile();
                pathField.setText(file.getAbsolutePath());
            }
        });

        Button removeBtn = new Button("X");
        removeBtn.setTooltip(new Tooltip("Remove key"));
        removeBtn.setStyle("-fx-text-fill: red;");
        removeBtn.setOnAction(e -> {
            iconMap.remove(charStr);
            container.getChildren().remove(row);
            renderTemplate();
        });

        row.getChildren().addAll(label, pathField, browseBtn, removeBtn);
        container.getChildren().add(row);
    }

    @FXML
    void handleNewDeck(ActionEvent event) {
        ChoiceDialog<CardDimension> dialog = new ChoiceDialog<>(CardDimension.POKER, CardDimension.values());
        dialog.setTitle("New Deck");
        dialog.setHeaderText("Select Physical Dimensions");
        dialog.setContentText("Card Size:");
        Optional<CardDimension> result = dialog.showAndWait();
        result.ifPresent(dimension -> {
            currentTemplate = new CardTemplate();
            currentTemplate.setDimension(dimension);
            setupTemplateListeners();
            updateCanvasSize();
            renderTemplate();
        });
    }

    @FXML
    void handleLoadCsv(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
            fileChooser.setInitialDirectory(lastOpenedDirectory);
        }
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Data Files", "*.csv", "*.ods"),
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("ODS Files", "*.ods")
        );
        File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            lastOpenedDirectory = file.getParentFile();
            loadCsvFile(file);
        }
    }

    private void loadCsvFile(File file) {
        try {
            DataMerger.CsvResult result = dataMerger.loadCsv(file.getAbsolutePath());
            csvData = result.records;
            csvHeaders = result.headers;
            currentTemplate.setCsvPath(file.getAbsolutePath());
            if (!csvData.isEmpty()) {
                currentRecordIndex = 0;
            } else {
                currentRecordIndex = -1;
            }
            updateRecordLabel();
            renderTemplate();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Error loading CSV: " + e.getMessage());
            alert.show();
        }
    }

    @FXML
    void handleAddText(ActionEvent event) {
        CardElement newEl = new TextElement("New Text");
        addElement(newEl);
    }

    @FXML
    void handleAddImage(ActionEvent event) {
        CardElement newEl = new ImageElement("New Image");
        addElement(newEl);
    }

    @FXML
    void handleAddContainer(ActionEvent event) {
        CardElement newEl = new ContainerElement("New Container");
        addElement(newEl);
    }

    @FXML
    void handleAddFont(ActionEvent event) {
        CardElement newEl = new FontElement("New Font Config");
        addElement(newEl);
    }

    @FXML
    void handleAddIcon(ActionEvent event) {
        addElement(new IconElement());
    }

    @FXML
    void handleAddCondition(ActionEvent event) {
        addElement(new ConditionElement());
    }

    @FXML
    public void handleManageIconLibrary(ActionEvent event) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Manage Icon Library");
        dialog.setHeaderText("Create and manage named icon mappings for your deck.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        content.setMinWidth(450);
        content.setPrefHeight(650);

        // Section: Select/Manage Mappings
        VBox mappingSection = new VBox(8);
        Label mappingLabel = new Label("Icon Mappings");
        mappingLabel.setStyle("-fx-font-weight: bold;");
        
        ListView<String> mappingList = new ListView<>();
        mappingList.getItems().addAll(currentTemplate.getIconLibrary().getMappings().keySet());
        mappingList.setPrefHeight(120);

        HBox mappingActions = new HBox(8);
        mappingActions.setAlignment(Pos.CENTER_LEFT);
        TextField newMapNameField = new TextField();
        newMapNameField.setPromptText("New Mapping Name");
        HBox.setHgrow(newMapNameField, Priority.ALWAYS);
        Button addMapBtn = new Button("Add Mapping");
        Button removeMapBtn = new Button("Remove Selected");
        mappingActions.getChildren().addAll(newMapNameField, addMapBtn, removeMapBtn);
        
        mappingSection.getChildren().addAll(mappingLabel, mappingList, mappingActions);

        // Section: Editor for selected mapping
        VBox editorSection = new VBox(8);
        VBox.setVgrow(editorSection, Priority.ALWAYS);
        Label editorLabel = new Label("Mapping Editor");
        editorLabel.setStyle("-fx-font-weight: bold;");
        
        VBox editorContainer = new VBox(10);
        editorContainer.setPadding(new Insets(5));
        ScrollPane editorScroll = new ScrollPane(editorContainer);
        editorScroll.setFitToWidth(true);
        VBox.setVgrow(editorScroll, Priority.ALWAYS);
        editorScroll.setStyle("-fx-background-color:transparent;");

        mappingList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            editorContainer.getChildren().clear();
            if (newVal != null) {
                Map<String, String> iconMap = currentTemplate.getIconLibrary().getMappings().get(newVal);
                if (iconMap != null) {
                    HBox addKeyRow = new HBox(8);
                    addKeyRow.setAlignment(Pos.CENTER_LEFT);
                    TextField newKeyField = new TextField();
                    newKeyField.setPromptText("New Key (e.g., F, FF, HP)");
                    HBox.setHgrow(newKeyField, Priority.ALWAYS);
                    Button addKeyBtn = new Button("Add Key");
                    addKeyRow.getChildren().addAll(newKeyField, addKeyBtn);
                    
                    VBox rowsContainer = new VBox(8);
                    
                    addKeyBtn.setOnAction(ak -> {
                        String key = newKeyField.getText().trim();
                        if (!key.isEmpty() && !iconMap.containsKey(key)) {
                            iconMap.put(key, "");
                            addMappingRow(iconMap, key, rowsContainer);
                            newKeyField.clear();
                        }
                    });

                    iconMap.keySet().stream().sorted().forEach(key -> addMappingRow(iconMap, key, rowsContainer));
                    
                    editorContainer.getChildren().addAll(addKeyRow, new Separator(), rowsContainer);
                }
            } else {
                editorContainer.getChildren().add(new Label("Select a mapping to edit its keys."));
            }
        });

        addMapBtn.setOnAction(e -> {
            String name = newMapNameField.getText().trim();
            if (!name.isEmpty() && !currentTemplate.getIconLibrary().getMappings().containsKey(name)) {
                currentTemplate.getIconLibrary().getMappings().put(name, new java.util.HashMap<>());
                mappingList.getItems().add(name);
                mappingList.getSelectionModel().select(name);
                newMapNameField.clear();
            }
        });

        removeMapBtn.setOnAction(e -> {
            String selected = mappingList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                currentTemplate.getIconLibrary().getMappings().remove(selected);
                mappingList.getItems().remove(selected);
                editorContainer.getChildren().clear();
            }
        });
        
        // Initial state
        if (mappingList.getItems().isEmpty()) {
            editorContainer.getChildren().add(new Label("No mappings defined. Add one above."));
        } else {
            mappingList.getSelectionModel().selectFirst();
        }

        editorSection.getChildren().addAll(new Separator(), editorLabel, editorScroll);
        content.getChildren().addAll(mappingSection, editorSection);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
        renderTemplate();
    }

    @FXML
    public void handleManageFontLibrary(ActionEvent event) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Manage Font Library");
        dialog.setHeaderText("Create and manage named font configurations for your deck.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        content.setMinWidth(450);
        content.setPrefHeight(650);

        // Section: Select/Manage Font Configs
        VBox fontSection = new VBox(8);
        Label fontLabel = new Label("Font Configurations");
        fontLabel.setStyle("-fx-font-weight: bold;");
        
        ListView<String> fontList = new ListView<>();
        fontList.getItems().addAll(currentTemplate.getFontLibrary().getFonts().keySet());
        fontList.setPrefHeight(120);

        HBox fontActions = new HBox(8);
        fontActions.setAlignment(Pos.CENTER_LEFT);
        TextField newFontNameField = new TextField();
        newFontNameField.setPromptText("New Font Config Name");
        HBox.setHgrow(newFontNameField, Priority.ALWAYS);
        Button addFontBtn = new Button("Add Font");
        Button removeFontBtn = new Button("Remove Selected");
        fontActions.getChildren().addAll(newFontNameField, addFontBtn, removeFontBtn);
        
        fontSection.getChildren().addAll(fontLabel, fontList, fontActions);

        // Section: Editor for selected font config
        VBox editorSection = new VBox(8);
        VBox.setVgrow(editorSection, Priority.ALWAYS);
        Label editorLabel = new Label("Font Editor");
        editorLabel.setStyle("-fx-font-weight: bold;");
        
        VBox editorContainer = new VBox(10);
        editorContainer.setPadding(new Insets(5));
        ScrollPane editorScroll = new ScrollPane(editorContainer);
        editorScroll.setFitToWidth(true);
        VBox.setVgrow(editorScroll, Priority.ALWAYS);
        editorScroll.setStyle("-fx-background-color:transparent;");

        fontList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            editorContainer.getChildren().clear();
            if (newVal != null) {
                FontElement fontEl = currentTemplate.getFontLibrary().getFonts().get(newVal);
                if (fontEl != null) {
                    VBox props = new VBox(5);
                    
                    TextField familyField = new TextField(fontEl.getFontFamily());
                    familyField.textProperty().bindBidirectional(fontEl.fontFamilyProperty());
                    
                    HBox sizeBox = createSliderWithNumericField(fontEl.fontSizeProperty(), 8, 120);
                    
                    ComboBox<FontWeight> weightBox = new ComboBox<>(FXCollections.observableArrayList(FontWeight.values()));
                    weightBox.setValue(fontEl.getFontWeight());
                    fontEl.fontWeightProperty().bind(weightBox.valueProperty());
                    
                    ComboBox<FontPosture> postureBox = new ComboBox<>(FXCollections.observableArrayList(FontPosture.values()));
                    postureBox.setValue(fontEl.getFontPosture());
                    fontEl.fontPostureProperty().bind(postureBox.valueProperty());
                    
                    ColorPicker colorPicker = new ColorPicker(Color.web(fontEl.getColor()));
                    colorPicker.setStyle("-fx-color-label-visible: true;");
                    colorPicker.setMaxWidth(Double.MAX_VALUE);
                    colorPicker.setOnAction(ce -> fontEl.setColor(toHexString(colorPicker.getValue())));

                    HBox angleBox = createSliderWithNumericField(fontEl.angleProperty(), -360, 360);
                    HBox outlineWidthBox = createSliderWithNumericField(fontEl.outlineWidthProperty(), 0, 20);
                    ColorPicker outlineColorPicker = new ColorPicker(Color.web(fontEl.getOutlineColor()));
                    outlineColorPicker.setStyle("-fx-color-label-visible: true;");
                    outlineColorPicker.setMaxWidth(Double.MAX_VALUE);
                    outlineColorPicker.setOnAction(ce -> fontEl.setOutlineColor(toHexString(outlineColorPicker.getValue())));

                    props.getChildren().addAll(
                        new Label("Font Family"), familyField,
                        new Label("Font Size"), sizeBox,
                        new Label("Font Weight"), weightBox,
                        new Label("Font Posture"), postureBox,
                        new Label("Color"), colorPicker,
                        new Label("Angle"), angleBox,
                        new Label("Outline Width"), outlineWidthBox,
                        new Label("Outline Color"), outlineColorPicker
                    );
                    editorContainer.getChildren().add(props);
                }
            } else {
                editorContainer.getChildren().add(new Label("Select a font config to edit."));
            }
        });

        addFontBtn.setOnAction(e -> {
            String name = newFontNameField.getText().trim();
            if (!name.isEmpty() && !currentTemplate.getFontLibrary().getFonts().containsKey(name)) {
                currentTemplate.getFontLibrary().getFonts().put(name, new FontElement(name));
                fontList.getItems().add(name);
                fontList.getSelectionModel().select(name);
                newFontNameField.clear();
            }
        });

        removeFontBtn.setOnAction(e -> {
            String selected = fontList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                currentTemplate.getFontLibrary().getFonts().remove(selected);
                fontList.getItems().remove(selected);
                editorContainer.getChildren().clear();
            }
        });
        
        // Initial state
        if (fontList.getItems().isEmpty()) {
            editorContainer.getChildren().add(new Label("No font configs defined. Add one above."));
        } else {
            fontList.getSelectionModel().selectFirst();
        }

        editorSection.getChildren().addAll(new Separator(), editorLabel, editorScroll);
        content.getChildren().addAll(fontSection, editorSection);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
        renderTemplate();
    }

    private void addElement(CardElement newEl) {
        CardElement selected = getSelectedElement();
        if (selected instanceof ContainerElement ce) {
            ce.getChildren().add(newEl);
        } else if (selected != null) {
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                int index = parentList.indexOf(selected);
                parentList.add(index + 1, newEl);
            } else {
                currentTemplate.getElements().add(newEl);
            }
        } else {
            currentTemplate.getElements().add(newEl);
        }
        selectElement(newEl);
    }

    private CardElement getSelectedElement() {
        TreeItem<CardElement> selectedItem = elementTreeView.getSelectionModel().getSelectedItem();
        return (selectedItem != null) ? selectedItem.getValue() : null;
    }

    private boolean selectElement(CardElement el) {
        if (el == null) return false;
        TreeItem<CardElement> item = findTreeItem(elementTreeView.getRoot(), el);
        if (item != null) {
            elementTreeView.getSelectionModel().select(item);
            return true;
        }
        return false;
    }

    private TreeItem<CardElement> findTreeItem(TreeItem<CardElement> root, CardElement el) {
        if (root == null) return null;
        if (root.getValue() == el) return root;
        for (TreeItem<CardElement> child : root.getChildren()) {
            TreeItem<CardElement> found = findTreeItem(child, el);
            if (found != null) return found;
        }
        return null;
    }

    @FXML
    void handleMoveForward(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            saveExpandedState(elementTreeView.getRoot());
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                int index = parentList.indexOf(selected);
                if (index < parentList.size() - 1) {
                    parentList.remove(selected);
                    parentList.add(index + 1, selected);
                    renderTemplate();
                    selectElement(selected);
                }
            }
        }
    }

    @FXML
    void handleMoveBackward(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            saveExpandedState(elementTreeView.getRoot());
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                int index = parentList.indexOf(selected);
                if (index > 0) {
                    parentList.remove(selected);
                    parentList.add(index - 1, selected);
                    renderTemplate();
                    selectElement(selected);
                }
            }
        }
    }

    @FXML
    void handleBringToFront(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            saveExpandedState(elementTreeView.getRoot());
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                parentList.remove(selected);
                parentList.add(selected);
                renderTemplate();
                selectElement(selected);
            }
        }
    }

    @FXML
    void handleSendToBack(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            saveExpandedState(elementTreeView.getRoot());
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                parentList.remove(selected);
                parentList.add(0, selected);
                renderTemplate();
                selectElement(selected);
            }
        }
    }

    private ParentCardElement findParentElement(CardElement el) {
        return findParentElementRecursive(currentTemplate.getElements(), el);
    }

    private ParentCardElement findParentElementRecursive(ObservableList<CardElement> elements, CardElement target) {
        for (CardElement el : elements) {
            if (el instanceof ParentCardElement pe) {
                if (pe.getChildren().contains(target)) {
                    return pe;
                }
                ParentCardElement found = findParentElementRecursive(pe.getChildren(), target);
                if (found != null) return found;
            }
        }
        return null;
    }

    @FXML
    void handleDeleteElement(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                parentList.remove(selected);
            }
        }
    }

    @FXML
    void handleCopyElement(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            try {
                copiedElement = DeckStorage.clone(selected, CardElement.class);
            } catch (IOException e) {
                System.err.println("Failed to copy element: " + e.getMessage());
            }
        }
    }

    @FXML
    void handlePasteElement(ActionEvent event) {
        if (copiedElement == null) return;

        try {
            CardElement newEl = DeckStorage.clone(copiedElement, CardElement.class);
            // Offsetting the pasted element slightly so it's visible if pasted in the same place
            newEl.setX(newEl.getX() + 10);
            newEl.setY(newEl.getY() + 10);
            newEl.setName(newEl.getName() + " (Copy)");

            CardElement selected = getSelectedElement();
            if (selected instanceof ParentCardElement pe) {
                pe.getChildren().add(newEl);
            } else if (selected != null) {
                ObservableList<CardElement> parentList = findParentList(selected);
                if (parentList != null) {
                    int index = parentList.indexOf(selected);
                    parentList.add(index + 1, newEl);
                }
            } else {
                currentTemplate.getElements().add(newEl);
            }
            selectElement(newEl);
        } catch (IOException e) {
            System.err.println("Failed to paste element: " + e.getMessage());
        }
    }

    private ObservableList<CardElement> findParentList(CardElement el) {
        if (currentTemplate.getElements().contains(el)) {
            return currentTemplate.getElements();
        }
        return findParentListRecursive(currentTemplate.getElements(), el);
    }

    private ObservableList<CardElement> findParentListRecursive(ObservableList<CardElement> elements, CardElement target) {
        for (CardElement el : elements) {
            if (el instanceof ParentCardElement pe) {
                if (pe.getChildren().contains(target)) {
                    return pe.getChildren();
                }
                ObservableList<CardElement> found = findParentListRecursive(pe.getChildren(), target);
                if (found != null) return found;
            }
        }
        return null;
    }

    @FXML
    void handlePrevRecord(ActionEvent event) {
        if (currentRecordIndex > 0) {
            currentRecordIndex--;
            updateRecordLabel();
            renderTemplate();
        }
    }

    @FXML
    void handleNextRecord(ActionEvent event) {
        if (currentRecordIndex < csvData.size() - 1) {
            currentRecordIndex++;
            updateRecordLabel();
            renderTemplate();
        }
    }

    @FXML
    void handleViewData(ActionEvent event) {
        if (csvData.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("CSV Data Viewer");
            alert.setHeaderText(null);
            alert.setContentText("No CSV data loaded. Please load a CSV file first via File -> Load CSV.");
            alert.show();
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("CSV Data Viewer");
        dialog.setHeaderText("Available Columns for Merge: " + 
            String.join(", ", csvHeaders.stream().map(h -> "{{" + h + "}}").toList()));
        
        TableView<Map<String, String>> tableView = new TableView<>();
        for (String header : csvHeaders) {
            TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
            column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(header)));
            tableView.getColumns().add(column);
        }
        
        tableView.getItems().addAll(csvData);
        
        dialog.getDialogPane().setContent(tableView);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(800, 600); // Increased size
        
        dialog.showAndWait();
    }

    @FXML
    void handlePrintDeck(ActionEvent event) {
        PrintService printService = new PrintService(currentTemplate, csvData, dataMerger, this);
        printService.showPrintDialog(propertiesPane.getScene().getWindow());
    }

    @FXML
    void handleSettings(ActionEvent event) {
        Dialog<AppSettings> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Global Application Settings");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField pathField = new TextField();
        pathField.setPrefWidth(300);
        pathField.setText(settings.getLastOpenedDeckPath() != null ? settings.getLastOpenedDeckPath() : "");

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CardMaker Files", "*.cm"));
            File file = fileChooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) {
                pathField.setText(file.getAbsolutePath());
            }
        });

        grid.add(new Label("Last Opened/Saved Deck:"), 0, 0);
        grid.add(pathField, 1, 0);
        grid.add(browseButton, 2, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                settings.setLastOpenedDeckPath(pathField.getText().isEmpty() ? null : pathField.getText());
                return settings;
            }
            return null;
        });

        Optional<AppSettings> result = dialog.showAndWait();
        result.ifPresent(s -> saveSettings());
    }

    @FXML
    void handleExit(ActionEvent event) {
        saveTempDeck();
        saveSettings();
        javafx.application.Platform.exit();
    }

    @FXML
    void handleTogglePreviewMode(ActionEvent event) {
        previewMode = ((CheckMenuItem) event.getSource()).isSelected();
        renderTemplate();
    }

    @FXML
    void handleToggleShowClippedContent(ActionEvent event) {
        showClippedContent = ((CheckMenuItem) event.getSource()).isSelected();
        updateCanvasSize();
        renderTemplate();
    }

    @FXML
    void handleZoomIn(ActionEvent event) {
        zoomLevel *= 1.2;
        updateZoom();
    }

    @FXML
    void handleZoomOut(ActionEvent event) {
        zoomLevel /= 1.2;
        updateZoom();
    }

    @FXML
    void handleResetZoom(ActionEvent event) {
        zoomLevel = 1.0;
        updateZoom();
    }

    private void updateZoom() {
        cardCanvas.setScaleX(zoomLevel);
        cardCanvas.setScaleY(zoomLevel);
        zoomLabel.setText(String.format("%.0f%%", zoomLevel * 100));
    }

    private void updateSizeLabel() {
        if (sizeLabel != null && currentTemplate != null) {
            CardDimension d = currentTemplate.getDimension();
            sizeLabel.setText(String.format("%.1f x %.1f mm", d.getWidthMm(), d.getHeightMm()));
        }
    }

    private void setupZoomListeners() {
        canvasContainer.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown() || event.isShortcutDown()) {
                if (event.getDeltaY() > 0) {
                    zoomLevel *= 1.1;
                } else if (event.getDeltaY() < 0) {
                    zoomLevel /= 1.1;
                }
                updateZoom();
                event.consume();
            }
        });
    }

    void saveTempDeck() {
        try {
            DeckStorage.save(currentTemplate, DeckStorage.getTempFile());
        } catch (IOException e) {
            System.err.println("Failed to save temp deck: " + e.getMessage());
        }
    }

    private void loadTempDeck() {
        File tempFile = DeckStorage.getTempFile();
        if (tempFile.exists()) {
            try {
                CardTemplate template = DeckStorage.load(tempFile);
                applyTemplate(template);
            } catch (IOException e) {
                System.err.println("Failed to load temp deck: " + e.getMessage());
            }
        }
    }

    private void loadSettings() {
        try {
            settings = DeckStorage.loadSettings();
            if (settings.getLastOpenedDeckPath() != null) {
                lastOpenedDirectory = new File(settings.getLastOpenedDeckPath()).getParentFile();
            }
        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
            settings = new AppSettings();
        }
    }

    void saveSettings() {
        try {
            DeckStorage.saveSettings(settings);
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    private void applyTemplate(CardTemplate template) {
        this.currentTemplate = template;
        setupTemplateListeners();
        updateCanvasSize();
        if (template.getCsvPath() != null) {
            loadCsvFile(new File(template.getCsvPath()));
        }
        renderTemplate();
    }

    @FXML
    void handleSaveDeck(ActionEvent event) {
        if (currentFile == null) {
            handleSaveDeckAs(event);
        } else {
            saveToFile(currentFile);
        }
    }

    @FXML
    void handleSaveDeckAs(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
            fileChooser.setInitialDirectory(lastOpenedDirectory);
        }
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CardMaker Files", "*.cm"));
        File file = fileChooser.showSaveDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            lastOpenedDirectory = file.getParentFile();
            currentFile = file;
            saveToFile(file);
        }
    }

    @FXML
    void handleOpenDeck(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
            fileChooser.setInitialDirectory(lastOpenedDirectory);
        }
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CardMaker Files", "*.cm"));
        File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            loadDeck(file);
        }
    }

    private void loadDeck(File file) {
        try {
            lastOpenedDirectory = file.getParentFile();
            CardTemplate template = DeckStorage.load(file);
            currentFile = file;
            settings.setLastOpenedDeckPath(file.getAbsolutePath());
            saveSettings();
            applyTemplate(template);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Error loading deck: " + e.getMessage());
            alert.show();
        }
    }

    private void saveToFile(File file) {
        try {
            DeckStorage.save(currentTemplate, file);
            settings.setLastOpenedDeckPath(file.getAbsolutePath());
            saveSettings();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Error saving deck: " + e.getMessage());
            alert.show();
        }
    }

    private void updateRecordLabel() {
        recordLabel.setText((currentRecordIndex + 1) + " / " + csvData.size());
    }
}
