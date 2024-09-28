package pt.up.fe.comp2024.optimization;

/**
 * Stores result of OLLIR code generation for an expression.
 * <p>
 * Both of its fields contain OLLIR code. The difference is as follows:
 * <ul>
 *  <li><code>computation</code>: OLLIR code to create temporaries used by <code>code</code></li>
 *  <li><code>code</code>: OLLIR code that references the temporaries created in <code>computation</code></li>
 * </ul>
 * <p>
 * Take this OLLIR snippet as an example:
 * <pre>
 * tmp0.i32 = .i32 invokevirtual(this, "constInstr").i32;
 * .i32 tmp0.i32;
 * </pre>
 * <p>
 * The first line is <code>computation</code>, the second line is <code>code</code>.
 * <code>code</code> references the temporaries in <code>computation</code>.
 */
public class OllirExprResult {

    public static final OllirExprResult EMPTY = new OllirExprResult("", "");

    private final String computation;
    private final String code;

    public OllirExprResult(String code, String computation) {
        this.code = code;
        this.computation = computation;
    }

    public OllirExprResult(String code) {
        this(code, "");
    }

    public OllirExprResult(String code, StringBuilder computation) {
        this(code, computation.toString());
    }

    public String getComputation() {
        return computation;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "OllirNodeResult{" + "computation='" + computation + '\'' + ", code='" + code + '\'' + '}';
    }
}
