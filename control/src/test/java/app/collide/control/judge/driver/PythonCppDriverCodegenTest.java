package app.collide.control.judge.driver;

import static org.assertj.core.api.Assertions.assertThat;

import app.collide.control.execution.model.Language;
import app.collide.control.problem.ProblemHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Python + C++ drivers are generated and asserted structurally but NOT executed — g++/python3 are
 * absent in this env (spec §8 honest caveat). Structurally identical to the exec-verified JS/Java
 * drivers.
 */
class PythonCppDriverCodegenTest {

    private final JudgeDriverGenerator gen = new JudgeDriverGenerator();

    private ProblemHarness twoSum() {
        return new ProblemHarness("twoSum",
                List.of(new ProblemHarness.Param("nums", "int[]"), new ProblemHarness.Param("target", "int")),
                "int[]", List.of(), "unordered", 2000, null);
    }

    private ProblemHarness mergeLists() {
        return new ProblemHarness("mergeTwoLists",
                List.of(new ProblemHarness.Param("l1", "list-node<int>"), new ProblemHarness.Param("l2", "list-node<int>")),
                "list-node<int>", List.of(), "exact", 2000, null);
    }

    private ProblemHarness minStack() {
        return new ProblemHarness("MinStack", List.of(new ProblemHarness.Param("ops", "operations")),
                "operations", List.of(), "exact", 2000, null);
    }

    @Test
    void pythonReadsStdinAndCallsSolution() {
        String p = gen.generate(Language.PYTHON, twoSum(), "class Solution:\n    def twoSum(self, nums, target):\n        return []");
        assertThat(p).contains("json.loads(sys.stdin").contains("Solution().twoSum(");
    }

    @Test
    void pythonInjectsListSerdeForListNodeProblem() {
        String p = gen.generate(Language.PYTHON, mergeLists(), "class Solution:\n    def mergeTwoLists(self, a, b):\n        return a");
        assertThat(p).contains("__to_list").contains("__from_list");
    }

    @Test
    void cppReadsStdinCompilesMainAndCallsSolution() {
        String p = gen.generate(Language.CPP, twoSum(), "class Solution { public: vector<int> twoSum(vector<int>& n, int t){ return {}; } };");
        assertThat(p).contains("__readArgs").contains("int main(").contains(".twoSum(");
    }

    @Test
    void cppInjectsListSerdeForListNodeProblem() {
        String p = gen.generate(Language.CPP, mergeLists(), "class Solution { public: ListNode* mergeTwoLists(ListNode* a, ListNode* b){ return a; } };");
        assertThat(p).contains("__toList").contains("__fromList");
    }

    @Test
    void pythonAndCppOperationsDispatch() {
        assertThat(gen.generate(Language.PYTHON, minStack(), "class MinStack:\n    def __init__(self): pass")).contains("getattr");
        assertThat(gen.generate(Language.CPP, minStack(), "class MinStack { };")).contains("main(");
    }
}
