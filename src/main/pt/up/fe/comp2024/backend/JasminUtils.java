package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.ArrayType;
import org.specs.comp.ollir.Element;
import org.specs.comp.ollir.ElementType;
import org.specs.comp.ollir.Type;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.stream.Collectors;

public class JasminUtils {
    public static String argumentsToDescriptor(List<Element> elements) {
        return elements.stream()
                .map(element -> toJasminType(element.getType()))
                .collect(Collectors.joining());
    }

    public static String store(ElementType type, int reg) {
        final String bytecode = switch (type) {
            case INT32, BOOLEAN -> "istore"; // There is no separate boolean type on the JVM.
            case OBJECTREF, STRING -> "astore";
            case THIS -> "astore_0"; // bartek: this seems invalid. Assignment to "this" is impossible.
            case ARRAYREF, CLASS, VOID -> throw new NotImplementedException(type);
        };

        return bytecode + " " + reg;
    }

    public static String load(ElementType type, int reg) {
        final String bytecode = switch (type) {
            case INT32, BOOLEAN -> "iload"; // There is no separate boolean type on the JVM.
            case OBJECTREF, STRING -> "aload";
            case THIS -> "aload_0";
            case ARRAYREF, CLASS, VOID -> throw new NotImplementedException(type);
        };

        return bytecode + " " + reg;
    }

    /**
     * Converts OLLIR type into Jasmin type.
     */
    public static String toJasminType(Type ollirType) {
        return switch (ollirType.getTypeOfElement()) {
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case ARRAYREF -> {
                final ArrayType ollirArrayType = ((ArrayType) ollirType);
                final StringBuilder code = new StringBuilder();

                code.append("[".repeat(ollirArrayType.getNumDimensions()));

                final String elementType = toJasminType(ollirArrayType.getElementType());
                code.append(elementType);

                yield code.toString();
            }
            case OBJECTREF -> {
                final StringBuilder code = new StringBuilder();
                yield code.append("L").append(ollirType.toString().replace("OBJECTREF(", "").replace(")", "")).append(";").toString();
            }
            case CLASS -> throw new NotImplementedException("ElementType.CLASS");
            case THIS -> throw new NotImplementedException("ElementType.THIS");
            case STRING -> "Ljava/lang/String;";
            case VOID -> "V";
        };
    }

    /**
     * Converts OLLIR type into Jasmin "superclass" type (without the L).
     */
    public static String toJasminSuperclassType(String ollirType) {
        if (ollirType == null || ollirType.equals("Object")) {
            return "java/lang/Object";
        }

        return ollirType;
    }
}
