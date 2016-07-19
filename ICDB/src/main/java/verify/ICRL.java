package verify;

import cipher.RNG;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;

/**
 * <p>
 *     Maintains the Integrity Code Revocation List (ICRL), storing and loading it from a file.
 * </p>
 * Created 5/8/2016
 * @author Dan Kondratyuk
 */
public class ICRL implements Serializable {

    private static final String ICRL_LOCATION = "./src/main/resources/icrl.db";

    private final long start;
    private long current;

    private DB db;
    private NavigableSet<Long> serials;

    private static final Logger logger = LogManager.getLogger();

    public static synchronized ICRL init() {
        try {
            // Delete the old DB
            Files.deleteIfExists(Paths.get(ICRL_LOCATION));
        } catch (IOException e) {
            logger.error("Failed to initialize ICRL DB: {}", e.getMessage());
            System.exit(1);
        }

        return getInstance();
    }


    private static ICRL icrl;
    public static synchronized ICRL getInstance() {
        if (icrl == null) {
            icrl = new ICRL();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(icrl::save));

        return icrl;
    }

    private ICRL() {
        // Load the DB
        db = DBMaker.fileDB(ICRL_LOCATION)
                .fileMmapEnable()
                .fileMmapPreclearDisable()
//                .allocateStartSize(80L * 1024*1024) // 80 MB
                .make();
        db.getStore().fileLoad();

        // Generate the set
        serials = db
                .treeSet("serial", Serializer.LONG)
                .createOrOpen();

        // Get the first and last values, if they exist (otherwise generate them)
        if (serials.isEmpty()) {
            start = RNG.randomInt();
            current = start - 1;
        } else {
            start = serials.first();
            current = serials.last();
        }
    }

    /**
     * Increments the current serial counter by 1, and copies it to the ICRL storage as a valid serial
     * @return the new serial number
     */
    public long getNext() {
        current++;
        serials.add(current);

        return current;
    }

    /**
     * @return the next serial number to be generated
     */
    public long peekNext() {
        return current + 1;
    }

    /**
     * Adds a serial number to the list
     */
    public void add(long serial) {
        serials.add(serial);
    }

    /**
     * Revokes the serial number from the list
     */
    public void revoke(long serial) {
        serials.remove(serial);
    }

    public boolean contains(long serial) {
        return serials.contains(serial);
    }

    private void save() {
        db.close();
    }

//    private static Set<Long> serials = new HashSet<>(5_000_000);

//        try(
//            FileOutputStream fileOutputStream = new FileOutputStream("./src/main/resources/icrl.ser");
//            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)
//        ) {
//            objectOutputStream.writeObject(serials);
//        } catch(Exception e) {
//            logger.error("Failed to save ICRL to disk: " + e.getMessage());
//        }
}
