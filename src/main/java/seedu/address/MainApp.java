package seedu.address;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.logging.Logger;
import javax.crypto.NoSuchPaddingException;

import javafx.application.Application;
import javafx.stage.Stage;
import seedu.address.commons.core.Config;
import seedu.address.commons.core.LogsCenter;
import seedu.address.commons.core.Version;
import seedu.address.commons.exceptions.DataConversionException;
import seedu.address.commons.util.ConfigUtil;
import seedu.address.commons.util.FileUtil;
import seedu.address.commons.util.StringUtil;
import seedu.address.encryption.Encryption;
import seedu.address.encryption.EncryptionKeyGenerator;
import seedu.address.encryption.EncryptionManager;
import seedu.address.encryption.exceptions.UnsupportedPasswordException;
import seedu.address.logic.Logic;
import seedu.address.logic.LogicManager;
import seedu.address.model.AddressBook;
import seedu.address.model.Model;
import seedu.address.model.ModelManager;
import seedu.address.model.ReadOnlyAddressBook;
import seedu.address.model.ReadOnlyUserPrefs;
import seedu.address.model.UserPrefs;
import seedu.address.model.util.SampleDataUtil;
import seedu.address.storage.AddressBookStorage;
import seedu.address.storage.JsonAddressBookStorage;
import seedu.address.storage.JsonUserPrefsStorage;
import seedu.address.storage.Storage;
import seedu.address.storage.StorageManager;
import seedu.address.storage.UserPrefsStorage;
import seedu.address.ui.LoginScreen;
import seedu.address.ui.Ui;
import seedu.address.ui.UiManager;

/**
 * Runs the application.
 */
public class MainApp extends Application {
    public static final Version VERSION = new Version(0, 2, 1, true);
    private static final Logger logger = LogsCenter.getLogger(MainApp.class);
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";

    protected Ui ui;
    protected Logic logic;
    protected Storage storage;
    protected Model model;
    protected Config config;
    protected Encryption cryptor;

    private Stage stage;
    private UserPrefs userPrefs;

    @Override
    public void init() throws Exception {
        logger.info("=============================[ Initializing AddressBook ]===========================");
        super.init();

        initImportsDirectory();

        AppParameters appParameters = AppParameters.parse(getParameters());
        config = initConfig(appParameters.getConfigPath());

        UserPrefsStorage userPrefsStorage = new JsonUserPrefsStorage(config.getUserPrefsFilePath());
        userPrefs = initPrefs(userPrefsStorage);
        AddressBookStorage addressBookStorage = new JsonAddressBookStorage(userPrefs.getAddressBookFilePath());
        storage = new StorageManager(addressBookStorage, userPrefsStorage);

        initLogging(config);
        ui = new LoginScreen(this, !FileUtil.isFileExists(userPrefs.getEncryptedFilePath()));
    }

    /**
     * Returns a {@code ModelManager} with the data from {@code storage}'s address book and {@code userPrefs}. <br>
     * The data from the sample address book will be used instead if {@code storage}'s address book is not found,
     * or an empty address book will be used instead if errors occur when reading {@code storage}'s address book.
     */
    private Model initModelManager(Storage storage, ReadOnlyUserPrefs userPrefs) {
        Optional<ReadOnlyAddressBook> addressBookOptional;
        ReadOnlyAddressBook initialData;

        try {
            cryptor.decrypt(userPrefs.getEncryptedFilePath(), storage.getAddressBookFilePath());
            addressBookOptional = storage.readAddressBook();
            initialData = addressBookOptional.orElseGet(SampleDataUtil::getSampleAddressBook);
            FileUtil.deleteFile(storage.getAddressBookFilePath());
        } catch (DataConversionException e) {
            logger.warning("Data file not in the correct format. Will be starting with an empty AddressBook");
            initialData = new AddressBook();
        } catch (IOException e) {
            logger.warning("Problem while reading from the file. Will be starting with an empty AddressBook");
            initialData = new AddressBook();
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            // These exceptions will never be thrown unless an invalid cipher or bad key (does not comply to the cipher)
            // is supplied, which will not be the user's fault.
            logger.warning("Encryption error. Will be starting with an empty AddressBook");
            initialData = new AddressBook();
        }

        return new ModelManager(initialData, userPrefs);
    }

    private void initLogging(Config config) {
        LogsCenter.init(config);
    }

    /**
     * Returns a {@code Config} using the file at {@code configFilePath}. <br>
     * The default file path {@code Config#DEFAULT_CONFIG_FILE} will be used instead
     * if {@code configFilePath} is null.
     */
    protected Config initConfig(Path configFilePath) {
        Config initializedConfig;
        Path configFilePathUsed;

        configFilePathUsed = Config.DEFAULT_CONFIG_FILE;

        if (configFilePath != null) {
            logger.info("Custom Config file specified " + configFilePath);
            configFilePathUsed = configFilePath;
        }

        logger.info("Using config file : " + configFilePathUsed);

        try {
            Optional<Config> configOptional = ConfigUtil.readConfig(configFilePathUsed);
            initializedConfig = configOptional.orElse(new Config());
        } catch (DataConversionException e) {
            logger.warning("Config file at " + configFilePathUsed + " is not in the correct format. "
                    + "Using default config properties");
            initializedConfig = new Config();
        }

        // Update config file in case it was missing to begin with or there are new/unused fields
        try {
            ConfigUtil.saveConfig(initializedConfig, configFilePathUsed);
        } catch (IOException e) {
            logger.warning("Failed to save config file : " + StringUtil.getDetails(e));
        }

        return initializedConfig;
    }

    /**
     * Returns a {@code UserPrefs} using the file at {@code storage}'s user prefs file path,
     * or a new {@code UserPrefs} with default configuration if errors occur when
     * reading from the file.
     */
    protected UserPrefs initPrefs(UserPrefsStorage storage) {
        Path prefsFilePath = storage.getUserPrefsFilePath();
        logger.info("Using prefs file : " + prefsFilePath);
        UserPrefs initializedPrefs;

        try {
            Optional<UserPrefs> prefsOptional = storage.readUserPrefs();
            initializedPrefs = prefsOptional.orElse(new UserPrefs());
        } catch (DataConversionException e) {
            logger.warning("UserPrefs file at " + prefsFilePath + " is not in the correct format. "
                    + "Using default user prefs");
            initializedPrefs = new UserPrefs();
        } catch (IOException e) {
            logger.warning("Problem while reading from the file. Will be starting with an empty AddressBook");
            initializedPrefs = new UserPrefs();
        }

        // Update prefs file in case it was missing to begin with or there are new/unused fields
        try {
            storage.saveUserPrefs(initializedPrefs);
        } catch (IOException e) {
            logger.warning("Failed to save config file : " + StringUtil.getDetails(e));
        }

        return initializedPrefs;
    }

    /**
     * Creates a imports directory within the project's home directory
     * and populates it with and importTemplate.csv file.
     */
    protected void initImportsDirectory() {
        String home = System.getProperty("user.dir");
        Path path = Paths.get(home, "imports");
        try {
            FileUtil.createDirectoryIfEmpty(path);
        } catch (IOException e) {
            logger.warning("Failed to create imports directory : " + StringUtil.getDetails(e));
        }

        InputStream src = this.getClass().getResourceAsStream("/templates/importTemplate.csv");

        try {
            Path target = Paths.get(home, "imports", "importTemplate.csv");
            Files.copy(src, target);
        } catch (IOException e) {
            logger.warning("Failed to retrieve importTemplate.csv : " + StringUtil.getDetails(e));
        }
    }

    /**
     * Attempts to log in with the given password.
     *
     * @param input The input password from user.
     * @throws UnsupportedPasswordException If error occurs when generating the encryption key.
     * @throws NoSuchPaddingException If the padding does not exist.
     * @throws NoSuchAlgorithmException If the specified algorithm does not exist.
     */
    public void logIn(String input) throws UnsupportedPasswordException,
            NoSuchPaddingException, NoSuchAlgorithmException {
        cryptor = new EncryptionManager(EncryptionKeyGenerator.generateKey(input), CIPHER_TRANSFORMATION);
        if (!FileUtil.isFileExists(userPrefs.getEncryptedFilePath())) {
            logger.info("Data file not found. Will be starting with a sample AddressBook");
            try {
                storage.saveAddressBook(SampleDataUtil.getSampleAddressBook());
                FileUtil.createFile(userPrefs.getEncryptedFilePath());
                cryptor.encrypt(storage.getAddressBookFilePath(), userPrefs.getEncryptedFilePath());
            } catch (IOException | InvalidKeyException e) {
                e.printStackTrace();
            }
        }
        try {
            cryptor.decrypt(userPrefs.getEncryptedFilePath(), storage.getAddressBookFilePath());
            FileUtil.deleteFile(storage.getAddressBookFilePath());
            afterLogIn();
        } catch (InvalidAlgorithmParameterException | IOException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets up the cryptor, model, logic for the addressBook page.
     * Displays the new UI.
     *
     * @throws UnsupportedPasswordException If error occurs when generating the encryption key.
     * @throws NoSuchPaddingException If the padding does not exist.
     * @throws NoSuchAlgorithmException If the specified algorithm does not exist.
     */
    public void afterLogIn() throws UnsupportedPasswordException, NoSuchPaddingException, NoSuchAlgorithmException {
        model = initModelManager(storage, userPrefs);
        logic = new LogicManager(model, storage, cryptor, userPrefs.getEncryptedFilePath());
        ui = new UiManager(logic);
        ui.start(stage);
    }

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting AddressBook " + MainApp.VERSION);
        this.stage = primaryStage;
        ui.start(primaryStage);
    }

    @Override
    public void stop() {
        logger.info("============================ [ Stopping Address Book ] =============================");
        try {
            storage.saveUserPrefs(model.getUserPrefs());
        } catch (IOException e) {
            logger.severe("Failed to save preferences " + StringUtil.getDetails(e));
        }
    }
}
