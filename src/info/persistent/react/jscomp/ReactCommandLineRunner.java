package info.persistent.react.jscomp;

import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CustomPassExecutionTime;

public class ReactCommandLineRunner extends CommandLineRunner {
    ReactCommandLineRunner(String[] args) {
        super(args);
    }

    @Override
    protected CompilerOptions createOptions() {
        Compiler compiler = this.getCompiler();

        ReactCompilerPass.Options passOptions = new ReactCompilerPass.Options();
        passOptions.propTypesTypeChecking = true;
        ReactCompilerPass compilerPass = new ReactCompilerPass(compiler, passOptions);

        CompilerOptions options = super.createOptions();
        options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, compilerPass);
        options.addWarningsGuard(new ReactWarningsGuard(compiler, compilerPass));

        return options;
    }

    public static void main(String[] args) {
        ReactCommandLineRunner runner = new ReactCommandLineRunner(args);
        if (runner.shouldRunCompiler()) {
            runner.run();
        }
        if (runner.hasErrors()) {
            System.exit(-1);
        }
    }
}