package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        // Each of these visitors must be stack-neutral.
        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {
        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generateClassUnit(ClassUnit classUnit) {
        final StringBuilder code = new StringBuilder();

        // generate class name
        final ClassUnit ollirClass = ollirResult.getOllirClass();
        final String className = ollirClass.getClassName();

        // TODO(bartek): Use real class access modifier
        final String accessModifier = "public ";

        code.append(".class ").append(accessModifier).append(className).append(NL);

        final String superClass = JasminUtils.toJasminSuperclassType(ollirClass.getSuperClass());
        code.append(".super ").append(superClass).append(NL).append(NL);

        ollirClass.getFields().forEach(field -> {
            code.append(".field ");
            System.out.println("fam: " + field.getFieldAccessModifier().name().toLowerCase());
            code.append(field.getFieldAccessModifier().name().toLowerCase().equals("default") ? "" : field.getFieldAccessModifier().name().toLowerCase() + " ");
            code.append(field.getFieldName()).append(" ");
            code.append(JasminUtils.toJasminType(field.getFieldType())).append(NL).append(NL);
        });

        // generate a single constructor method
        String defaultConstructor =
                ".method public <init>()V" + NL +
                        "   aload_0" + NL +
                        "   invokespecial " + superClass + "/" + "<init>()V" + NL +
                        "   return" + NL +
                        ".end method" + NL;

        code.append(defaultConstructor);

        // generate code for all other methods
        for (final Method method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        // set method
        currentMethod = method;

        final StringBuilder code = new StringBuilder();

        // calculate access accessModifier
        final String accessModifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        final String nonAccessModifier = method.isStaticMethod() ? "static " : "";
        final String methodName = method.getMethodName();

        code.append("\n.method ").append(accessModifier).append(nonAccessModifier).append(methodName);

        // generate parameters
        code.append("(");
        for (final Element param : method.getParams()) {
            code.append(JasminUtils.toJasminType(param.getType()));
        }
        code.append(")");

        code.append(JasminUtils.toJasminType(method.getReturnType())); // Return type

        code.append(NL);

        // add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (final Instruction inst : method.getInstructions()) {
            final String instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        final StringBuilder code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        final Element lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        final int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        final ElementType elementType = lhs.getType().getTypeOfElement();

        code.append(JasminUtils.store(elementType, reg)).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        final int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        final ElementType elementType = operand.getType().getTypeOfElement();
        return JasminUtils.load(elementType, reg) + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        final StringBuilder code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operationComplexArgsFuncCall
        final String op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv"; //TODO(goncalo)
            case SUB -> "isub";//TODO(goncalo)
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        final StringBuilder code = new StringBuilder();

        final Element operand = returnInst.getOperand();
        if (operand != null) {
            code.append(generators.apply(operand));
            code.append("ireturn").append(NL);
        } else {
            code.append("return");
        }


        return code.toString();
    }

    private String generateCall(CallInstruction callInst) {
        final StringBuilder code = new StringBuilder();

        switch (callInst.getInvocationType()) {
            case invokevirtual -> {
                final String classname = ((ClassType) callInst.getCaller().getType()).getName();
                final String methodname = ((LiteralElement) callInst.getMethodName()).getLiteral().replace("\"", "");

                // Find matching method by name. Java-- does not support method overloading.
                // TODO(bartek): Support imported and inherited methods (i.e. methods not present in ClassUnit)

                final var caller = (Operand) callInst.getCaller();
                final int objectrefReg = currentMethod.getVarTable().get(caller.getName()).getVirtualReg();
                code.append("aload ").append(objectrefReg).append(NL);
                // Push operands onto the stack from the registers.
                for (final Element element : callInst.getArguments()) {
                    if (element instanceof Operand) {
                        final Operand operand = (Operand) element;
                        final int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
                        code.append(JasminUtils.load(operand.getType().getTypeOfElement(), reg)).append(NL);
                    }
                    if (element instanceof LiteralElement) {
                        final var literal = ((LiteralElement) element).getLiteral();
                        code.append("ldc ").append(literal).append(NL);
                    }
                }

                code.append("invokevirtual ");
                code.append(classname).append("/").append(methodname);
                code.append("(");
                code.append(callInst.getArguments().stream()
                        .map(p -> JasminUtils.toJasminType(p.getType()))
                        .collect(Collectors.joining())
                );
                code.append(")");
                code.append(JasminUtils.toJasminType(callInst.getReturnType()));


                code.append(NL);
            }
            case invokeinterface -> throw new NotImplementedException("Not supported by Java--.");
            case invokespecial -> {
                // invokespecial was named invokenonvirtual in the past.
                // The difference to invokevirtual is that invokespecial is resolved at compile time.
                // The first argument to invokespecial is an objectref.

                final Operand operand = ((Operand) callInst.getCaller());
                final String classname = ((ClassType) operand.getType()).getName();
                final String methodname = "<init>";
                final String descriptor = "()V";

                final int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
                code.append("aload ").append(reg).append(NL);
                code.append("invokespecial ").append(classname).append("/").append(methodname).append(descriptor).append(NL);
                code.append("pop ").append(NL); // Dismiss the void result of invokespecial

            }
            case invokestatic -> {
                var classname = ((Operand) callInst.getCaller()).getName();
                // If this classname is imported, use the fully-qualified name
                for (final var fullImportPath : ollirResult.getOllirClass().getImports()) {
                    final var importParts = fullImportPath.split("\\.");
                    if (importParts.length > 1) {
                        final var importedClass = importParts[importParts.length - 1];
                        if (importedClass.equals(classname)) {
                            classname = fullImportPath.replace(".", "/");
                        }
                    }
                }

                final String methodname = ((LiteralElement) callInst.getMethodName()).getLiteral().replace("\"", "");
                final String descriptor = "(" + JasminUtils.argumentsToDescriptor(callInst.getArguments()) + ")" + JasminUtils.toJasminType(callInst.getReturnType());

                for (final Element element : callInst.getArguments()) {
                    if (element.isLiteral()) {
                        final var literal = ((LiteralElement) element).getLiteral();
                        code.append("ldc ").append(literal).append(NL);
                    } else {
                        final Operand operand = (Operand) element;
                        final int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
                        code.append(JasminUtils.load(operand.getType().getTypeOfElement(), reg)).append(NL);
                    }
                }

                code.append("invokestatic ").append(classname).append("/").append(methodname).append(descriptor).append(NL);
            }
            case NEW -> {
                final String classname = ((Operand) callInst.getCaller()).getName();

                code.append("new ").append(classname).append(NL);
                code.append("dup").append(NL);
            }
            case arraylength -> throw new NotImplementedException("arraylength is not yet implemented");
            case ldc -> throw new NotImplementedException("Not suported by Java--");
        }

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putFieldInst) {
        // TODO(bartek): Implement
        final StringBuilder code = new StringBuilder();
        final String className = ollirResult.getOllirClass().getClassName();

        // Push last operand onto the stack. Last operand is the value.
        final var value = (LiteralElement) putFieldInst.getOperands().get(putFieldInst.getOperands().size() - 1);
        code.append("aload_0").append(NL);
        code.append("bipush ").append(value.getLiteral()).append(NL);
        code.append("putfield ").append(className).append("/").append(putFieldInst.getField().getName()).append(" ").append(JasminUtils.toJasminType(putFieldInst.getField().getType())).append(NL);
        // Example:
        // putfield ClassName/fieldName I
        // code.append("putfield ").append();

        /*for (final Element operand : putFieldInst.getOperands()) {
            System.out.println("operand: " + operand.toString());
        }*/


        return code.toString();
    }

    private String generateGetField(GetFieldInstruction putFieldInst) {
        final String className = ollirResult.getOllirClass().getClassName();
        final StringBuilder code = new StringBuilder();
        code.append("aload_0").append(NL);
        code.append("getfield ").append(className).append("/").append(putFieldInst.getField().getName()).append(" ").append(JasminUtils.toJasminType(putFieldInst.getField().getType())).append(NL);
        return code.toString();
    }
}
