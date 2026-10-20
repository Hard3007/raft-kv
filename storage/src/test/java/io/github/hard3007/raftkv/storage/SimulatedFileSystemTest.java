package io.github.hard3007.raftkv.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SimulatedFileSystemTest {

    private static final byte[] DATA = {1, 2, 3, 4};

    @Test
    void forcedWritesSurvivePowerLoss() {
        SimulatedFileSystem fs = new SimulatedFileSystem(new Random(1));
        StorageFile file = fs.open("f");
        file.write(0, DATA, 0, DATA.length);
        file.force();
        fs.simulatePowerLoss();

        byte[] read = new byte[4];
        fs.open("f").read(0, read, 0, 4);
        assertArrayEquals(DATA, read);
    }

    @Test
    void unforcedWritesSurviveAProcessCrash() {
        SimulatedFileSystem fs = new SimulatedFileSystem(new Random(1));
        fs.open("f").write(0, DATA, 0, DATA.length);
        fs.simulateProcessCrash();
        assertEquals(4, fs.open("f").size());
    }

    @Test
    void unforcedWritesAreSometimesLostOrTornOnPowerLoss() {
        boolean sawLoss = false;
        boolean sawSurvival = false;
        for (int seed = 0; seed < 50; seed++) {
            SimulatedFileSystem fs = new SimulatedFileSystem(new Random(seed));
            fs.open("f").write(0, DATA, 0, DATA.length);
            fs.simulatePowerLoss();
            long size = fs.open("f").size();
            sawLoss |= size < 4;
            sawSurvival |= size == 4;
        }
        assertTrue(sawLoss);
        assertTrue(sawSurvival);
    }

    @Test
    void handlesFromBeforeACrashAreDead() {
        SimulatedFileSystem fs = new SimulatedFileSystem(new Random(1));
        StorageFile file = fs.open("f");
        fs.simulateProcessCrash();
        assertThrows(IllegalStateException.class, file::size);
    }

    @Test
    void crashAfterFailsTheNthMutationAndEverythingAfterIt() {
        SimulatedFileSystem fs = new SimulatedFileSystem(new Random(1));
        StorageFile file = fs.open("f");
        fs.crashAfter(1);
        file.write(0, DATA, 0, 1); // operation 0 succeeds
        assertThrows(SimulatedCrashException.class, () -> file.write(1, DATA, 0, 1));
        assertThrows(SimulatedCrashException.class, file::size);
        fs.simulatePowerLoss();
        assertTrue(fs.exists("f"));
        assertFalse(fs.exists("g"));
    }
}
