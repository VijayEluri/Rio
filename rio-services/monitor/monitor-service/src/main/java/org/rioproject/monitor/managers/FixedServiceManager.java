/*
 * Copyright to the original author or authors.
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
package org.rioproject.monitor.managers;

import org.rioproject.jsb.ServiceElementUtil;
import org.rioproject.monitor.*;
import org.rioproject.monitor.tasks.ProvisionTask;
import org.rioproject.monitor.util.LoggingUtil;
import org.rioproject.resources.servicecore.ServiceResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages services of type fixed.
 */
public class FixedServiceManager extends PendingServiceElementManager {
    private final List<ServiceResource> inProcessResource = Collections.synchronizedList(new ArrayList<ServiceResource>());
    private final ServiceProvisionContext context;
    private final Logger logger = Logger.getLogger("org.rioproject.monitor.provision");

    /**
     * Create a FixedServiceManager
     *
     * @param context The ServiceProvisionContext
     */
    public FixedServiceManager(final ServiceProvisionContext context) {
        super("Fixed-Service TestManager");
        this.context = context;
    }        

    /**
     * Process the entire Fixed collection over all known ServiceResource
     * instances
     */
    public void process() {
        ServiceResource[] resources = context.getSelector().getServiceResources();
        if(logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("%s processing %d resources", getType(), resources.length));
        }
        for (ServiceResource resource : resources)
            process(resource);
    }

    /**
     * Used to deploy a specific ProvisionRequest to all ServiceResource
     * instances that support the requirements of the ServiceElement
     *
     * @param request The ProvisionRequest to deploy
     */
    public void deploy(final ProvisionRequest request) {
        try {
            if (logger.isLoggable(Level.FINER))
                logger.log(Level.FINER,  "Deploy [" + LoggingUtil.getLoggingName(request) + "]");
            ServiceResource[] resources = context.getSelector().getServiceResources(request.getServiceElement());
            if(logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("%s processing %d resources", getType(), resources.length));
            }
            /* Filter out isolated associations and max per machine levels
             * set at the physical level */
            resources = context.getSelector().filterMachineBoundaries(request.getServiceElement(), resources);
            if (resources.length > 0)
                resources = context.getSelector().filterIsolated(request.getServiceElement(), resources);

            if (resources.length > 0) {
                for (ServiceResource resource : resources) {
                    try {
                        inProcessResource.add(resource);
                        doDeploy(resource, request);
                    } finally {
                        inProcessResource.remove(resource);
                    }
                }                
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "FixedServiceManager deployNew", t);
        }
    }

    /* Process the fixed services collection with an input ServiceResource
     */
    public void process(final ServiceResource resource) {
        if (resource == null)
            throw new IllegalArgumentException("ServiceResource is null");
        if (inProcessResource.contains(resource))
            return;
        inProcessResource.add(resource);
        InstantiatorResource ir = (InstantiatorResource) resource.getResource();
        if(logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("%s processing %s", getType(), ir.getHostAddress()));
        }
        try {
            if(getSize() == 0)
                return;
            if(logger.isLoggable(Level.FINEST)) {
                dumpCollection();
            }
            /* Now traverse the collection for everything else, skipping
             * the service elements that have been processed */
            synchronized (collection) {
                Set<Key> requests = collection.keySet();
                for (Key requestKey : requests) {
                    ProvisionRequest request = collection.get(requestKey);
                    try {
                        if (clearedMaxPerMachineAndIsolated(request, ir.getHostAddress()) &&
                            ir.canProvision(request.getServiceElement()))
                            doDeploy(resource, request);
                    } catch (ProvisionException e) {
                        request.setType(ProvisionRequest.Type.UNINSTANTIABLE);
                        if (logger.isLoggable(Level.FINE))
                            logger.fine(String.format("Service [%s] is un-instantiable, do not resubmit",
                                                      LoggingUtil.getLoggingName(request)));
                    }
                }
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, "Processing FixedService Collection", t);
        } finally {
            inProcessResource.remove(resource);
            ir.setDynamicEnabledOn();
        }
    }

    /**
     * Do the deployment for a fixed service
     *
     * @param resource The ServiceResource
     * @param request  The ProvisionRequest
     * @return The number deployed
     * @throws Exception If there are errors
     */
    private int doDeploy(final ServiceResource resource, final ProvisionRequest request) throws Exception {
        return (doDeploy(resource, request, true));
    }

    /**
     * Do the deployment for a fixed service
     *
     * @param resource         The ServiceResource
     * @param req              The ProvisionRequest
     * @param changeInstanceID If true, increment the instanceID
     * @return The number deployed
     * @throws Exception If there are errors
     */
    private int doDeploy(final ServiceResource resource, final ProvisionRequest req, final boolean changeInstanceID)
        throws Exception {
        int numAllowed = getNumAllowed(resource, req);
        if (numAllowed > 0) {
            long currentID = req.getServiceElement().getServiceBeanConfig().getInstanceID();
            StringBuilder b = new StringBuilder();
            b.append("doDeploy ").append(numAllowed).append(" [")
                .append(LoggingUtil.getLoggingName(req)).append("] instances");

            for (int i = 0; i < numAllowed; i++) {
                ProvisionRequest request = ProvisionRequest.copy(req);
                ServiceProvisionContext spc = getServiceProvisionContext();
                long nextID = (changeInstanceID ?
                               request.getInstanceIDMgr().getNextInstanceID() : currentID);
                request.setServiceElement(ServiceElementUtil.prepareInstanceID(request.getServiceElement(),
                                                                               true,
                                                                               nextID));

                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest(String.format("[%s] instanceID : %d",
                                                LoggingUtil.getLoggingName(request),
                                                request.getServiceElement().getServiceBeanConfig().getInstanceID()));
                }
                spc.getInProcess().add(request.getServiceElement());
                spc.setProvisionRequest(request);
                spc.setServiceResource(resource);
                spc.getProvisioningPool().execute(new ProvisionTask(spc, null));
            }
            if (logger.isLoggable(Level.FINER))
                logger.finer(b.toString());
        }
        return (numAllowed);
    }

    /*
    * Determine how many services can be allocated based on how many
    * are already on the resource minus the number planned
    */
    private int getNumAllowed(final ServiceResource resource, final ProvisionRequest request) {
        /* Filter out isolated associations and max per machine levels set
         * at the physical level */
        InstantiatorResource ir = (InstantiatorResource) resource.getResource();
        if (!clearedMaxPerMachineAndIsolated(request, ir.getHostAddress()))
            return 0;

        int planned = request.getServiceElement().getPlanned();
        int actual = ir.getServiceElementCount(request.getServiceElement());
        int numAllowed = planned - actual;
        if (request.getServiceElement().getMaxPerMachine() != -1 &&
            request.getServiceElement().getMaxPerMachine() < numAllowed)
            numAllowed = request.getServiceElement().getMaxPerMachine();
        if(logger.isLoggable(Level.FINER))
            logger.finer(String.format("Cybernode at %s has %d, can accommodate %d",
                                       ir.getHostAddress(), actual, numAllowed));
        return (numAllowed);
    }

    /*
    * Filter out isolated associations and max per machine levels set
    * at the physical level
    */
    private boolean clearedMaxPerMachineAndIsolated(final ProvisionRequest request, final String hostAddress) {
        /* Filter out isolated associations and max per machine levels set
         * at the physical level */
        ServiceResource[] sr =
            context.getSelector().filterMachineBoundaries(request.getServiceElement(),
                                                          context.getSelector().getServiceResources(hostAddress, true));
        if (sr.length == 0)
            return false;
        sr = context.getSelector().filterIsolated(request.getServiceElement(), sr);
        return sr.length != 0;
    }

    public ServiceProvisionContext getServiceProvisionContext() {
        return new ServiceProvisionContext(context.getSelector(),
                                           context.getProvisioningPool(),
                                           context.getInProcess(),
                                           context.getEventSource(),
                                           context.getWatch(),
                                           context.getDispatcher(),
                                           context.getProvisionFailurePool(),
                                           context.getFailureHandler(),
                                           context.getServiceProvisionEventSequenceNumber());
    }
}
