package io.github.ihongs.serv.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.ihongs.util.Synt;

/**
 * 演示工具
 * @author Hongs
 */
public class TestTools {

    @Tool("echo text, to test tool available")
    public String echo(
        @P("content text")
        String text
    ) {
        return "You say: " + text;
    }

    @Tool("simple calculation")
    public String calc(
        @P("operation")
        @E({"+", "-", "*", "/", "add", "subtract", "multiply", "divide"})
        String p,
        @P("number a")
        double a,
        @P("number b")
        double b
    ) {
        return switch (p) {
            case "+" -> Synt.asString(a + b);
            case "-" -> Synt.asString(a - b);
            case "*" -> Synt.asString(a * b);
            case "/" -> Synt.asString(a / b);
            default  -> {
                throw new UnsupportedOperationException("unsupported operation");
            }
        };
    }

}
