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
package org.rioproject.examples.hospital;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for the right hand side execution
 */
public class RuleServiceHelper {
    private static final Logger logger = Logger.getLogger(RuleServiceHelper.class.getName());

    public static void patientNotify(Patient p) {

    }

    public static void doctorNotify(List<Doctor> doctors) {
        try {
            for(Doctor d : doctors) {
                try {
                    if(d.getStatus().equals(Doctor.Status.ON_CALL)) {
                        if(logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "Setting {0} to be ON_DUTY", d.getName());
                        }
                        d.onDuty();
                        break;
                    }
                } catch(Exception e) {
                    logger.log(Level.WARNING, "Trying to set a Doctor to be ON_CALL", e);
                }
            }
        } catch(java.lang.ClassCastException e) {
            logger.log(Level.WARNING, "Type cast issue trying to access List of Doctors", e);
        }
    }
}
