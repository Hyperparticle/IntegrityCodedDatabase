package verify;

import cipher.mac.AlgorithmType;
import cipher.mac.CodeGen;
import cipher.mac.Signature;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import convert.DBConnection;
import convert.Format;
import main.args.ExecuteQueryCommand;
import main.args.config.Config;
import main.args.option.Granularity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *     Verifies a SQL query
 * </p>
 * Created 5/8/2016
 * @author Dan Kondratyuk
 */
public class QueryVerifier {

//    private final List<String> queries;
//    private final List<String> files;
//    private final String icdbName;
    private final String icdbQuery;
    private final DBConnection icdb;
    private final Granularity granularity;
    private final CodeGen codeGen;

    private StringBuilder errorStatus = new StringBuilder();

    private static final Logger logger = LogManager.getLogger();

    public QueryVerifier(ExecuteQueryCommand command, DBConnection icdb, Config dbConfig, String icdbQuery) {
        this.icdbQuery = icdbQuery;
//        this.files = command.files;
        this.granularity = dbConfig.granularity;
        this.codeGen = new CodeGen(dbConfig.algorithm, dbConfig.key.getBytes(Charsets.UTF_8));
//        this.icdbName = dbConfig.schema + Format.ICDB_SUFFIX;
        this.icdb = icdb;
    }

    public boolean verify() {
        Stopwatch queryVerificationTime = Stopwatch.createStarted();

        final DSLContext icdbCreate = DSL.using(icdb.getConnection(), SQLDialect.MYSQL);
//        final Schema icdbSchema = icdbCreate.meta().getSchemas().stream()
//                .filter(schema -> schema.getName().equals(icdbName))
//                .findFirst().get();

//        Result<Record> result = icdbCreate.fetch(icdbQuery);
        Cursor<Record> cursor = icdbCreate.fetchLazy(icdbQuery);
        boolean verified = granularity.equals(Granularity.TUPLE) ?
                verifyOCT(cursor) :
                verifyOCF(cursor);
        cursor.close();

        logger.debug("Total query verification time: {}", queryVerificationTime);
        return verified;
    }

    private boolean verifyOCT(Cursor<Record> cursor) {
        return cursor.stream().map(record -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < record.size() - 2; i++) {
                Object value = record.get(i);
                builder.append(value);
            }

            // TODO: serial
            byte[] serial    = (byte[]) record.get(Format.SERIAL_COLUMN);
            byte[] signature = (byte[]) record.get(Format.SVC_COLUMN);
            byte[] dataBytes = builder.toString().getBytes(Charsets.UTF_8);

            boolean verified = codeGen.verify(dataBytes, signature);

            if (!verified) {
                errorStatus.append("\n")
                    .append(record.toString())
                    .append("\n");
            }

            return verified;
        }).allMatch(verified -> verified);
    }

    private boolean verifyOCF(Cursor<Record> cursor) {
        return cursor.stream().map(record -> {
            final int dataSize = record.size() / 3;
            for (int i = 0; i < dataSize; i++) {
                // TODO: serial
                byte[] serial    = (byte[]) record.get(dataSize + 2*i + 1);
                byte[] signature = (byte[]) record.get(dataSize + 2*i);
                byte[] dataBytes = record.get(i).toString().getBytes(Charsets.UTF_8);

                boolean verified = codeGen.verify(dataBytes, signature);

                if (!verified) {
                    errorStatus.append("\n")
                            .append(record.field(i))
                            .append(" : ")
                            .append(record.get(i))
                            .append("\n");
                    return false;
                }
            }

            return true;
        }).allMatch(verified -> verified);
    }

    public String getError() {
        return errorStatus.toString();
    }

}
