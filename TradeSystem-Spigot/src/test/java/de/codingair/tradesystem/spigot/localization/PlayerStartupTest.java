package de.codingair.tradesystem.spigot.localization;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStartupTest {
    @Test void genuineApiClassAbsenceRetainsBootstrapNativeDeliveryAndGuiFallback() throws Exception {
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .filter(entry -> !Path.of(entry).getFileName().toString().contains("impeerium-api"))
                .collect(Collectors.joining(File.pathSeparator));
        Path log = Path.of("target/api-absent-probe.log");
        Files.writeString(Path.of("target/api-absent-classpath.txt"), classpath);
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XX:ActiveProcessorCount=2", "-XX:+EnableDynamicAgentLoading", "-Dnet.bytebuddy.experimental=true",
                "-cp", classpath, ApiAbsentProbe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "API-absent bootstrap probe timed out");
            assertEquals(0, process.exitValue(), () -> {
                try {return Files.readString(log);} catch (Exception failure) {return failure.toString();}
            });
            assertTrue(Files.readString(log).contains("API_ABSENT_BOOTSTRAP_AND_NATIVE_GUI_PASSED"));
        } finally {
            if (process.isAlive()) {process.destroyForcibly();process.waitFor(5, TimeUnit.SECONDS);}
        }
    }
}
