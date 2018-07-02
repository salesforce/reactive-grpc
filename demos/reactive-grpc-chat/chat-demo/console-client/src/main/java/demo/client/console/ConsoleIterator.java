package demo.client.console;

import jline.console.ConsoleReader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Iterator;

/**
 * Adapts jLine to Iterator.
 */
public class ConsoleIterator implements Iterable<String>, Iterator<String> {
    private ConsoleReader console;
    private String prompt;
    private String lastLine;

    ConsoleIterator(ConsoleReader console, String prompt) {
        this.console = console;
        this.prompt = prompt;
    }

    @Override
    @Nonnull
    public Iterator<String> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        try {
            lastLine = console.readLine(prompt);
            return !lastLine.equals("/quit");
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String next() {
        return lastLine;
    }
}
