/*
 * Copyright 2008 to the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.impl.system.measurable.cpu;

import org.rioproject.impl.system.measurable.MeasurableMonitor;
import org.rioproject.impl.system.measurable.SigarHelper;
import org.rioproject.system.measurable.cpu.CpuUtilization;
import org.rioproject.watch.ThresholdValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * CPU monitor that obtains system CPU utilization. This utility uses either
 * Hyperic SIGAR, JDK 1.6 or operating system utilities to obtain CPU
 * utilization for the machine. Hyperic SIGAR is preferred. If not available
 * JDK 1.6 facilities will be used. If neither of these approaches work
 * operating system utilities will be used (depending on the OS) to obtain
 * CPU utilization.
 *
 * @author Dennis Reedy
 */
public class SystemCPUHandler implements MeasurableMonitor<CpuUtilization> {
    private String id;
    private ThresholdValues tVals;
    private SigarHelper sigar;
    private OperatingSystemMXBean opSysMBean = null;
    private static Logger logger = LoggerFactory.getLogger(SystemCPUHandler.class.getPackage().getName());

    public SystemCPUHandler() {
        sigar = SigarHelper.getInstance();
        if(sigar==null) {
            opSysMBean = ManagementFactory.getOperatingSystemMXBean();
        } else {
            logger.debug("Using SIGAR for CPU utilization");
        }
    }

    public void setID(String id) {
        this.id = id;
    }

    public void setThresholdValues(ThresholdValues tVals) {
        this.tVals = tVals;
    }

    public CpuUtilization getMeasuredResource() {
        CpuUtilization util;
        if(sigar!=null) {
            util = getSigarCpuUtilization();
        } else {
            util = getJmxCpuUtilization();
        }
        return util;
    }

    public void terminate() {
    }

    private CpuUtilization getSigarCpuUtilization() {
        return new CpuUtilization(id,
                                   sigar.getSystemCpuPercentage(),
                                   sigar.getUserCpuPercentage(),
                                   sigar.getLoadAverage(),
                                   Runtime.getRuntime().availableProcessors(),
                                   tVals);
    }

    private CpuUtilization getJmxCpuUtilization() {
        double cpuUtilization = 0;
        if(opSysMBean instanceof com.sun.management.OperatingSystemMXBean) {
            cpuUtilization = ((com.sun.management.OperatingSystemMXBean)opSysMBean).getSystemCpuLoad();
            cpuUtilization = cpuUtilization>0?cpuUtilization:0;
        } else {
            cpuUtilization = opSysMBean.getSystemLoadAverage();
            cpuUtilization = cpuUtilization>0?cpuUtilization/100:0;
        }
        return new CpuUtilization(id, cpuUtilization, tVals);
    }
}

