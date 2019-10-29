/*
 * The MIT License
 *
 * Copyright 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.queuecleanup;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.PeriodicWork;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.util.FormValidation;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
@Symbol("queue-cleanup")
public class QueueCleanup extends PeriodicWork implements Describable<QueueCleanup> {

    private static final String PERIOD_KEY = QueueCleanup.class.getName()+".period";

    private static final Logger LOGGER = Logger.getLogger(QueueCleanup.class.getName());

    /**
     * Check period in hours.
     */
    private static final Float QUEUE_CLEANUP_PERIOD;

    static {
        Float period = 0.017f; // 1.02 minutes
        try {
             period = Float.valueOf(System.getProperty(PERIOD_KEY, period.toString()));
        } catch(NumberFormatException e) {
             LOGGER.warning(String.format("Cannot convert string %s to integer, using dafault %d", System.getProperty(PERIOD_KEY), period));
        } finally {
            QUEUE_CLEANUP_PERIOD = period;
        }
    }

    @Override
    public long getRecurrencePeriod() {
        return ((long)(QUEUE_CLEANUP_PERIOD*Float.valueOf(HOUR)));
    }

    @Override
    protected void doRun() throws Exception {
        String pattern = getDescriptor().getItemPattern();
        final float timeout = getDescriptor().getTimeout();
        long timeoutMillis = (long)(timeout*Float.valueOf(HOUR));

        LOGGER.log(Level.INFO,
                "Queue clenaup started. Max time to wait is {0} hours. Pattern is {1}",
                new String[] {Float.toString(timeout), pattern}
        );

        Queue queue = Jenkins.getActiveInstance().getQueue();
        if (queue != null) {
            Queue.Item[] items = queue.getItems();
            long currTime = System.currentTimeMillis();
            for (Queue.Item item : items) {
                long inQueue = currTime - item.getInQueueSince();
                if (inQueue > timeoutMillis && item.task.getFullDisplayName().matches(pattern)) {
                    queue.cancel(item);
                    LOGGER.log(Level.WARNING,
                            "Item {0} removed from queue after {1}",
                            new String[]{item.task.getFullDisplayName(), Util.getTimeSpanString(inQueue)}
                    );
                }
            }
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<QueueCleanup> {

        private float timeout = 24f;
        private String itemPattern = "a^";

        public DescriptorImpl() {
            super(QueueCleanup.class);
            load();
        }

        public float getTimeout() {

            return (timeout > 0.005) ? timeout : 24f;
        }

        public String getItemPattern() {
            try {

                Pattern.compile(itemPattern);
            } catch (PatternSyntaxException ex) {

                LOGGER.warning("Invalid pattern; To be on the safe side, not matching any jobs");
                return "a^";
            }
            return itemPattern;
        }

        @DataBoundSetter
        public void setItemPattern(String itemPattern) {
            this.itemPattern = itemPattern;
        }

        @DataBoundSetter
        public void setTimeout(float timeout) {
            this.timeout = timeout;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            try {
                this.timeout = Float.parseFloat(formData.getString("timeout"));
            } catch(NumberFormatException e) {
                this.timeout = 24f;
            }
            this.itemPattern = formData.getString("itemPattern");

            save();
            return true;
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckItemPattern(@QueryParameter String itemPattern) {
            try {

                Pattern.compile(itemPattern);
                return FormValidation.ok();
            } catch (PatternSyntaxException ex) {

                // Wrap exception message to <pre> tag as the error messages
                // uses position indicator (^) prefixed with spaces which work
                // with monospace fonts only.
                return FormValidation.errorWithMarkup("Not a regular expression: <pre>" + ex.getMessage() + "</pre>");
            }
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckTimeout(@QueryParameter String timeout) {
            try {

                if (Float.parseFloat(timeout) > 0.005) return FormValidation.ok();
            } catch (NumberFormatException e) {
                // Fallthrough
            }

            return FormValidation.error("Must be a floating point number greater than 0.005");
        }

        @Override
        public String getDisplayName() {
            return "Queue cleanup";
        }
    }
}
