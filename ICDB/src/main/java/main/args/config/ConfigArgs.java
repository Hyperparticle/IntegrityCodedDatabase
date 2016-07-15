package main.args.config;

import cipher.AlgorithmType;
import main.args.option.Granularity;

/**
 * <p>
 *     Represents a configuration file
 * </p>
 * Created on 6/8/2016
 *
 * @author Dan Kondratyuk
 */
public class ConfigArgs {
    public String ip;
    public int port;
    public String user;
    public String password;
    public String schema;
    public String icdbSchema;
    public Granularity granularity;
    public AlgorithmType algorithm;
    public String macKey;
    public String rsaKeyFile;
}