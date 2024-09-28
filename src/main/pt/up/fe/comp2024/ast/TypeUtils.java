package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.optimization.OptUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

public class TypeUtils {

    public static final String INT_TYPE_NAME = "int";
    public static final String VARARG_TYPE_NAME = "int...";
    public static final String BOOL_TYPE_NAME = "boolean";
    public static final String VOID_TYPE_NAME = "void";
    public static final String ARR_INDEX_NAME = "int[]";

    /**
     * Gets the {@link Type} of an arbitrary expression.
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded

        final Kind kind = Kind.fromString(expr.getKind());

        return switch (kind) {
            case BOOL_OP -> getBoolExprType(expr);
            case NOT_OP-> new Type(BOOL_TYPE_NAME, false);
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case BOOL -> new Type(BOOL_TYPE_NAME, false);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case VARARG -> new Type(VARARG_TYPE_NAME, false);
            case NEW_CLASS -> new Type(expr.get("id"), false);
            case NEW_INT_ARR -> new Type(INT_TYPE_NAME, true);
            case ARR_REF_EXPR -> new Type(INT_TYPE_NAME, true);
            case ARRAY_INDEX -> new Type(ARR_INDEX_NAME, true);
            case ID_USE_EXPR -> {
                final String name = expr.get("name");

                // Determining the method's return type:
                //  Case 1. If the method is defined in current file, get its return type from the symbol table
                //  Case 2. If the method is imported AND the result is assigned to a variable, the method's return type is the variable's type
                //  Case 3. If the method is imported AND the result is not assigned to a variable, the method's return type is void
                String returnType = "";
                try {
                    returnType = table.getReturnType(name).getName();
                } catch (NullPointerException ex) {
                    // This is okay. Method is not defined in the current file, so it must be imported.
                    // If the result of the method call is assigned to a variable, get the variable's type

                    final String assignedVariableName = expr.getAncestor(ASSIGN_STMT).map(assign -> assign.get("id")).orElse(null);
                    if (assignedVariableName != null) {
                        // TODO(bartek): Handle class field variables, not only local variables
                        String surroundingMethodName = expr.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();

                        // Try to find type by looking at local variables
                        Type assignedVariableType = table.getLocalVariables(surroundingMethodName).stream()
                                .filter(var -> var.getName().equals(assignedVariableName))
                                .findFirst()
                                .map(Symbol::getType)
                                .orElse(null);


                        if (assignedVariableType == null) {
                            // Try to find type by looking at class fields
                            assignedVariableType = table.getFields().stream()
                                    .filter(var -> var.getName().equals(assignedVariableName))
                                    .findFirst()
                                    .map(Symbol::getType)
                                    .orElseThrow();
                        }

                        returnType = assignedVariableType.getName();
                    } else {
                        returnType = "void";
                    }
                }

                yield new Type(returnType, false);
            }
            case IDENTIFIER -> {
                final String ident = expr.get("id");
                final String methodName = expr.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();

                Type localType = null;

                // Search for identifier in method's locals
                final List<Symbol> locals = new ArrayList<>(table.getLocalVariables(methodName));
                for (final Symbol local : locals) {
                    if (local.getName().equals(ident)) {
                        localType = local.getType();
                        break;
                    }
                }

                // Search for identifier in method's parameters
                final List<Symbol> params = new ArrayList<>(table.getParameters(methodName));
                for (final Symbol param : params) {
                    if (param.getName().equals(ident)) {
                        // FIXME(bartek): Ugly hack to reference actuals. Working around the need of prefixing with $ and actual's index.
                        //  Code from OllirExprGeneratorVisitor#visitIdentifier() should actually be here.
                        localType = param.getType();
                        break;
                    }
                }

                final List<Symbol> fields = new ArrayList<>(table.getFields());
                for (final Symbol field : fields) {
                    if (field.getName().equals(ident)) {
                        localType = field.getType();
                        break;
                    }
                }


                // Search for identifier in file's imports
                final List<String> imports = new ArrayList<>(table.getImports());
                for (final String imp : imports) {
                    final var lastDotIdx = imp.lastIndexOf('.');
                    final var importedIdentifier = imp.substring(lastDotIdx + 1);
                    if (importedIdentifier.equals(ident)) {
                        // TODO(bartek): It's possible that it should not be VOID_TYPE_NAME, but an empty string.
                        //  No time to verify this though.
                        localType = new Type(VOID_TYPE_NAME, false);
                        break;
                    }

                }

                yield localType;
            }
            case NEW_OBJECT -> {
                final String className = expr.get("id");

                yield new Type(className, false);
            }
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };
    }

    private static Type getBinExprType(JmmNode binaryExpr) {

        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/", "<", ">" ->
                    new Type(INT_TYPE_NAME, false); //todo(goncalo): added - and / ask where tf < and > r and how this func is used
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    private static Type getBoolExprType(JmmNode boolExpr) {

        String operator = boolExpr.get("op");

        return switch (operator) {
            case "&&", "||" ->
                    new Type(BOOL_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + boolExpr + "'");
        };
    }

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        final String varName = varRefExpr.get("name");

        if(varName.equals("this")) return new Type("this", false);

        String currentMethod = varRefExpr.getAncestor(METHOD_DECL).orElseThrow().get("name");

        final var fields = table.getFields();
        final var params = table.getParameters(currentMethod);
        final var locals = table.getLocalVariables(currentMethod);

        // Check if the variable is a field
        for (var field : fields) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }

        // Check if the variable is a parameter
        for (var param : params) {
            if (param.getName().equals(varName)) {
                return param.getType();
            }
        }

        // Check if the variable is a local variable
        for (var local : locals) {
            if (local.getName().equals(varName)) {
                return local.getType();
            }
        }

        throw new RuntimeException("Variable " + varName + " not found.");
    }

    public static Type getVarExprAssignType(JmmNode node, SymbolTable table) {
        String leftSymbol = node.get("id");

        String currentMethod = node.getAncestor(METHOD_DECL).orElseThrow().get("name");

        var fields = table.getFields();
        var params = table.getParameters(currentMethod);
        var locals = table.getLocalVariables(currentMethod);

        // Check if the variable is a field
        for (var field : fields) {
            if (field.getName().equals(leftSymbol)) {
                return field.getType();
            }
        }

        // Check if the variable is a parameter
        for (var param : params) {
            if (param.getName().equals(leftSymbol)) {
                return param.getType();
            }
        }

        // Check if the variable is a local variable
        for (var local : locals) {
            if (local.getName().equals(leftSymbol)) {
                return local.getType();
            }
        }

        throw new RuntimeException("Variable " + leftSymbol + "not found.");
    }

    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {

        return sourceType.equals(destinationType);
    }

    public static boolean isField(JmmNode node, SymbolTable table, String currentMethod) {
        final var locals = table.getLocalVariables(currentMethod);
        final var params = table.getParameters(currentMethod);
        final var fields = table.getFields();
        final var nodeAnnotation = node.isInstance(VAR_REF_EXPR) ? "name" : "id";

        for (final var local : locals) {
            if (local.getName().equals(node.get(nodeAnnotation))) {
                return false;
            }
        }

        for (final var param : params) {
            if (param.getName().equals(node.get(nodeAnnotation))) {
                return false;
            }
        }

        for (final var field : fields) {
            if (field.getName().equals(node.get(nodeAnnotation))) {
                return true;
            }
        }

        return false;
    }
}
