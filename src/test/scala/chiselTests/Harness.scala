// See LICENSE for license details.

package chiselTests
import Chisel.testers.BasicTester
import org.scalatest._
import org.scalatest.prop._
import java.io.File

class HarnessSpec extends ChiselPropSpec
  with Chisel.BackendCompilationUtilities {

  def makeTrivialVerilog: (File => File) = makeHarness((prefix: String) => s"""
module ${prefix};
  initial begin
    $$display("$prefix!");
    $$finish;
  end
endmodule
""", ".v") _

  def makeFailingVerilog: (File => File) = makeHarness((prefix: String) => s"""
module $prefix;
  initial begin
    assert (1 == 0) else $$error("My specific, expected error message!");
    $$display("$prefix!");
    $$finish;
  end
endmodule
""", ".v") _

  def makeCppHarness: (File => File) = makeHarness((prefix: String) => s"""
#include "V$prefix.h"
#include "verilated.h"

vluint64_t main_time = 0;
double sc_time_stamp () { return main_time; }

int main(int argc, char **argv, char **env) {
    Verilated::commandArgs(argc, argv);
    V${prefix}* top = new V${prefix};
    while (!Verilated::gotFinish()) { top->eval(); }
    delete top;
    exit(0);
}
""", ".cpp") _

  /** Compiles a C++ emulator from Verilog and returns the path to the
    * executable and the executable filename as a tuple.
    */
  def simpleHarnessBackend(make: File => File): (File, String) = {
    val target = "test"
    val path = createTempDirectory(target)
    val fname = File.createTempFile(target, "", path)
    val prefix = fname.toString.split("/").last

    val cppHarness = makeCppHarness(fname)

    make(fname)
    verilogToCpp(prefix, path, Seq(), cppHarness).!
    cppToExe(prefix, path).!
    (path, prefix)
  }

  property("Test making trivial verilog harness and executing") {
    val (path, prefix) = simpleHarnessBackend(makeTrivialVerilog)

    assert(executeExpectingSuccess(prefix, path))
  }

  property("Test that assertion failues in Verilog are caught") {
    val (path, prefix) = simpleHarnessBackend(makeFailingVerilog)

    assert(!executeExpectingSuccess(prefix, path))
    assert(executeExpectingFailure(prefix, path))
    assert(executeExpectingFailure(prefix, path, "My specific, expected error message!"))
    assert(!executeExpectingFailure(prefix, path, "A string that doesn't match any test output"))
  }
}
