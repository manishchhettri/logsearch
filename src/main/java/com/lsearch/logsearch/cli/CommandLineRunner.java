package com.lsearch.logsearch.cli;

import com.lsearch.logsearch.service.LogFileIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

@Component
public class CommandLineRunner implements org.springframework.boot.CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandLineRunner.class);

    private final ApplicationArguments applicationArguments;
    private final LogFileIndexer fileIndexer;

    public CommandLineRunner(ApplicationArguments applicationArguments, LogFileIndexer fileIndexer) {
        this.applicationArguments = applicationArguments;
        this.fileIndexer = fileIndexer;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0 || "start".equals(args[0])) {
            handleStartCommand();
        } else if ("index".equals(args[0])) {
            handleIndexCommand();
        } else {
            printUsage();
        }
    }

    private void handleStartCommand() {
        log.info("=================================================================");
        log.info("Starting Log Search Application");
        log.info("=================================================================");

        if (applicationArguments.containsOption("logs-dir")) {
            log.info("Using logs directory: {}", applicationArguments.getOptionValues("logs-dir").get(0));
        }

        try {
            log.info("Checking for new logs to index...");
            fileIndexer.indexAllLogs();

            log.info("");
            log.info("=================================================================");
            log.info("Log Search is running!");
            log.info("Web UI: http://localhost:8080");
            log.info("API: http://localhost:8080/api/search");
            log.info("=================================================================");
            log.info("");

            openBrowser("http://localhost:8080");

        } catch (Exception e) {
            log.error("Failed to start application", e);
        }
    }

    private void handleIndexCommand() {
        log.info("=================================================================");
        log.info("Indexing Log Files");
        log.info("=================================================================");

        try {
            fileIndexer.indexAllLogs();
            log.info("Indexing completed successfully!");
            log.info("Total files indexed: {}", fileIndexer.getIndexedFileCount());

            // Exit after indexing
            System.exit(0);

        } catch (Exception e) {
            log.error("Indexing failed", e);
            System.exit(1);
        }
    }

    private void printUsage() {
        System.out.println("Usage: java -jar log-search.jar [command] [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  start     Start the web server (default)");
        System.out.println("  index     Index log files and exit");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --logs-dir=<path>    Override logs directory");
        System.out.println("  --index-dir=<path>   Override index directory");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar log-search.jar start");
        System.out.println("  java -jar log-search.jar index --logs-dir=/path/to/logs");
        System.out.println("  java -jar log-search.jar start --logs-dir=/path/to/logs");
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("Opening browser...");
            }
        } catch (Exception e) {
            log.debug("Could not open browser automatically", e);
        }
    }
}
