/*
 * This file is part of JModel.
 *
 * JModel is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JModel is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JModel.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.battledash.jmodel.Methods.Controllers;

import com.battledash.jmodel.JModel;
import com.battledash.jmodel.Methods.Utilities.Logger;
import com.battledash.jmodel.Methods.Utilities.PAKsUtility;
import com.battledash.jmodel.Methods.Utilities.TreeUtility;
import com.battledash.jmodel.PakReader.PakFileContainer;
import com.battledash.jmodel.PakReader.PakIndex;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import me.fungames.jfortniteparse.converters.fort.ItemDefinitionContainer;
import me.fungames.jfortniteparse.converters.fort.ItemDefinitionsKt;
import me.fungames.jfortniteparse.ue4.assets.Package;
import me.fungames.jfortniteparse.ue4.assets.exports.athena.AthenaItemDefinition;
import me.fungames.jfortniteparse.ue4.pak.GameFile;
import me.fungames.jfortniteparse.ue4.pak.PakFileReader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class JModelMain {

    @FXML
    public TextArea LogText;
    @FXML
    public TextArea JsonAssetData;
    @FXML
    public TreeView PakDirectoryTree;
    @FXML
    public ListView PakDirectoryFiles;
    @FXML
    public ImageView AssetImage;
    @FXML
    public Button ExtractButton;

    public static Logger logger;

    public PakIndex index;
    public PakFileContainer container;
    public GameFile loadedGameFile;
    public Package loadedPackage;

    @FXML
    public void initialize() {
        LogText.setText("Initialized JModel, Version 0.0.1 by BattleDash");
        logger = new Logger(LogText);

        PakDirectoryTree.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            Node node = event.getPickResult().getIntersectedNode();
            if (node instanceof Text || (node instanceof TreeCell && ((TreeCell) node).getText() != null)) {
                TreeItem<String> item = (TreeItem) PakDirectoryTree.getSelectionModel().getSelectedItem();
                PakDirectoryFiles.getItems().clear();
                List<GameFile> files = index.getDirectories().get(TreeUtility.getPathFromItem(item));
                if (files == null) return;
                for (GameFile gameFile : files) {
                    if (PakDirectoryFiles.getItems().contains(gameFile.getNameWithoutExtension())) continue;
                    PakDirectoryFiles.getItems().add(gameFile.getNameWithoutExtension());
                    Collections.sort(PakDirectoryFiles.getItems(), (Comparator<String>) (s1, s2) -> s1.compareToIgnoreCase(s2));
                    PakDirectoryFiles.setVisible(true);
                }
            }
        });

        PakDirectoryFiles.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            Node node = event.getPickResult().getIntersectedNode();
            if (node instanceof Text || (node instanceof ListCell && ((ListCell) node).getText() != null)) {
                JsonAssetData.setText("");
                AssetImage.setImage(null);
                String folder = TreeUtility.getPathFromItem((TreeItem) PakDirectoryTree.getSelectionModel().getSelectedItem());
                String assetName = PakDirectoryFiles.getSelectionModel().getSelectedItem().toString();
                String path = folder + assetName;
                logger.info("Loading game asset " + path);
                // Gets GameFile Object for pak name from asset path
                loadedGameFile = index.getGameFileAtPath(folder, assetName);
                // Loads Package from path
                loadedPackage = container.provider.loadGameFile(path);
                logger.info("Loaded game file from " + loadedGameFile.getPakFileName());
                logger.debug("Attempting JSON conversion");
                // Attempts to grab JSON data from the package
                try {
                    String jsonUnparsed = loadedPackage.toJson();
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    JsonElement je = JsonParser.parseString(jsonUnparsed);
                    String prettyJsonString = gson.toJson(je.getAsJsonObject().get("export_properties"));
                    JsonAssetData.setText(prettyJsonString);
                } catch (Exception e) {
                    logger.warn("Failed, resetting JsonFields");
                    JsonAssetData.setText("");
                }
                logger.debug("Attempting Image generation");
                // Attempts to generate an item image from the asset. Experimental
                try {
                    // TODO Add support for non AthenaItemDefinition objects
                    AthenaItemDefinition item = (AthenaItemDefinition) loadedPackage.getExports().get(0);
                    ItemDefinitionContainer container1 = ItemDefinitionsKt.createContainer(item, container.provider, true, false, null);
                    logger.debug("Loading image " + item.getDisplayName().getText());
                    BufferedImage image = container1.getImage();
                    AssetImage.setImage(SwingFXUtils.toFXImage(image, null));
                } catch (Exception e) {
                    logger.warn("Failed, resetting BufferedImage");
                    AssetImage.setImage(null);
                }
            }
        });
    }

    public void onExtractButton(MouseEvent event) throws IOException {
        Stage window = new Stage();
        window.setTitle("JModel Extract");

        // Load the root layout from the fxml file
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/FXML/JModel_Extract.fxml"));
        //FXMLLoader loader = new FXMLLoader(new File("src/main/java/com/battledash/jmodel/Graphical/FXML/JModel_Extract.fxml").toURI().toURL());

        Parent root = loader.load();
        JModelExport controller = loader.getController();
        controller.handleAsset(TreeUtility.getPathFromItem((TreeItem) JModel.mainSceneController.PakDirectoryTree.getSelectionModel().getSelectedItem()), loadedGameFile, loadedPackage, JsonAssetData.getText(), AssetImage.getImage());
        window.setScene(new Scene(root));
        window.show();
    }

    public void loadAllPaks(Event e) {
        Platform.runLater(() -> {
            index = new PakIndex();

            File directory = PAKsUtility.getGameFilesLocation();
            String aesKey = "0xb5dbd6c9db714cc3e2c9c7422eb0a7e667168d92c59770214ec6abc68d8c2d3e";

            container = new PakFileContainer(directory, aesKey);

            for (String pakName : directory.list()) {
                if (!pakName.endsWith(".pak")) continue;
                PakFileReader reader = new PakFileReader(directory + "\\" + pakName);
                if (!reader.testAesKey(aesKey)) continue;
                reader.setAesKey(aesKey);
                logger.debug("Loading " + pakName);
                index.addPak(reader);
            }

            logger.info("Requesting pak index tree generation");
            setPakDirectoryTree();

        });
    }

    private void setPakDirectoryTree() {
        PakDirectoryTree.setRoot(TreeUtility.generateTree(index.getIndex()));
        logger.info("Done generating");
        PakDirectoryTree.setShowRoot(false);
    }

}