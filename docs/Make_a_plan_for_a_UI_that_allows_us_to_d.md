Based on the requirements and the existing project structure, here is a comprehensive plan for the Playing Card Design UI.

### UI Overview and Layout
The UI is designed using JavaFX and is organized into four main areas:
*   **Top (Toolbar/Menu):** Contains global actions like "New Deck", "Load CSV", and "Insert Element" (Text or Image).
*   **Left (Element Browser):** A list of all elements (images, text) currently on the card template, allowing for easy selection and layering management.
*   **Center (Design Canvas):** The primary workspace where the card is displayed. It uses a `Pane` with a fixed size based on the selected physical dimensions.
*   **Right (Properties & Data Merge):** A panel that updates contextually based on the selected element (e.g., font size for text, file path for images) and provides navigation controls for the merged CSV data.

### 1. Dimension Selection
When starting a new project, a dialog allows the user to select from standard physical dimensions:
*   **Standard Poker:** 2.5" x 3.5"
*   **Standard Tarot:** 2.75" x 4.75"
*   **Standard Bridge:** 2.25" x 3.5"
*   **Square:** 2.5" x 2.5"
*   **Mini:** 1.75" x 2.5"

The system translates these physical measurements into pixels (at 96 DPI) to size the canvas correctly.

### 2. Design and Drag-and-Drop Elements
The UI supports two primary element types that can be added via the "Insert" menu:
*   **Text Elements:** Users can enter text, adjust font size, and pick colors.
*   **Image Elements:** Users can browse for local image files to place on the card.

**Interactivity:**
*   **Dragging:** Every element on the canvas can be clicked and dragged to position it. The `makeDraggable` function handles `MouseEvent` to update the element's coordinates in real-time.
*   **Selection:** Clicking an element on the canvas or in the side list selects it and opens its specific settings in the Properties panel.

### 3. Data Merge Functionality
The application automates card generation through CSV integration:
*   **Loading CSV:** A "Load CSV" action opens a file picker. The `DataMerger` class reads the file and extracts records as key-value pairs where the headers are the keys.
*   **Template Placeholders:** Users can use the syntax `{{ColumnName}}` within text fields or image paths.
*   **Real-time Preview:** Once a CSV is loaded, the "Data Preview" controls (Next/Prev) allow the user to cycle through records. The canvas instantly re-renders, replacing the placeholders with actual data from the current record.
*   **Path Merging:** Data merge works for image paths as well, allowing different images to be loaded for each card based on the CSV data.

### Implementation Details
*   **`CardDimension` (Enum):** Stores the physical dimensions and handles pixel conversion.
*   **`CardMakerController`:** Orchestrates the UI logic, including drag events, property updates, and the rendering loop.
*   **`DataMerger`:** Handles CSV parsing (using OpenCSV) and the string replacement logic for placeholders.
*   **`card-maker-view.fxml`:** Defines the structural layout using `BorderPane`, `ScrollPane`, and `VBox`.