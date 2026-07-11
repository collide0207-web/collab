import { DEFAULT_BUDGET } from '../framework/buckets.js'
import { fillBuckets } from '../framework/gen.js'
import type { ProblemModule } from '../framework/types.js'
import type { Rng } from '../framework/rng.js'

/**
 * min-stack (operations mode): input is one `operations` param
 *   [["MinStack",[]], ["push",[x]], ["pop",[]], ["top",[]], ["getMin",[]], ...]
 * expected is an array of the same length: index 0 is null (constructor), each other slot the
 * op's return (null for void push/pop). Reference and brute simulate with different internals.
 */

type Op = [string, unknown[]]

/** Simulate an op sequence with a `factory` producing an object exposing the four methods. */
function simulate(ops: Op[], factory: () => Record<string, (...a: number[]) => unknown>): unknown[] {
  const out: unknown[] = [null] // constructor slot
  const stack = factory()
  for (let i = 1; i < ops.length; i++) {
    const [name, args] = ops[i]
    const ret = stack[name](...(args as number[]))
    out.push(ret === undefined ? null : ret)
  }
  return out
}

/** Reference internals: parallel min-stack. */
function refFactory() {
  const data: number[] = []
  const mins: number[] = []
  return {
    push(x: number) {
      data.push(x)
      mins.push(mins.length === 0 ? x : Math.min(x, mins[mins.length - 1]))
    },
    pop() {
      data.pop()
      mins.pop()
    },
    top() {
      return data[data.length - 1]
    },
    getMin() {
      return mins[mins.length - 1]
    },
  }
}

/** Brute internals: single stack of [value, minSoFar] pairs — different representation. */
function bruteFactory() {
  const st: [number, number][] = []
  return {
    push(x: number) {
      const min = st.length === 0 ? x : Math.min(x, st[st.length - 1][1])
      st.push([x, min])
    },
    pop() {
      st.pop()
    },
    top() {
      return st[st.length - 1][0]
    },
    getMin() {
      return st[st.length - 1][1]
    },
  }
}

/** Build a valid random op sequence (never pop/top/getMin on an empty stack). */
function randomOps(rng: Rng, count: number, lo: number, hi: number): Op[] {
  const ops: Op[] = [['MinStack', []]]
  let size = 0
  for (let i = 0; i < count; i++) {
    const canRead = size > 0
    const choice = !canRead || rng.next() < 0.5 ? 'push' : rng.pick(['pop', 'top', 'getMin'])
    if (choice === 'push') {
      ops.push(['push', [rng.int(lo, hi)]])
      size++
    } else if (choice === 'pop') {
      ops.push(['pop', []])
      size--
    } else {
      ops.push([choice, []])
    }
  }
  return ops
}

const minStack: ProblemModule = {
  meta: {
    slug: 'min-stack',
    version: 1,
    rngSeed: 20260716,
    budget: DEFAULT_BUDGET,
    timeLimitMs: 2000,
    checker: 'exact',
  },

  generator(rng, budget) {
    return fillBuckets(rng, budget, (kind, i, r) => {
      if (kind === 'edge') {
        const catalog: unknown[][] = [
          [[['MinStack', []], ['push', [-2]], ['push', [0]], ['push', [-3]], ['getMin', []], ['pop', []], ['top', []], ['getMin', []]]],
          [[['MinStack', []]]],
          [[['MinStack', []], ['push', [1]], ['top', []], ['getMin', []]]],
          [[['MinStack', []], ['push', [0]], ['push', [0]], ['getMin', []], ['pop', []], ['getMin', []]]],
        ]
        if (i < catalog.length) return catalog[i]
        return [randomOps(r, r.int(1, 8), -10, 10)]
      }
      if (kind === 'randomSmall') return [randomOps(r, r.int(1, 20), -100, 100)]
      if (kind === 'randomMedium') return [randomOps(r, r.int(20, 100), -100000, 100000)]
      return [randomOps(r, r.int(200, 800), -2147483648, 2147483647)]
    })
  },

  validator(input) {
    const [ops] = input as [Op[]]
    if (!Array.isArray(ops) || ops.length === 0) throw new Error('min-stack: empty ops')
    if (ops[0][0] !== 'MinStack' || (ops[0][1] as unknown[]).length !== 0) throw new Error('min-stack: first op must be constructor')
    let size = 0
    for (let i = 1; i < ops.length; i++) {
      const [name, args] = ops[i]
      if (name === 'push') {
        if ((args as unknown[]).length !== 1) throw new Error('min-stack: push takes one arg')
        size++
      } else if (name === 'pop' || name === 'top' || name === 'getMin') {
        if (size === 0) throw new Error(`min-stack: ${name} on empty stack`)
        if (name === 'pop') size--
      } else {
        throw new Error(`min-stack: unknown op ${name}`)
      }
    }
  },

  reference(...input) {
    const [ops] = input as [Op[]]
    return simulate(ops, refFactory)
  },

  brute(...input) {
    const [ops] = input as [Op[]]
    return simulate(ops, bruteFactory)
  },
}

export default minStack
