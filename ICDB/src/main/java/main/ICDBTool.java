package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import crypto.AlgorithmType;
import io.Format;
import io.source.DataSource;
import main.args.*;
import main.args.config.UserConfig;
import main.args.option.Granularity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Stopwatch;

import io.DBConnection;
import io.DBConverter;
import io.SchemaConverter;
import main.args.config.ConfigArgs;
import parse.ICDBQuery;
import stats.RunStatistics;
import stats.Statistics;
import stats.StatisticsMetadata;
import verify.QueryVerifier;
import verify.serial.Icrl;

/**
 * <p>
 * A tool for performing ICDB-related tasks.
 * </p>
 * Created on 5/10/2016
 *
 * @author Dan Kondratyuk
 */
public class ICDBTool {

    // The time unit for all timed log statements
    public static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

	private static final Logger logger = LogManager.getLogger();

	public static void main(String[] args) throws FileNotFoundException {
		Stopwatch totalTime = Stopwatch.createStarted();

		// Parse the command-line arguments
		CommandLineArgs cmd = new CommandLineArgs(args);
		ConfigArgs configArgs = cmd.getConfig();
        UserConfig dbConfig = UserConfig.init(configArgs);

        Icrl.Companion.debug(!dbConfig.validateIcrl);

		DBConnection.configure(dbConfig);

		// Execute a command
		if (cmd.isCommand(CommandLineArgs.CONVERT_DB)) {
			convertDB(cmd, dbConfig);
		} else if (cmd.isCommand(CommandLineArgs.CONVERT_QUERY)) {
			convertQuery(cmd, dbConfig);
		} else if (cmd.isCommand(CommandLineArgs.EXECUTE_QUERY)) {
			executeQuery(cmd, dbConfig);
		} else if (cmd.isCommand(CommandLineArgs.BENCHMARK)) {
            benchmark(cmd, dbConfig);
		} else { // TODO: add revoke serial command
			cmd.jCommander.usage();
			System.exit(0);
		}

        logger.info("");
		logger.info("Total time elapsed: {}", totalTime.elapsed(ICDBTool.TIME_UNIT));
	}

	/**
	 * Converts the specified DB to an ICDB
	 */
	private static void convertDB(CommandLineArgs cmd, UserConfig dbConfig) {
		final ConvertDBCommand convertConfig = cmd.convertDBCommand;

		// Duplicate the DB, and add additional columns
		DBConnection db = DBConnection.connect(dbConfig.schema, dbConfig);
		SchemaConverter.convertSchema(db, dbConfig, convertConfig);

		// Connect to the newly created DB
		DBConnection icdb = DBConnection.connect(dbConfig.icdbSchema, dbConfig);
		DBConverter dbConverter = new DBConverter(db, icdb, dbConfig, convertConfig);

		// Export, convert, and load all data
		dbConverter.convertAll();
	}

	/**
	 * Converts the Query to an ICDB Query
	 */
	private static void convertQuery(CommandLineArgs cmd, UserConfig dbConfig) {
        final ConvertQueryCommand convertQueryCmd = cmd.convertQueryCommand;
        final String icdbSchema = dbConfig.icdbSchema;

        DBConnection icdb = DBConnection.connect(icdbSchema, dbConfig);

        convertQueryCmd.queries.forEach(query -> {
            ICDBQuery icdbQuery = dbConfig.granularity.getQuery(query, icdb, dbConfig.codeGen, new RunStatistics());

            logger.info("Verify query:");
            logger.info(Format.limit(icdbQuery.getVerifyQuery()));

            logger.info("Converted query:");
            logger.info(Format.limit(icdbQuery.getConvertedQuery()));
        });
    }

    /**
     * Executes a query
     */
    private static void executeQuery(CommandLineArgs cmd, UserConfig dbConfig) {
        final ExecuteQueryCommand executeQueryCommand = cmd.executeQueryCommand;

        StatisticsMetadata metadata = new StatisticsMetadata(
            dbConfig.codeGen.getAlgorithm(), dbConfig.granularity, dbConfig.icdbSchema,
            executeQueryCommand.fetch, executeQueryCommand.threads, executeQueryCommand.query
        );

        Statistics statistics = new Statistics(metadata, new File("./src/main/resources/statistics/data.csv"));
        RunStatistics run = new RunStatistics();
        statistics.addRun(run);

        executeQueryRun(
            executeQueryCommand.query, executeQueryCommand.fetch, executeQueryCommand.threads, dbConfig, run, true
        );

        statistics.outputRuns();
    }

    /**
     * Benchmarks insert, select, and delete queries from stdin
     * Note: VERY hacky (I was so frustrated I wanted it to work)
     */
    private static void benchmark(CommandLineArgs cmd, UserConfig dbConfig) {
        final BenchmarkCommand benchmarkCommand = cmd.benchmarkCommand;
        final String dbSchema = benchmarkCommand.schemaName != null ? benchmarkCommand.schemaName : dbConfig.icdbSchema;

        final AlgorithmType algorithm = dbConfig.codeGen.getAlgorithm();
        final Granularity granularity = dbConfig.granularity;

        File[] insertFiles = new File(benchmarkCommand.insertPath).listFiles();
        File[] selectFiles = new File(benchmarkCommand.selectPath).listFiles();
        File[] deleteFiles = new File(benchmarkCommand.deletePath).listFiles();
        if (insertFiles == null || selectFiles == null || deleteFiles == null) {
            return;
        }

        Statistics insertStatistics = new Statistics(
            new StatisticsMetadata(
                    algorithm, granularity, dbSchema, benchmarkCommand.fetch, benchmarkCommand.threads, "insert"
            ),
            new File("./src/main/resources/statistics/" + algorithm + "-" + granularity + "-insert.csv")
        );
        Statistics selectStatistics = new Statistics(
                new StatisticsMetadata(
                        algorithm, granularity, dbSchema, benchmarkCommand.fetch, benchmarkCommand.threads, "select"
                ),
                new File("./src/main/resources/statistics/" + algorithm + "-" + granularity + "-select.csv")
        );
        Statistics deleteStatistics = new Statistics(
                new StatisticsMetadata(
                        algorithm, granularity, dbSchema, benchmarkCommand.fetch, benchmarkCommand.threads, "delete"
                ),
                new File("./src/main/resources/statistics/" + algorithm + "-" + granularity + "-delete.csv")
        );


        List<String> insertQueries = Arrays.stream(insertFiles)
            .sorted((f1, f2) -> f1.toString().compareTo(f2.toString()))
            .map(file -> {
                try { return FileUtils.readFileToString(file, Charsets.UTF_8); }
                catch (IOException e) { return null; }
            })
            .filter(s -> s != null)
            .collect(Collectors.toList());
        List<String> deleteQueries = Arrays.stream(deleteFiles)
            .sorted((f1, f2) -> f1.toString().compareTo(f2.toString()))
            .map(file -> {
                try { return FileUtils.readFileToString(file, Charsets.UTF_8); }
                catch (IOException e) { return null; }
            })
            .filter(s -> s != null)
            .collect(Collectors.toList());
        List<String> selectQueries = Arrays.stream(selectFiles)
                .sorted((f1, f2) -> f1.toString().compareTo(f2.toString()))
                .map(file -> {
                    try { return FileUtils.readFileToString(file, Charsets.UTF_8); }
                    catch (IOException e) { return null; }
                })
                .filter(s -> s != null)
                .collect(Collectors.toList());


        for (int i = 0; i < insertQueries.size(); i++) {
            final int numRuns = 5; // Number of the same run

            for (int j = 0; j < numRuns; j++) {
                RunStatistics insertRun = new RunStatistics();
                RunStatistics selectRun = new RunStatistics();
                RunStatistics deleteRun = new RunStatistics();
                insertRun.setRun(j+1);
                selectRun.setRun(j+1);
                deleteRun.setRun(j+1);

                // Insert values, then delete
                Stopwatch executionTime = Stopwatch.createStarted();

                if (benchmarkCommand.baseline) {
                    executeBaselineRun(insertQueries.get(i), dbConfig, insertRun);
                    executeBaselineRun(selectQueries.get(i), dbConfig, selectRun);
                    executeBaselineRun(deleteQueries.get(i), dbConfig, deleteRun);
                } else {
                    executeQueryRun(insertQueries.get(i), benchmarkCommand.fetch, benchmarkCommand.threads, dbConfig, insertRun, true);
                    executeQueryRun(selectQueries.get(i), benchmarkCommand.fetch, benchmarkCommand.threads, dbConfig, selectRun, true);
                    executeQueryRun(deleteQueries.get(i), benchmarkCommand.fetch, benchmarkCommand.threads, dbConfig, deleteRun, true);
                }

                logger.debug("Run time: {}", executionTime.elapsed(ICDBTool.TIME_UNIT));

                insertRun.setQueryFetchSize(deleteRun.getQueryFetchSize());

                deleteStatistics.addRun(deleteRun);
                selectStatistics.addRun(selectRun);
                insertStatistics.addRun(insertRun);
            }
        }

        deleteStatistics.outputRuns();
        selectStatistics.outputRuns();
        insertStatistics.outputRuns();
    }

    /**
     * Executes a query
     */
    private static void executeQueryRun(String query, DataSource.Fetch fetch, int threads, UserConfig dbConfig, RunStatistics run, boolean execute) {
        DBConnection icdb = DBConnection.connect(dbConfig.icdbSchema, dbConfig);
        ICDBQuery icdbQuery = dbConfig.granularity.getQuery(query, icdb, dbConfig.codeGen, run);

        logger.info("Original Query: {}", Format.limit(query));

        QueryVerifier verifier = dbConfig.granularity.getVerifier(icdb, dbConfig, threads, fetch, run);

        if (!icdbQuery.needsVerification()) {
            if (execute) { verifier.execute(icdbQuery); }
        } else if (verifier.verify(icdbQuery)) {
            logger.info("Query verified");
            if (execute) { verifier.execute(icdbQuery); }
        } else {
            logger.info(Format.limit(icdbQuery.getVerifyQuery()));
            logger.info("Query failed to verify");
            logger.error(verifier.getError());
        }
    }

    /**
     * Executes a query (baseline)
     */
    private static void executeBaselineRun(String query, UserConfig dbConfig, RunStatistics run) {
        DBConnection icdb = DBConnection.connect(dbConfig.icdbSchema, dbConfig);
        logger.info("Query: {}", Format.limit(query));

        Stopwatch executeTime = Stopwatch.createStarted();
        icdb.getCreate().execute(query);
        run.setExecutionTime(executeTime.elapsed(TIME_UNIT));

        logger.info("Execution time: {}", run.getExecutionTime());
    }

	static {
        System.setProperty("org.jooq.no-logo", "true");
	}

}
