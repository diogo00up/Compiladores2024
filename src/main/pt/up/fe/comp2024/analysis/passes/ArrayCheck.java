package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

public class ArrayCheck extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ARR_REF_EXPR, this::visitNewIntArr);
        addVisit(Kind.ARRAY_INDEX, this::visitArrIdx);
    }

    private Void visitArrIdx(JmmNode node, SymbolTable table) {
        var leftType = TypeUtils.getExprType(node.getChild(0),table);
        System.out.println("here");
        var rightType = TypeUtils.getExprType(node.getChild(1),table);
        System.out.println("left: " + node.getChild(0));
        System.out.println(leftType);
        System.out.println("right: " + node.getChild(1));
        System.out.println(rightType);
        System.out.println("leftT array?: " + leftType.isArray());
        System.out.println("leftT vararg?: " + leftType.getName().equals("int..."));
        System.out.println("rightT int?: " + rightType.getName().equals("int"));

        if((leftType.isArray() || leftType.getName().equals("int...")) && rightType.getName().equals("int")) {
            return null;
        }

        /*if (node.getChild(0).getKind().equals(Kind.BOOL_OP) || node.getChild(0).getKind().equals(Kind.BINARY_EXPR)) {

        }*/
        System.out.println("fodeu");
        System.out.println("left: " + node.getChild(0));
        System.out.println(leftType);
        System.out.println("right: " + node.getChild(1));
        System.out.println(rightType);
        var message = String.format("Variable '%s' does not exist - arr_arridxvisit", node);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null)
        );
        return null;
    }
    private Void visitMethodDecl(JmmNode node, SymbolTable table) {
        currentMethod = node.get("name");
        System.out.println(node.toTree());
        return null;
    }

    private Void visitNewIntArr(JmmNode node, SymbolTable table){
        var type = TypeUtils.getExprType(node, table).getName();
        for (var child : node.getChildren()) {
            var childType = TypeUtils.getExprType(child, table).getName();
            if (!childType.equals(type)) {
                var message = String.format("Variable '%s' does not exist - arr_newintarrvisit", node.getChild(0));
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        message,
                        null)
                );
            }
        }
        return null;
    }
}
