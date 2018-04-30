package com.tfkfan.app.ui.mainform;

import com.google.api.services.sheets.v4.model.*;
import com.tfkfan.app.helpers.DbHelper;
import com.tfkfan.app.services.DbService;
import com.tfkfan.app.services.SheetsService;
import com.tfkfan.app.services.impl.DbServiceImpl;
import com.tfkfan.app.services.impl.SheetsServiceImpl;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static com.tfkfan.app.helpers.AppHelper.*;

public class MainFormController implements Initializable {

    @FXML
    public Button stopBtn;

    @FXML
    public Button startBtn;

    @FXML
    public Slider timeSlider;

    @FXML
    public Label timeLabel;

    @FXML
    public TextField spreadsheetUrlField;

    @FXML
    public ProgressBar progressBar;

    @FXML
    public TextField db_user;

    @FXML
    public TextField db_password;

    @FXML
    public TextField db_host;

    @FXML
    public TextField db_port;

    @FXML
    public TextField db_name;

    @FXML
    public Button checkConnectionBtn;

    @FXML
    public CheckBox hostCheckbox;

    @FXML
    public CheckBox portCheckbox;

    @FXML
    public Label resultsLabel;

    @FXML
    public CheckComboBox<String> tablesList;

    private final SheetsService sheetsService = new SheetsServiceImpl();
    private final DbService dbService = new DbServiceImpl();

    private Stage mainStage;

    private Map<String, String> properties;
    private Connection connection;

    private static final List<String> tables = new ArrayList<>();
    private static final String[] defaultTables = {"invoice", "salesorder", "salesorderlinedetail"};
    private static final Integer page = 1000;
    private static final Integer maxRows = 100000;

    @FXML
    public void startBtnClick(ActionEvent actionEvent) {
        progressBar.setProgress(0);
        resultsLabel.setVisible(false);

        updateProperties();
        setConnection(DbHelper.getConnection(getProperties().get("host"), Integer.valueOf(getProperties().get("port")),
                getProperties().get("name"), getProperties().get("user"), getProperties().get("password")));
        if (getConnection() == null) {
            showAlert("info", "Database connection not established.");
            return;
        }

        if (spreadsheetUrlField.getText().isEmpty()) {
            showAlert("info", "You have not specified Spreadsheet Url to update.");
            return;
        }

        processApp(spreadsheetUrlField.getText());
    }

    @FXML
    public void stopBtnClick(ActionEvent actionEvent) {
        progressBar.setProgress(0.33d);
    }

    @FXML
    public void checkButtonClick(MouseEvent mouseEvent) {
        updateProperties();

        setConnection(DbHelper.getConnection(getProperties().get("host"), Integer.valueOf(getProperties().get("port")),
                getProperties().get("name"), getProperties().get("user"), getProperties().get("password")));
        if (getConnection() == null) {
            showAlert("info", "Database connection not established.");
        } else
            showAlert("info", "Database connection established.");
    }

    @FXML
    public void localCheckboxValueChanged(ActionEvent actionEvent) {
        Boolean isSelected = ((CheckBox) actionEvent.getSource()).isSelected();
        String val = isSelected ? "localhost" : "";
        db_host.setText(val);
        db_host.setDisable(isSelected);
    }

    @FXML
    public void defaultCheckboxValueChanged(ActionEvent actionEvent) {
        Boolean isSelected = ((CheckBox) actionEvent.getSource()).isSelected();
        String val = isSelected ? "1433" : "";
        db_port.setText(val);
        db_port.setDisable(isSelected);
    }

    private void processApp(String spreadsheetUrl) {
        try {
            if (tables == null)
                return;

            String spreadsheetId = sheetsService.getSpreadsheetIdFromUrl(spreadsheetUrl);
            int totalUpdated = 0;
            for (int i = 0; i < tables.size(); i++) {
                final String table = tables.get(i);
                int rows = 0;

                List<List<Object>> allValues = new ArrayList<>(new ArrayList<>());
                while (rows <= maxRows) {
                    List<List<Object>> values = dbService.getValues(connection, table, rows, page);
                    rows += values.size() + 1;
                    if (values.size() == 0)
                        break;

                    allValues.addAll(values);
                }
                sheetsService.createSheetIfNotExist(spreadsheetId, table);
                sheetsService.clearSheet(spreadsheetId, table);
                BatchUpdateValuesResponse response = sheetsService.executeBatchRequest(allValues, spreadsheetId, table + "!A1");

                totalUpdated += response.getTotalUpdatedRows();
                progressBar.setProgress((i + 1) / (double) tables.size());
            }

            resultsLabel.setVisible(true);
            resultsLabel.setText(String.format("%d rows updated", totalUpdated));
            progressBar.setProgress(1);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            showAlert("error", "SQL Error occured. Try again");
            e.printStackTrace();
        } finally {
            if (connection != null) try {
                connection.close();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        timeLabel.setText("Every " + (int) timeSlider.getValue() + " minutes...");
        timeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            Integer val = newValue.intValue();
            timeLabel.setText("Every " + val + " minutes...");
            timeSlider.setValue(val);

        });

        portCheckbox.setSelected(true);
        hostCheckbox.setSelected(true);
        db_host.setText("localhost");
        db_host.setDisable(true);
        db_port.setText("1433");
        db_port.setDisable(true);


        tablesList.getItems().addAll(FXCollections.observableArrayList(defaultTables));
        tablesList.getCheckModel().setSelectionMode(SelectionMode.MULTIPLE);
        tablesList.getCheckModel().getSelectedItems().addListener((ListChangeListener<? super String>) c -> {
            tables.clear();
            tables.addAll(c.getList());
        });

    }

    private void updateProperties() {
        properties = new HashMap<>();
        properties.put("user", db_user.getText());
        properties.put("password", db_password.getText());
        properties.put("host", hostCheckbox.isSelected() ? "localhost" : db_host.getText());
        properties.put("port", portCheckbox.isSelected() ? String.valueOf(1433) : db_port.getText());
        properties.put("name", db_name.getText());
    }

    public Stage getMainStage() {
        return mainStage;
    }

    public void setMainStage(Stage mainStage) {
        this.mainStage = mainStage;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }
}
