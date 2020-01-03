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
    private static final Integer QUEUE_CLEANUP_PERIOD;

    static {
        Integer period = Integer.valueOf(1);
        try {
             period = Integer.valueOf(System.getProperty(PERIOD_KEY, period.toString()));
        } catch(NumberFormatException e) {
             LOGGER.warning(String.format("Cannot convert string %s to integer, using dafault %d", System.getProperty(PERIOD_KEY), period));
        } finally {
            QUEUE_CLEANUP_PERIOD = period;
        }
    }

    @Override
    public long getRecurrencePeriod() {
        return QUEUE_CLEANUP_PERIOD*HOUR;
    }

    @Override
    protected void doRun() throws Exception {
        String regex = getDescriptor().getItemRegex();
        final int timeout = getDescriptor().getTimeout();
        long timeoutMillis = timeout*HOUR;

        LOGGER.log(Level.INFO,
                "Queue clenaup started. Max time to wait is {0} hours. Regex is {1}",
                new String[] {Integer.toString(timeout), regex}
        );

        Queue queue = Jenkins.getActiveInstance().getQueue();
        if (queue != null) {
            Queue.Item[] items = queue.getItems();
            long currTime = System.currentTimeMillis();
            for (Queue.Item item : items) {
                long inQueue = currTime - item.getInQueueSince();
                if (inQueue > timeoutMillis && item.task.getDisplayName().matches(regex)) {
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

        private int timeout = 24;
        private String itemRegex = ".*";

        public DescriptorImpl() {
            super(QueueCleanup.class);
            load();
        }

        public int getTimeout() {

            return (timeout < 1) ? 24 : timeout;
        }

        public String getItemRegex() {
            return itemRegex;
        }

        @DataBoundSetter
        public void setItemRegex(String itemRegex) {
		try {

                Pattern.compile(itemRegex);
		this.itemRegex = itemRegex;
            } catch (PatternSyntaxException ex) {
			//sets nothing
            }
            
        }

        @DataBoundSetter
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            try {
                this.timeout = Integer.parseInt(formData.getString("timeout"));
            } catch(NumberFormatException e) {
                this.timeout = 24;
            }
            this.itemRegex = formData.getString("itemRegex");

            save();
            return true;
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckItemRegex(@QueryParameter String itemRegex) {
            try {

                Pattern.compile(itemRegex);
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

                if (Integer.parseInt(timeout) > 0) return FormValidation.ok();
            } catch (NumberFormatException e) {
                // Fallthrough
            }

            return FormValidation.error("Not a positive number");
        }

        @Override
        public String getDisplayName() {
            return "Queue cleanup";
        }
    }
}
