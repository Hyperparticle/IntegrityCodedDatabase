package convert;

import main.args.option.Granularity;

import java.io.IOException;
import java.sql.*;

/**
 * <p>
 *      Converts a given DB schema to an ICDB using a JDBC connection
 * </p>
 * Created on 6/3/2016
 *
 * @author Dan Kondratyuk
 */
public class SchemaConverter {

    private final String schema;
    private final Connection connection;
    private final Granularity granularity;

    public SchemaConverter(String schema, Connection connection, Granularity granularity) {
        this.schema = schema;
        this.connection = connection;
        this.granularity = granularity;
    }

    public void convert() throws SQLException {
        duplicateDB();

        connection.setSchema(schema);
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet resultSet = metaData.getTables(null, null, "%", new String[] {"TABLE"});

//        Statement statement = connection.createStatement();
//        ResultSet resultSet = statement.executeQuery("SELECT * FROM actor WHERE first_name = 'PENELOPE'");

        while (resultSet.next()) {
            System.out.println(resultSet.getString(3));
        }

        resultSet.close();
//        statement.close();
        connection.close();

//        try {
//            final SchemaCrawlerOptions options = new SchemaCrawlerOptions();
//
//            final Catalog catalog = SchemaCrawlerUtility.getCatalog(connection, options);
//            for (final Schema schema: catalog.getSchemas())
//            {
//                System.out.println(schema);
//                for (final Table table: catalog.getTables(schema))
//                {
//                    System.out.println("o--> " + table);
//                    for (final Column column: table.getColumns())
//                    {
//                        System.out.println("     o--> " + column);
//                    }
//                }
//            }
//        } catch (SchemaCrawlerException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * Duplicates the schema by running a Bash script
     */
    private void duplicateDB() {
        try {
            new ProcessBuilder("bash", "./src/main/resources/scripts/duplicate-schema.sh", schema).start();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}
