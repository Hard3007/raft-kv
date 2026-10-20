package io.github.hard3007.raftkv.server;

import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point of a node process. Configuration comes from the environment (see {@link NodeConfig}). */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        NodeConfig config = NodeConfig.fromEnvironment(System.getenv());
        RaftKvNode node = RaftKvNode.start(config);
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("node={} event=shutting_down", config.id());
            node.close(); // SIGTERM (docker stop): checkpoint and close cleanly
            stopped.countDown();
        }));
        stopped.await();
    }
}
