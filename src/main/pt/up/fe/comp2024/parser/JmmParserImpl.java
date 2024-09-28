package pt.up.fe.comp2024.parser;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import pt.up.fe.comp.jmm.ast.antlr.AntlrParser;
import pt.up.fe.comp.jmm.parser.JmmParser;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.Map;

public class JmmParserImpl implements JmmParser {

    @Override
    public String getDefaultRule() {
        return "program";
    }

    @Override
    public JmmParserResult parse(String jmmCode, String startingRule, Map<String, String> config) {

        try {
            // Convert code string into a character stream
            var input = new ANTLRInputStream(jmmCode);
            // Transform characters into tokens using the lexer
            var lex = new pt.up.fe.comp2024.JavammLexer(input);
            // Wrap lexer around a token stream
            var tokens = new CommonTokenStream(lex);
            // Transforms tokens into a parse tree
            var parser = new pt.up.fe.comp2024.JavammParser(tokens);


            // Convert ANTLR CST to JmmNode AST
            return AntlrParser.parse(lex, parser, startingRule, config);

        } catch (Exception e) {
            // There was an uncaught exception during parsing, create an error JmmParserResult without root node
            return JmmParserResult.newError(Report.newError(Stage.SYNTATIC, -1, -1, "Exception during parsing", e), config);
        }
    }
}
