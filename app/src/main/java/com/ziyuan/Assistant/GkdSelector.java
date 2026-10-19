package com.ziyuan.Assistant;

import java.util.*;
import java.util.regex.Pattern;

/**
 * GKD 选择器引擎 Java 移植 (源: gkd-kit/gkd gkd-selector, Kotlin 1:1)
 * 支持: 关系链 > < << + - -> / n次偏移 / @target / 名字匹配 / 属性过滤
 *       属性表达式: && || ! 比较算子 = != ^= !^= *= !*= $= !$= < <= > >= ~= !~=
 *       值: 字面量/标识符/成员/调用(childCount,text,desc,id,vid,clickable 等 + toInt,toString,indexAt,substring,indexOf,getPrev,getParent,equal...)
 */
public final class GkdSelector {
    private static final String WS = " \t\r\n";
    private static final String DIGITS = "0123456789";
    private static final String ID_START = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_";
    private static final String ID_PART = ID_START + DIGITS;
    private static final String QUOTES = "\"'`";
    private static final String VALUE_PRIMARY_START = "-" + DIGITS + QUOTES + ID_START;
    private static final String VALUE_START = "(" + VALUE_PRIMARY_START;
    private static final String PROP_START = "@[*" + ID_START;
    private static final String HEX = "0123456789abcdefABCDEF";

    // ===== 适配器: 接 AccessibilityNodeInfo =====
    public interface NodeAdapter {
        String getName(Object node);
        Object getAttr(Object node, String name);
        int getChildCount(Object node);
        Object getChild(Object node, int i);
        Object getParent(Object node);
        Object getRoot(Object node);
    }

    // ===== 匹配上下文 =====
    static final class Ctx {
        final Object node; final Ctx prev; final int offset;
        Ctx(Object n, Ctx p, int o) { node = n; prev = p; offset = o; }
    }

    // ===== 顶层 =====
    public static final class Selector {
        final Expr expr;
        Selector(Expr e) { expr = e; }
        /** 返回 @target 命中节点(null=不匹配)。root=窗口根节点 */
        public Object match(Object root, NodeAdapter a) { return expr.eval(new Ctx(root, null, 0), a); }
    }

    // ===== 选择器表达式 =====
    static abstract class Expr { abstract Object eval(Ctx ctx, NodeAdapter a); }
    static final class UnitExpr extends Expr {
        final PropertySelector[] ps; final RelationSelector[] rs; final int targetIdx;
        UnitExpr(PropertySelector[] p, RelationSelector[] r) {
            ps = p; rs = r;
            int t = r.length; // 默认链尾
            for (int k = 0; k < ps.length; k++) if (ps[k].at) { t = k; break; }
            targetIdx = t;
        }
        static Object nodeAt(Ctx c, int k) { for (int i = 0; i < k && c != null; i++) c = c.prev; return c == null ? null : c.node; }
        private static List<Object> preOrder(Object node, NodeAdapter a) {
            List<Object> out = new ArrayList<>();
            ArrayDeque<Object> dq = new ArrayDeque<>();
            dq.push(node);
            while (!dq.isEmpty()) {
                Object x = dq.pop(); out.add(x);
                int cn = a.getChildCount(x);
                for (int i = cn - 1; i >= 0; i--) { Object ch = a.getChild(x, i); if (ch != null) dq.push(ch); }
                if (out.size() > 8000) break;
            }
            return out;
        }
        Object eval(Ctx ctx, NodeAdapter a) {
            // MATCH_UNIT 语义: 自身+全部后代逐一作为链尾候选, 前序首个命中
            for (Object nd : preOrder(ctx.node, a)) {
                Object r = matchFrom(new Ctx(nd, null, -1), a);
                if (r != null) return r;
            }
            return null;
        }

        private Object matchFrom(Ctx ctx, NodeAdapter a) {
            int n = ps.length;
            if (n == 1) return ps[0].match(ctx, a) ? nodeAt(ctx, targetIdx) : null;
            int idx = n - 1;
            Ctx cur = ctx;
            List<Object[]> stack = new ArrayList<>();
            while (true) {
                if (ps[idx].match(cur, a)) {
                    if (idx == 0) return nodeAt(cur, targetIdx);
                    final int fi = idx - 1;
                    stack.add(new Object[]{Integer.valueOf(idx - 1), cur,
                            new CandidateIter(rs[idx - 1], cur, a)});
                }
                boolean advanced = false;
                for (int i = stack.size() - 1; i >= 0; i--) {
                    Object[] fr = stack.get(i);
                    CandidateIter it = (CandidateIter) fr[2];
                    Candidate c = it.next();
                    if (c != null) {
                        cur = new Ctx(c.node, (Ctx) fr[1], c.offset);
                        idx = (Integer) fr[0];
                        advanced = true; break;
                    }
                    stack.remove(i);
                }
                if (!advanced) return null;
            }
        }
    }
    static final class NotExpr extends Expr {
        final Expr e; NotExpr(Expr x) { e = x; }
        Object eval(Ctx ctx, NodeAdapter a) { return e.eval(ctx, a) == null ? ctx.node : null; }
    }
    static final class LogExpr extends Expr {
        final boolean isAnd; final Expr l, r;
        LogExpr(boolean and, Expr x, Expr y) { isAnd = and; l = x; r = y; }
        Object eval(Ctx ctx, NodeAdapter a) {
            if (isAnd) { Object rv = r.eval(ctx, a); Object lv = l.eval(ctx, a); return (lv != null && rv != null) ? lv : null; }
            Object lv = l.eval(ctx, a);
            return lv != null ? lv : r.eval(ctx, a);
        }
    }

    // ===== 关系遍历 =====
    static final class Candidate { final Object node; final int offset; Candidate(Object n, int o) { node = n; offset = o; } }
    static final class CandidateIter {
        // ops: 0=+ 1=- 2=> 3=< 4=<< 5=->
        final int op; final RelExpr rel; final Ctx ctx; final NodeAdapter a;
        int st = 0; Object walker; int offset; int childIdx; Ctx prevWalk;
        CandidateIter(RelationSelector rs, Ctx c, NodeAdapter ad) { op = rs.op; rel = rs.rel; ctx = c; a = ad; init(); }
        private void init() {
            offset = rel.minOffset;
            switch (op) {
                case 0: case 1: {
                    Object p = a.getParent(ctx.node);
                    if (p == null) { st = -1; return; }
                    int cnt = a.getChildCount(p), myIdx = -1;
                    for (int i = 0; i < cnt; i++) if (a.getChild(p, i) == ctx.node) { myIdx = i; break; }
                    if (myIdx < 0) { st = -1; return; }
                    st = 0; walker = p; childIdx = myIdx; return;
                }
                case 2: { st = 0; walker = a.getParent(ctx.node); return; }
                case 3: { st = 0; walker = ctx.node; childIdx = 0; return; }
                case 4: { st = 0; walker = ctx.node; childIdx = 0; return; }
                case 5: {
                    Ctx p = ctx.prev; int o = rel.minOffset;
                    for (int i = 0; i < rel.minOffset && p != null; i++) p = p.prev;
                    prevWalk = p; offset = rel.minOffset; st = 0; return;
                }
                default: st = -1;
            }
        }
        Candidate next() {
            while (st >= 0) {
                switch (op) {
                    case 0: { // + 前面兄弟
                        if (childIdx <= 0) { st = -1; return null; }
                        int o = childIdx - 1 - 0; // 前一个兄弟 offset 0
                        int candIdx = childIdx - offset - 1;
                        if (rel.maxOffset != null && offset > rel.maxOffset) { st = -1; return null; }
                        if (candIdx < 0) { st = -1; return null; }
                        Object n = a.getChild(walker, candIdx);
                        offset++;
                        if (rel.checkOffset(offset - 1)) { if (n != null) return new Candidate(n, offset - 1); }
                        continue;
                    }
                    case 1: { // - 后面兄弟
                        int cnt = a.getChildCount(walker);
                        int candIdx = childIdx + offset + 1;
                        if (rel.maxOffset != null && offset > rel.maxOffset || candIdx >= cnt) { st = -1; return null; }
                        Object n = a.getChild(walker, candIdx);
                        offset++;
                        if (rel.checkOffset(offset - 1)) { if (n != null) return new Candidate(n, offset - 1); }
                        continue;
                    }
                    case 2: { // > 祖先
                        if (walker == null || rel.maxOffset != null && offset > rel.maxOffset) { st = -1; return null; }
                        Object n = walker;
                        walker = a.getParent(n);
                        offset++;
                        if (rel.checkOffset(offset - 1)) { if (n != null) return new Candidate(n, offset - 1); }
                        continue;
                    }
                    case 3: { // < 子
                        int cnt = a.getChildCount(walker);
                        if (childIdx >= cnt || rel.maxOffset != null && offset > rel.maxOffset) { st = -1; return null; }
                        Object n = a.getChild(walker, childIdx);
                        childIdx++; offset++;
                        if (rel.checkOffset(offset - 1)) { if (n != null) return new Candidate(n, offset - 1); }
                        continue;
                    }
                    case 4: { // << 后代 前序
                        Object n = nextDesc();
                        if (n == null || rel.maxOffset != null && offset > rel.maxOffset) { st = -1; return null; }
                        offset++;
                        if (rel.checkOffset(offset - 1)) return new Candidate(n, offset - 1);
                        continue;
                    }
                    case 5: { // -> 上文
                        if (prevWalk == null) { st = -1; return null; }
                        Ctx c = prevWalk;
                        if (rel.checkOffset(offset)) return new Candidate(c.node, offset);
                        prevWalk = c.prev; offset++;
                        if (rel.maxOffset != null && offset > rel.maxOffset) { st = -1; return null; }
                        continue;
                    }
                }
            }
            return null;
        }
        private final List<Object[]> descStack = new ArrayList<>();
        private Object nextDesc() {
            if (st == 0) {
                st = 1;
                int cnt = a.getChildCount(walker);
                for (int i = cnt - 1; i >= 0; i--) { Object c = a.getChild(walker, i); if (c != null) descStack.add(new Object[]{c, Integer.valueOf(0)}); }
            }
            if (descStack.isEmpty()) return null;
            Object[] top = descStack.remove(descStack.size() - 1);
            Object n = top[0];
            int cnt = a.getChildCount(n);
            for (int i = cnt - 1; i >= 0; i--) { Object c = a.getChild(n, i); if (c != null) descStack.add(new Object[]{c, Integer.valueOf(0)}); }
            return n;
        }
    }

    // ===== 关系表达式 =====
    static final class RelationSelector { final int op; final RelExpr rel;
        RelationSelector(int o, RelExpr r) { op = o; rel = r; } }
    static abstract class RelExpr {
        int minOffset; Integer maxOffset;
        int minOffset() { return minOffset; }
        Integer maxOffset() { return maxOffset; }
        abstract boolean checkOffset(int offset);
        boolean matchesAllOffsets() { return false; }
    }
    static final class PolyExpr extends RelExpr {
        final int a, b;
        PolyExpr(int a, int b) {
            this.a = a; this.b = b;
            int mn;
            if (a > 0 && b > 0) mn = a + b;
            else if (a > 0 && b == 0) mn = a;
            else if (a > 0) { int minN = -b / a + 1; mn = a * minN + b; }
            else if (a == 0) mn = b;
            else { int maxN = -b / a - (b % a == 0 ? 1 : 0); mn = a * maxN + b; }
            minOffset = mn - 1;
            Integer mx;
            if (a > 0) mx = null; else if (a == 0) mx = b; else mx = a + b;
            maxOffset = mx == null ? null : mx - 1;
        }
        boolean matchesAllOffsets() { return a == 1 && b == 0; }
        boolean checkOffset(int offset) {
            if (minOffset == maxOffset && maxOffset != null) return offset == minOffset;
            int y = offset + 1 - b;
            return y % a == 0 && y / a >= 1;
        }
    }
    static final class TupleExpr extends RelExpr {
        final int[] idx;
        TupleExpr(List<Integer> nums) {
            idx = new int[nums.size()];
            for (int i = 0; i < nums.size(); i++) idx[i] = nums.get(i) - 1;
            minOffset = (nums.isEmpty() ? 1 : nums.get(0)) - 1;
            maxOffset = nums.isEmpty() ? null : nums.get(nums.size() - 1) - 1;
        }
        boolean checkOffset(int offset) { for (int v : idx) if (v == offset) return true; return false; }
    }

    // ===== 属性选择器 =====
    static final class PropertySelector {
        final boolean at; final String name; final PropExpr[] filters;
        final boolean matchAnyName;
        PropertySelector(boolean at, String name, PropExpr[] f) {
            this.at = at; this.name = name; filters = f;
            matchAnyName = name.isEmpty() || name.equals("*");
        }
        boolean matchName(Object node, NodeAdapter a) {
            if (matchAnyName) return true;
            String s = a.getName(node);
            if (s == null) return false;
            if (s.length() == name.length()) return s.equals(name);
            if (s.length() > name.length()) return s.charAt(s.length() - name.length() - 1) == '.' && s.endsWith(name);
            return false;
        }
        boolean match(Ctx ctx, NodeAdapter a) {
            if (!matchName(ctx.node, a)) return false;
            for (PropExpr e : filters) if (!e.eval(ctx, a)) return false;
            return true;
        }
    }

    // ===== 属性表达式 =====
    static abstract class PropExpr { abstract boolean eval(Ctx ctx, NodeAdapter a); }
    static final class Comparison extends PropExpr {
        final Value left, right; final String op; Pattern regex; boolean expectedMatch = true;
        Comparison(Value l, String o, Value r) { left = l; op = o; right = r; }
        boolean eval(Ctx ctx, NodeAdapter a) {
            if (op.equals("~=") || op.equals("!~=")) {
                Object v = left.eval(ctx, a);
                if (!(v instanceof CharSequence)) return op.startsWith("!");
                boolean m = regex != null && regex.matcher(String.valueOf(v)).find();
                return m == (op.equals("~="));
            }
            Object l = left.eval(ctx, a), r = right.eval(ctx, a);
            switch (op) {
                case "=": return cmpEq(l, r);
                case "!=": return !cmpEq(l, r);
                case "^=": return str(l, r, false, true, true);
                case "!^=": return !str(l, r, false, true, true);
                case "*=": return str(l, r, false, false, true);
                case "!*=": return !str(l, r, false, false, true);
                case "$=": return str(l, r, true, false, true);
                case "!$=": return !str(l, r, true, false, true);
                case "<": return intCmp(l, r) < 0;
                case "<=": return intCmp(l, r) <= 0;
                case ">": return intCmp(l, r) > 0;
                case ">=": return intCmp(l, r) >= 0;
                default: return false;
            }
        }
        private static boolean cmpEq(Object l, Object r) {
            if (l instanceof Number && r instanceof Number) return ((Number) l).intValue() == ((Number) r).intValue();
            if (l == null || r == null) return l == r;
            return String.valueOf(l).contentEquals(String.valueOf(r));
        }
        private static boolean str(Object l, Object r, boolean end, boolean start, boolean contains) {
            if (!(l instanceof CharSequence) || !(r instanceof CharSequence)) return false;
            String s = l.toString(), v = r.toString();
            if (end) return s.endsWith(v);
            if (start) return s.startsWith(v);
            return s.contains(v);
        }
        private static int intCmp(Object l, Object r) {
            int a = l instanceof Number ? ((Number) l).intValue() : 0;
            int b = r instanceof Number ? ((Number) r).intValue() : 0;
            return Integer.compare(a, b);
        }
    }
    static final class LogicalP extends PropExpr {
        final boolean isAnd; final PropExpr l, r;
        LogicalP(boolean and, PropExpr x, PropExpr y) { isAnd = and; l = x; r = y; }
        boolean eval(Ctx ctx, NodeAdapter a) { return isAnd ? l.eval(ctx, a) && r.eval(ctx, a) : l.eval(ctx, a) || r.eval(ctx, a); }
    }
    static final class NotP extends PropExpr {
        final PropExpr e; NotP(PropExpr x) { e = x; }
        boolean eval(Ctx ctx, NodeAdapter a) { return !e.eval(ctx, a); }
    }

    // ===== 值表达式 =====
    static abstract class Value { abstract Object eval(Ctx ctx, NodeAdapter a); }
    static final class IntV extends Value { final int v; IntV(int x) { v = x; } Object eval(Ctx c, NodeAdapter a) { return v; } }
    static final class StrV extends Value { final String v; StrV(String x) { v = x; } Object eval(Ctx c, NodeAdapter a) { return v; } }
    static final class BoolV extends Value { final boolean v; BoolV(boolean x) { v = x; } Object eval(Ctx c, NodeAdapter a) { return v; } }
    static final class NullV extends Value { Object eval(Ctx c, NodeAdapter a) { return null; } }
    static final class IdentV extends Value { final String name; IdentV(String n) { name = n; }
        Object eval(Ctx ctx, NodeAdapter a) {
            if (name.equals("current")) return ctx.node;
            if (name.equals("prev")) return ctx.prev;
            return a.getAttr(ctx.node, name);
        }
    }
    static final class MemberV extends Value { final Value obj; final String prop; MemberV(Value o, String p) { obj = o; prop = p; }
        Object eval(Ctx ctx, NodeAdapter a) {
            Object recv = obj.eval(ctx, a);
            if (recv == null) return null;
            if (recv instanceof Ctx) {
                Ctx c = (Ctx) recv;
                if (prop.equals("prev")) return c.prev;
                if (prop.equals("current")) return c.node;
                return a.getAttr(c.node, prop);
            }
            if (recv instanceof CharSequence && prop.equals("length")) return ((CharSequence) recv).length();
            return null;
        }
    }
    static final class CallV extends Value { final Value callee; final List<Value> args; CallV(Value c, List<Value> x) { callee = c; args = x; }
        Object eval(Ctx ctx, NodeAdapter a) {
            String name;
            if (callee instanceof IdentV) name = ((IdentV) callee).name;
            else if (callee instanceof MemberV) name = ((MemberV) callee).prop;
            else return null;
            Object recv = null;
            if (callee instanceof MemberV) {
                recv = ((MemberV) callee).obj.eval(ctx, a);
                if (recv == null) return null;
            }
            Object[] av = new Object[args.size()];
            for (int i = 0; i < av.length; i++) {
                av[i] = args.get(i).eval(ctx, a);
                if (av[i] instanceof Ctx) av[i] = ((Ctx) av[i]).node;
            }
            if (recv instanceof Ctx) recv = ((Ctx) recv).node;
            return invoke(ctx, recv, name, av, a);
        }
    }
    private static Object invoke(Ctx ctx, Object recv, String name, Object[] args, NodeAdapter a) {
        if (recv == null) {
            if (name.equals("getPrev")) {
                int i = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue() : 1;
                Ctx p = ctx;
                for (int k = 0; k < i && p != null; k++) p = p.prev;
                return p;
            }
            if (name.equals("getParent")) return ctx.prev != null ? ctx.prev : null;
            return null;
        }
        try {
            switch (name) {
                case "equal": return args.length == 2 ? cmpEqS(args[0], args[1]) : null;
                case "notEqual": return args.length == 2 ? !cmpEqS(args[0], args[1]) : null;
                case "toInt": return Integer.parseInt(String.valueOf(recv).trim());
                case "toString": return String.valueOf(recv);
                case "or": return (recv instanceof Boolean && (Boolean) recv) || (args[0] instanceof Boolean && (Boolean) args[0]);
                case "and": return (recv instanceof Boolean && (Boolean) recv) && (args[0] instanceof Boolean && (Boolean) args[0]);
                case "not": return !(Boolean) recv;
                case "ifElse": return (Boolean) recv ? args[0] : args[1];
                case "more": return (Integer) recv > (Integer) args[0];
                case "moreEqual": return (Integer) recv >= (Integer) args[0];
                case "less": return (Integer) recv < (Integer) args[0];
                case "lessEqual": return (Integer) recv <= (Integer) args[0];
                case "plus": case "+": return (Integer) recv + (Integer) args[0];
                case "minus": case "-": return (Integer) recv - (Integer) args[0];
                case "times": case "*": return (Integer) recv * (Integer) args[0];
                case "div": case "/": return (Integer) args[0] == 0 ? null : (Integer) recv / (Integer) args[0];
                case "rem": case "%": return (Integer) args[0] == 0 ? null : (Integer) recv % (Integer) args[0];
                case "get": { String s = String.valueOf(recv); int i = (Integer) args[0]; return i >= 0 && i < s.length() ? String.valueOf(s.charAt(i)) : null; }
                case "at": { String s = String.valueOf(recv); int i = (Integer) args[0]; return i >= 0 && i < s.length() ? String.valueOf(s.charAt(i)) : null; }
                case "substring": { String s = String.valueOf(recv);
                    if (args.length == 1) return s.substring(Math.min((Integer) args[0], s.length()));
                    int e = (Integer) args[1]; return s.substring(Math.min((Integer) args[0], s.length()), Math.min(Math.max(e, 0), s.length())); }
                case "indexOf": return String.valueOf(recv).indexOf(String.valueOf(args[0]));
                case "matches": return String.valueOf(recv).matches(String.valueOf(args[0]));
            }
        } catch (Throwable ignored) { return null; }
        return null;
    }
    private static boolean cmpEqS(Object l, Object r) {
        if (l instanceof Number && r instanceof Number) return ((Number) l).intValue() == ((Number) r).intValue();
        if (l == null || r == null) return l == r;
        return String.valueOf(l).equals(String.valueOf(r));
    }

    // ===== 解析器 =====
    static final class Cur {
        final String s; int i;
        Cur(String src) { s = src; i = 0; }
        Character cur() { return i < s.length() ? s.charAt(i) : null; }
        boolean isOneOf(String cs) { Character c = cur(); return c != null && cs.indexOf(c) >= 0; }
        void ws() { while (isOneOf(WS)) i++; }
        void expect(String cs, String msg) { if (!isOneOf(cs)) throw new IllegalArgumentException(msg + " @" + i); }
        int readUnsignedInt() {
            int st = i; expect(DIGITS, "digit");
            if (s.charAt(i) == '0') { i++; return 0; }
            while (isOneOf(DIGITS)) i++;
            return Integer.parseInt(s.substring(st, i));
        }
        int readInt() {
            int st = i; if (cur() != null && s.charAt(i) == '-') i++;
            expect(DIGITS, "digit");
            if (s.charAt(i) == '0') { i++; return 0; }
            while (isOneOf(DIGITS)) i++;
            return Integer.parseInt(s.substring(st, i));
        }
        String readString() {
            expect(QUOTES, "string");
            char q = s.charAt(i); i++;
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == q) { i++; return b.toString(); }
                if (c == '\\') {
                    i++;
                    if (i >= s.length()) break;
                    char e = s.charAt(i);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 'r': b.append('\r'); break;
                        case 't': b.append('\t'); break;
                        case 'b': b.append('\b'); break;
                        case 'x': case 'u': {
                            int len = e == 'x' ? 2 : 4; int ds = i + 1;
                            for (int k = 0; k < len; k++) if (ds + k >= s.length() || HEX.indexOf(s.charAt(ds + k)) < 0) throw new IllegalArgumentException("hex");
                            b.append((char) Integer.parseInt(s.substring(ds, ds + len), 16));
                            i = ds + len - 1; break;
                        }
                        default: b.append(e);
                    }
                    i++; continue;
                }
                b.append(c); i++;
            }
            throw new IllegalArgumentException("unterminated string @" + i);
        }
    }

    public static Selector parse(String src) {
        Cur c = new Cur(src);
        Expr e = parseSelExpr(c);
        c.ws();
        if (c.i < c.s.length()) throw new IllegalArgumentException("多余字符 @" + c.i);
        return new Selector(e);
    }

    // selector := orExpr
    private static Expr parseSelExpr(Cur c) {
        c.ws();
        List<Expr> vals = new ArrayList<>(); List<Boolean> ops = new ArrayList<>(); // true=&&
        vals.add(parseSelTerm(c));
        while (true) {
            int save = c.i; c.ws();
            if (c.i + 1 < c.s.length() && c.s.startsWith("&&", c.i)) { c.i += 2; ops.add(true); }
            else if (c.i + 1 < c.s.length() && c.s.startsWith("||", c.i)) { c.i += 2; ops.add(false); }
            else { c.i = save; break; }
            c.ws();
            vals.add(parseSelTerm(c));
        }
        // && 优先
        while (ops.contains(true)) {
            int ix = ops.indexOf(true);
            vals.set(ix, new LogExpr(true, vals.remove(ix), vals.remove(ix + 1)));
            ops.remove(ix);
        }
        Expr out = vals.get(0);
        for (int i = 1; i < vals.size(); i++) out = new LogExpr(false, out, vals.get(i));
        return out;
    }
    private static Expr parseSelTerm(Cur c) {
        c.ws();
        if (c.cur() != null && c.s.charAt(c.i) == '!') {
            c.i++; return new NotExpr(parseSelTerm(c));
        }
        if (c.cur() != null && c.s.charAt(c.i) == '(') {
            c.i++; Expr e = parseSelExpr(c); c.ws(); c.expect(")", "')'");
            c.i++; return e;
        }
        return parseUnit(c);
    }
    private static Expr parseUnit(Cur c) {
        c.ws();
        List<PropertySelector> ps = new ArrayList<>();
        List<RelationSelector> rs = new ArrayList<>();
        ps.add(parsePropSel(c));
        while (true) {
            int save = c.i; c.ws();
            Character ch = c.cur();
            if (ch == null) break;
            if (c.s.startsWith("->", c.i)) {
                RelationSelector r = parseRelSel(c, 5); rs.add(r); ps.add(parsePropSel(c)); continue;
            }
            if (ch == '+' || ch == '-') { RelationSelector r = parseRelSel(c, ch == '+' ? 0 : 1); rs.add(r); ps.add(parsePropSel(c)); continue; }
            if (ch == '>') { RelationSelector r = parseRelSel(c, 2); rs.add(r); ps.add(parsePropSel(c)); continue; }
            if (c.s.startsWith("<<", c.i)) { RelationSelector r = parseRelSel(c, 4); rs.add(r); ps.add(parsePropSel(c)); continue; }
            if (ch == '<') { RelationSelector r = parseRelSel(c, 3); rs.add(r); ps.add(parsePropSel(c)); continue; }
            if (c.isOneOf(PROP_START)) { rs.add(new RelationSelector(2, new PolyExpr(1, 0))); ps.add(parsePropSel(c)); continue; }
            c.i = save; break;
        }
        return new UnitExpr(ps.toArray(new PropertySelector[0]), rs.toArray(new RelationSelector[0]));
    }
    private static RelationSelector parseRelSel(Cur c, int op) {
        if (op == 5 || op == 4) c.i += 2;
        else c.i += 1;
        RelExpr rel = new PolyExpr(0, 1);
        c.ws();
        if (c.isOneOf("(n" + DIGITS)) rel = parsePolyOrTuple(c);
        c.ws();
        return new RelationSelector(op, rel);
    }
    private static RelExpr parsePolyOrTuple(Cur c) {
        if (c.cur() != null && c.s.charAt(c.i) == '(') {
            c.i++; c.ws();
            List<Integer> nums = new ArrayList<>();
            int a2 = 0, b2 = 0;
            int cnt = 0;
            while (true) {
                int sg = 1;
                if (c.cur() != null && (c.s.charAt(c.i) == '-' || c.s.charAt(c.i) == '+')) { if (c.s.charAt(c.i) == '-') sg = -1; c.i++; c.ws(); }
                int co = 1; boolean hasN = false;
                if (c.isOneOf(DIGITS)) { co = c.readUnsignedInt(); }
                if (c.cur() != null && c.s.charAt(c.i) == 'n') { c.i++; hasN = true; }
                if (hasN) { a2 += sg * co; } else { b2 += sg * co; }
                cnt++;
                c.ws();
                if (c.cur() != null && (c.s.charAt(c.i) == '+' || c.s.charAt(c.i) == '-')) continue;
                if (c.cur() != null && c.s.charAt(c.i) == ',') { c.i++; c.ws(); nums.add(cnt > 0 && !hasN ? b2 : cnt); b2 = 0; cnt = 0; continue; }
                break;
            }
            if (c.cur() != null && c.s.charAt(c.i) == ')') { c.i++; return new PolyExpr(a2, b2); }
            // 元组 (4,5)
            return new TupleExpr(nums);
        }
        if (c.cur() != null && c.s.charAt(c.i) == 'n') { c.i++; return new PolyExpr(1, 0); }
        int n1 = c.readUnsignedInt();
        if (c.cur() != null && c.s.charAt(c.i) == 'n') { c.i++; return new PolyExpr(n1, 0); }
        return new TupleExpr(Arrays.asList(n1));
    }
    private static PropertySelector parsePropSel(Cur c) {
        c.ws();
        boolean at = false;
        if (c.cur() != null && c.s.charAt(c.i) == '@') { at = true; c.i++; }
        String name = "";
        if (c.cur() != null && c.s.charAt(c.i) == '*') { c.i++; name = "*"; }
        else if (c.isOneOf(ID_START)) {
            int st = c.i;
            while (true) {
                if (c.cur() != null && c.s.charAt(c.i) == '.') { c.i++; c.expect(ID_START, "segment"); c.i++; }
                else if (c.isOneOf(ID_PART)) c.i++;
                else break;
            }
            name = c.s.substring(st, c.i);
        }
        List<PropExpr> fs = new ArrayList<>();
        while (c.cur() != null && c.s.charAt(c.i) == '[') fs.add(parseFilter(c));
        return new PropertySelector(at, name, fs.toArray(new PropExpr[0]));
    }
    private static PropExpr parseFilter(Cur c) {
        c.expect("[", "["); c.i++; c.ws();
        PropExpr e = parsePropOr(c);
        c.ws(); c.expect("]", "]'"); c.i++;
        return e;
    }
    private static PropExpr parsePropOr(Cur c) {
        List<PropExpr> vs = new ArrayList<>(); List<Boolean> ops = new ArrayList<>();
        vs.add(parsePropExpr(c));
        while (true) {
            int save = c.i; c.ws();
            if (c.s.startsWith("&&", c.i)) { c.i += 2; ops.add(true); }
            else if (c.s.startsWith("||", c.i)) { c.i += 2; ops.add(false); }
            else { c.i = save; break; }
            c.ws(); vs.add(parsePropExpr(c));
        }
        while (ops.contains(true)) {
            int ix = ops.indexOf(true);
            PropExpr rgt = vs.remove(ix + 1), lft = vs.remove(ix);
            vs.add(ix, new LogicalP(true, lft, rgt)); ops.remove(ix);
        }
        PropExpr out = vs.get(0);
        for (int i = 1; i < vs.size(); i++) out = new LogicalP(false, out, vs.get(i));
        return out;
    }
    private static PropExpr parsePropExpr(Cur c) {
        c.ws();
        boolean neg = false;
        while (c.cur() != null && c.s.charAt(c.i) == '!') { neg = !neg; c.i++; c.ws(); }
        PropExpr base;
        if (c.cur() != null && c.s.charAt(c.i) == '(') {
            c.i++; c.ws(); base = parsePropExpr(c); c.ws(); c.expect(")", "')'"); c.i++;
        } else {
            Value l = parseValue(c);
            c.ws();
            String op = readCompareOp(c);
            c.ws();
            Value r = parseValue(c);
            Comparison cmp = new Comparison(l, op, r);
            if (op.equals("~=") || op.equals("!~=")) {
                if (!(r instanceof StrV)) throw new IllegalArgumentException("正则必须是字符串 @" + c.i);
                String sv = ((StrV) r).v;
                int flags = 0;
                if (sv.startsWith("/")) {
                    int le = sv.lastIndexOf('/');
                    if (le > 0) {
                        String fl = sv.substring(le + 1), body = sv.substring(1, le);
                        if (fl.contains("i")) flags |= Pattern.CASE_INSENSITIVE;
                        cmp.regex = Pattern.compile(body, flags);
                    } else cmp.regex = Pattern.compile(sv, flags);
                } else cmp.regex = Pattern.compile(sv, flags);
                cmp.expectedMatch = op.equals("~=");
            }
            base = cmp;
        }
        return neg ? new NotP(base) : base;
    }
    private static final String[] CMP_OPS = {"!^=", "!*=", "!$=", "!~=", "^=", "*=", "$=", "~=", "!=", "<=", ">=", "=", "<", ">"};
    private static String readCompareOp(Cur c) {
        for (String o : CMP_OPS) if (c.s.startsWith(o, c.i)) { c.i += o.length(); return o; }
        throw new IllegalArgumentException("缺比较算子 @" + c.i);
    }
    private static Value parseValue(Cur c) {
        c.ws();
        c.expect(VALUE_PRIMARY_START, "value");
        char ch = c.s.charAt(c.i);
        if (ch == 'n' && c.s.startsWith("null", c.i)) { c.i += 4; return new NullV(); }
        if (ch == 'f' && c.s.startsWith("false", c.i)) { c.i += 5; return new BoolV(false); }
        if (ch == 't' && c.s.startsWith("true", c.i)) { c.i += 4; return new BoolV(true); }
        if (ch == '-' || c.isOneOf(DIGITS)) return new IntV(c.readInt());
        if (c.isOneOf(QUOTES)) return new StrV(c.readString());
        // 标识符/成员/调用
        Value v = new IdentV(readIdent(c));
        return parseValueSuffix(c, v);
    }
    private static Value parseValueSuffix(Cur c, Value v) {
        while (true) {
            int save = c.i; c.ws();
            Character ch = c.cur();
            if (ch != null && ch == '.') {
                c.i++; c.ws(); String p = readIdent(c);
                v = new MemberV(v, p);
                // 可能继续调用: 在 parseValueSuffix 下一轮处理 '(' — 但 call 需要整体; 处理 '(' 直接跟在成员后
                int s2 = c.i; c.ws();
                if (c.cur() != null && c.s.charAt(c.i) == '(') {
                    c.i++; c.ws();
                    List<Value> args = new ArrayList<>();
                    if (c.cur() != null && c.s.charAt(c.i) == ')') { c.i++; v = new CallV(v, args); continue; }
                    while (true) {
                        args.add(parseValue(c)); c.ws();
                        if (c.cur() != null && c.s.charAt(c.i) == ',') { c.i++; c.ws(); continue; }
                        if (c.cur() != null && c.s.charAt(c.i) == ')') { c.i++; break; }
                        throw new IllegalArgumentException("',' 或 ')' @" + c.i);
                    }
                    v = new CallV(v, args);
                } else c.i = s2;
                continue;
            }
            if (ch != null && ch == '(' && v instanceof IdentV) {
                c.i++; c.ws();
                List<Value> args = new ArrayList<>();
                if (c.cur() != null && c.s.charAt(c.i) == ')') { c.i++; return new CallV(v, args); }
                while (true) {
                    args.add(parseValue(c)); c.ws();
                    if (c.cur() != null && c.s.charAt(c.i) == ',') { c.i++; c.ws(); continue; }
                    if (c.cur() != null && c.s.charAt(c.i) == ')') { c.i++; break; }
                    throw new IllegalArgumentException("',' 或 ')' @" + c.i);
                }
                return new CallV(v, args);
            }
            c.i = save; return v;
        }
    }
    private static String readIdent(Cur c) {
        c.expect(ID_START, "identifier");
        int st = c.i;
        c.i++;
        while (c.isOneOf(ID_PART)) c.i++;
        return c.s.substring(st, c.i);
    }
}
