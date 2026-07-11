package app.collide.control.judge.driver;

import app.collide.control.common.ApiException;
import app.collide.control.execution.model.Language;
import app.collide.control.problem.ProblemHarness;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Generates a complete, compile-once judge driver per language. Unlike the client Run harness
 * (which bakes each case as a literal and recompiles per case), the judge compiles once and feeds
 * each case's input array as one JSON line on stdin, so this driver reads+deserializes at runtime
 * via {@link WirePreludes}. Parallels harness.ts's parseType/argExpr/print dispatch so server and
 * client produce byte-identical canonical output.
 */
@Component
public class JudgeDriverGenerator {

    // --- type tags (mirror harness.ts parseType) ---
    private sealed interface Tag permits Scalar, Node, Ops, Arr {}
    private record Scalar(String raw) implements Tag {}
    private record Node(String kind) implements Tag {} // list-node | tree-node | graph-node
    private record Ops() implements Tag {}
    private record Arr(Tag of) implements Tag {}

    private static final Pattern NODE = Pattern.compile("^(list-node|tree-node|graph-node)<(.+)>$");
    private static final Pattern ARR = Pattern.compile("^array<(.+)>$");
    private static final Pattern IMPORT_LINE = Pattern.compile("(?m)^\\s*import\\s+[^;]+;\\s*$");

    private static Tag parse(String tag) {
        String t = tag.trim();
        if (t.equals("operations")) return new Ops();
        Matcher a = ARR.matcher(t);
        if (a.matches()) return new Arr(parse(a.group(1)));
        Matcher n = NODE.matcher(t);
        if (n.matches()) return new Node(n.group(1));
        return new Scalar(t);
    }

    private boolean usesKind(ProblemHarness h, String kind) {
        if (hitKind(parse(h.returns()), kind)) return true;
        return h.params().stream().anyMatch(p -> hitKind(parse(p.type()), kind));
    }

    private boolean hitKind(Tag t, String kind) {
        return (t instanceof Node n && n.kind().equals(kind)) || (t instanceof Arr a && hitKind(a.of(), kind));
    }

    public String generate(Language language, ProblemHarness harness, String userSource) {
        boolean ops = harness.params().size() == 1 && parse(harness.params().get(0).type()) instanceof Ops;
        return switch (language) {
            case JAVASCRIPT -> ops ? jsOperations(harness, userSource) : js(harness, userSource);
            case JAVA -> ops ? javaOperations(harness, userSource) : java(harness, userSource);
            case PYTHON -> ops ? pyOperations(harness, userSource) : python(harness, userSource);
            case CPP -> ops ? cppOperations(harness, userSource) : cpp(harness, userSource);
        };
    }

    // ---------------- JavaScript ----------------
    private String js(ProblemHarness h, String user) {
        StringBuilder prelude = new StringBuilder();
        if (usesKind(h, "list-node")) prelude.append(WirePreludes.JS_LIST);
        if (usesKind(h, "tree-node")) prelude.append(WirePreludes.JS_TREE);
        if (usesKind(h, "graph-node")) prelude.append(WirePreludes.JS_GRAPH);
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < h.params().size(); i++) {
            if (i > 0) args.append(", ");
            args.append(jsArg(parse(h.params().get(i).type()), "__in[" + i + "]"));
        }
        String call = h.entry() + "(" + args + ")";
        String printed = jsPrint(parse(h.returns()), call);
        return prelude + user + "\n\n;(function(){\n"
                + "  const __in = JSON.parse(require('fs').readFileSync(0,'utf8'));\n"
                + "  console.log(JSON.stringify(" + printed + "));\n"
                + "})();\n";
    }

    private String jsArg(Tag t, String v) {
        if (t instanceof Node n) {
            return switch (n.kind()) {
                case "list-node" -> "__toList(" + v + ")";
                case "tree-node" -> "__toTree(" + v + ")";
                default -> "__toGraph(" + v + ")";
            };
        }
        if (t instanceof Arr ar && ar.of() instanceof Node n && n.kind().equals("list-node")) {
            return "(" + v + ").map(__toList)";
        }
        return v; // scalars/arrays: JSON already gives native JS values
    }

    private String jsPrint(Tag t, String expr) {
        if (t instanceof Node n) {
            return switch (n.kind()) {
                case "list-node" -> "__fromList(" + expr + ")";
                case "tree-node" -> "__fromTree(" + expr + ")";
                default -> "__fromGraph(" + expr + ")";
            };
        }
        return expr;
    }

    // ---------------- Java ----------------
    private String java(ProblemHarness h, String user) {
        StringBuilder types = new StringBuilder();
        StringBuilder fns = new StringBuilder(WirePreludes.JAVA_JSON);
        if (usesKind(h, "list-node")) { types.append(WirePreludes.JAVA_LIST_TYPES); fns.append(WirePreludes.JAVA_LIST_FNS); }
        if (usesKind(h, "tree-node")) { types.append(WirePreludes.JAVA_TREE_TYPES); fns.append(WirePreludes.JAVA_TREE_FNS); }
        if (usesKind(h, "graph-node")) { types.append(WirePreludes.JAVA_GRAPH_TYPES); fns.append(WirePreludes.JAVA_GRAPH_FNS); }

        StringBuilder decls = new StringBuilder();
        StringBuilder callArgs = new StringBuilder();
        for (int i = 0; i < h.params().size(); i++) {
            Tag t = parse(h.params().get(i).type());
            decls.append("        ").append(javaDecl(t)).append(" __a").append(i)
                 .append(" = ").append(javaArg(t, "__in.get(" + i + ")")).append(";\n");
            if (i > 0) callArgs.append(", ");
            callArgs.append("__a").append(i);
        }
        String call = "__sol." + h.entry() + "(" + callArgs + ")";
        String print = javaPrint(parse(h.returns()), call);

        Hoisted hoisted = hoistJavaImports(user);
        return hoisted.imports + types + hoisted.body + "\n\npublic class Main {\n"
                + indent(fns.toString())
                + "    public static void main(String[] args) throws Exception {\n"
                + "        java.util.List<Object> __in = __readArgs();\n"
                + "        Solution __sol = new Solution();\n"
                + decls
                + "        " + print + "\n"
                + "    }\n}\n";
    }

    /** Java requires all imports before any type; hoist any the user wrote, always including java.util.*. */
    private Hoisted hoistJavaImports(String user) {
        StringBuilder imports = new StringBuilder("import java.util.*;\n");
        Matcher m = IMPORT_LINE.matcher(user);
        while (m.find()) {
            String line = m.group().trim();
            if (!line.equals("import java.util.*;")) {
                imports.append(line).append("\n");
            }
        }
        String body = IMPORT_LINE.matcher(user).replaceAll("").strip();
        return new Hoisted(imports.append("\n").toString(), body);
    }

    private record Hoisted(String imports, String body) {}

    private String indent(String block) {
        StringBuilder b = new StringBuilder();
        for (String line : block.split("\n", -1)) {
            if (line.isEmpty()) b.append("\n");
            else b.append("    ").append(line).append("\n");
        }
        return b.toString();
    }

    private String javaDecl(Tag t) {
        if (t instanceof Node n) return switch (n.kind()) { case "list-node" -> "ListNode"; case "tree-node" -> "TreeNode"; default -> "Node"; };
        if (t instanceof Arr ar && ar.of() instanceof Node n && n.kind().equals("list-node")) return "ListNode[]";
        String raw = ((Scalar) t).raw();
        return switch (raw) {
            case "int" -> "int"; case "long" -> "long"; case "double" -> "double"; case "bool" -> "boolean"; case "string" -> "String";
            case "int[]" -> "int[]"; case "double[]" -> "double[]"; case "string[]" -> "String[]"; case "int[][]" -> "int[][]";
            default -> throw ApiException.badRequest("unsupported Java param type: " + raw);
        };
    }

    private String javaArg(Tag t, String v) {
        if (t instanceof Node n) return switch (n.kind()) {
            case "list-node" -> "__toList(__asIntArray(" + v + "))";
            case "tree-node" -> "__toTree(__asInteger(" + v + "))";
            default -> "__toGraph(__asIntMatrix(" + v + "))";
        };
        if (t instanceof Arr ar && ar.of() instanceof Node n && n.kind().equals("list-node")) {
            return "((java.util.List<Object>)" + v + ").stream().map(x -> __toList(__asIntArray(x))).toArray(ListNode[]::new)";
        }
        String raw = ((Scalar) t).raw();
        return switch (raw) {
            case "int" -> "__asInt(" + v + ")"; case "long" -> "__asLong(" + v + ")"; case "double" -> "__asDouble(" + v + ")";
            case "bool" -> "__asBool(" + v + ")"; case "string" -> "__asStr(" + v + ")";
            case "int[]" -> "__asIntArray(" + v + ")"; case "double[]" -> "__asDoubleArray(" + v + ")";
            case "string[]" -> "__asStrArray(" + v + ")"; case "int[][]" -> "__asIntMatrix(" + v + ")";
            default -> throw ApiException.badRequest("unsupported Java param type: " + raw);
        };
    }

    private String javaPrint(Tag t, String expr) {
        if (t instanceof Node n) return switch (n.kind()) {
            case "list-node" -> "System.out.print(__fromList(" + expr + "));";
            case "tree-node" -> "System.out.print(__fromTree(" + expr + "));";
            default -> "System.out.print(__fromGraph(" + expr + "));";
        };
        String raw = ((Scalar) t).raw();
        return switch (raw) {
            case "bool" -> "System.out.print((" + expr + ") ? \"true\" : \"false\");";
            case "string" -> "System.out.print(\"\\\"\" + (" + expr + ") + \"\\\"\");";
            case "int[]", "double[]" -> "{ var __v = " + expr + "; StringBuilder __sb = new StringBuilder(\"[\"); for (int __i = 0; __i < __v.length; __i++) { if (__i > 0) __sb.append(\",\"); __sb.append(__v[__i]); } __sb.append(\"]\"); System.out.print(__sb); }";
            case "string[]" -> "{ var __v = " + expr + "; StringBuilder __sb = new StringBuilder(\"[\"); for (int __i = 0; __i < __v.length; __i++) { if (__i > 0) __sb.append(\",\"); __sb.append(\"\\\"\").append(__v[__i]).append(\"\\\"\"); } __sb.append(\"]\"); System.out.print(__sb); }";
            case "int[][]" -> "{ var __v = " + expr + "; StringBuilder __sb = new StringBuilder(\"[\"); for (int __i = 0; __i < __v.length; __i++) { if (__i > 0) __sb.append(\",\"); __sb.append(\"[\"); for (int __j = 0; __j < __v[__i].length; __j++){ if(__j>0) __sb.append(\",\"); __sb.append(__v[__i][__j]); } __sb.append(\"]\"); } __sb.append(\"]\"); System.out.print(__sb); }";
            default -> "System.out.print(" + expr + ");"; // int/long/double
        };
    }

    // ---------------- Python (Task 7) ----------------
    private String python(ProblemHarness h, String user) {
        StringBuilder prelude = new StringBuilder();
        if (usesKind(h, "list-node")) prelude.append(WirePreludes.PY_LIST);
        if (usesKind(h, "tree-node")) prelude.append(WirePreludes.PY_TREE);
        if (usesKind(h, "graph-node")) prelude.append(WirePreludes.PY_GRAPH);
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < h.params().size(); i++) {
            if (i > 0) args.append(", ");
            args.append(pyArg(parse(h.params().get(i).type()), "__in[" + i + "]"));
        }
        String call = "Solution()." + h.entry() + "(" + args + ")";
        String printed = pyPrint(parse(h.returns()), call);
        return prelude + user + "\n\nimport sys, json\n"
                + "__in = json.loads(sys.stdin.read())\n"
                + "print(json.dumps(" + printed + ", separators=(',', ':')))\n";
    }

    private String pyArg(Tag t, String v) {
        if (t instanceof Node n) return switch (n.kind()) {
            case "list-node" -> "__to_list(" + v + ")"; case "tree-node" -> "__to_tree(" + v + ")"; default -> "__to_graph(" + v + ")";
        };
        if (t instanceof Arr ar && ar.of() instanceof Node n && n.kind().equals("list-node")) return "[__to_list(__x) for __x in " + v + "]";
        return v;
    }

    private String pyPrint(Tag t, String expr) {
        if (t instanceof Node n) return switch (n.kind()) {
            case "list-node" -> "__from_list(" + expr + ")"; case "tree-node" -> "__from_tree(" + expr + ")"; default -> "__from_graph(" + expr + ")";
        };
        return expr;
    }

    private String pyOperations(ProblemHarness h, String user) {
        return user + "\n\nimport sys, json\n"
                + "__in = json.loads(sys.stdin.read())\n"
                + "__ops = __in[0]\n"
                + "__ctor = __ops[0]\n"
                + "__obj = globals()[__ctor[0]](*(__ctor[1] or []))\n"
                + "__res = [None]\n"
                + "for __op in __ops[1:]:\n"
                + "    __res.append(getattr(__obj, __op[0])(*(__op[1] or [])))\n"
                + "print(json.dumps(__res, separators=(',', ':')))\n";
    }

    // ---------------- C++ (Task 7) ----------------
    private String cpp(ProblemHarness h, String user) {
        StringBuilder prelude = new StringBuilder(WirePreludes.CPP_JSON);
        if (usesKind(h, "list-node")) prelude.append(WirePreludes.CPP_LIST);
        if (usesKind(h, "tree-node")) prelude.append(WirePreludes.CPP_TREE);
        if (usesKind(h, "graph-node")) prelude.append(WirePreludes.CPP_GRAPH);
        StringBuilder decls = new StringBuilder();
        StringBuilder callArgs = new StringBuilder();
        for (int i = 0; i < h.params().size(); i++) {
            Tag t = parse(h.params().get(i).type());
            decls.append("    ").append(cppDecl(t)).append(" __a").append(i)
                 .append(" = ").append(cppArg(t, "__in.arr[" + i + "]")).append(";\n");
            if (i > 0) callArgs.append(", ");
            callArgs.append("__a").append(i);
        }
        String print = cppPrint(parse(h.returns()), "__sol." + h.entry() + "(" + callArgs + ")");
        String includes = user.contains("#include <bits/stdc++.h>") ? "" : "#include <bits/stdc++.h>\nusing namespace std;\n\n";
        return includes + prelude + user + "\n\nint main(){\n"
                + "    __J __in = __readArgs();\n"
                + "    Solution __sol;\n"
                + decls
                + "    " + print + "\n"
                + "    return 0;\n}\n";
    }

    private String cppDecl(Tag t) {
        if (t instanceof Node n) return switch (n.kind()) { case "list-node" -> "ListNode*"; case "tree-node" -> "TreeNode*"; default -> "Node*"; };
        if (t instanceof Arr ar && ar.of() instanceof Node n && n.kind().equals("list-node")) return "vector<ListNode*>";
        String raw = ((Scalar) t).raw();
        return switch (raw) {
            case "int" -> "int"; case "long" -> "long long"; case "double" -> "double"; case "bool" -> "bool"; case "string" -> "string";
            case "int[]" -> "vector<int>"; case "double[]" -> "vector<double>"; case "string[]" -> "vector<string>"; case "int[][]" -> "vector<vector<int>>";
            default -> throw ApiException.badRequest("unsupported C++ param type: " + raw);
        };
    }

    private String cppArg(Tag t, String v) {
        if (t instanceof Node n) return switch (n.kind()) {
            case "list-node" -> "__toList(__asIntVec(" + v + "))"; case "tree-node" -> "__toTree(__asIntVecNullable(" + v + "))"; default -> "__toGraph(__asIntMat(" + v + "))";
        };
        if (t instanceof Arr ar && ar.of() instanceof Node n && n.kind().equals("list-node"))
            return "[&]{ vector<ListNode*> __r; for(auto& __x : (" + v + ").arr) __r.push_back(__toList(__asIntVec(__x))); return __r; }()";
        String raw = ((Scalar) t).raw();
        return switch (raw) {
            case "int" -> "__asInt(" + v + ")"; case "long" -> "__asLong(" + v + ")"; case "double" -> "__asDouble(" + v + ")";
            case "bool" -> "__asBool(" + v + ")"; case "string" -> "__asStr(" + v + ")";
            case "int[]" -> "__asIntVec(" + v + ")"; case "double[]" -> "__asDoubleVec(" + v + ")";
            case "string[]" -> "__asStrVec(" + v + ")"; case "int[][]" -> "__asIntMat(" + v + ")";
            default -> throw ApiException.badRequest("unsupported C++ param type: " + raw);
        };
    }

    private String cppPrint(Tag t, String expr) {
        if (t instanceof Node n) return switch (n.kind()) {
            case "list-node" -> "cout << __fromList(" + expr + ");"; case "tree-node" -> "cout << __fromTree(" + expr + ");"; default -> "cout << __fromGraph(" + expr + ");";
        };
        String raw = ((Scalar) t).raw();
        return switch (raw) {
            case "bool" -> "cout << (" + expr + " ? \"true\" : \"false\");";
            case "string" -> "cout << \"\\\"\" << " + expr + " << \"\\\"\";";
            case "int[]", "double[]" -> "{ auto __v = " + expr + "; cout << \"[\"; for (size_t __i = 0; __i < __v.size(); ++__i) { if (__i) cout << \",\"; cout << __v[__i]; } cout << \"]\"; }";
            case "string[]" -> "{ auto __v = " + expr + "; cout << \"[\"; for (size_t __i = 0; __i < __v.size(); ++__i) { if (__i) cout << \",\"; cout << \"\\\"\" << __v[__i] << \"\\\"\"; } cout << \"]\"; }";
            case "int[][]" -> "{ auto __v = " + expr + "; cout << \"[\"; for (size_t __i = 0; __i < __v.size(); ++__i) { if (__i) cout << \",\"; cout << \"[\"; for (size_t __j = 0; __j < __v[__i].size(); ++__j){ if(__j) cout << \",\"; cout << __v[__i][__j]; } cout << \"]\"; } cout << \"]\"; }";
            default -> "cout << (" + expr + ");";
        };
    }

    private String cppOperations(ProblemHarness h, String user) {
        // C++ has no runtime reflection, so a generic operations dispatch isn't possible without
        // per-problem method tables. C++ isn't runnable in this env and nothing depends on it, so
        // this is a documented stub (spec §9). It reads the ops and prints a nulls array.
        String includes = user.contains("#include <bits/stdc++.h>") ? "" : "#include <bits/stdc++.h>\nusing namespace std;\n\n";
        return includes + WirePreludes.CPP_JSON + user + "\n\nint main(){\n"
                + "    __J __in = __readArgs();\n"
                + "    __J __ops = __in.arr[0];\n"
                + "    cout << \"[\"; for(size_t __i=0; __i<__ops.arr.size(); ++__i){ if(__i) cout << \",\"; cout << \"null\"; } cout << \"]\";\n"
                + "    return 0;\n}\n";
    }

    // ---------------- operations: JS + Java (Task 6) ----------------
    private String jsOperations(ProblemHarness h, String user) {
        return user + "\n\n;(function(){\n"
                + "  const __in = JSON.parse(require('fs').readFileSync(0,'utf8'));\n"
                + "  const __ops = __in[0];\n"
                + "  const __ctor = __ops[0];\n"
                + "  const __C = eval(__ctor[0]);\n"
                + "  const __obj = new __C(...(__ctor[1]||[]));\n"
                + "  const __res = [null];\n"
                + "  for (let __i=1; __i<__ops.length; __i++){ const __r = __obj[__ops[__i][0]](...(__ops[__i][1]||[])); __res.push(__r===undefined?null:__r); }\n"
                + "  console.log(JSON.stringify(__res));\n"
                + "})();\n";
    }

    private String javaOperations(ProblemHarness h, String user) {
        Hoisted hoisted = hoistJavaImports(user);
        return hoisted.imports + hoisted.body + "\n\npublic class Main {\n"
                + indent(WirePreludes.JAVA_JSON)
                + "    @SuppressWarnings(\"unchecked\")\n"
                + "    public static void main(String[] args) throws Exception {\n"
                + "        java.util.List<Object> __in = __readArgs();\n"
                + "        java.util.List<Object> __ops = (java.util.List<Object>) __in.get(0);\n"
                + "        java.util.List<Object> __ctor = (java.util.List<Object>) __ops.get(0);\n"
                + "        Class<?> __cls = Class.forName((String) __ctor.get(0));\n"
                + "        Object __obj = __construct(__cls, (java.util.List<Object>) __ctor.get(1));\n"
                + "        StringBuilder __out = new StringBuilder(\"[null\");\n"
                + "        for (int __i = 1; __i < __ops.size(); __i++) {\n"
                + "            java.util.List<Object> __op = (java.util.List<Object>) __ops.get(__i);\n"
                + "            Object __r = __invoke(__obj, (String) __op.get(0), (java.util.List<Object>) __op.get(1));\n"
                + "            __out.append(\",\").append(__json(__r));\n"
                + "        }\n"
                + "        __out.append(\"]\");\n"
                + "        System.out.print(__out);\n"
                + "    }\n"
                + "    @SuppressWarnings(\"unchecked\")\n"
                + "    static Object __coerce(Class<?> __t, Object __v) {\n"
                + "        if (__v == null) return null;\n"
                + "        if (__t == int.class || __t == Integer.class) return (int)(long)(Long) __v;\n"
                + "        if (__t == long.class || __t == Long.class) return (Long) __v;\n"
                + "        if (__t == double.class || __t == Double.class) return __v instanceof Long ? (double)(long)(Long)__v : (Double) __v;\n"
                + "        if (__t == boolean.class || __t == Boolean.class) return (Boolean) __v;\n"
                + "        if (__t == int[].class) return __asIntArray(__v);\n"
                + "        return __v;\n"
                + "    }\n"
                + "    static Object __construct(Class<?> __cls, java.util.List<Object> __a) throws Exception {\n"
                + "        for (java.lang.reflect.Constructor<?> __c : __cls.getDeclaredConstructors()) {\n"
                + "            if (__c.getParameterCount() == __a.size()) { __c.setAccessible(true); return __c.newInstance(__args(__c.getParameterTypes(), __a)); }\n"
                + "        }\n"
                + "        throw new RuntimeException(\"no ctor arity \" + __a.size());\n"
                + "    }\n"
                + "    static Object __invoke(Object __obj, String __name, java.util.List<Object> __a) throws Exception {\n"
                + "        for (java.lang.reflect.Method __m : __obj.getClass().getDeclaredMethods()) {\n"
                + "            if (__m.getName().equals(__name) && __m.getParameterCount() == __a.size()) {\n"
                + "                __m.setAccessible(true); Object __r = __m.invoke(__obj, __args(__m.getParameterTypes(), __a));\n"
                + "                return __m.getReturnType() == void.class ? null : __r;\n"
                + "            }\n"
                + "        }\n"
                + "        throw new RuntimeException(\"no method \" + __name);\n"
                + "    }\n"
                + "    static Object[] __args(Class<?>[] __types, java.util.List<Object> __a) {\n"
                + "        Object[] __out = new Object[__types.length];\n"
                + "        for (int __i = 0; __i < __types.length; __i++) __out[__i] = __coerce(__types[__i], __a.get(__i));\n"
                + "        return __out;\n"
                + "    }\n"
                + "    static String __json(Object __v) {\n"
                + "        if (__v == null) return \"null\";\n"
                + "        if (__v instanceof Boolean) return __v.toString();\n"
                + "        if (__v instanceof String) return \"\\\"\" + __v + \"\\\"\";\n"
                + "        if (__v instanceof int[]) { int[] __x=(int[])__v; StringBuilder __b=new StringBuilder(\"[\"); for(int __i=0;__i<__x.length;__i++){ if(__i>0)__b.append(\",\"); __b.append(__x[__i]); } return __b.append(\"]\").toString(); }\n"
                + "        return __v.toString();\n"
                + "    }\n"
                + "}\n";
    }
}
