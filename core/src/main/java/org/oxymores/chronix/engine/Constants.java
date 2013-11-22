package org.oxymores.chronix.engine;

public final class Constants
{
    private Constants()
    {

    }

    // GENERAL
    public static final int KB = 1024;
    public static final int MB = KB * 1024;

    // BROKER
    public static final int DEFAULT_BROKER_MEM_USAGE = 20 * MB;
    public static final int DEFAULT_BROKER_STORE_USAGE = 38 * MB;
    public static final int DEFAULT_BROKER_TEMP_USAGE = 38 * MB;
    public static final int DEFAULT_BROKER_NETWORK_CONNECTOR_TTL_S = 20;

    // ENGINE
    public static final int BROKER_PORT_FREEING_MS = 1000;
    public static final int DEFAULT_NB_RUNNER = 4;

    // TOKENS
    public static final int MAX_TOKEN_VALIDITY_MN = 10;
    public static final int TOKEN_RENEWAL_MN = 5;
    public static final int TOKEN_AUTO_RENEWAL_LOOP_PERIOD_S = 60;

    // RUNNER AGENT
    public static final int MAX_RETURNED_SMALL_LOG_LINES = 500;
    public static final int MAX_RETURNED_SMALL_LOG_CHARACTERS = 10000;
    public static final int MAX_RETURNED_BIG_LOG_LINES = 10000;
    public static final int MAX_RETURNED_BIG_LOG_END_LINES = 100;

    // STATUS
    public static final String JI_STATUS_OVERRIDEN = "OVERRIDEN";
    public static final String JI_STATUS_DONE = "DONE";
    public static final String JI_STATUS_RUNNING = "RUNNING";
    public static final String JI_STATUS_CHECK_SYNC_CONDS = "CHECK_SYNC_CONDS";
    
    // RUN METHODS
    public static final String JD_METHOD_SHELL = "Shell";
}
