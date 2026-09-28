package ch.software_atelier.simpleflex.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListConfigTest {
    @Test void emptyAndUnrecognizedOptionsKeepSafeDefaults() {
        assertEquals(new ListConfig(false, false), ListConfig.parse(""));
        assertEquals(new ListConfig(false, false), ListConfig.parse("# comment\nfoo=true\nhidden=yes\nup=1"));
    }

    @Test void parsesIndependentBooleansWithWhitespaceAndComments() {
        assertEquals(new ListConfig(true, false), ListConfig.parse(" hidden = true \n# up=true\nup=false\n"));
        assertEquals(new ListConfig(false, true), ListConfig.parse("hidden=true\r\nhidden=false\r\nup=true"));
    }
}
