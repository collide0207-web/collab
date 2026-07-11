package app.collide.control.judge.driver;

/**
 * Per-language source blocks injected ahead of the generated judge driver: the wire (de)serializers
 * (ported byte-for-byte from the client harness {@code collide/src/run/harness.ts} so server output
 * matches the SP3 bundle expected values exactly) plus, for the compiled languages, a minimal JSON
 * reader + typed converters — the "written once per language, not per problem" deserializer the
 * master spec §7.2 calls for. Unlike the client Run tier (which bakes each case as a literal), the
 * judge compiles once and reads each case's input from stdin, so the driver parses JSON at runtime.
 *
 * <p>The JSON readers deliberately omit string-escape handling: the wire inputs the judge feeds are
 * numbers, arrays, booleans, null, and plain identifier strings (design-problem method names), never
 * escaped text. Escape handling is a mechanical follow-on if a later problem needs it.
 */
public final class WirePreludes {

    private WirePreludes() {}

    // ---- JavaScript: native JSON.parse, so only list/tree/graph serde is needed. ----
    public static final String JS_LIST =
            "function ListNode(val, next){ this.val = val===undefined?0:val; this.next = next===undefined?null:next; }\n"
            + "function __toList(a){ let d=new ListNode(0), c=d; for(const x of a){ c.next=new ListNode(x); c=c.next; } return d.next; }\n"
            + "function __fromList(n){ const r=[]; while(n){ r.push(n.val); n=n.next; } return r; }\n\n";

    public static final String JS_TREE =
            "function TreeNode(val,left,right){ this.val=val===undefined?0:val; this.left=left||null; this.right=right||null; }\n"
            + "function __toTree(a){ if(!a.length||a[0]==null) return null; const root=new TreeNode(a[0]); const q=[root]; let i=1;\n"
            + "  while(q.length&&i<a.length){ const n=q.shift();\n"
            + "    if(i<a.length){ const v=a[i++]; if(v!=null){ n.left=new TreeNode(v); q.push(n.left); } }\n"
            + "    if(i<a.length){ const v=a[i++]; if(v!=null){ n.right=new TreeNode(v); q.push(n.right); } } }\n"
            + "  return root; }\n"
            + "function __fromTree(root){ const out=[]; const q=[root]; while(q.length){ const n=q.shift(); if(n){ out.push(n.val); q.push(n.left,n.right); } else out.push(null); }\n"
            + "  while(out.length && out[out.length-1]==null) out.pop(); return out; }\n\n";

    public static final String JS_GRAPH =
            "function Node(val, neighbors){ this.val=val===undefined?0:val; this.neighbors=neighbors||[]; }\n"
            + "function __toGraph(adj){ if(!adj.length) return null; const nodes=adj.map((_,i)=>new Node(i+1));\n"
            + "  adj.forEach((nb,i)=>{ nodes[i].neighbors = nb.map((v)=>nodes[v-1]); }); return nodes[0]; }\n"
            + "function __fromGraph(node){ if(!node) return []; const seen=new Map(); const q=[node]; seen.set(node.val,node);\n"
            + "  while(q.length){ const n=q.shift(); for(const m of n.neighbors){ if(!seen.has(m.val)){ seen.set(m.val,m); q.push(m); } } }\n"
            + "  const vals=[...seen.keys()].sort((a,b)=>a-b); return vals.map((v)=>seen.get(v).neighbors.map((m)=>m.val).sort((a,b)=>a-b)); }\n\n";

    // ---- Python: native json, only serde needed. ----
    public static final String PY_LIST =
            "class ListNode:\n    def __init__(self, val=0, next=None):\n        self.val=val; self.next=next\n"
            + "def __to_list(a):\n    d=ListNode(0); c=d\n    for x in a:\n        c.next=ListNode(x); c=c.next\n    return d.next\n"
            + "def __from_list(n):\n    r=[]\n    while n:\n        r.append(n.val); n=n.next\n    return r\n\n";

    public static final String PY_TREE =
            "class TreeNode:\n    def __init__(self, val=0, left=None, right=None):\n        self.val=val; self.left=left; self.right=right\n"
            + "def __to_tree(a):\n    if not a or a[0] is None: return None\n    root=TreeNode(a[0]); q=[root]; i=1\n"
            + "    while q and i<len(a):\n        n=q.pop(0)\n"
            + "        if i<len(a):\n            v=a[i]; i+=1\n            if v is not None: n.left=TreeNode(v); q.append(n.left)\n"
            + "        if i<len(a):\n            v=a[i]; i+=1\n            if v is not None: n.right=TreeNode(v); q.append(n.right)\n    return root\n"
            + "def __from_tree(root):\n    out=[]; q=[root]\n    while q:\n        n=q.pop(0)\n        if n: out.append(n.val); q.append(n.left); q.append(n.right)\n        else: out.append(None)\n"
            + "    while out and out[-1] is None: out.pop()\n    return out\n\n";

    public static final String PY_GRAPH =
            "class Node:\n    def __init__(self, val=0, neighbors=None):\n        self.val=val; self.neighbors=neighbors if neighbors else []\n"
            + "def __to_graph(adj):\n    if not adj: return None\n    nodes=[Node(i+1) for i in range(len(adj))]\n"
            + "    for i,nb in enumerate(adj):\n        nodes[i].neighbors=[nodes[v-1] for v in nb]\n    return nodes[0]\n"
            + "def __from_graph(node):\n    if not node: return []\n    seen={node.val:node}; q=[node]\n"
            + "    while q:\n        n=q.pop(0)\n        for m in n.neighbors:\n            if m.val not in seen: seen[m.val]=m; q.append(m)\n"
            + "    return [sorted(x.val for x in seen[v].neighbors) for v in sorted(seen)]\n\n";

    // ---- C++: serde. ----
    public static final String CPP_LIST =
            "struct ListNode { int val; ListNode* next; ListNode(int x):val(x),next(nullptr){} };\n"
            + "static ListNode* __toList(vector<int> a){ ListNode d(0); ListNode* c=&d; for(int x:a){ c->next=new ListNode(x); c=c->next; } return d.next; }\n"
            + "static string __fromList(ListNode* n){ string s=\"[\"; bool f=true; while(n){ if(!f) s+=\",\"; s+=to_string(n->val); f=false; n=n->next; } s+=\"]\"; return s; }\n\n";

    public static final String CPP_TREE =
            "static const int __NUL = INT_MIN;\n"
            + "struct TreeNode { int val; TreeNode* left; TreeNode* right; TreeNode(int x):val(x),left(nullptr),right(nullptr){} };\n"
            + "static TreeNode* __toTree(vector<int> a){ if(a.empty()||a[0]==__NUL) return nullptr; TreeNode* root=new TreeNode(a[0]); queue<TreeNode*> q; q.push(root); size_t i=1;\n"
            + "  while(!q.empty()&&i<a.size()){ TreeNode* n=q.front(); q.pop();\n"
            + "    if(i<a.size()){ int v=a[i++]; if(v!=__NUL){ n->left=new TreeNode(v); q.push(n->left); } }\n"
            + "    if(i<a.size()){ int v=a[i++]; if(v!=__NUL){ n->right=new TreeNode(v); q.push(n->right); } } }\n"
            + "  return root; }\n"
            + "static string __fromTree(TreeNode* root){ vector<string> out; queue<TreeNode*> q; q.push(root);\n"
            + "  while(!q.empty()){ TreeNode* n=q.front(); q.pop(); if(n){ out.push_back(to_string(n->val)); q.push(n->left); q.push(n->right); } else out.push_back(\"null\"); }\n"
            + "  while(!out.empty()&&out.back()==\"null\") out.pop_back(); string s=\"[\"; for(size_t i=0;i<out.size();++i){ if(i) s+=\",\"; s+=out[i]; } s+=\"]\"; return s; }\n\n";

    public static final String CPP_GRAPH =
            "struct Node { int val; vector<Node*> neighbors; Node(int x):val(x){} };\n"
            + "static Node* __toGraph(vector<vector<int>> adj){ if(adj.empty()) return nullptr; vector<Node*> nodes; for(size_t i=0;i<adj.size();++i) nodes.push_back(new Node(i+1));\n"
            + "  for(size_t i=0;i<adj.size();++i) for(int v:adj[i]) nodes[i]->neighbors.push_back(nodes[v-1]); return nodes[0]; }\n"
            + "static string __fromGraph(Node* node){ if(!node) return \"[]\"; map<int,Node*> seen; queue<Node*> q; q.push(node); seen[node->val]=node;\n"
            + "  while(!q.empty()){ Node* n=q.front(); q.pop(); for(Node* m:n->neighbors) if(!seen.count(m->val)){ seen[m->val]=m; q.push(m); } }\n"
            + "  string s=\"[\"; bool f1=true; for(auto& kv:seen){ if(!f1) s+=\",\"; f1=false; vector<int> vs; for(Node* m:kv.second->neighbors) vs.push_back(m->val); sort(vs.begin(),vs.end());\n"
            + "    s+=\"[\"; for(size_t i=0;i<vs.size();++i){ if(i) s+=\",\"; s+=to_string(vs[i]); } s+=\"]\"; } s+=\"]\"; return s; }\n\n";

    // ---- Java: serde. Split into node-CLASS defs (emitted TOP-LEVEL so the user's Solution
    // signatures can reference them unqualified) and helper FUNCTIONS (emitted as static members
    // inside Main). The client harness nests everything, but it never compiles Java — the server
    // does, so top-level node types are required. ----
    public static final String JAVA_LIST_TYPES =
            "class ListNode { int val; ListNode next; ListNode(int x){ val=x; } }\n";

    public static final String JAVA_LIST_FNS =
            "static ListNode __toList(int[] a){ ListNode d=new ListNode(0), c=d; for(int x:a){ c.next=new ListNode(x); c=c.next; } return d.next; }\n"
            + "static String __fromList(ListNode n){ StringBuilder b=new StringBuilder(\"[\"); boolean f=true; while(n!=null){ if(!f) b.append(\",\"); b.append(n.val); f=false; n=n.next; } b.append(\"]\"); return b.toString(); }\n";

    public static final String JAVA_TREE_TYPES =
            "class TreeNode { int val; TreeNode left, right; TreeNode(int x){ val=x; } }\n";

    public static final String JAVA_TREE_FNS =
            "static TreeNode __toTree(Integer[] a){ if(a.length==0||a[0]==null) return null; TreeNode root=new TreeNode(a[0]); java.util.Queue<TreeNode> q=new java.util.LinkedList<>(); q.add(root); int i=1;\n"
            + "  while(!q.isEmpty()&&i<a.length){ TreeNode n=q.poll();\n"
            + "    if(i<a.length){ Integer v=a[i++]; if(v!=null){ n.left=new TreeNode(v); q.add(n.left); } }\n"
            + "    if(i<a.length){ Integer v=a[i++]; if(v!=null){ n.right=new TreeNode(v); q.add(n.right); } } }\n"
            + "  return root; }\n"
            + "static String __fromTree(TreeNode root){ java.util.List<String> out=new java.util.ArrayList<>(); java.util.Queue<TreeNode> q=new java.util.LinkedList<>(); q.add(root);\n"
            + "  while(!q.isEmpty()){ TreeNode n=q.poll(); if(n!=null){ out.add(String.valueOf(n.val)); q.add(n.left); q.add(n.right); } else out.add(\"null\"); }\n"
            + "  while(!out.isEmpty()&&out.get(out.size()-1).equals(\"null\")) out.remove(out.size()-1); return \"[\"+String.join(\",\",out)+\"]\"; }\n";

    public static final String JAVA_GRAPH_TYPES =
            "class Node { int val; java.util.List<Node> neighbors=new java.util.ArrayList<>(); Node(int x){ val=x; } }\n";

    public static final String JAVA_GRAPH_FNS =
            "static Node __toGraph(int[][] adj){ if(adj.length==0) return null; Node[] nodes=new Node[adj.length]; for(int i=0;i<adj.length;i++) nodes[i]=new Node(i+1);\n"
            + "  for(int i=0;i<adj.length;i++) for(int v:adj[i]) nodes[i].neighbors.add(nodes[v-1]); return nodes[0]; }\n"
            + "static String __fromGraph(Node node){ if(node==null) return \"[]\"; java.util.TreeMap<Integer,Node> seen=new java.util.TreeMap<>(); java.util.Queue<Node> q=new java.util.LinkedList<>(); q.add(node); seen.put(node.val,node);\n"
            + "  while(!q.isEmpty()){ Node n=q.poll(); for(Node m:n.neighbors) if(!seen.containsKey(m.val)){ seen.put(m.val,m); q.add(m); } }\n"
            + "  StringBuilder b=new StringBuilder(\"[\"); boolean f1=true; for(Node n:seen.values()){ if(!f1) b.append(\",\"); f1=false; java.util.List<Integer> vs=new java.util.ArrayList<>(); for(Node m:n.neighbors) vs.add(m.val); java.util.Collections.sort(vs);\n"
            + "    b.append(\"[\"); for(int i=0;i<vs.size();i++){ if(i>0) b.append(\",\"); b.append(vs.get(i)); } b.append(\"]\"); } b.append(\"]\"); return b.toString(); }\n";

    /**
     * Minimal JSON value parser + typed converters for Java. Parses the single stdin line into a
     * {@code List<Object>} of args (each Long/Double/String/Boolean/null/List). Handles the shapes
     * the wire format uses: numbers, strings (no escapes), booleans, null, and arrays (never objects).
     */
    public static final String JAVA_JSON =
            "static int __jp; static String __jsrc;\n"
            + "static void __jws(){ while(__jp<__jsrc.length() && Character.isWhitespace(__jsrc.charAt(__jp))) __jp++; }\n"
            + "static Object __jval(){ __jws(); char c=__jsrc.charAt(__jp);\n"
            + "  if(c=='[') return __jarr();\n"
            + "  if(c=='\"') return __jstr();\n"
            + "  if(c=='t'){ __jp+=4; return Boolean.TRUE; }\n"
            + "  if(c=='f'){ __jp+=5; return Boolean.FALSE; }\n"
            + "  if(c=='n'){ __jp+=4; return null; }\n"
            + "  return __jnum(); }\n"
            + "static java.util.List<Object> __jarr(){ java.util.List<Object> out=new java.util.ArrayList<>(); __jp++; __jws();\n"
            + "  if(__jsrc.charAt(__jp)==']'){ __jp++; return out; }\n"
            + "  while(true){ out.add(__jval()); __jws(); char c=__jsrc.charAt(__jp++); if(c==']') break; }\n"
            + "  return out; }\n"
            + "static String __jstr(){ StringBuilder b=new StringBuilder(); __jp++; while(__jsrc.charAt(__jp)!='\"') b.append(__jsrc.charAt(__jp++)); __jp++; return b.toString(); }\n"
            + "static Object __jnum(){ int s=__jp; while(__jp<__jsrc.length() && \"+-0123456789.eE\".indexOf(__jsrc.charAt(__jp))>=0) __jp++; String n=__jsrc.substring(s,__jp);\n"
            + "  if(n.contains(\".\")||n.contains(\"e\")||n.contains(\"E\")) return Double.parseDouble(n); return Long.parseLong(n); }\n"
            + "@SuppressWarnings(\"unchecked\")\n"
            + "static java.util.List<Object> __readArgs() throws java.io.IOException {\n"
            + "  java.io.BufferedReader __r=new java.io.BufferedReader(new java.io.InputStreamReader(System.in));\n"
            + "  StringBuilder __sb=new StringBuilder(); String __ln; while((__ln=__r.readLine())!=null) __sb.append(__ln);\n"
            + "  __jsrc=__sb.toString(); __jp=0; return (java.util.List<Object>) __jval(); }\n"
            + "static int __asInt(Object o){ return (int)(long)(Long)o; }\n"
            + "static long __asLong(Object o){ return (Long)o; }\n"
            + "static double __asDouble(Object o){ return o instanceof Long ? (double)(long)(Long)o : (Double)o; }\n"
            + "static boolean __asBool(Object o){ return (Boolean)o; }\n"
            + "static String __asStr(Object o){ return (String)o; }\n"
            + "@SuppressWarnings(\"unchecked\")\n"
            + "static int[] __asIntArray(Object o){ java.util.List<Object> l=(java.util.List<Object>)o; int[] a=new int[l.size()]; for(int i=0;i<a.length;i++) a[i]=__asInt(l.get(i)); return a; }\n"
            + "@SuppressWarnings(\"unchecked\")\n"
            + "static double[] __asDoubleArray(Object o){ java.util.List<Object> l=(java.util.List<Object>)o; double[] a=new double[l.size()]; for(int i=0;i<a.length;i++) a[i]=__asDouble(l.get(i)); return a; }\n"
            + "@SuppressWarnings(\"unchecked\")\n"
            + "static String[] __asStrArray(Object o){ java.util.List<Object> l=(java.util.List<Object>)o; String[] a=new String[l.size()]; for(int i=0;i<a.length;i++) a[i]=__asStr(l.get(i)); return a; }\n"
            + "@SuppressWarnings(\"unchecked\")\n"
            + "static Integer[] __asInteger(Object o){ java.util.List<Object> l=(java.util.List<Object>)o; Integer[] a=new Integer[l.size()]; for(int i=0;i<a.length;i++) a[i]=l.get(i)==null?null:__asInt(l.get(i)); return a; }\n"
            + "@SuppressWarnings(\"unchecked\")\n"
            + "static int[][] __asIntMatrix(Object o){ java.util.List<Object> l=(java.util.List<Object>)o; int[][] a=new int[l.size()][]; for(int i=0;i<a.length;i++) a[i]=__asIntArray(l.get(i)); return a; }\n";

    /**
     * Minimal JSON parser + converters for C++. Same contract as {@link #JAVA_JSON}: parse the stdin
     * line into a nested variant tree, then typed accessors. Uses a small tagged struct __J.
     */
    public static final String CPP_JSON =
            "struct __J { int t; double num; string str; bool b; vector<__J> arr; };\n"
            + "static string __JS; static size_t __JP;\n"
            + "static void __jws(){ while(__JP<__JS.size() && isspace((unsigned char)__JS[__JP])) __JP++; }\n"
            + "static __J __jval();\n"
            + "static __J __jarr(){ __J j; j.t=4; __JP++; __jws(); if(__JS[__JP]==']'){__JP++; return j;} while(true){ j.arr.push_back(__jval()); __jws(); char c=__JS[__JP++]; if(c==']') break; } return j; }\n"
            + "static __J __jstr(){ __J j; j.t=2; __JP++; string s; while(__JS[__JP]!='\"') s+=__JS[__JP++]; __JP++; j.str=s; return j; }\n"
            + "static __J __jnum(){ __J j; j.t=1; size_t s=__JP; while(__JP<__JS.size() && string(\"+-0123456789.eE\").find(__JS[__JP])!=string::npos) __JP++; j.num=stod(__JS.substr(s,__JP-s)); return j; }\n"
            + "static __J __jval(){ __jws(); char c=__JS[__JP]; if(c=='[') return __jarr(); if(c=='\"') return __jstr(); if(c=='t'){__JP+=4; __J j; j.t=3; j.b=true; return j;} if(c=='f'){__JP+=5; __J j; j.t=3; j.b=false; return j;} if(c=='n'){__JP+=4; __J j; j.t=0; return j;} return __jnum(); }\n"
            + "static __J __readArgs(){ string line, all; while(getline(cin,line)) all+=line; __JS=all; __JP=0; return __jval(); }\n"
            + "static int __asInt(const __J& j){ return (int)j.num; }\n"
            + "static long long __asLong(const __J& j){ return (long long)j.num; }\n"
            + "static double __asDouble(const __J& j){ return j.num; }\n"
            + "static bool __asBool(const __J& j){ return j.b; }\n"
            + "static string __asStr(const __J& j){ return j.str; }\n"
            + "static vector<int> __asIntVec(const __J& j){ vector<int> v; for(auto& x: j.arr) v.push_back(__asInt(x)); return v; }\n"
            + "static vector<double> __asDoubleVec(const __J& j){ vector<double> v; for(auto& x: j.arr) v.push_back(__asDouble(x)); return v; }\n"
            + "static vector<string> __asStrVec(const __J& j){ vector<string> v; for(auto& x: j.arr) v.push_back(__asStr(x)); return v; }\n"
            + "static vector<int> __asIntVecNullable(const __J& j){ vector<int> v; for(auto& x: j.arr) v.push_back(x.t==0 ? INT_MIN : __asInt(x)); return v; }\n"
            + "static vector<vector<int>> __asIntMat(const __J& j){ vector<vector<int>> v; for(auto& x: j.arr) v.push_back(__asIntVec(x)); return v; }\n";
}
