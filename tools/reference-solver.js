'use strict';
/*
 * Independent reference solver — the source of the expected solution counts in
 * app/src/test/java/com/a24solver/wakusei/solver/SolverTest.kt.
 *
 * This is a deliberately separate, throwaway implementation: it mirrors the search the *previous*
 * Kotlin solver ran (every binary tree shape x every operator assignment x every permutation of the
 * input positions, exact BigInt rationals, fully parenthesised printing), and then re-groups the
 * answers with a small hand-written parser that normalises the commutativity and associativity of
 * `+` and `*`. It shares no code at all with the app.
 *
 * Why it is worth keeping: the counts it prints are the ones the current solver is asserted
 * against, so a reader can re-derive them instead of trusting a comment. The two implementations
 * disagreeing would be the interesting signal — and what this script actually produced is that
 * 1,2,3,4 -> 24 has 242 distinct expressions but only 28 that are essentially different.
 *
 * Run it with Node (no dependencies):   node tools/reference-solver.js
 *
 * Note on printing: this script prints with full parentheses, while the app prints with the minimum
 * that keeps an expression unambiguous (README §2.3). The number of *distinct* strings is the same
 * either way — minimal-parenthesis printing is injective on trees — and the Kotlin differential
 * test against BruteForceOracle compares the actual strings.
 */

const gcd = (a, b) => { a = a < 0n ? -a : a; b = b < 0n ? -b : b; while (b) { const t = b; b = a % b; a = t; } return a; };

class F {
    constructor(n, d = 1n) {
        if (d === 0n) throw new Error('zero denominator');
        if (n === 0n) { this.n = 0n; this.d = 1n; return; }
        if (d < 0n) { n = -n; d = -d; }
        const g = gcd(n, d);
        this.n = n / g; this.d = d / g;
    }
    add(o) { return new F(this.n * o.d + o.n * this.d, this.d * o.d); }
    sub(o) { return new F(this.n * o.d - o.n * this.d, this.d * o.d); }
    mul(o) { return new F(this.n * o.n, this.d * o.d); }
    div(o) { return o.n === 0n ? null : new F(this.n * o.d, this.d * o.n); }
    eq(o) { return this.n === o.n && this.d === o.d; }
    toString() { return this.d === 1n ? this.n.toString() : `${this.n}/${this.d}`; }
}

function* genTrees(size, offset = 0, offset2 = 0) {
    if (size === 1) { yield { leaf: true, leafIdx: offset }; return; }
    for (let leftSize = 1; leftSize < size; leftSize++) {
        const rightSize = size - leftSize;
        for (const lt of genTrees(leftSize, offset, offset2 + 1)) {
            for (const rt of genTrees(rightSize, offset + leftSize, offset2 + leftSize)) {
                yield { leaf: false, signIdx: offset2, left: lt, right: rt };
            }
        }
    }
}

function resolve(root, signs, perm, numbers) {
    if (root.leaf) return numbers[perm[root.leafIdx]];
    const lv = resolve(root.left, signs, perm, numbers);
    const rv = resolve(root.right, signs, perm, numbers);
    if (lv === null || rv === null) return null;
    switch (signs[root.signIdx] % 4) {
        case 0: return lv.add(rv);
        case 1: return lv.sub(rv);
        case 2: return lv.mul(rv);
        default: return lv.div(rv);
    }
}

function myPrint(root, signs, perm, numbers) {
    if (root.leaf) return numbers[perm[root.leafIdx]].toString();
    const l = myPrint(root.left, signs, perm, numbers);
    const r = myPrint(root.right, signs, perm, numbers);
    return `(${l} ${'+-*/'[signs[root.signIdx] % 4]} ${r})`;
}

function* signVectors(size) {
    const total = 4 ** size;
    for (let i = 0; i < total; i++) {
        const v = new Array(size); let x = i;
        for (let k = 0; k < size; k++) { v[k] = x % 4; x = (x / 4) | 0; }
        yield v;
    }
}

function* permutations(size) {
    const bucket = new Array(size), vis = new Array(size).fill(false);
    function* bt(cur) {
        if (cur === size) { yield bucket.slice(); return; }
        for (let num = 0; num < size; num++) {
            if (vis[num]) continue;
            vis[num] = true; bucket[cur] = num;
            yield* bt(cur + 1);
            vis[num] = false;
        }
    }
    yield* bt(0);
}

function allSolutions(target, numbers) {
    const n = numbers.length;
    const nums = numbers.map(v => new F(BigInt(v)));
    const tgt = new F(BigInt(target));
    const out = new Set();
    let loops = 0;
    for (const root of genTrees(n)) {
        for (const signs of signVectors(n)) {
            for (const perm of permutations(n)) {
                loops++;
                const val = resolve(root, signs, perm, nums);
                if (val !== null && val.eq(tgt)) out.add(myPrint(root, signs, perm, nums));
            }
        }
    }
    return { out, loops };
}

// ---- AC canonical form (associativity + commutativity for + and *) ----

function parse(s) {
    s = s.trim();
    if (!s.startsWith('(')) return { op: 'num', v: s };
    const inner = s.slice(1, -1);
    let depth = 0;
    for (let i = 0; i < inner.length; i++) {
        const c = inner[i];
        if (c === '(') depth++;
        else if (c === ')') depth--;
        else if (depth === 0 && i > 0 && '+-*/'.includes(c)) {
            return { op: c, l: parse(inner.slice(0, i)), r: parse(inner.slice(i + 1)) };
        }
    }
    throw new Error('cannot parse ' + s);
}

function flatten(t, op, out) {
    // collect every operand of a maximal chain of the same associative operator
    if (t.op === op) { flatten(t.l, op, out); flatten(t.r, op, out); }
    else out.push(t);
    return out;
}

function canonKey(t) {
    if (t.op === 'num') return t.v;
    if (t.op === '+' || t.op === '*') {
        const parts = flatten(t, t.op, []).map(canonKey).sort();
        return '(' + parts.join(t.op) + ')';
    }
    return '(' + canonKey(t.l) + t.op + canonKey(t.r) + ')';
}

function report(target, numbers) {
    const { out, loops } = allSolutions(target, numbers);
    const classes = new Map();
    for (const s of out) {
        const k = canonKey(parse(s));
        if (!classes.has(k)) classes.set(k, []);
        classes.get(k).push(s);
    }
    let worst = null;
    for (const [, v] of classes) if (!worst || v.length > worst.length) worst = v;
    console.log(
        `${JSON.stringify(numbers).padEnd(18)} target=${String(target).padEnd(5)} ` +
        `loops=${String(loops).padEnd(11)} strings=${String(out.size).padEnd(5)} ` +
        `essential=${String(classes.size).padEnd(4)} largest_class=${worst ? worst.length : 0}`
    );
    return { out, classes };
}

console.log('== "all expression strings" vs "essentially different" (AC-normalised) ==');
report(24, [1, 2, 3, 4]);
report(24, [3, 3, 8, 8]);
report(24, [5, 5, 5, 1]);
report(24, [23, 23, 23, 23]);
report(24, [1, 1, 1, 1]);
report(24, [6, 6, 6, 6]);
report(24, [7, 7, 7, 7]);

console.log();
console.log('== traversed search space (note the 4^n sign vectors for n-1 slots) ==');
const comb = (n, k) => { let r = 1n; for (let i = 0n; i < BigInt(k); i++) r = r * (BigInt(n) - i) / (i + 1n); return r; };
const fact = n => { let r = 1n; for (let i = 2n; i <= BigInt(n); i++) r *= i; return r; };
for (let n = 2; n <= 8; n++) {
    const cat = comb(2 * (n - 1), n - 1) / BigInt(n);
    console.log(`  n=${n}  Catalan(n-1)=${cat}  4^n=${4 ** n}  n!=${fact(n)}  loops=${cat * BigInt(4 ** n) * fact(n)}`);
}

console.log();
console.log('== extra cases used as test expectations ==');
report(60, [1, 1, 9, 9, 6]);
report(0, [1, 1, 1, 1]);
report(24, [8, 3, 8, 3]);
report(10, [-5, 2, 3]);
report(-6, [1, 2, 3]);

console.log();
console.log('== Long overflow: a denominator that needs more than 63 bits ==');
{
    const a = new F(1n, 255n).add(new F(1n, 254n));
    const b = new F(1n, 253n).add(new F(1n, 252n));
    const c = new F(1n, 251n).add(new F(1n, 250n));
    const d = new F(1n, 249n).add(new F(1n, 248n));
    const product = a.mul(b).mul(c.mul(d));
    console.log(`  (1/255 + 1/254) * (1/253 + 1/252) * (1/251 + 1/250) * (1/249 + 1/248)`);
    console.log(`  = ${product.n} / ${product.d}`);
    console.log(`  numerator  needs ${product.n.toString(2).length} bits`);
    console.log(`  denominator needs ${product.d.toString(2).length} bits (Long holds 63)`);
    console.log(`  signed 64-bit wrap of the denominator = ${BigInt.asIntN(64, product.d)}`);
}

