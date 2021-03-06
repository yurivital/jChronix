/**
 * By Marc-Antoine Gouillart, 2012
 *
 * See the NOTICE file distributed with this work for
 * information regarding copyright ownership.
 * This file is licensed to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.oxymores.chronix.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.oxymores.chronix.engine.data.RunDescription;
import org.oxymores.chronix.engine.data.RunResult;
import org.slf4j.LoggerFactory;

public final class RunnerShell
{
    private RunnerShell()
    {

    }

    // RunnerAgent logger, yes. Clearer logs that way.
    private static final Logger log = LoggerFactory.getLogger(RunnerAgent.class);

    public static RunResult run(RunDescription rd, String logFilePath, boolean storeLogFile, boolean returnFullerLog)
    {
        RunResult res = new RunResult();
        Process p;
        String nl = System.getProperty("line.separator");
        Pattern setVarPattern = Pattern.compile("^set ([a-zA-Z]+[a-zA-Z0-9]*)=(.+)");
        Matcher setVarMatcher = setVarPattern.matcher("Testing123Testing");
        String logFileEncoding = getEncoding(rd);
        log.debug("Encoding is " + logFileEncoding);

        // ///////////////////////////
        // Build command
        List<String> argsStrings = buildCommand(rd);

        // /////////////////////////////////////////////////////////////////////////
        // Create a process builder with the command line contained in the array
        ProcessBuilder pb = new ProcessBuilder(argsStrings);

        // Mix stdout and stderr (easier to put errors in context this way)
        pb.redirectErrorStream(true);

        // Create array containing environment
        Map<String, String> env = pb.environment();
        for (int i = 0; i < rd.getEnvNames().size(); i++)
        {
            env.put(rd.getEnvNames().get(i), rd.getEnvValues().get(i));
        }

        BufferedReader br = null;
        Writer output = null;
        try
        {
            // Start!
            p = pb.start();

            // Read output (err & out), write it to file
            InputStreamReader isr = new InputStreamReader(p.getInputStream(), logFileEncoding);
            br = new BufferedReader(isr);

            String line;
            int i = 0;
            LinkedHashMap<Integer, String> endBuffer = new LinkedHashMap<Integer, String>()
            {
                private static final long serialVersionUID = -6773540176968046737L;

                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Integer, String> eldest)
                {
                    return this.size() > Constants.MAX_RETURNED_BIG_LOG_END_LINES;
                }
            };

            if (storeLogFile)
            {
                output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFilePath), "UTF-8"));
            }
            line = br.readLine();
            while (line != null)
            {
                i++;

                // Local log file gets all lines
                if (storeLogFile)
                {
                    output.write(line + nl);
                }

                // Small log gets first 500 lines or 10000 characters (the smaller of the two)
                if (i < Constants.MAX_RETURNED_SMALL_LOG_LINES && res.logStart.length() < Constants.MAX_RETURNED_SMALL_LOG_CHARACTERS)
                {
                    res.logStart += nl + line;
                }

                // Scheduler internal log gets first line only
                if (i == 1)
                {
                    log.debug(String.format("Job running. First line of output is: %s", line));
                }

                // Fuller log gets first 10k lines, then last 1k lines.
                if (returnFullerLog)
                {
                    if (i < Constants.MAX_RETURNED_BIG_LOG_LINES)
                    {
                        res.fullerLog += line;
                    }
                    else
                    {
                        endBuffer.put(i, line);
                    }
                }

                // Analysis: there may be a new variable definition in the line
                setVarMatcher.reset(line);
                if (setVarMatcher.find())
                {
                    log.debug("Key detected :" + setVarMatcher.group(1));
                    log.debug("Value detected :" + setVarMatcher.group(2));
                    res.newEnvVars.put(setVarMatcher.group(1), setVarMatcher.group(2));
                }
                line = br.readLine();
            }
            IOUtils.closeQuietly(br);

            if (i > Constants.MAX_RETURNED_BIG_LOG_LINES
                    && i < Constants.MAX_RETURNED_BIG_LOG_LINES + Constants.MAX_RETURNED_BIG_LOG_END_LINES && returnFullerLog)
            {
                res.fullerLog += Arrays.toString(endBuffer.entrySet().toArray());
            }
            if (i >= Constants.MAX_RETURNED_BIG_LOG_LINES + Constants.MAX_RETURNED_BIG_LOG_END_LINES && returnFullerLog)
            {
                res.fullerLog += "\n\n\n*******\n LOG TRUNCATED - See full log on server\n********\n\n\n"
                        + Arrays.toString(endBuffer.entrySet().toArray());
            }

            // Done: close log file
            if (storeLogFile)
            {
                IOUtils.closeQuietly(output);
                File f = new File(logFilePath);
                res.logSizeBytes = f.length();
            }
        }
        catch (IOException e)
        {
            log.error("error occurred while running job", e);
            res.logStart = e.getMessage();
            res.returnCode = -1;
            IOUtils.closeQuietly(br);
            IOUtils.closeQuietly(output);
            return res;
        }

        // Return
        res.returnCode = p.exitValue();
        res.logPath = logFilePath;
        res.envtUser = System.getProperty("user.name");
        try
        {
            res.envtServer = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e)
        {
            res.envtServer = "unknown";
        }
        log.info(String.format("Job ended, RC is %s", res.returnCode));
        return res;
    }

    private static String getEncoding(RunDescription rd)
    {
        String encoding = System.getProperty("file.encoding");
        Constants.SHELL shell = Constants.SHELL.valueOf(rd.getSubMethod());
        if (shell == Constants.SHELL.POWERSHELL || shell == Constants.SHELL.CMD)
        {
            try
            {
                encoding = "cp" + WinRegistry.readString(WinRegistry.HKEY_LOCAL_MACHINE, "SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage", "OEMCP");
            }
            catch (Exception e)
            {
                log.warn("Windows console encoding could not be found. Default encoding will be used. Logs may be encoded weirdly", e);
            }
        }
        return encoding;
    }

    private static List<String> buildCommand(RunDescription rd)
    {
        ArrayList<String> argsStrings = new ArrayList<>();
        Constants.SHELL shell = Constants.SHELL.valueOf(rd.getSubMethod());

        // Depending on the shell, we may have to add shell start parameters to allow batch processing
        switch (shell)
        {
            case CMD:
                argsStrings.add("cmd.exe");
                argsStrings.add("/C");
                break;
            case POWERSHELL:
                argsStrings.add("powershell.exe");
                argsStrings.add("-NoLogo");
                argsStrings.add("-NonInteractive");
                argsStrings.add("-WindowStyle");
                argsStrings.add("Hidden");
                argsStrings.add("-Command");
                break;
            case BASH:
                argsStrings.add("/bin/bash");
                argsStrings.add("-c");
                break;
            case SH:
                argsStrings.add("/bin/sh");
                argsStrings.add("-c");
                break;
            case KSH:
                argsStrings.add("/bin/ksh");
                argsStrings.add("-c");
                break;
            default:
                throw new IllegalArgumentException("unknown shell");
        }

        // Then add the command itself
        argsStrings.add(rd.getCommand());

        // Finally add parameters (if any - there may be none or they may be contained inside the command itself)
        for (int i = 0; i < rd.getParamNames().size(); i++)
        {
            String key = rd.getParamNames().get(i);
            String value = rd.getParamValues().get(i);
            String arg = "";
            if (key != null && !"".equals(key))
            {
                arg = key;
            }
            if (value != null && !"".equals(value) && (key != null && !"".equals(key)))
            {
                arg += " " + value;
            }
            if (value != null && !"".equals(value) && !(key != null && !"".equals(key)))
            {
                arg += value;
            }

            argsStrings.add(arg);
        }

        return argsStrings;
    }
}
