package parse;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import main.args.ConvertQueryCommand;
import main.args.config.Config;
import main.args.option.Granularity;
import net.sf.jsqlparser.JSQLParserException;

/**
 * <p>
 * Converts a SQL query into an ICDB SQL query.
 * </p>
 * Created 5/8/2016
 * 
 * @author Dan Kondratyuk
 */

// TODO ICDB query output file path.//Database name to fetch the metadata.
public class QueryConverter {

	private Path outputPath = Paths.get("/Users/ujwal-mac/Desktop/queries");;
	private File queryFile;

	private final List<String> queries;
	private final List<String> files;
	private final String Schema;
	private final Connection icdb;
	private final Granularity granularity;

	private static final Logger logger = LogManager.getLogger();

	public QueryConverter(ConvertQueryCommand command, Config config, Connection icdb) {
		this.queries = command.queries;
		this.files = command.files;
		this.granularity = command.granularity;
		this.Schema = config.schema;
		this.icdb = icdb;
	}

	public QueryConverter(String Query, Granularity granularity, Config config, Connection icdb) {
		this.queries = new ArrayList<String>();
		queries.add(Query);
		this.files = new ArrayList<String>();
		this.granularity = granularity;
		this.Schema = config.schema;
		this.icdb = icdb;
	}

	/**
	 * <p>
	 * Convert a query or files with queries to their respective ICDB Query
	 * </p>
	 * 
	 * @throws JSQLParserException
	 */
	public void convert() throws JSQLParserException {
		try {

			String outputName = "ICDBquery.sql";
			File outputFile = Paths.get(outputPath.toString(), outputName).toFile();
			FileWriter writer = new FileWriter(outputFile);
			StringBuilder builder = new StringBuilder();
			String Schema = "";
			if (this.queries.size() != 0) {

				for (String query : queries) {
					builder.setLength(0);
					// Get the database name mentioned in USE command
					StringTokenizer tokenizer = new StringTokenizer(query, ";");

					while (tokenizer.hasMoreTokens()) {
						String next = tokenizer.nextToken();
						if (next.contains("USE")) {

							Schema = (next.substring(next.indexOf(" ")).trim() + "_ICDB").toUpperCase();
							builder.append((next + "_ICDB").toUpperCase()).append(";").append("\n");
						} else {
							if (Schema == "") {
								System.out.println("No Database used for the Query provided.");
								break;
							}
							SQLParser parser;
							if (granularity == Granularity.TUPLE) {
								parser = new OCTparser(next, Schema, this.granularity, this.icdb);
							} else {
								parser = new OCFparser(next, Schema, this.granularity, this.icdb);
							}
							// SQLParser parser = new SQLParser(next, Schema,
							// this.granularity, this.icdb);
							builder.append(parser.parse()).append(";").append("\n");

						}
					}
					writer.append(builder);
				}
				writer.close();
			} else {
				for (String file : files) {
					// Each queries in the files should include USE SCHEMA
					// command

					this.queryFile = Paths.get(file).toFile();
					outputName = queryFile.getName() + "-icdb";
					outputFile = Paths.get(outputPath.toString(), outputName).toFile();
					writer = new FileWriter(outputFile);
					Scanner queryScan = new Scanner(this.queryFile);

					while (queryScan.hasNextLine()) {
						builder.setLength(0);

						String Query = queryScan.nextLine();
						if (Query.contains("USE")) {

							Schema = (Query.substring(Query.indexOf(" ")).trim() + "_ICDB").toUpperCase();
							builder.append((Query + "_ICDB").toUpperCase()).append(";").append("\n");
						} else {
							if (Schema == "") {
								System.out.println("No Database used for the Query provided.");
								break;
							}

							SQLParser parser;
							if (granularity == Granularity.TUPLE) {
								parser = new OCTparser(Query, Schema, this.granularity, this.icdb);
							} else {
								parser = new OCFparser(Query, Schema, this.granularity, this.icdb);
							}
							// SQLParser parser = new SQLParser(Query, Schema,
							// this.granularity, this.icdb);
							builder.append(parser.parse()).append(";").append("\n");
						}

						writer.append(builder);
					}
					writer.close();
					queryScan.close();
				}

			}
		} catch (IOException e) {
			// TODO: handle exception
			System.err.println("Failed to convert query ");
		}

	}

}
