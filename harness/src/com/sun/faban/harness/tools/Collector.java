/* The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.sun.com/cddl/cddl.html or
 * install_dir/legal/LICENSE
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at install_dir/legal/LICENSE.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id: Collector.java,v 1.3 2008/05/23 05:57:42 akara Exp $
 *
 * Copyright 2005 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.faban.harness.tools;

import com.sun.faban.common.Command;
import com.sun.faban.common.CommandHandle;
import com.sun.faban.harness.agent.CmdAgentImpl;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

/**
 * Collector is a wrapper for the Collector. This will send the
 * Config.PROFILE_SIGNAL to the processes started though collector.
 * <br>Note: This class is Solaris-specific. It may work on Linux or other
 * unix systems collector is supported. It should compile on all systems
 * but cause a warning/exception at runtime when trying to invoke if
 * collector is not available.
 * <ul>
 * <li> It implements the Tool interface by extending GenericTool
 * </ul>
 *
 * @author Ramesh Ramachandran
 * @see GenericTool
 * @see Tool
 */
public class Collector extends GenericTool {

    private ArrayList<String> pids = new ArrayList<String>();

    public void configure(String tool, List<String> argList, String path,
                          String outDir, String host, String masterhost,
                          CmdAgentImpl cmdAgent, CountDownLatch latch) {
        super.configure(tool, argList, path, outDir, host, masterhost,
                cmdAgent, latch);
    }

    protected void start() {

        // Locate the process with collector, starting with user processes.
        Command c = new Command("/usr/bin/ps", "-u", System.getProperty("user.name"));
        c.setStreamHandling(Command.STDOUT, Command.CAPTURE);
        String result = null;
        try {
            CommandHandle handle = cmdAgent.execute(c);
            result = new String(handle.fetchOutput(Command.STDOUT));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error executing ps", e);
            return;
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Interrupted executing ps", e);
            return;
        }

        int startIdx = 0;
        int endIdx = result.indexOf('\n');
        while (endIdx > 0) {
            String line = result.substring(startIdx, endIdx).trim();
            startIdx = endIdx + 1;
            endIdx = result.indexOf('\n', startIdx);
            if (line == null || line.length() == 0)
                continue;
            if (line.startsWith("PID ")) // skip header
                continue;
            String pid = line.substring(0, line.indexOf(' '));
            c = new Command("/usr/bin/pldd", pid);
            c.setStreamHandling(Command.STDOUT, Command.CAPTURE);

            // Check for process that started with collector.
            try {
                CommandHandle handle = cmdAgent.execute(c);
                result = new String(handle.fetchOutput(Command.STDOUT));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error executing pldd", e);
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, "Interrupted executing pldd", e);
            }
            if (result.indexOf("libcollector") > 0)
                pids.add(pid);
        }

        logger.info("Found process(es) started with Collector, pid = "+ pids);

        toolCmd = new ArrayList<String>();
        toolCmd.add("kill");
        toolCmd.add("-PROF");
        toolCmd.addAll(pids);
        super.start();
    }

    public void stop() {
        // We use the same command to start and stop the collection
        // So there is no need to reconstruct the command strings.
        Command c = new Command(toolCmd);
        try {
            cmdAgent.execute(c);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error stopping collector", e);

        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Interrupted stopping collector", e);
        }
        // call GenericTool.stop() to xfer logs
        super.stop();
    }

    // All other methods are inherited from GenericTool
}