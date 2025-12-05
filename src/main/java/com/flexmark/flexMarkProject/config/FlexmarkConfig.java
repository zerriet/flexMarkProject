package com.flexmark.flexMarkProject.config;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

/**
 * Configuration class for the Flexmark Markdown engine.
 * <p>
 * This class is responsible for initializing the heavy-weight {@link Parser} and
 * {@link HtmlRenderer} objects. By defining them as Spring {@link Bean}s, we ensure
 * they are created only once (Singleton scope) and reused across the application.
 * </p>
 * <p>
 * <strong>Optimization Note:</strong> Flexmark Parsers and Renderers are thread-safe.
 * Initializing them here prevents the expensive overhead of rebuilding the parser
 * configuration for every single HTTP request.
 * </p>
 */
public class FlexmarkConfig {
    /**
     * Creates a configured Markdown Parser instance.
     * <p>
     * This bean includes the {@link TablesExtension} to support GitHub-flavored tables.
     * </p>
     *
     * @return A thread-safe, ready-to-use Parser instance.
     */
    @Bean
    public Parser markdownParser() {
        MutableDataSet options = new MutableDataSet();

        // Enable the Tables extension (and any other desired extensions)
        // This corresponds to the "Extensions" logic needed for your report data.
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));

        return Parser.builder(options).build();
    }
    /**
     * Creates a configured HTML Renderer instance.
     * <p>
     * It shares the same configuration options as the Parser to ensure consistency
     * (e.g., if the parser expects tables, the renderer must know how to render tables).
     * </p>
     *
     * @return A thread-safe, ready-to-use HtmlRenderer instance.
     */
    @Bean
    public HtmlRenderer htmlRenderer() {
        MutableDataSet options = new MutableDataSet();

        // Critical: Ensure renderer knows about the same extensions as the parser
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));

        return HtmlRenderer.builder(options).build();
    }
}
