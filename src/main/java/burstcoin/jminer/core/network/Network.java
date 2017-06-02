/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 by luxe - https://github.com/de-luxe - BURST-LUXE-RED2-G6JW-H4HG5
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

package burstcoin.jminer.core.network;

import burstcoin.jminer.core.CoreProperties;
import burstcoin.jminer.core.network.event.NetworkStateChangeEvent;
import burstcoin.jminer.core.network.task.NetworkRequestLastWinnerTask;
import burstcoin.jminer.core.network.task.NetworkRequestMiningInfoTask;
import burstcoin.jminer.core.network.task.NetworkRequestPoolInfoTask;
import burstcoin.jminer.core.network.task.NetworkRequestTriggerServerTask;
import burstcoin.jminer.core.network.task.NetworkSubmitPoolNonceTask;
import burstcoin.jminer.core.network.task.NetworkSubmitSoloNonceFallbackTask;
import burstcoin.jminer.core.network.task.NetworkSubmitSoloNonceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Timer;
import java.util.TimerTask;

@Component
@Scope("singleton")
public class Network {
    private static final Logger LOG = LoggerFactory.getLogger(Network.class);

    @Autowired
    private ApplicationContext context;

    @Autowired
    @Qualifier(value = "networkPool")
    private SimpleAsyncTaskExecutor networkPool;

    private String numericAccountId;
    private boolean poolMining;

    private String poolServer;
    private String walletServer;

    private String soloServer;
    private String passPhrase;
    private long defaultTargetDeadline;

    private long connectionTimeout;

    private int winnerRetriesOnAsync;
    private long winnerRetryIntervalInMs;

    private long blockNumber;
    private Timer timer;

    @PostConstruct
    protected void postConstruct() {
        timer = new Timer();
        poolMining = CoreProperties.isPoolMining();
        if (poolMining) {
            String poolServer = CoreProperties.getPoolServer();
            String numericAccountId = CoreProperties.getNumericAccountId();

            if (!StringUtils.isEmpty(poolServer) && !StringUtils.isEmpty(numericAccountId)) {
                this.poolServer = CoreProperties.getPoolServer();
                this.numericAccountId = CoreProperties.getNumericAccountId();

                this.walletServer = CoreProperties.getWalletServer();
                this.winnerRetriesOnAsync = CoreProperties.getWinnerRetriesOnAsync();
                this.winnerRetryIntervalInMs = CoreProperties.getWinnerRetryIntervalInMs();
            } else {
                LOG.error("init pool network failed!");
                LOG.error("jminer.properties: 'poolServer' or 'numericAccountId' is missing?!");
            }
        } else {
            String soloServer = CoreProperties.getSoloServer();
            String passPhrase = CoreProperties.getPassPhrase();

            if (!StringUtils.isEmpty(soloServer) && !StringUtils.isEmpty(passPhrase)) {
                this.soloServer = soloServer;
                this.passPhrase = passPhrase;
            } else {
                LOG.error("init solo network failed!");
                LOG.error("jminer.properties: 'soloServer' or 'passPhrase' is missing?!");
            }
        }
        this.defaultTargetDeadline = CoreProperties.getTargetDeadline();
        this.connectionTimeout = CoreProperties.getConnectionTimeout();
    }

    @EventListener
    public void handleMessage(NetworkStateChangeEvent event) {
        blockNumber = event.getBlockNumber();
    }

    public void checkNetworkState() {
        String server = poolMining ? poolServer : soloServer;
        if (!StringUtils.isEmpty(server)) {
            NetworkRequestMiningInfoTask networkRequestMiningInfoTask = context.getBean(NetworkRequestMiningInfoTask.class);
            networkRequestMiningInfoTask.init(server, blockNumber, poolMining, connectionTimeout, defaultTargetDeadline);
            networkPool.execute(networkRequestMiningInfoTask);
        }
    }

    // ensure wallet-server does not stuck on solo mining
    public void triggerServer() {
        if (!StringUtils.isEmpty(soloServer)) {
            NetworkRequestTriggerServerTask networkRequestTriggerServerTask = context.getBean(NetworkRequestTriggerServerTask.class);
            networkRequestTriggerServerTask.init(soloServer, numericAccountId, connectionTimeout);
            networkPool.execute(networkRequestTriggerServerTask);
        }
    }

    public void checkLastWinner(long blockNumber) {
        // find winner of lastBlock on new round, if server available
        String server = !poolMining ? soloServer : walletServer != null ? walletServer : null;
        if (!StringUtils.isEmpty(server)) {
            NetworkRequestLastWinnerTask networkRequestLastWinnerTask = context.getBean(NetworkRequestLastWinnerTask.class);
            networkRequestLastWinnerTask.init(server, blockNumber, connectionTimeout, winnerRetriesOnAsync, winnerRetryIntervalInMs);
            networkPool.execute(networkRequestLastWinnerTask);
        }
    }

    public void checkPoolInfo() {
        if (CoreProperties.isPoolMining() && walletServer != null) {
            NetworkRequestPoolInfoTask networkRequestPoolInfoTask = context.getBean(NetworkRequestPoolInfoTask.class);
            networkRequestPoolInfoTask.init(walletServer, numericAccountId, connectionTimeout);
            networkPool.execute(networkRequestPoolInfoTask);
        }
    }

    public void commitResult(long blockNumber, long calculatedDeadline, BigInteger nonce, BigInteger chunkPartStartNonce, long totalCapacity, BigInteger result) {
        if (poolMining) {
            NetworkSubmitPoolNonceTask networkSubmitPoolNonceTask = context.getBean(NetworkSubmitPoolNonceTask.class);
            networkSubmitPoolNonceTask.init(blockNumber, numericAccountId, poolServer, connectionTimeout, nonce,
                    chunkPartStartNonce, calculatedDeadline, totalCapacity, result);
            networkPool.execute(networkSubmitPoolNonceTask);
        } else {
            NetworkSubmitSoloNonceTask networkSubmitSoloNonceTask = context.getBean(NetworkSubmitSoloNonceTask.class);
            networkSubmitSoloNonceTask.init(blockNumber, passPhrase, soloServer, connectionTimeout, nonce, chunkPartStartNonce, calculatedDeadline, result);
            networkPool.execute(networkSubmitSoloNonceTask);

            if (CoreProperties.isRecommitDeadlines() && calculatedDeadline < 1200) {
                // recommit #1 after 5 sec.
                NetworkSubmitSoloNonceFallbackTask networkSubmitSoloNonceRecommitTask = context.getBean(NetworkSubmitSoloNonceFallbackTask.class);
                networkSubmitSoloNonceRecommitTask.init(soloServer, 5000L, passPhrase, connectionTimeout, nonce, calculatedDeadline);
                networkPool.execute(networkSubmitSoloNonceRecommitTask);

                // recommit #2 after 10 sec.
                NetworkSubmitSoloNonceFallbackTask networkSubmitSoloNonceRecommitTask2 = context.getBean(NetworkSubmitSoloNonceFallbackTask.class);
                networkSubmitSoloNonceRecommitTask2.init(soloServer, 10000L, passPhrase, connectionTimeout, nonce, calculatedDeadline);
                networkPool.execute(networkSubmitSoloNonceRecommitTask2);

                // recommit #3 after 15 sec.
                NetworkSubmitSoloNonceFallbackTask networkSubmitSoloNonceRecommitTask3 = context.getBean(NetworkSubmitSoloNonceFallbackTask.class);
                networkSubmitSoloNonceRecommitTask3.init(soloServer, 15000L, passPhrase, connectionTimeout, nonce, calculatedDeadline);
                networkPool.execute(networkSubmitSoloNonceRecommitTask3);
            }
        }
    }

    public void startMining() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkNetworkState();
            }
        }, 100, CoreProperties.getRefreshInterval());

        // on solo mining
        if (!CoreProperties.isPoolMining() && CoreProperties.isTriggerServer()) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    triggerServer();
                }
            }, 5000, 25000);
        }
    }
}
