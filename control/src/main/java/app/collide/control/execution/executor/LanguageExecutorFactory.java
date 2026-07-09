package app.collide.control.execution.executor;

import app.collide.control.common.ApiException;
import app.collide.control.execution.model.Language;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Language -> LanguageExecutor lookup. Spring collects every {@link LanguageExecutor} bean
 * into the constructor list automatically, so adding a language is: implement the interface,
 * annotate it {@code @Component}, add the enum constant — nothing here changes.
 */
@Component
public class LanguageExecutorFactory {

    private final Map<Language, LanguageExecutor> executors;

    public LanguageExecutorFactory(List<LanguageExecutor> executors) {
        this.executors = new EnumMap<>(Language.class);
        for (LanguageExecutor executor : executors) {
            this.executors.put(executor.language(), executor);
        }
    }

    public LanguageExecutor get(Language language) {
        LanguageExecutor executor = executors.get(language);
        if (executor == null) {
            throw ApiException.badRequest("unsupported language: " + language);
        }
        return executor;
    }
}
