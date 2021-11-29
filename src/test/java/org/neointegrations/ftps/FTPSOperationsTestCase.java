package org.neointegrations.ftps;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class FTPSOperationsTestCase extends MuleArtifactFunctionalTestCase {
    private static final Logger _logger = LoggerFactory.getLogger(FTPSOperationsTestCase.class);

    /**
     * Specifies the mule config xml with the flows that are going to be executed in the tests, this file lives in the test resources.
     */
    @Override
    protected String getConfigFile() {
        return "test-mule-config.xml";
    }


    @Before
    public void executeInit() throws Exception {
        _logger.info("Calling executeInit...");
        flowRunner("init-flow").run();
    }
    @After
    public void executeDestroy() throws Exception {
        _logger.info("Calling executeDestroy...");
        flowRunner("destroy-flow").run();
    }

    @Test
    public void executeRead() throws Exception {
        _logger.info("Calling executeRead...");
        Boolean payloadValue = ((Boolean) flowRunner("read-flow").run()
                .getMessage()
                .getPayload()
                .getValue());
        assertThat(payloadValue, is(true));
    }

    @Test
    public void executeWrite() throws Exception {
        _logger.info("Calling executeWrite...");

        Boolean payloadValue = ((Boolean) flowRunner("write-flow").run()
                .getMessage()
                .getPayload()
                .getValue());
        assertThat(payloadValue, is(true));
    }

    @Test
    public void executeList() throws Exception {
        _logger.info("Calling executeList...");
        Boolean payloadValue = ((Boolean) flowRunner("list-flow").run()
                .getMessage()
                .getPayload()
                .getValue());
        assertThat(payloadValue, is(true));
    }

    @Test
    public void executeRemoveDir() throws Exception {
        _logger.info("Calling executeRemoveDir...");
        Boolean payloadValue = ((Boolean) flowRunner("rmdir-flow").run()
                .getMessage()
                .getPayload()
                .getValue());
        assertThat(payloadValue, is(true));
    }
    @Test
    public void executeRemoveFile() throws Exception {
        _logger.info("Calling executeRemoveFile...");
        Boolean payloadValue = ((Boolean) flowRunner("rm-flow").run()
                .getMessage()
                .getPayload()
                .getValue());
        assertThat(payloadValue, is(true));
    }
}
