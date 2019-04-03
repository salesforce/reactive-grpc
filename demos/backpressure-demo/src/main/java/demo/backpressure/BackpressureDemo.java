package demo.backpressure;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class BackpressureDemo extends Application {

    public static void doMain(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getClassLoader().getResource("Backpressure.fxml"));

        stage.setTitle("Reactive-gRPC Backpressure Demo");
        stage.setScene(new Scene(root, 800, 450));
        stage.show();
    }
}
