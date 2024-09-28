package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.IMPORT_DECL, this::visitImportDecl);
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ID_USE_EXPR, this::visitIDUseExpr);
        addVisit(Kind.BINARY_EXPR, this::visitOp);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitImportDecl(JmmNode node, SymbolTable table) {

        if(table.getImports().isEmpty()) return null;

        if(Collections.frequency(table.getImports(),node.get("ID")) == 1) return null;

        var message = String.format("Import '%s' does not exist or is duplicated - undv_dupimportvisit", node);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null)
        );
        return null;
    }

    private Void visitClassDecl(JmmNode node, SymbolTable table) {
        if(table.getFields().isEmpty()) return null;
        List<String> fieldNames = new ArrayList<>();
        for (var field : table.getFields()) {
            fieldNames.add(field.getName());
        }

        for (var field : table.getFields()) {
            if(Collections.frequency(fieldNames, field.getName())==1) return null;
        }

        var message = String.format("Variable '%s' does not exist or is duplicated - undv_dupfieldvisit", node);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null)
        );
        return null;
    }

    private Void visitIDUseExpr(JmmNode node, SymbolTable table) {
        for (final var method : table.getMethods()) {
            if (method.equals(node.get("name"))) {
                return null;
            }
        }


        final var type = TypeUtils.getExprType(node.getChild(0), table);
        for (final var tableImport : table.getImports()) {
            if (tableImport.equals(table.getSuper()) || tableImport.equals(type.getName())) {
                return null;
            }

            // Check if method comes from an imported class
            final JmmNode childNode = node.getChild(0);
            if (childNode.getOptional("id").isPresent() && childNode.getOptional("id").get().equals(tableImport)) {
                return null;
            }
        }

        var message = String.format("Variable '%s' does not exist - undv_iduseexprvisit", node);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null)
        );

        return null;
    }

    private Void visitOp(JmmNode op, SymbolTable table) {
        System.out.println("undvop");
        final JmmNode firstOperand = op.getChild(0);
        final JmmNode secondOperand = op.getChild(1);

        // Check if operand types are the same
        final Type firstOperandType = TypeUtils.getExprType(firstOperand, table);
        final Type secondOperandType = TypeUtils.getExprType(secondOperand, table);
        final boolean firstOperandTypeOk = firstOperandType.getName().equals("int") || firstOperandType.getName().equals("int[]");
        final boolean secondOperandTypeOk = secondOperandType.getName().equals("int") || secondOperandType.getName().equals("int[]");
        final boolean sameOperandTypes = firstOperandTypeOk && secondOperandTypeOk;

        if (sameOperandTypes) return null;


        var message = String.format("Variable '%s' does not exist - undv_opvisit", op);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(op),
                NodeUtils.getColumn(op),
                message,
                null)
        );
        return null;
    }
    private Void visitReturnStmt(JmmNode stmt, SymbolTable table) {
        JmmNode returnVar = stmt.getChild(0);

        if(returnVar.getKind().equals("Bool")) {
            return null;
        } else if (returnVar.getKind().equals("IntegerLiteral")) {
            return null;
        } else if (returnVar.getKind().equals("Identifier")) {
            SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

            List<String> fieldNames = new ArrayList<>();
            for (var field : table.getFields()) {
                fieldNames.add(field.getName());
            }
            // Var is a field, return
            if(Collections.frequency(fieldNames,returnVar.get("id")) == 1) return null;

            // Var is a parameter, return
            if(Collections.frequency(table.getParameters(currentMethod), returnVar.get("id")) == 1) return null;

            // Var is a declared variable, return
            List<String> locName = new ArrayList<>();
            for (var loc : table.getLocalVariables(currentMethod)) {
                locName.add(loc.getName());
            }
            if(Collections.frequency(locName, returnVar.get("id")) == 1) return null;
        } else if (returnVar.getKind().equals("ArrayIndex") ) {
            return null;
        } else if(returnVar.getKind().equals("IdUseExpr")) {
            for (var method : table.getMethods()) {
                if (method.equals(returnVar.get("name"))) {
                    return null;
                }
            }
        }

        var message = String.format("Variable '%s' does not exist - undv_retvisit", stmt.getChild(0));
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(stmt),
                NodeUtils.getColumn(stmt),
                message,
                null)
        );

        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        boolean dupParam = false;
        boolean dupLocal = false;
        method.toTree();
        int count  = Collections.frequency(table.getMethods(), currentMethod);
        if(count == 1) {
            for(var param : table.getParameters(currentMethod)) {
                if(Collections.frequency(table.getParameters(currentMethod), param) > 1) dupParam = true;
            }
            for (var local : table.getLocalVariables(currentMethod)){
                if(Collections.frequency(table.getLocalVariables(currentMethod), local) > 1) dupLocal = true;
            }
        }

        if(count == 1 && !dupParam && !dupLocal) return null;

        var message = String.format("Variable '%s' does not exist - undv_methodvisit", method.getChild(0));
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(method),
                NodeUtils.getColumn(method),
                message,
                null)
        );
        return null;
    }
}
