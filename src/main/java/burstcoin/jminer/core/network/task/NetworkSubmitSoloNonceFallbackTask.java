/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 by luxe - https://github.com/de-luxe - BURST-LUXE-RED2-G6JW-H4HG5
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package burstcoin.jminer.core.network.task;

import burstcoin.jminer.core.network.model.SubmitResultResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The type Network submit solo nonce task.
 */
@Component
@Scope("prototype")
public class NetworkSubmitSoloNonceFallbackTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkSubmitSoloNonceTask.class);

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String soloServer;
    private String passPhrase;
    private BigInteger nonce;

    private long calculatedDeadline;
    private long delay;
    private long connectionTimeout;

    public void init(String soloServer, long delay, String passPhrase, long connectionTimeout, BigInteger nonce, long calculatedDeadline) {
        this.delay = delay;
        this.connectionTimeout = connectionTimeout;

        this.soloServer = soloServer;
        this.passPhrase = passPhrase;
        this.nonce = nonce;

        this.calculatedDeadline = calculatedDeadline;
    }

    @Override
    public void run() {
        // wait delay
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            ContentResponse response = httpClient.POST(soloServer + "/burst")
                    .param("requestType", "submitNonce")
                    .param("secretPhrase", passPhrase)
                    .param("nonce", nonce.toString())
                    .timeout(connectionTimeout, TimeUnit.MILLISECONDS)
                    .send();

            SubmitResultResponse result = objectMapper.readValue(response.getContentAsString(), SubmitResultResponse.class);

            if (result.getResult().equals("success")) {
                if (calculatedDeadline == result.getDeadline()) {
                    LOG.info("recommit dl '" + result.getDeadline() + "' after '" + delay / 1000 + " sec.'");
                }
            } else {
                LOG.warn("Error: Recommit solo nonce not successful: " + response.getContentAsString());
            }
        } catch (TimeoutException timeoutException) {
            LOG.warn("Unable to recommit solo nonce, caused by connectionTimeout, currently '" + (connectionTimeout / 1000) + " sec.' try increasing it!");
        } catch (Exception e) {
            LOG.warn("Error: Failed to recommit solo nonce: " + e.getMessage());
        }
    }
}
