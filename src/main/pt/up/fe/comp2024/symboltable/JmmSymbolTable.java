package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JmmSymbolTable implements SymbolTable {

    private final List<String> imports;
    private final String className;
    private final String extendedName;
    private final List<Symbol> fields;
    private final List<String> methods;
    private final Map<String, Type> returnTypes;
    private final Map<String, List<Symbol>> params;

    /**
     * Contains local variables for methods.
     * Method names are map keys.
     * Method's local variables are map values.
     */
    private final Map<String, List<Symbol>> locals;

    public JmmSymbolTable(
            List<String> imports,
            String className,
            String extendedName,
            List<Symbol> fields,
            List<String> methods,
            Map<String, Type> returnTypes,
            Map<String, List<Symbol>> params,
            Map<String, List<Symbol>> locals) {
        this.imports = imports;
        this.className = className;
        this.extendedName = extendedName;
        this.fields = fields;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return extendedName;
    }

    @Override
    public List<Symbol> getFields() {
        return Collections.unmodifiableList(fields);
    }

    @Override
    public List<String> getMethods() {
        return Collections.unmodifiableList(methods);
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return returnTypes.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return Collections.unmodifiableList(params.get(methodSignature));
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return Collections.unmodifiableList(locals.get(methodSignature));
    }

}
