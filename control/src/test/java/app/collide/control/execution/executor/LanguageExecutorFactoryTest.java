package app.collide.control.execution.executor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.collide.control.common.ApiException;
import app.collide.control.execution.model.Language;
import java.util.List;
import org.junit.jupiter.api.Test;

/** No Spring context — the factory is a plain map builder, testable with hand-built executors. */
class LanguageExecutorFactoryTest {

    @Test
    void resolvesEachRegisteredExecutorByItsLanguage() {
        LanguageExecutorFactory factory = new LanguageExecutorFactory(
                List.of(new PythonExecutor("python3"), new NodeExecutor("node")));

        assertTrue(factory.get(Language.PYTHON) instanceof PythonExecutor);
        assertTrue(factory.get(Language.JAVASCRIPT) instanceof NodeExecutor);
    }

    @Test
    void rejectsALanguageWithNoRegisteredExecutor() {
        LanguageExecutorFactory factory = new LanguageExecutorFactory(List.of(new PythonExecutor("python3")));

        assertThrows(ApiException.class, () -> factory.get(Language.JAVA));
    }
}
