package org.jenkinsci.plugins.queuecleanup;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigAsCodeTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void should_support_configuration_as_code() throws Exception {
        ConfigurationAsCode.get().configure(ConfigAsCodeTest.class.getResource("configuration-as-code.yml").toString());
        Assert.assertEquals(((QueueCleanup.DescriptorImpl)r.jenkins.getDescriptor(QueueCleanup.class)).getTimeout(),123);
        Assert.assertEquals(((QueueCleanup.DescriptorImpl)r.jenkins.getDescriptor(QueueCleanup.class)).getItemPattern(),"^abc.*");
    }
}