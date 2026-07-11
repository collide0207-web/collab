package app.collide.control.judge.driver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WirePreludesTest {

    @Test
    void jsListPreludeDefinesTheSameHelpersAsTheClientHarness() {
        assertThat(WirePreludes.JS_LIST).contains("function __toList").contains("function __fromList");
    }

    @Test
    void javaJsonBlockProvidesReaderAndTypedConverters() {
        assertThat(WirePreludes.JAVA_JSON)
                .contains("__readArgs")
                .contains("__asIntArray")
                .contains("__asInteger")
                .contains("__asDouble");
    }

    @Test
    void cppJsonBlockProvidesReaderAndTypedConverters() {
        assertThat(WirePreludes.CPP_JSON).contains("__readArgs").contains("__asIntVec");
    }
}
