package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

class BiblioCraftWarningFilterTest {

    @Test
    void suppressesKnownBiblioCraftTileEntityWarning() {
        assertTrue(BiblioCraftWarningFilter.shouldSuppress(
            "FML",
            Level.WARN,
            "Potentially Dangerous alternative prefix `minecraft` for name `bookcase`, expected `bibliocraft`. " +
                "This could be a intended override, but in most cases indicates a broken mod."
        ));
    }

    @Test
    void keepsOtherModWarningsVisible() {
        assertFalse(BiblioCraftWarningFilter.shouldSuppress(
            "FML",
            Level.WARN,
            "Potentially Dangerous alternative prefix `minecraft` for name `ironchest.iron`, expected `ironchest`. " +
                "This could be a intended override, but in most cases indicates a broken mod."
        ));
    }

    @Test
    void keepsNonWarningMessagesVisible() {
        assertFalse(BiblioCraftWarningFilter.shouldSuppress(
            "FML",
            Level.INFO,
            "Potentially Dangerous alternative prefix `minecraft` for name `bookcase`, expected `bibliocraft`."
        ));
    }
}
