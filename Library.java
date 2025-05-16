package com.example.demo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Singleton class to manage the library's documents and users.
 * Ensures a single instance of Library to avoid data inconsistencies.
 */
public class Library {
    private static Library instance = null; // Singleton instance
    private ObservableList<Document> documents = FXCollections.observableArrayList();
    private Map<String, User> users = new HashMap<>();

    private static final String DOCUMENTS_FILE = "documents.csv";
    private static final String BORROWED_FILE = "borrowed.csv";
    private static final String RATINGS_FILE = "ratings.csv";
    private static final String ACCOUNT_FILE = "account.csv";
    private static final String USER_FILE = "user.csv";

    // Private constructor for Singleton pattern
    private Library() {
        loadDocumentsFromFile();
        loadUsersFromFile();
        loadBorrowedRecordsFromFile();
    }

    /**
     * Gets the singleton instance of Library.
     * @return The single instance of Library.
     */
    public static Library getInstance() {
        if (instance == null) {
            instance = new Library();
        }
        return instance;
    }

    /**
     * Gets the observable list of documents in the library.
     * @return ObservableList of Document objects.
     */
    public ObservableList<Document> getDocuments() {
        return documents;
    }

    /**
     * Gets a copy of all users in the library.
     * @return Map of user IDs to User objects.
     */
    public Map<String, User> getAllUsers() {
        return new HashMap<>(users);
    }

    /**
     * Adds a new document to the library and saves it to file.
     * @param doc The document to add.
     */
    public void addDocument(Document doc) {
        documents.add(doc);
        saveDocumentsToFile();
    }

    /**
     * Removes a document by its ID and updates the file.
     * @param id The ID of the document to remove.
     */
    public void removeDocumentById(String id) {
        Document doc = findById(id);
        if (doc == null) {
            throw new IllegalArgumentException("Document does not exist!");
        }

        // Kiểm tra xem tài liệu có đang được mượn không
        boolean isBorrowed = users.values().stream()
                .anyMatch(user -> user.getBorrowedDocuments().containsKey(id) && user.getBorrowedDocuments().get(id) > 0);
        if (isBorrowed) {
            throw new IllegalStateException("Cannot remove document because it is currently borrowed!");
        }

        // Nếu không bị mượn, tiến hành xóa
        documents.removeIf(document -> document.getId().equals(id));
        saveDocumentsToFile();
    }

    /**
     * Finds a document by its ID.
     * @param id The ID of the document to find.
     * @return The Document object if found, null otherwise.
     */
    public Document findById(String id) {
        return documents.stream()
                .filter(doc -> doc.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds a user by their ID.
     * @param userId The ID of the user to find.
     * @return The User object if found, null otherwise.
     */
    public User findUserById(String userId) {
        return users.get(userId);
    }

    /**
     * Adds a new user to the library and saves to file.
     * @param user The user to add.
     */
    public void addUser(User user) {
        users.put(user.getUsername(), user);
        saveUsersToFile();
    }

    /**
     * Clears all documents from the library and updates the file.
     */
    public void clearDocuments() {
        documents.clear();
        saveDocumentsToFile();
    }

    /**
     * Loads documents from the documents.csv file.
     * Ensures no duplicates by using a Map to aggregate quantities.
     */
    private void loadDocumentsFromFile() {
        documents.clear();
        Map<String, Document> uniqueDocuments = new HashMap<>();
        File file = new File(DOCUMENTS_FILE);
        if (file.exists()) {
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
                        if (uniqueDocuments.containsKey(id)) {
                            Document existingDoc = uniqueDocuments.get(id);
                            existingDoc.setQuantity(existingDoc.getQuantity() + quantity);
                        } else {
                            uniqueDocuments.put(id, new Book(id, title, author, quantity, subject));
                        }
                    }
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("Failed to load documents: " + e.getMessage());
            }
        }
        documents.addAll(uniqueDocuments.values());
    }

    /**
     * Saves the current documents to documents.csv.
     * Aggregates quantities for documents with the same ID.
     */
    public void saveDocumentsToFile() {
        Map<String, Document> uniqueDocuments = new HashMap<>();
        for (Document doc : documents) {
            if (uniqueDocuments.containsKey(doc.getId())) {
                Document existingDoc = uniqueDocuments.get(doc.getId());
                existingDoc.setQuantity(existingDoc.getQuantity() + doc.getQuantity());
            } else {
                uniqueDocuments.put(doc.getId(), new Book(doc.getId(), doc.getTitle(), doc.getAuthor(), doc.getQuantity(), doc.getSubject()));
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DOCUMENTS_FILE))) {
            for (Document doc : uniqueDocuments.values()) {
                writer.write(doc.toCSV());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to save documents: " + e.getMessage());
        }
    }

    /**
     * Loads users from the user.csv file.
     */
    private void loadUsersFromFile() {
        users.clear();
        File file = new File(USER_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(USER_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        User user = new User(parts[0], parts[1]);
                        user.setName(parts[2]);
                        users.put(parts[0], user);
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to load users: " + e.getMessage());
            }
        }
    }

    /**
     * Saves the current users to user.csv.
     */
    private void saveUsersToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE))) {
            for (User user : users.values()) {
                writer.write(user.getUsername() + "," + user.getPassword() + "," + user.getName());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to save users: " + e.getMessage());
        }
    }

    /**
     * Loads borrowed records from borrowed.csv and updates user borrowing data.
     */
    private void loadBorrowedRecordsFromFile() {
        for (User user : users.values()) {
            user.getBorrowedDocuments().clear();
        }
        File file = new File(BORROWED_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(BORROWED_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        String userId = parts[0];
                        String docId = parts[1];
                        int quantity = Integer.parseInt(parts[2]);
                        User user = users.get(userId);
                        Document doc = findById(docId);
                        if (user != null && doc != null) {
                            user.borrowDocument(doc, quantity);
                        }
                    }
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("Failed to load borrowed records: " + e.getMessage());
            }
        }
    }

    /**
     * Saves the current borrowed records to borrowed.csv.
     */
    public void saveBorrowedRecordsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(BORROWED_FILE))) {
            for (User user : users.values()) {
                for (Map.Entry<String, Integer> entry : user.getBorrowedDocuments().entrySet()) {
                    String docId = entry.getKey();
                    int quantity = entry.getValue();
                    String record = user.getUsername() + "," + docId + "," + quantity;
                    writer.write(record);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save borrowed records: " + e.getMessage());
        }
    }
}
