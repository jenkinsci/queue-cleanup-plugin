package org.jenkinsci.plugins.queuecleanup;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.PeriodicWork;
import hudson.model.Descriptor;
import hudson.model.Queue;

import java.util.logging.Logger;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

@Extension
public class QueueCleanup extends PeriodicWork implements Describable<QueueCleanup> {
    
    private static final Logger LOGGER = Logger.getLogger(QueueCleanup.class.getName());
    
    private static final Integer QUEUE_CLEANUP_PERIOD;
    
    static {
        Integer period = new Integer(1440); // default 24 hours
        try {
             period = new Integer(System.getProperty(QueueCleanup.class.getName()+".period",period.toString()));
        } catch(NumberFormatException e) {
             LOGGER.warning(String.format("Cannot convert string %s to integer, using dafault %d", System.getProperty(QueueCleanup.class.getName()+".period"), period));
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
        System.out.println("Queue clenaup called! Timeout is " + getDescriptor().getTimeout());
        Queue queue = Jenkins.getInstance().getQueue();
        Queue.Item[] items = queue.getItems();
        long currTime = System.currentTimeMillis();
        long timeoutMillis = getDescriptor().getTimeout()*HOUR;
        String pattern = getDescriptor().getItemPattern();
        for(Queue.Item item : items) {
            long inQueue = currTime - item.getInQueueSince();
            if(inQueue > timeoutMillis && item.task.getDisplayName().matches(pattern)) 
                queue.cancel(item);
        }
    }
    
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<QueueCleanup> {

        private int timeout = 24; //task timeout in the queue - in hours
        private String itemPattern = "*";
        
        public DescriptorImpl() {
            super(QueueCleanup.class);
            load();
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public String getItemPattern() {
            return itemPattern;
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            try {
                this.timeout = new Integer(req.getParameter("queueCleanup.timeout"));
            } catch(NumberFormatException e) {
                throw new FormException("Cannot convert to integer - timeout has to be integer!", "timeout");
            }
            this.itemPattern = req.getParameter("queueCleanup.itemPattern");
            
            save();
            return true;
        }
        
        @Override
        public String getDisplayName() {
            return "Queue cleanup";
        }
    }
    

}
