package de.momokli.citypost.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DAO-Tests gegen H2 im MySQL-Kompatibilitätsmodus — validiert das SQL
 * (Schema + Queries) ohne echte MariaDB. Produktiv wird dieselbe Tabelle
 * gegen MariaDB/MySQL genutzt (LONGBLOB statt BLOB).
 */
class PackageDaoTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    private Database database;
    private PackageDao dao;

    @BeforeEach
    void setUp() throws Exception {
        // Eigene In-Memory-DB pro Test, damit keine Zeilen aus anderen Tests "durchschimmern"
        String url = "jdbc:h2:mem:citypost_test" + DB_SEQ.incrementAndGet()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        database = new Database(url, "sa", "", 2);
        database.initSchema();
        dao = new PackageDao(database.dataSource());
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void insertReturnsGeneratedId() throws Exception {
        int id = dao.insert(UUID.randomUUID().toString(), "YmxvYg==", "world1");
        assertTrue(id > 0);
    }

    @Test
    void findUnclaimedReturnsOnlyOpenPackagesOldestFirst() throws Exception {
        String uuid = UUID.randomUUID().toString();
        int first = dao.insert(uuid, "cGFja2V0MQ==", "world1");
        int second = dao.insert(uuid, "cGFja2V0Mg==", "world2");
        dao.insert(uuid, "cGFja2V0Mw==", "world1");

        List<CityPackage> open = dao.findUnclaimed();
        assertEquals(3, open.size());
        assertEquals(first, open.get(0).id());
        assertEquals(second, open.get(1).id());
        assertEquals("world1", open.get(0).world());
        assertEquals(uuid, open.get(0).playerUuid().toString());
        assertFalse(open.get(0).claimed());
    }

    @Test
    void markClaimedRemovesFromUnclaimed() throws Exception {
        String uuid = UUID.randomUUID().toString();
        int a = dao.insert(uuid, "YQ==", "world1");
        int b = dao.insert(uuid, "Yg==", "world2");

        assertTrue(dao.markClaimed(a));
        assertFalse(dao.markClaimed(a)); // zweites Mal: bereits abgeholt

        List<CityPackage> open = dao.findUnclaimed();
        assertEquals(1, open.size());
        assertEquals(b, open.get(0).id());
    }

    @Test
    void blobRoundTripPreservesContent() throws Exception {
        String blob = "c2VyaWFsaXNpZXJ0ZXItaW5oYWx0LXRlc3Q="; // Base64-Testinhalt
        int id = dao.insert(UUID.randomUUID().toString(), blob, "world1");

        CityPackage pkg = dao.findUnclaimed().get(0);
        assertEquals(blob, pkg.itemsBlob());
        assertEquals(id, pkg.id());
    }
}
