package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.ollir.OllirUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.optimization.OllirTokens.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOL, this::visitBool);
        addVisit(IDENTIFIER, this::visitIdentifier);
        addVisit(ID_USE_EXPR, this::visitMethodCallExpr);
        addVisit(NEW_OBJECT, this::visitNewObjectExpr);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.INT_TYPE_NAME, false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBool(JmmNode node, Void unused) {
        var boolType = new Type(TypeUtils.BOOL_TYPE_NAME, false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        String code = node.get("value") + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        /* Let's say we're visiting:

            int a;
            int b;
            int c;
            c = a + b;
        */

        // tmp0.i32 := .i32 a.i32 + .i32 b.i32;
        // c.int32 := .i32 tmp0.32;


        final Type exprType = TypeUtils.getExprType(node, table);
        final String exprOllirType = OptUtils.toOllirType(exprType);

        final var lhs = visit(node.getChild(0));
        final var rhs = visit(node.getChild(1));

        final StringBuilder computation = new StringBuilder();
        final String code = OptUtils.getTemp() + exprOllirType;                       // tmp0.i32

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        computation.append(code);                                                     // tmp0.i32
        computation.append(SPACE).append(ASSIGN).append(SPACE);                       // :=
        computation.append(exprOllirType).append(SPACE).append(lhs.getCode());        // .i32 a.i32
        computation.append(SPACE).append(node.get("op")).append(SPACE);                // +
        computation.append(exprOllirType).append(SPACE).append(rhs.getCode());        // .i32 b.i32
        computation.append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        final String code = "this";
        return new OllirExprResult(code);
    }

    private OllirExprResult visitIdentifier(JmmNode node, Void unused) {
        String id = node.get("id");
        final String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        final Type type = TypeUtils.getExprType(node, table);
        final String ollirType = OptUtils.toOllirType(type);

        final StringBuilder computation = new StringBuilder();

        // Determine if this identifier refers to a class field.
        for (final var field : table.getFields()) {
            if (!field.getName().equals(id)) {
                continue;
            }

            final String className = node.getAncestor(CLASS_DECL).map(classNode -> classNode.get("name")).orElseThrow();

            // Example:
            // t1.i32 := .i32 getfield(this.Structure_fields, a.i32).i32;

            final var tmp = OptUtils.getTemp();                                     // t1
            computation.append(tmp).append(ollirType);                              // t1.i32
            computation.append(SPACE).append(ASSIGN).append(SPACE);                 // :=
            computation.append(ollirType).append(SPACE).append("getfield(");        // .i32 getfield(
            computation.append("this.").append(className).append(", ");             // this.Structure_fields
            computation.append(id).append(ollirType).append(")").append(ollirType); // a.i32).i32;
            computation.append(END_STMT);

            final String code = tmp + ollirType;
            return new OllirExprResult(code, computation);
        }

        // Determine if this identifier refers to a method formal parameter.
        // This is a hacky hack. Search for identifier in method's parameters.
        final List<Symbol> params = new ArrayList<>(table.getParameters(methodName));
        for (int i = 0; i < params.size(); i++) {
            final Symbol param = params.get(i);

            // FIXME(bartek): Ugly hack to reference actuals. Working around the need of prefixing with $ and actual's index.
            if (param.getName().equals(id)) {
                id = "$" + (i + 1) + "." + id;
                break;
            }
        }

        final String code = id + ollirType;
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        final String methodName = node.get("name");
        final Type returnType = TypeUtils.getExprType(node, table);
        final String ollirType = OptUtils.toOllirType(returnType);

        // First child of IdUseExpr is:
        //  * Identifier:
        //   * the receiver, a.k.a the object that the method is called on: obj.foo(bar)
        //  * VarRefExpr ("this"), in case it is a virtual method
        final JmmNode firstChild = node.getChild(0);
        final String invocationCode = switch (firstChild.getKind()) {
            case "Identifier" -> {
                final String id = firstChild.get("id");
                final boolean receiverIsImport = table.getImports().stream().anyMatch(importName -> importName.equals(id));

                if (receiverIsImport) {
                    // Receiver is an imported class.
                    // Example:
                    //   e.g io.println(foo)
                    yield "invokestatic(" + id;
                } else {
                    final Type idType = TypeUtils.getExprType(firstChild, table);

                    yield "invokevirtual(" + id + "." + idType.getName();
                }
            }
            case "VarRefExpr" -> "invokevirtual(this";
            default -> throw new IllegalStateException("Invalid first child node of a method");
        };

        final StringBuilder subcomputations = new StringBuilder();
        final StringBuilder invocation = new StringBuilder();
        {
            final List<OllirExprResult> results = node.getChildrenStream().skip(1).map(this::visit).toList();
            final List<String> computations = results.stream().map(OllirExprResult::getComputation).toList();
            for (final var computation : computations) {
                if (computation.isEmpty()) continue;
                subcomputations.append(computation);
            }


            final List<String> actuals = results.stream().map(OllirExprResult::getCode).toList();
            final List<String> codes = new ArrayList<>();
            codes.add(invocationCode);
            codes.add('"' + methodName + '"');
            codes.addAll(actuals);

            invocation.append(String.join(", ", codes) + R_PAREN);
        }

        // Example code I want to generate:
        //
        // tmp0.i32 = .i32 invokevirtual(this, "constInstr").i32
        // .i32 tmp0.i32
        //
        // First line is computation.
        // Second line is code.


        final String code = OptUtils.getTemp() + ollirType;

        final StringBuilder computation = new StringBuilder();
        computation.append(subcomputations);

        if (ollirType.equals(".V")) {
            // TODO(bartek): This is not the prettiest way to handle this case, but hey, it works.
            computation.append(invocation).append(ollirType);        // invokevirtual(this, "constInstr").V
            computation.append(END_STMT);
        } else {
            computation.append(code);                                // tmp0.i32
            computation.append(SPACE).append(ASSIGN).append(SPACE);  // :=
            computation.append(ollirType).append(SPACE);             // .i32
            computation.append(invocation).append(ollirType);        // invokevirtual(this, "constInstr").i32
            computation.append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewObjectExpr(JmmNode node, Void unused) {
        /*
            tmp2.Simple :=.Simple new(Simple).Simple;
            invokespecial(tmp2.Simple, "").V;
            s.Simple :=.Simple tmp2.Simple;
         */

        final String type = node.get("id");
        final String ollirType = "." + type;

        final StringBuilder computation = new StringBuilder();
        final String code = OptUtils.getTemp() + ollirType;     // tmp2.Simple

        computation.append(code)                                                          // tmp2.Simple
                .append(SPACE).append(ASSIGN).append(SPACE)                               // :=
                .append(ollirType).append(" new(").append(type).append(")")               // .Simple new(Simple)
                .append(ollirType)                                                        // .Simple
                .append(END_STMT);

        computation
                .append("invokespecial")
                .append(L_PAREN).append(code).append(", ").append("\"\"").append(R_PAREN) // (tmp2.Simple, "")
                .append(ollirType)                                                        // .V
                .append(END_STMT);


        return new OllirExprResult(code, computation);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }
}
