package com.example.demo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main GUI class for the Library Management System using JavaFX.
 * Provides a user interface to manage documents, users, and borrowing records.
 */
public class LibraryAppGUI extends Application {
    private Library library = Library.getInstance(); // Singleton instance
    private UserManager userManager = UserManager.getInstance(); // Singleton instance
    private User loggedInUser;
    private TableView<Document> table = new TableView<>();
    private List<BookRating> bookRatings = new ArrayList<>();
    private VBox buttonBox;
    private boolean isButtonBoxVisible = true;

    private static final String DOCUMENTS_FILE = "documents.csv";
    private static final String BORROWED_FILE = "borrowed.csv";
    private static final String RATINGS_FILE = "ratings.csv";
    private static final String BORROW_LOG_FILE = "borrow_log.csv"; // File to track borrow dates

    /**
     * Represents a record of a borrowed document with quantity and borrow date.
     */
    private static class BorrowedRecord {
        String userId;
        String docId;
        int quantity;
        LocalDate borrowDate; // Added to track borrow date

        BorrowedRecord(String userId, String docId, int quantity, LocalDate borrowDate) {
            this.userId = userId;
            this.docId = docId;
            this.quantity = quantity;
            this.borrowDate = borrowDate;
        }

        String toCSV() {
            return userId + "," + docId + "," + quantity + "," + borrowDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    /**
     * Represents a book rating given by a user.
     */
    private static class BookRating {
        String bookTitle;
        String userId;
        int rating;
        String comment;

        BookRating(String bookTitle, String userId, int rating, String comment) {
            this.bookTitle = bookTitle;
            this.userId = userId;
            this.rating = rating;
            this.comment = comment;
        }

        @Override
        public String toString() {
            return "Book: " + bookTitle + "\nUser: " + userId + "\nRating: " + rating + " stars\nComment: " + comment + "\n";
        }

        String toCSV() {
            return bookTitle + "," + userId + "," + rating + "," + comment.replace(",", ";");
        }
    }

    /**
     * Initializes the GUI with the logged-in user.
     * @param loggedInUser The user who logged in.
     */
    public void init(User loggedInUser) {
        this.loggedInUser = loggedInUser;
        loadRatingsFromFile();
        checkOverdueBooks(); // Check for overdue books on startup
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("📚 Electronic Library System");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #e0f7fa, #ffffff);");

        // Logo and Title
        ImageView logo = new ImageView(new Image("file:logo.png", 80, 80, true, true));
        logo.setPreserveRatio(true);

        Label title = new Label("📖 LIBRARY MANAGEMENT SYSTEM");
        title.setFont(Font.font("Arial", 24));
        title.setTextFill(Color.web("#006064"));
        title.setStyle("-fx-font-weight: bold;");

        // Search Bar
        TextField searchField = new TextField();
        searchField.setPromptText("Search for books on Google Books...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-background-radius: 20; -fx-border-radius: 20; -fx-border-color: #d1d5db; -fx-border-width: 1; -fx-padding: 8 16 8 16;");
        Button searchButton = new Button("🔍");
        searchButton.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 16 8 16;");
        searchButton.setOnAction(e -> {
            String query = searchField.getText();
            if (query != null && !query.isEmpty()) {
                searchGoogleBooks(query);
            } else {
                showAlert("Error", "Please enter a book title to search.");
            }
        });

        HBox searchBox = new HBox(10, searchField, searchButton);
        searchBox.setAlignment(Pos.CENTER_RIGHT);

        VBox topBox = new VBox(10, logo, title, searchBox);
        topBox.setAlignment(Pos.CENTER);
        root.setTop(topBox);

        // Sidebar with Buttons
        Button toggleButton = new Button("☰");
        toggleButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #006064; -fx-font-size: 24px;");
        toggleButton.setOnAction(e -> {
            isButtonBoxVisible = !isButtonBoxVisible;
            buttonBox.setVisible(isButtonBoxVisible);
            buttonBox.setManaged(isButtonBoxVisible);
            toggleButton.setText(isButtonBoxVisible ? "☰" : "▶");
        });

        String[] labels = {
                "❌ Exit",
                "➕ Add Document",
                "🗑 Remove Document",
                "✏ Update Document",
                "🔍 Find Document",
                "📄 Display by Subject",
                "👤 Add User",
                "📥 Borrow Document",
                "📤 Return Document",
                "🧾 Display User Info",
                "📊 View Statistics", // Button for statistics
                "📊 View Book Ratings"
        };

        buttonBox = new VBox(10);
        buttonBox.setPadding(new Insets(10));
        buttonBox.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #d1d5db; -fx-border-width: 1;");

        for (int i = 0; i < labels.length; i++) {
            Button b = new Button(labels[i]);
            b.setPrefWidth(200);
            b.setPrefHeight(40);
            b.setStyle("-fx-background-color: #00acc1; -fx-text-fill: white; -fx-font-size: 14; -fx-background-radius: 10;");
            b.setEffect(new DropShadow(5, Color.GRAY));
            int index = i;
            b.setOnAction(e -> handleAction(index));
            buttonBox.getChildren().add(b);
        }

        ScrollPane buttonScrollPane = new ScrollPane(buttonBox);
        buttonScrollPane.setFitToWidth(true);
        buttonScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox leftBox = new VBox(10, toggleButton, buttonScrollPane);
        leftBox.setAlignment(Pos.TOP_LEFT);
        root.setLeft(leftBox);

        // Table for Documents
        TableColumn<Document, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Document, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));

        TableColumn<Document, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));

        TableColumn<Document, Integer> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<Document, String> subjectCol = new TableColumn<>("Subject");
        subjectCol.setCellValueFactory(new PropertyValueFactory<>("subject"));

        table.getColumns().addAll(idCol, titleCol, authorCol, qtyCol, subjectCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);
        refreshTable();

        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 10;");
        refreshButton.setOnAction(e -> refreshTable());

        VBox centerBox = new VBox(10, table, refreshButton);
        centerBox.setAlignment(Pos.CENTER);
        root.setCenter(centerBox);

        // Make the layout responsive
        root.prefWidthProperty().bind(primaryStage.widthProperty());
        root.prefHeightProperty().bind(primaryStage.heightProperty());
        centerBox.prefWidthProperty().bind(root.widthProperty().subtract(leftBox.widthProperty()).subtract(40));
        table.prefWidthProperty().bind(centerBox.widthProperty());
        searchField.prefWidthProperty().bind(topBox.widthProperty().multiply(0.5));

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add("style.css");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Handles button actions based on the index.
     * @param action The index of the action to perform.
     */
    private void handleAction(int action) {
        switch (action) {
            case 0 -> {
                saveBorrowedRecordsToFile();
                saveRatingsToFile();
                System.exit(0);
            }
            case 1 -> addDocument();
            case 2 -> removeDocument();
            case 3 -> updateDocument();
            case 4 -> findDocument();
            case 5 -> displayDocuments();
            case 6 -> addUser();
            case 7 -> borrowDocument();
            case 8 -> returnDocument();
            case 9 -> displayUserInfo();
            case 10 -> displayStatistics(); // Updated to show days remaining
            case 11 -> displayBookRatings();
        }
    }

    /**
     * Refreshes the table with the current documents, avoiding duplicates.
     */
    private void refreshTable() {
        Map<String, Document> uniqueDocuments = new HashMap<>();
        for (Document doc : library.getDocuments()) {
            uniqueDocuments.compute(doc.getId(), (id, existingDoc) -> {
                if (existingDoc == null) {
                    return new Book(doc.getId(), doc.getTitle(), doc.getAuthor(), doc.getQuantity(), doc.getSubject());
                } else {
                    existingDoc.setQuantity(existingDoc.getQuantity() + doc.getQuantity());
                    return existingDoc;
                }
            });
        }
        table.setItems(FXCollections.observableArrayList(uniqueDocuments.values()));
    }

    /**
     * Adds a new document to the library.
     */
    private void addDocument() {
        Dialog<Document> dialog = new Dialog<>();
        dialog.setTitle("Add Document");
        dialog.setHeaderText("Enter Document Information");

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField idField = new TextField();
        TextField titleField = new TextField();
        TextField authorField = new TextField();
        TextField quantityField = new TextField();
        TextField subjectField = new TextField();

        grid.add(new Label("ID:"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("Title:"), 0, 1);
        grid.add(titleField, 1, 1);
        grid.add(new Label("Author:"), 0, 2);
        grid.add(authorField, 1, 2);
        grid.add(new Label("Quantity:"), 0, 3);
        grid.add(quantityField, 1, 3);
        grid.add(new Label("Subject:"), 0, 4);
        grid.add(subjectField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                try {
                    String id = idField.getText();
                    String title = titleField.getText();
                    String author = authorField.getText();
                    int quantity = Integer.parseInt(quantityField.getText());
                    String subject = subjectField.getText();
                    return new Book(id, title, author, quantity, subject);
                } catch (NumberFormatException e) {
                    showAlert("Error", "Invalid input. Quantity must be a number.");
                    return null;
                }
            }
            return null;
        });

        Optional<Document> result = dialog.showAndWait();
        result.ifPresent(doc -> {
            library.addDocument(doc);
            showAlert("Success", "Document added successfully.");
            refreshTable();
        });
    }

    /**
     * Removes a specified quantity of a document from the library.
     */
    private void removeDocument() {
        if (library.getDocuments().isEmpty()) {
            showAlert("Error", "Document list is empty, cannot remove.");
            return;
        }
        String idOrTitle = prompt("Enter Document ID or Title to remove:");
        String qtyStr = prompt("Enter quantity to remove:");
        if (idOrTitle != null && qtyStr != null) {
            try {
                int quantityToRemove = Integer.parseInt(qtyStr);
                Document doc = library.findById(idOrTitle);
                if (doc == null) {
                    doc = library.getDocuments().stream()
                            .filter(d -> d.getTitle().equalsIgnoreCase(idOrTitle))
                            .findFirst()
                            .orElse(null);
                }
                if (doc != null) {
                    // Kiểm tra xem tài liệu có đang được mượn không
                    Document finalDoc = doc;
                    boolean isBorrowed = library.getAllUsers().values().stream()
                            .anyMatch(user -> user.getBorrowedDocuments().containsKey(finalDoc.getId()) && user.getBorrowedDocuments().get(finalDoc.getId()) > 0);
                    if (isBorrowed) {
                        showAlert("Error", "Cannot remove document because it is currently borrowed!");
                        return;
                    }
                    // Nếu không bị mượn, kiểm tra số lượng và tiến hành xóa
                    if (quantityToRemove > doc.getQuantity()) {
                        showAlert("Error", "Quantity to remove exceeds available copies (" + doc.getQuantity() + ").");
                    } else {
                        doc.setQuantity(doc.getQuantity() - quantityToRemove);
                        if (doc.getQuantity() == 0) {
                            library.removeDocumentById(doc.getId());
                        }
                        saveDocumentsToFile();
                        showAlert("Success", "Removed " + quantityToRemove + " copies of " + doc.getTitle());
                        refreshTable();
                    }
                } else {
                    showAlert("Error", "Document does not exist!");
                }
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid quantity. Please enter a number.");
            }
        }
    }

    /**
     * Updates the quantity of a document in the library.
     */
    private void updateDocument() {
        String id = prompt("Enter Document ID:");
        Document doc = library.findById(id);
        if (doc == null) {
            showAlert("Error", "Document not found in library.");
            return;
        }
        String qtyStr = prompt("Enter new quantity:");
        if (qtyStr != null) {
            try {
                int quantity = Integer.parseInt(qtyStr);
                doc.setQuantity(quantity);
                showAlert("Success", "Quantity updated successfully for " + doc.getTitle());
                refreshTable();
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid quantity. Please enter a number.");
            }
        }
    }

    /**
     * Finds a document by its ID or title.
     */
    private void findDocument() {
        String searchKey = prompt("Enter Document ID or Title to find:");
        if (searchKey != null) {
            Document doc = library.findById(searchKey);
            if (doc == null) {
                doc = library.getDocuments().stream()
                        .filter(d -> d.getTitle().equalsIgnoreCase(searchKey))
                        .findFirst()
                        .orElse(null);
            }
            if (doc != null) {
                showAlert("Document Info", doc.getInfo());
            } else {
                showAlert("Error", "Document not found in library.");
            }
        }
    }

    /**
     * Displays documents filtered by subject.
     */
    private void displayDocuments() {
        Set<String> subjects = new TreeSet<>();
        for (Document doc : library.getDocuments()) {
            String subject = doc.getSubject();
            if (subject != null && !subject.isEmpty()) {
                subjects.add(subject);
            }
        }

        if (subjects.isEmpty()) {
            showAlert("Error", "No subjects available in library.");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Display by Subject");
        dialog.setHeaderText("Select a subject to filter documents");

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        ComboBox<String> subjectComboBox = new ComboBox<>();
        subjectComboBox.getItems().addAll(subjects);
        subjectComboBox.setPromptText("Select subject...");

        dialog.getDialogPane().setContent(subjectComboBox);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return subjectComboBox.getValue();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(selectedSubject -> {
            Map<String, Document> uniqueDocuments = new HashMap<>();
            for (Document doc : library.getDocuments().filtered(doc -> doc.getSubject().equals(selectedSubject))) {
                uniqueDocuments.compute(doc.getId(), (id, existingDoc) -> {
                    if (existingDoc == null) {
                        return new Book(doc.getId(), doc.getTitle(), doc.getAuthor(), doc.getQuantity(), doc.getSubject());
                    } else {
                        existingDoc.setQuantity(existingDoc.getQuantity() + doc.getQuantity());
                        return existingDoc;
                    }
                });
            }
            table.setItems(FXCollections.observableArrayList(uniqueDocuments.values()));
        });
    }

    /**
     * Adds a new user to the library.
     */
    private void addUser() {
        String username = prompt("Enter User ID:");
        String name = prompt("Enter User Name:");
        if (username != null && name != null) {
            User newUser = new User(username, "user123");
            newUser.setName(name);
            library.addUser(newUser);
            showAlert("Success", "User " + name + " added successfully with ID " + username);
        }
    }

    /**
     * Borrows a document for a user and logs the borrow date.
     */
    private void borrowDocument() {
        String userId = prompt("Enter User ID to borrow:");
        User user = library.findUserById(userId);
        if (user == null) {
            showAlert("Error", "User not found. Please create user first.");
            return;
        }
        String docId = prompt("Enter Document ID to borrow:");
        Document doc = library.findById(docId);
        if (doc == null) {
            showAlert("Error", "Document not found.");
            return;
        }
        String qtyStr = prompt("Enter quantity to borrow:");
        if (qtyStr != null) {
            try {
                int quantityToBorrow = Integer.parseInt(qtyStr);
                if (quantityToBorrow > doc.getQuantity()) {
                    showAlert("Error", "Not enough copies available. Only " + doc.getQuantity() + " left.");
                } else {
                    user.borrowDocument(doc, quantityToBorrow);
                    logBorrowRecord(user.getUsername(), doc.getId(), quantityToBorrow, LocalDate.now());
                    showAlert("Success", user.getUsername() + " borrowed " + quantityToBorrow + " copies of " + doc.getTitle());
                    refreshTable();
                }
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid quantity. Please enter a number.");
            }
        }
    }

    /**
     * Returns a document for a user and prompts for a rating.
     */
    private void returnDocument() {
        String userId = prompt("Enter User ID to return:");
        User user = library.findUserById(userId);
        if (user == null) {
            showAlert("Error", "User not found.");
            return;
        }
        String docId = prompt("Enter Document ID to return:");
        Document doc = library.findById(docId);
        if (doc == null) {
            showAlert("Error", "Document not found.");
            return;
        }
        String qtyStr = prompt("Enter quantity to return:");
        if (qtyStr != null) {
            try {
                int quantityToReturn = Integer.parseInt(qtyStr);
                int borrowedCount = user.getBorrowedQuantity(docId);
                if (quantityToReturn > borrowedCount) {
                    showAlert("Error", "Cannot return more than borrowed. Only " + borrowedCount + " copies borrowed.");
                    return;
                }
                user.returnDocument(doc, quantityToReturn);
                updateBorrowLog(user.getUsername(), doc.getId(), quantityToReturn);

                Dialog<BookRating> ratingDialog = new Dialog<>();
                ratingDialog.setTitle("Rate the Book");
                ratingDialog.setHeaderText("Please rate your experience with \"" + doc.getTitle() + "\"");

                ButtonType okButtonType = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
                ratingDialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

                GridPane grid = new GridPane();
                grid.setHgap(10);
                grid.setVgap(10);
                grid.setPadding(new Insets(20, 150, 10, 10));

                Spinner<Integer> ratingSpinner = new Spinner<>(1, 5, 1);
                ratingSpinner.setEditable(true);
                TextArea commentArea = new TextArea();
                commentArea.setPromptText("Enter your comments (optional)");
                commentArea.setPrefRowCount(3);

                grid.add(new Label("Rating (1-5):"), 0, 0);
                grid.add(ratingSpinner, 1, 0);
                grid.add(new Label("Comments:"), 0, 1);
                grid.add(commentArea, 1, 1);

                ratingDialog.getDialogPane().setContent(grid);

                ratingDialog.setResultConverter(dialogButton -> {
                    if (dialogButton == okButtonType) {
                        int rating = ratingSpinner.getValue();
                        String comment = commentArea.getText().isEmpty() ? "No comment" : commentArea.getText();
                        return new BookRating(doc.getTitle(), user.getUsername(), rating, comment);
                    }
                    return null;
                });

                Optional<BookRating> ratingResult = ratingDialog.showAndWait();
                ratingResult.ifPresent(rating -> {
                    bookRatings.add(rating);
                    saveRatingsToFile();
                    showAlert("Success", "Thank you for rating \"" + rating.bookTitle + "\" with " + rating.rating + " stars!");
                });

                showAlert("Success", user.getUsername() + " returned " + quantityToReturn + " copies of " + doc.getTitle());
                refreshTable();
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid quantity. Please enter a number.");
            }
        }
    }

    /**
     * Displays information about all users and their borrowed documents.
     */
    private void displayUserInfo() {
        Map<String, User> users = library.getAllUsers();
        if (users.isEmpty()) {
            showAlert("Error", "No users available.");
            return;
        }
        StringBuilder sb = new StringBuilder("All Users:\n");
        for (User user : users.values()) {
            sb.append("User ID: ").append(user.getUsername())
                    .append(", Name: ").append(user.getName())
                    .append(", Borrowed Books: ").append(user.getBorrowedDocuments().size())
                    .append("\nBorrowed Documents:\n");
            for (Map.Entry<String, Integer> entry : user.getBorrowedDocuments().entrySet()) {
                Document doc = library.findById(entry.getKey());
                if (doc != null) {
                    sb.append("- ").append(doc.getTitle()).append(" (").append(entry.getValue()).append(" copies)\n");
                }
            }
            sb.append("\n");
        }
        showAlert("User Info", sb.toString());
    }

    /**
     * Displays borrowing statistics for all users, including days remaining.
     * Calculates the number of days left until the 7-day borrowing limit is exceeded.
     */
    private void displayStatistics() {
        Map<String, User> users = library.getAllUsers();
        if (users.isEmpty()) {
            showAlert("Error", "No users available.");
            return;
        }
        List<BorrowedRecord> borrowRecords = new ArrayList<>();
        File file = new File(BORROW_LOG_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(BORROW_LOG_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String userId = parts[0];
                        String docId = parts[1];
                        int quantity = Integer.parseInt(parts[2]);
                        LocalDate borrowDate = LocalDate.parse(parts[3], DateTimeFormatter.ISO_LOCAL_DATE);
                        borrowRecords.add(new BorrowedRecord(userId, docId, quantity, borrowDate));
                    }
                }
            } catch (IOException | NumberFormatException e) {
                showAlert("Error", "Failed to load borrow logs: " + e.getMessage());
                return;
            }
        }

        StringBuilder sb = new StringBuilder("Borrowing Statistics:\n\n");
        LocalDate today = LocalDate.now();
        for (User user : users.values()) {
            int totalBorrowed = user.getBorrowedDocuments().values().stream().mapToInt(Integer::intValue).sum();
            sb.append("User: ").append(user.getUsername())
                    .append(" (").append(user.getName()).append(")\n")
                    .append("Total Books Borrowed: ").append(totalBorrowed).append("\n")
                    .append("Details:\n");

            for (Map.Entry<String, Integer> entry : user.getBorrowedDocuments().entrySet()) {
                String docId = entry.getKey();
                int quantity = entry.getValue();
                Document doc = library.findById(docId);
                if (doc != null) {
                    Optional<BorrowedRecord> record = borrowRecords.stream()
                            .filter(r -> r.userId.equals(user.getUsername()) && r.docId.equals(docId))
                            .findFirst();
                    record.ifPresent(r -> {
                        long daysBorrowed = java.time.temporal.ChronoUnit.DAYS.between(r.borrowDate, today);
                        long daysRemaining = Math.max(0, 7 - daysBorrowed); // Calculate remaining days
                        String status = daysRemaining > 0 ? daysRemaining + " days remaining" : "Overdue by " + Math.abs(daysRemaining - 7) + " days";
                        sb.append("- ").append(doc.getTitle()).append(" (").append(quantity).append(" copies) - ").append(status).append("\n");
                    });
                }
            }
            sb.append("\n");
        }
        showAlert("Statistics", sb.toString());
    }

    /**
     * Searches for books using the Google Books API.
     * @param query The search query.
     */
    private void searchGoogleBooks(String query) {
        Task<String> searchTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                String encoded = query.replace(" ", "+");
                URL url = new URL("https://www.googleapis.com/books/v1/volumes?q=" + encoded);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = reader.lines().collect(Collectors.joining());
                reader.close();

                JSONObject jsonResponse = new JSONObject(response);
                int totalItems = jsonResponse.getInt("totalItems");

                if (totalItems == 0) {
                    return "No books found for \"" + query + "\".";
                }

                JSONArray items = jsonResponse.getJSONArray("items");
                StringBuilder result = new StringBuilder("Search Results for \"" + query + "\":\n\n");

                for (int i = 0; i < Math.min(3, items.length()); i++) {
                    JSONObject item = items.getJSONObject(i);
                    JSONObject volumeInfo = item.getJSONObject("volumeInfo");

                    String title = volumeInfo.optString("title", "Unknown Title");
                    String authors = volumeInfo.has("authors") ? volumeInfo.getJSONArray("authors").join(", ") : "Unknown Author";
                    String publisher = volumeInfo.optString("publisher", "Unknown Publisher");
                    String publishedDate = volumeInfo.optString("publishedDate", "Unknown Date");
                    String description = volumeInfo.optString("description", "No description available.");
                    if (description.length() > 150) {
                        description = description.substring(0, 150) + "...";
                    }

                    result.append("Book ").append(i + 1).append(":\n")
                            .append("Title: ").append(title).append("\n")
                            .append("Authors: ").append(authors).append("\n")
                            .append("Publisher: ").append(publisher).append("\n")
                            .append("Published Date: ").append(publishedDate).append("\n")
                            .append("Description: ").append(description).append("\n\n");
                }

                return result.toString();
            }
        };

        searchTask.setOnSucceeded(event -> {
            String result = searchTask.getValue();
            if (result.startsWith("No books found")) {
                showAlert("Search Result", result);
            } else {
                showAlert("Google Books Result", result);
            }
        });

        searchTask.setOnFailed(event -> {
            showAlert("Error", "Failed to fetch data from Google Books: " + searchTask.getException().getMessage());
        });

        showAlert("Loading", "Searching for \"" + query + "\" on Google Books... Please wait.");
        new Thread(searchTask).start();
    }

    /**
     * Displays all book ratings.
     */
    private void displayBookRatings() {
        if (bookRatings.isEmpty()) {
            showAlert("No Ratings", "No book ratings available.");
            return;
        }

        StringBuilder sb = new StringBuilder("Book Ratings:\n\n");
        for (BookRating rating : bookRatings) {
            sb.append(rating.toString()).append("\n");
        }
        showAlert("Book Ratings", sb.toString());
    }

    /**
     * Logs a borrow record with the current date.
     * @param userId The ID of the user borrowing.
     * @param docId The ID of the document.
     * @param quantity The quantity borrowed.
     * @param borrowDate The date of borrowing.
     */
    private void logBorrowRecord(String userId, String docId, int quantity, LocalDate borrowDate) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(BORROW_LOG_FILE, true))) {
            BorrowedRecord record = new BorrowedRecord(userId, docId, quantity, borrowDate);
            writer.write(record.toCSV());
            writer.newLine();
        } catch (IOException e) {
            showAlert("Error", "Failed to log borrow record: " + e.getMessage());
        }
        library.saveBorrowedRecordsToFile();
    }

    /**
     * Updates the borrow log when a document is returned.
     * @param userId The ID of the user returning.
     * @param docId The ID of the document.
     * @param quantityReturned The quantity returned.
     */
    private void updateBorrowLog(String userId, String docId, int quantityReturned) {
        List<BorrowedRecord> records = new ArrayList<>();
        File file = new File(BORROW_LOG_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(BORROW_LOG_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String recordUserId = parts[0];
                        String recordDocId = parts[1];
                        int quantity = Integer.parseInt(parts[2]);
                        LocalDate borrowDate = LocalDate.parse(parts[3], DateTimeFormatter.ISO_LOCAL_DATE);
                        if (recordUserId.equals(userId) && recordDocId.equals(docId)) {
                            quantity -= quantityReturned;
                            if (quantity > 0) {
                                records.add(new BorrowedRecord(recordUserId, recordDocId, quantity, borrowDate));
                            }
                        } else {
                            records.add(new BorrowedRecord(recordUserId, recordDocId, quantity, borrowDate));
                        }
                    }
                }
            } catch (IOException e) {
                showAlert("Error", "Failed to update borrow log: " + e.getMessage());
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(BORROW_LOG_FILE))) {
            for (BorrowedRecord record : records) {
                writer.write(record.toCSV());
                writer.newLine();
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to save updated borrow log: " + e.getMessage());
        }
        library.saveBorrowedRecordsToFile();
    }

    /**
     * Checks for overdue books (borrowed more than 7 days ago) and alerts the user.
     */
    private void checkOverdueBooks() {
        List<BorrowedRecord> records = new ArrayList<>();
        File file = new File(BORROW_LOG_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(BORROW_LOG_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String userId = parts[0];
                        String docId = parts[1];
                        int quantity = Integer.parseInt(parts[2]);
                        LocalDate borrowDate = LocalDate.parse(parts[3], DateTimeFormatter.ISO_LOCAL_DATE);
                        records.add(new BorrowedRecord(userId, docId, quantity, borrowDate));
                    }
                }
            } catch (IOException e) {
                showAlert("Error", "Failed to check overdue books: " + e.getMessage());
            }
        }

        LocalDate today = LocalDate.now();
        StringBuilder overdueMessage = new StringBuilder("Overdue Books:\n\n");
        boolean hasOverdue = false;

        for (BorrowedRecord record : records) {
            long daysBorrowed = java.time.temporal.ChronoUnit.DAYS.between(record.borrowDate, today);
            if (daysBorrowed > 7) {
                User user = library.findUserById(record.userId);
                Document doc = library.findById(record.docId);
                if (user != null && doc != null) {
                    hasOverdue = true;
                    overdueMessage.append("User: ").append(user.getName())
                            .append(" (ID: ").append(user.getUsername()).append(")\n")
                            .append("Book: ").append(doc.getTitle())
                            .append(" (").append(record.quantity).append(" copies)\n")
                            .append("Borrowed on: ").append(record.borrowDate)
                            .append(" (").append(daysBorrowed).append(" days ago)\n\n");
                }
            }
        }

        if (hasOverdue) {
            showAlert("Overdue Alert", overdueMessage.toString());
        }
    }

    /**
     * Loads documents from documents.csv.
     */
    private void loadDocumentsFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(DOCUMENTS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    String id = parts[0];
                    String title = parts[1];
                    String author = parts[2];
                    int quantity = Integer.parseInt(parts[3]);
                    String subject = parts[4];
                    library.addDocument(new Book(id, title, author, quantity, subject));
                }
            }
        } catch (FileNotFoundException e) {
            // File not found, will be created on first save
        } catch (IOException | NumberFormatException e) {
            showAlert("Error", "Failed to load documents: " + e.getMessage());
        }
    }

    /**
     * Saves documents to documents.csv.
     */
    private void saveDocumentsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DOCUMENTS_FILE))) {
            for (Document doc : library.getDocuments()) {
                writer.write(doc.toCSV());
                writer.newLine();
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to save documents: " + e.getMessage());
        }
    }

    /**
     * Loads borrowed records from borrowed.csv.
     */
    private void loadBorrowedRecordsFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(BORROWED_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String userId = parts[0];
                    String docId = parts[1];
                    int quantity = Integer.parseInt(parts[2]);
                    User user = library.findUserById(userId);
                    Document doc = library.findById(docId);
                    if (user != null && doc != null) {
                        user.borrowDocument(doc, quantity);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // File not found, will be created on first save
        } catch (IOException | NumberFormatException e) {
            showAlert("Error", "Failed to load borrowed records: " + e.getMessage());
        }
    }

    /**
     * Saves borrowed records to borrowed.csv.
     */
    private void saveBorrowedRecordsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(BORROWED_FILE))) {
            for (User user : library.getAllUsers().values()) {
                for (Map.Entry<String, Integer> entry : user.getBorrowedDocuments().entrySet()) {
                    String docId = entry.getKey();
                    int quantity = entry.getValue();
                    String record = user.getUsername() + "," + docId + "," + quantity;
                    writer.write(record);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to save borrowed records: " + e.getMessage());
        }
    }

    /**
     * Loads ratings from ratings.csv.
     */
    private void loadRatingsFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(RATINGS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 4);
                if (parts.length >= 4) {
                    String bookTitle = parts[0];
                    String userId = parts[1];
                    int rating = Integer.parseInt(parts[2]);
                    String comment = parts[3].replace(";", ",");
                    bookRatings.add(new BookRating(bookTitle, userId, rating, comment));
                }
            }
        } catch (FileNotFoundException e) {
            // File not found, will be created on first save
        } catch (IOException | NumberFormatException e) {
            showAlert("Error", "Failed to load ratings: " + e.getMessage());
        }
    }

    /**
     * Saves ratings to ratings.csv.
     */
    private void saveRatingsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(RATINGS_FILE))) {
            for (BookRating rating : bookRatings) {
                writer.write(rating.toCSV());
                writer.newLine();
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to save ratings: " + e.getMessage());
        }
    }

    /**
     * Shows an alert with the specified title and message.
     * @param title The title of the alert.
     * @param msg The message to display.
     */
    private void showAlert(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    /**
     * Prompts the user for input with a dialog.
     * @param message The message to display in the dialog.
     * @return The user's input, or null if cancelled.
     */
    private String prompt(String message) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(message);
        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
