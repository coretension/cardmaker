package io.github.parseworks.cardmaker;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;

import java.io.File;
import java.io.IOException;
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

    private CardTemplate currentTemplate = new CardTemplate();
    private List<Map<String, String>> csvData = new ArrayList<>();
    private List<String> csvHeaders = new ArrayList<>();
    private int currentRecordIndex = -1;
    private final DataMerger dataMerger = new DataMerger();
    private File currentFile;
    private boolean previewMode = false;
    private boolean showClippedContent = false;

    @FXML
    public void initialize() {
        setupTemplateListeners();
        updateCanvasSize();
        loadTempDeck();
        
        elementTreeView.setCellFactory(tv -> {
            TreeCell<CardElement> cell = new TreeCell<>() {
                @Override
                protected void updateItem(CardElement item, boolean empty) {
                    super.updateItem(item, empty);
                    textProperty().unbind();
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        final String type;
                        if (item instanceof TextElement) type = "[T]";
                        else if (item instanceof ImageElement) type = "[I]";
                        else if (item instanceof ContainerElement) type = "[C]";
                        else if (item instanceof FontElement) type = "[F]";
                        else type = "[E]";
                        textProperty().bind(item.nameProperty().map(name -> type + " " + name));
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
        });
    }

    private void moveElement(CardElement element, TreeItem<CardElement> targetItem, double relativeY) {
        ObservableList<CardElement> sourceParentList = findParentList(element);
        if (sourceParentList == null) return;

        CardElement targetElement = targetItem.getValue();
        ObservableList<CardElement> targetParentList;
        int targetIndex;

        if (targetElement instanceof ContainerElement ce && relativeY > 0.25 && relativeY < 0.75) {
            // Drop inside the container
            targetParentList = ce.getChildren();
            targetIndex = targetParentList.size(); // Add to end of container by default
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
        if (ancestor instanceof ContainerElement ce) {
            for (CardElement child : ce.getChildren()) {
                if (isDescendant(child, potentialDescendant)) return true;
            }
        }
        return false;
    }

    private void setupTemplateListeners() {
        rebuildTree();
        currentTemplate.getElements().addListener((ListChangeListener<CardElement>) c -> {
            rebuildTree();
            renderTemplate();
        });
    }

    private void rebuildTree() {
        if (elementTreeView.getRoot() == null) {
            TreeItem<CardElement> root = new TreeItem<>(new ContainerElement("Root"));
            elementTreeView.setRoot(root);
            elementTreeView.setShowRoot(false);
        }
        
        refreshTreeItems(elementTreeView.getRoot(), currentTemplate.getElements());
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
        item.setExpanded(true);
        if (el instanceof ContainerElement ce) {
            // Listen for children changes to refresh this branch
            ce.getChildren().removeListener(nestedListener); // avoid duplicates
            ce.getChildren().addListener(nestedListener);
            
            for (CardElement child : ce.getChildren()) {
                item.getChildren().add(createTreeItemRecursive(child));
            }
        }
        return item;
    }

    private final ListChangeListener<CardElement> nestedListener = c -> {
        rebuildTree();
        renderTemplate();
    };

    private void highlightOnCanvas(CardElement selectedEl) {
        // First clear all effects
        clearAllHighlights(cardCanvas);

        if (selectedEl == null || previewMode) return;

        // Find the node corresponding to the selected element
        Node found = findNodeForElement(cardCanvas, selectedEl);
        if (found != null) {
            found.setEffect(new DropShadow(10, Color.BLUE));
        }
    }

    private void clearAllHighlights(Pane pane) {
        for (Node node : pane.getChildren()) {
            node.setEffect(null);
            if (node instanceof Pane childPane) {
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

    private void updateCanvasSize() {
        double width = currentTemplate.getDimension().getWidthPx();
        double height = currentTemplate.getDimension().getHeightPx();
        cardCanvas.setMinWidth(width);
        cardCanvas.setMaxWidth(width);
        cardCanvas.setMinHeight(height);
        cardCanvas.setMaxHeight(height);
        
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

        renderElements(currentTemplate.getElements(), cardCanvas, currentRecord, null, ContainerElement.LayoutType.POSITIONAL);
        highlightOnCanvas(getSelectedElement());
    }

    private void renderElements(ObservableList<CardElement> elements, Pane targetPane, Map<String, String> currentRecord, FontElement inheritedFont, ContainerElement.LayoutType containerLayout) {
        FontElement currentFont = inheritedFont;
        for (CardElement el : elements) {
            if (el instanceof FontElement fe) {
                currentFont = fe;
                continue;
            }
            Node node = createNodeForElement(el, currentRecord, currentFont, containerLayout);
            if (node != null) {
                targetPane.getChildren().add(node);
                if (el instanceof ContainerElement ce && node instanceof Pane childPane) {
                    renderElements(ce.getChildren(), childPane, currentRecord, currentFont, ce.getLayoutType());
                }
            }
        }
    }

    private Node createNodeForElement(CardElement el, Map<String, String> currentRecord, FontElement fontConfig, ContainerElement.LayoutType parentLayout) {
        Node node;
        boolean isPositional = parentLayout == null || parentLayout == ContainerElement.LayoutType.POSITIONAL;

        if (el instanceof TextElement te) {
            String displayText = (currentRecord != null) ? dataMerger.merge(te.getText(), currentRecord) : te.getText();
            Text text = new Text(displayText);
            text.getStyleClass().add("text-element");
            
            if (fontConfig != null) {
                text.setFont(Font.font(fontConfig.getFontFamily(), fontConfig.getFontWeight(), fontConfig.getFontPosture(), fontConfig.getFontSize()));
                try {
                    text.setFill(Color.web(fontConfig.getColor()));
                } catch (Exception e) {
                    text.setFill(Color.BLACK);
                }
            } else {
                text.setFont(Font.font(te.getFontSize()));
                try {
                    text.setFill(Color.web(te.getColor()));
                } catch (Exception e) {
                    text.setFill(Color.BLACK);
                }
            }
            
            node = text;
            if (isPositional) {
                text.layoutXProperty().bind(te.xProperty());
                text.layoutYProperty().bind(te.yProperty().add(te.fontSizeProperty())); // Text layout Y is baseline
            }
        } else if (el instanceof ImageElement ie) {
            String path = (currentRecord != null) ? dataMerger.merge(ie.getImagePath(), currentRecord) : ie.getImagePath();
            ImageView imageView = new ImageView();
            imageView.getStyleClass().add("image-element");
            if (path != null && !path.isEmpty()) {
                try {
                    File file = new File(path);
                    if (file.exists()) {
                        imageView.setImage(new Image(file.toURI().toString()));
                    }
                } catch (Exception e) {
                    // Ignore image load error
                }
            }
            imageView.fitWidthProperty().bind(ie.widthProperty());
            imageView.fitHeightProperty().bind(ie.heightProperty());
            imageView.preserveRatioProperty().bind(ie.lockAspectRatioProperty());
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
                    pane = vbox;
                    break;
                case HORIZONTAL:
                    HBox hbox = new HBox();
                    hbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), false));
                    ce.alignmentProperty().addListener((obs, old, newVal) -> hbox.setAlignment(mapAlignmentToPos(newVal, false)));
                    pane = hbox;
                    break;
                case FLOW:
                    FlowPane flowPane = new FlowPane();
                    flowPane.setAlignment(mapAlignmentToPos(ce.getAlignment(), false));
                    ce.alignmentProperty().addListener((obs, old, newVal) -> flowPane.setAlignment(mapAlignmentToPos(newVal, false)));
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
            
            updatePaneStyle(pane, ce.getBackgroundColor(), ce.getAlpha());
            ce.backgroundColorProperty().addListener((obs, old, newVal) -> updatePaneStyle(pane, newVal, ce.getAlpha()));
            ce.alphaProperty().addListener((obs, old, newVal) -> updatePaneStyle(pane, ce.getBackgroundColor(), newVal.doubleValue()));

            node = pane;
            if (isPositional) {
                pane.layoutXProperty().bind(ce.xProperty());
                pane.layoutYProperty().bind(ce.yProperty());
            }
        } else {
            return null;
        }

        makeDraggable(node, el);
        node.getProperties().put("cardElement", el);
        return node;
    }

    private javafx.geometry.Pos mapAlignmentToPos(ContainerElement.Alignment alignment, boolean vertical) {
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

    private void updatePaneStyle(Pane pane, String color, double alpha) {
        try {
            Color c = Color.web(color);
            String alphaColor = String.format("rgba(%d, %d, %d, %.2f)", 
                (int)(c.getRed() * 255), 
                (int)(c.getGreen() * 255), 
                (int)(c.getBlue() * 255), 
                alpha);
            
            // Ensure container is visible with a subtle dashed border even if background is transparent
            StringBuilder style = new StringBuilder("-fx-background-color: " + alphaColor + "; ");
            if (!previewMode) {
                style.append("-fx-border-color: #888888; "); // Stronger border color
                style.append("-fx-border-style: dashed; ");
                style.append("-fx-border-width: 1; ");
            }
            pane.setStyle(style.toString());
        } catch (Exception e) {
            // Ignore styling errors
        }
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
            el.setX(mouseEvent.getSceneX() + dragDelta.x);
            el.setY(mouseEvent.getSceneY() + dragDelta.y);
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

        if (el instanceof TextElement te) {
            TextArea textArea = new TextArea(te.getText());
            textArea.setPrefRowCount(3);
            textArea.textProperty().bindBidirectional(te.textProperty());
            addManagedListener(te.textProperty(), (obs, old, newVal) -> renderTemplate());
            
            HBox sizeBox = createSliderWithNumericField(te.fontSizeProperty(), 8, 72);
            addManagedListener(te.fontSizeProperty(), (obs, old, newVal) -> renderTemplate());

            ColorPicker colorPicker = new ColorPicker(Color.web(te.getColor()));
            colorPicker.setOnAction(e -> {
                te.setColor(toHexString(colorPicker.getValue()));
                renderTemplate();
            });

            propertiesPane.getChildren().addAll(new Label("Text content (use {{header}} for merge)"), textArea, 
                                            new Label("Font Size"), sizeBox,
                                            new Label("Color"), colorPicker);
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
                                ie.setHeight(ie.getWidth() * ratio);
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
                fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
                );
                File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
                if (file != null) {
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
                renderTemplate();
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
                renderTemplate();
            });

            CheckBox lockAspectBox = new CheckBox("Lock Aspect Ratio");
            lockAspectBox.selectedProperty().bindBidirectional(ie.lockAspectRatioProperty());

            propertiesPane.getChildren().addAll(new Label("Image Path"), new HBox(5, pathField, browseBtn),
                                            new Label("Width"), widthBox,
                                            new Label("Height"), heightBox,
                                            lockAspectBox);
        } else if (el instanceof ContainerElement ce) {
            HBox widthBox = createSliderWithNumericField(ce.widthProperty(), 10, 500);
            addManagedListener(ce.widthProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ce.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ce.getHeight() / old.doubleValue();
                    ce.setHeight(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
                renderTemplate();
            });

            HBox heightBox = createSliderWithNumericField(ce.heightProperty(), 10, 500);
            addManagedListener(ce.heightProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ce.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ce.getWidth() / old.doubleValue();
                    ce.setWidth(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
                renderTemplate();
            });

            CheckBox lockAspectBox = new CheckBox("Lock Aspect Ratio");
            lockAspectBox.selectedProperty().bindBidirectional(ce.lockAspectRatioProperty());

            HBox alphaBox = createSliderWithNumericField(ce.alphaProperty(), 0.0, 1.0);
            addManagedListener(ce.alphaProperty(), (obs, old, newVal) -> renderTemplate());

            ColorPicker colorPicker = new ColorPicker(Color.TRANSPARENT);
            try {
                colorPicker.setValue(Color.web(ce.getBackgroundColor()));
            } catch (Exception e) {
                // Ignore
            }
            colorPicker.setOnAction(e -> {
                ce.setBackgroundColor(toHexString(colorPicker.getValue()));
                renderTemplate();
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

            propertiesPane.getChildren().addAll(
                new Label("Width"), widthBox,
                new Label("Height"), heightBox,
                lockAspectBox,
                new Label("Background Alpha"), alphaBox,
                new Label("Background Color"), colorPicker,
                new Label("Layout Type"), layoutBox,
                new Label("Alignment"), alignBox
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
            colorPicker.setOnAction(e -> {
                fe.setColor(toHexString(colorPicker.getValue()));
            });

            propertiesPane.getChildren().addAll(
                new Label("Font Family"), familyBox,
                new Label("Font Size"), sizeBox,
                new Label("Font Weight"), weightBox,
                new Label("Font Posture"), postureBox,
                new Label("Color"), colorPicker
            );
        }
    }


    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255));
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
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Data Files", "*.csv", "*.ods"),
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("ODS Files", "*.ods")
        );
        File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
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
    void handleDeleteElement(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                parentList.remove(selected);
            }
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
            if (el instanceof ContainerElement ce) {
                if (ce.getChildren().contains(target)) {
                    return ce.getChildren();
                }
                ObservableList<CardElement> found = findParentListRecursive(ce.getChildren(), target);
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
    void handleExit(ActionEvent event) {
        saveTempDeck();
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
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CardMaker Files", "*.cm"));
        File file = fileChooser.showSaveDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            currentFile = file;
            saveToFile(file);
        }
    }

    @FXML
    void handleOpenDeck(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CardMaker Files", "*.cm"));
        File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            try {
                CardTemplate template = DeckStorage.load(file);
                currentFile = file;
                applyTemplate(template);
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error loading deck: " + e.getMessage());
                alert.show();
            }
        }
    }

    private void saveToFile(File file) {
        try {
            DeckStorage.save(currentTemplate, file);
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
