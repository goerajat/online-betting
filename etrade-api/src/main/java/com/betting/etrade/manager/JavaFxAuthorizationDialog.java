package com.betting.etrade.manager;

import com.betting.marketdata.api.AuthorizationHandler;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * JavaFX dialog-based implementation of AuthorizationCallback.
 *
 * <p>This class provides a GUI dialog for E*TRADE OAuth authorization,
 * suitable for use in JavaFX applications.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // In a JavaFX application
 * MarketDataManager manager = MarketDataManager.fromPropertiesFile("config.properties");
 *
 * // Use the JavaFX dialog for authentication
 * manager.authenticate(new JavaFxAuthorizationDialog(primaryStage));
 * }</pre>
 *
 * <p>The dialog will:</p>
 * <ul>
 *   <li>Display the authorization URL</li>
 *   <li>Provide a button to open the URL in the default browser</li>
 *   <li>Provide a button to copy the URL to clipboard</li>
 *   <li>Show a text field for entering the verification code</li>
 * </ul>
 */
public class JavaFxAuthorizationDialog implements AuthorizationCallback, AuthorizationHandler {

    private final Stage ownerStage;
    private final String dialogTitle;
    private final boolean autoOpenBrowser;

    /**
     * Create a new JavaFX authorization dialog.
     *
     * @param ownerStage the owner stage for the dialog (can be null)
     */
    public JavaFxAuthorizationDialog(Stage ownerStage) {
        this(ownerStage, "E*TRADE Authorization", true);
    }

    /**
     * Create a new JavaFX authorization dialog with custom settings.
     *
     * @param ownerStage the owner stage for the dialog (can be null)
     * @param dialogTitle the title for the dialog
     * @param autoOpenBrowser whether to automatically open the browser
     */
    public JavaFxAuthorizationDialog(Stage ownerStage, String dialogTitle, boolean autoOpenBrowser) {
        this.ownerStage = ownerStage;
        this.dialogTitle = dialogTitle;
        this.autoOpenBrowser = autoOpenBrowser;
    }

    @Override
    public String onAuthorizationRequired(String authorizationUrl) {
        // If we're on the JavaFX Application Thread, show dialog directly
        if (Platform.isFxApplicationThread()) {
            return showDialog(authorizationUrl);
        }

        // Otherwise, run on the JavaFX thread and wait for result
        CompletableFuture<String> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                String result = showDialog(authorizationUrl);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to show authorization dialog", e.getCause());
        }
    }

    @Override
    public String handleAuthorization(String authorizationUrl) {
        // Delegate to the same implementation
        return onAuthorizationRequired(authorizationUrl);
    }

    private String showDialog(String authorizationUrl) {
        // Auto-open browser if enabled
        if (autoOpenBrowser) {
            openBrowser(authorizationUrl);
        }

        // Create the custom dialog
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(dialogTitle);
        dialog.initModality(Modality.APPLICATION_MODAL);

        if (ownerStage != null) {
            dialog.initOwner(ownerStage);
        }

        // Dark theme colors (matching betting app)
        String bgDark = "#1a1a2e";
        String bgMedium = "#16213e";
        String bgLight = "#0f3460";
        String textWhite = "#ffffff";
        String textGray = "#b0b0b0";
        String accentBlue = "#4fc3f7";
        String accentGreen = "#4CAF50";
        String accentOrange = "#FF9800";

        // Set the button types
        ButtonType authorizeButtonType = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(authorizeButtonType, ButtonType.CANCEL);

        // Apply dark theme to dialog pane
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setStyle(
            "-fx-background-color: " + bgDark + ";" +
            "-fx-border-color: " + bgLight + ";" +
            "-fx-border-width: 2px;"
        );

        // Create the content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(550);
        content.setStyle("-fx-background-color: " + bgDark + ";");

        // Header
        Label headerLabel = new Label("E*TRADE Authorization Required");
        headerLabel.setStyle(
            "-fx-font-size: 18px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + textWhite + ";"
        );

        // Instructions box
        VBox instructionBox = new VBox(8);
        instructionBox.setPadding(new Insets(12));
        instructionBox.setStyle(
            "-fx-background-color: " + bgMedium + ";" +
            "-fx-background-radius: 8px;"
        );

        Label instructionLabel = new Label(
                "Please authorize this application to access your E*TRADE account:"
        );
        instructionLabel.setWrapText(true);
        instructionLabel.setStyle("-fx-text-fill: " + textWhite + "; -fx-font-size: 13px;");

        Label step1 = new Label("1. Click 'Open Browser' or copy the URL below");
        Label step2 = new Label("2. Log in to E*TRADE and authorize the application");
        Label step3 = new Label("3. Enter the verification code you receive");
        for (Label step : new Label[]{step1, step2, step3}) {
            step.setStyle("-fx-text-fill: " + textGray + "; -fx-font-size: 12px;");
        }

        instructionBox.getChildren().addAll(instructionLabel, step1, step2, step3);

        // URL section
        VBox urlBox = new VBox(8);
        urlBox.setPadding(new Insets(12));
        urlBox.setStyle(
            "-fx-background-color: " + bgMedium + ";" +
            "-fx-background-radius: 8px;"
        );

        Label urlLabel = new Label("AUTHORIZATION URL");
        urlLabel.setStyle(
            "-fx-text-fill: " + accentBlue + ";" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 11px;"
        );

        TextField urlField = new TextField(authorizationUrl);
        urlField.setEditable(false);
        urlField.setStyle(
            "-fx-font-family: monospace;" +
            "-fx-font-size: 11px;" +
            "-fx-background-color: " + bgLight + ";" +
            "-fx-text-fill: " + textWhite + ";" +
            "-fx-border-color: transparent;"
        );

        // URL buttons
        Button openBrowserBtn = new Button("Open Browser");
        openBrowserBtn.setStyle(
            "-fx-background-color: " + accentGreen + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;"
        );
        openBrowserBtn.setOnAction(e -> openBrowser(authorizationUrl));

        Button copyUrlBtn = new Button("Copy URL");
        copyUrlBtn.setStyle(
            "-fx-background-color: " + accentOrange + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;"
        );
        copyUrlBtn.setOnAction(e -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(authorizationUrl);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
            copyUrlBtn.setText("Copied!");
            copyUrlBtn.setStyle(
                "-fx-background-color: #66BB6A;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;"
            );
            // Reset button text after 2 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> {
                        copyUrlBtn.setText("Copy URL");
                        copyUrlBtn.setStyle(
                            "-fx-background-color: " + accentOrange + ";" +
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: bold;" +
                            "-fx-cursor: hand;"
                        );
                    });
                } catch (InterruptedException ignored) {}
            }).start();
        });

        HBox urlButtons = new HBox(10, openBrowserBtn, copyUrlBtn);
        urlButtons.setAlignment(Pos.CENTER_LEFT);

        urlBox.getChildren().addAll(urlLabel, urlField, urlButtons);

        // Verification code section
        VBox codeBox = new VBox(8);
        codeBox.setPadding(new Insets(12));
        codeBox.setStyle(
            "-fx-background-color: " + bgMedium + ";" +
            "-fx-background-radius: 8px;"
        );

        Label codeLabel = new Label("VERIFICATION CODE");
        codeLabel.setStyle(
            "-fx-text-fill: " + accentBlue + ";" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 11px;"
        );

        TextField codeField = new TextField();
        codeField.setPromptText("Enter the code from E*TRADE");
        codeField.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-color: " + bgLight + ";" +
            "-fx-text-fill: " + textWhite + ";" +
            "-fx-prompt-text-fill: #808080;" +
            "-fx-border-color: " + accentBlue + ";" +
            "-fx-border-width: 2px;" +
            "-fx-border-radius: 4px;" +
            "-fx-background-radius: 4px;"
        );
        GridPane.setHgrow(codeField, Priority.ALWAYS);

        codeBox.getChildren().addAll(codeLabel, codeField);

        // Add all components
        content.getChildren().addAll(
                headerLabel,
                instructionBox,
                urlBox,
                codeBox
        );

        dialogPane.setContent(content);

        // Style the buttons
        dialogPane.lookupButton(authorizeButtonType).setStyle(
            "-fx-background-color: " + accentGreen + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;"
        );
        dialogPane.lookupButton(ButtonType.CANCEL).setStyle(
            "-fx-background-color: #666666;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;"
        );

        // Enable/disable submit button based on code field
        Button submitButton = (Button) dialogPane.lookupButton(authorizeButtonType);
        submitButton.setDisable(true);
        codeField.textProperty().addListener((obs, oldVal, newVal) -> {
            submitButton.setDisable(newVal == null || newVal.trim().isEmpty());
        });

        // Focus on code field
        Platform.runLater(codeField::requestFocus);

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == authorizeButtonType) {
                return codeField.getText().trim();
            }
            return null;
        });

        // Show and wait for result
        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            // Silently fail - user can still copy the URL manually
        }
    }

    /**
     * Create a simple authorization callback for JavaFX applications.
     * This is a convenience method that creates a dialog without an owner stage.
     *
     * @return a new JavaFxAuthorizationDialog instance
     */
    public static JavaFxAuthorizationDialog create() {
        return new JavaFxAuthorizationDialog(null);
    }

    /**
     * Create a simple authorization callback for JavaFX applications.
     *
     * @param ownerStage the owner stage for the dialog
     * @return a new JavaFxAuthorizationDialog instance
     */
    public static JavaFxAuthorizationDialog create(Stage ownerStage) {
        return new JavaFxAuthorizationDialog(ownerStage);
    }
}
